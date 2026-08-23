package com.owlmedia.racecontrol.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Flexible ISO-8601 parsing, matching the iOS `ISO8601.flexible` helper.
 *
 * The backend emits several shapes depending on which upstream produced the
 * value: offset datetimes with and without fractional seconds, naive datetimes
 * (treated as UTC, as on iOS), and bare dates.
 */
object FlexibleDate {

    fun parse(value: String?): ZonedDateTime? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()

        // Offset / zoned, with or without fractional seconds.
        attempt { OffsetDateTime.parse(trimmed).toZonedDateTime() }?.let { return it }
        attempt { Instant.parse(trimmed).atZone(ZoneId.systemDefault()) }?.let { return it }
        // Naive datetime - the iOS build interprets these as UTC, so we match.
        attempt { LocalDateTime.parse(trimmed).atZone(ZoneId.of("UTC")) }?.let { return it }
        // Bare date.
        attempt { LocalDate.parse(trimmed).atStartOfDay(ZoneId.of("UTC")) }?.let { return it }
        return null
    }

    private inline fun attempt(block: () -> ZonedDateTime): ZonedDateTime? =
        try {
            block()
        } catch (e: DateTimeParseException) {
            null
        }
}

private val dateMedium: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dateLong: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
private val timeShort: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** "2:24:07 PM GMT+1": pattern "O" is a localized zone offset. */
private val timeWithZone: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm:ss a O", Locale.getDefault())

/** "12 Jul 2026": the abbreviated style used in race rows. */
fun ZonedDateTime.formatDateMedium(): String =
    withZoneSameInstant(ZoneId.systemDefault()).format(dateMedium)

/** "12 July 2026": the long style used in the race detail header. */
fun ZonedDateTime.formatDateLong(): String =
    withZoneSameInstant(ZoneId.systemDefault()).format(dateLong)

/** "14:00" in the user's locale and timezone. */
fun ZonedDateTime.formatTime(): String =
    withZoneSameInstant(ZoneId.systemDefault()).format(timeShort)

/**
 * "2:24:07 PM GMT+1": clock time with the offset spelled out, so it's never
 * ambiguous which zone is being shown. Used for race-control timestamps:
 * they come from the timing feed in UTC, but a race can be happening in any
 * timezone and the viewer is in another again.
 */
fun ZonedDateTime.formatTimeWithZone(): String =
    withZoneSameInstant(ZoneId.systemDefault()).format(timeWithZone)

/** "Sat 14:00": used in weekend schedules and day-before reminders. */
fun ZonedDateTime.formatWeekdayTime(): String {
    val local = withZoneSameInstant(ZoneId.systemDefault())
    val weekday = local.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    return "$weekday ${local.format(timeShort)}"
}

/**
 * Lap and gap formatting, ported from the iOS `ResultRow.format(ms:leading:)`.
 *
 * `leading = true` gives an absolute time ("1:32.145"); `false` gives a gap
 * with all minutes folded into seconds ("+63.221"), which is how F1 timing
 * screens present it.
 */
object LapTimeFormat {

    fun format(ms: Int, leading: Boolean): String {
        val safe = abs(ms)
        val minutes = safe / 60_000
        val seconds = (safe % 60_000) / 1000
        val millis = safe % 1000
        return if (leading && minutes > 0) {
            String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis)
        } else {
            String.format(Locale.US, "%d.%03d", seconds + minutes * 60, millis)
        }
    }

    /** Seconds as a lap time, used for chart axis labels. */
    fun fromSeconds(seconds: Double): String =
        format((seconds * 1000).toInt(), leading = true)

    /** "+1.234" gap string; empty when there is no reference. */
    fun gap(ms: Int?, referenceMs: Int?): String? {
        if (ms == null || referenceMs == null) return null
        return "+" + format(ms - referenceMs, leading = false)
    }
}

/** Formats a Double without a trailing ".0": the iOS `numberLabel` behaviour. */
fun Double.pointsLabel(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
