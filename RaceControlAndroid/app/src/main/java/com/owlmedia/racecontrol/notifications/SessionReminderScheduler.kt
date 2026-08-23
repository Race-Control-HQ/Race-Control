package com.owlmedia.racecontrol.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.owlmedia.racecontrol.core.util.formatTime
import com.owlmedia.racecontrol.core.util.formatWeekdayTime
import com.owlmedia.racecontrol.data.local.SettingsDataStore
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Schedules on-device reminders for F1 sessions.
 *
 * The iOS app schedules up to 60 local notifications and rebuilds the window on
 * each launch. Android differs in three ways that matter:
 *
 *  - Alarms do **not** survive a reboot, so [BootCompletedReceiver] re-runs this.
 *  - Users may not open the app for weeks, so a daily worker also re-runs it.
 *  - Exact alarms need a permission on API 31+; without it we fall back to
 *    inexact, which may fire a few minutes late but still fires.
 */
@Singleton
class SessionReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RaceControlRepository,
    private val settings: SettingsDataStore,
    private val notifier: ReminderNotifier,
) {
    private companion object {
        const val TAG = "ReminderScheduler"
        /** Bounded so we never monopolise the device's alarm slots. */
        const val MAX_ALARMS = 50
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /** Rebuilds the whole reminder window from scratch. */
    suspend fun refresh() {
        val current = settings.settings.first()
        cancelAll()
        if (!current.notificationsEnabled || !notifier.hasPermission()) return

        val year = Year.now().value
        val events = repository.schedule(year).getOrElse {
            Log.w(TAG, "Schedule unavailable, keeping reminders cleared", it)
            return
        }

        val now = ZonedDateTime.now()
        val candidates = mutableListOf<Reminder>()

        events.forEach { event ->
            event.sessions.forEach { session ->
                if (!current.wantsSession(session.identifier)) return@forEach
                val start = session.parsedDate ?: return@forEach
                if (!start.isAfter(now)) return@forEach

                val sessionKey = "${event.year}-${event.round}-${session.identifier ?: "?"}"

                if (current.notify15Min) {
                    val fire = start.minusMinutes(15)
                    if (fire.isAfter(now)) {
                        candidates += Reminder(
                            id = sessionKey.hashCode() + 1,
                            fireAt = fire,
                            title = "${session.reminderName} starting soon",
                            body = "${event.displayName} · starts ${start.formatTime()} (15 min)",
                            year = event.year,
                            round = event.round,
                        )
                    }
                }
                if (current.notify1Hour) {
                    val fire = start.minusHours(1)
                    if (fire.isAfter(now)) {
                        candidates += Reminder(
                            id = sessionKey.hashCode() + 2,
                            fireAt = fire,
                            title = "${session.reminderName} in 1 hour",
                            body = "${event.displayName} · starts ${start.formatTime()}",
                            year = event.year,
                            round = event.round,
                        )
                    }
                }
                if (current.notifyDayBefore) {
                    // 09:00 local on the day before, matching iOS.
                    val fire = start
                        .withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDate()
                        .minusDays(1)
                        .atTime(LocalTime.of(9, 0))
                        .atZone(ZoneId.systemDefault())
                    if (fire.isAfter(now)) {
                        candidates += Reminder(
                            id = sessionKey.hashCode() + 3,
                            fireAt = fire,
                            title = "${session.reminderName} tomorrow",
                            body = "${event.displayName} · ${start.formatWeekdayTime()}",
                            year = event.year,
                            round = event.round,
                        )
                    }
                }
            }
        }

        candidates
            .sortedBy { it.fireAt }
            .take(MAX_ALARMS)
            .forEach(::schedule)

        storeScheduledIds(candidates.sortedBy { it.fireAt }.take(MAX_ALARMS).map { it.id })
    }

    private fun schedule(reminder: Reminder) {
        val manager = alarmManager ?: return
        val pendingIntent = pendingIntentFor(reminder)
        val triggerAt = reminder.fireAt.toInstant().toEpochMilli()

        try {
            if (canScheduleExact()) {
                // A session reminder is useless if it lands after lights out,
                // which is exactly the case exact alarms exist for.
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            // The permission can be revoked between the check and the call.
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelAll() {
        val manager = alarmManager ?: return
        loadScheduledIds().forEach { id ->
            val intent = Intent(context, ReminderAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                manager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        storeScheduledIds(emptyList())
    }

    private fun pendingIntentFor(reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_ID, reminder.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, reminder.title)
            putExtra(ReminderAlarmReceiver.EXTRA_BODY, reminder.body)
            putExtra(ReminderAlarmReceiver.EXTRA_YEAR, reminder.year)
            putExtra(ReminderAlarmReceiver.EXTRA_ROUND, reminder.round)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Alarm ids are tracked in plain prefs so they can be cancelled later:
     * AlarmManager offers no way to enumerate what an app has scheduled.
     */
    private fun storeScheduledIds(ids: List<Int>) {
        context.getSharedPreferences("reminder_ids", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("ids", ids.map { it.toString() }.toSet())
            .apply()
    }

    private fun loadScheduledIds(): List<Int> =
        context.getSharedPreferences("reminder_ids", Context.MODE_PRIVATE)
            .getStringSet("ids", emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }

    private data class Reminder(
        val id: Int,
        val fireAt: ZonedDateTime,
        val title: String,
        val body: String,
        val year: Int,
        val round: Int,
    )
}
