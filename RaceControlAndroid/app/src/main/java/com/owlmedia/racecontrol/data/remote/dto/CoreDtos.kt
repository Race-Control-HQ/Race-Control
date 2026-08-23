package com.owlmedia.racecontrol.data.remote.dto

import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.util.FlexibleDate
import com.owlmedia.racecontrol.core.util.LapTimeFormat
import com.owlmedia.racecontrol.core.util.pointsLabel
import com.owlmedia.racecontrol.data.remote.JsonValue
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/*
 * The backend serialises with camelCase keys (it was written against the iOS
 * Codable models), so property names map directly with no naming strategy.
 * Everything is nullable with a default: FastF1 omits fields freely depending
 * on the season and session, and a missing key must never be fatal.
 */

/* ------------------------------------------------------------------ schedule */

@Serializable
data class RaceEventDto(
    val round: Int = 0,
    val name: String? = null,
    val officialName: String? = null,
    val country: String? = null,
    val location: String? = null,
    val date: String? = null,
    val format: String? = null,
    val sessions: List<EventSessionDto> = emptyList(),
    val completed: Boolean = false,
    val year: Int = 0,
) {
    val displayName: String get() = name ?: "Round $round"

    val isSprintWeekend: Boolean
        get() = format.orEmpty().contains("sprint", ignoreCase = true)

    val parsedDate: ZonedDateTime? get() = FlexibleDate.parse(date)

    /** Stable identity for lists and navigation. */
    val id: String get() = "$year-$round"
}

@Serializable
data class EventSessionDto(
    val name: String? = null,
    val date: String? = null,
    val identifier: String? = null,
) {
    val parsedDate: ZonedDateTime? get() = FlexibleDate.parse(date)

    /** Friendly name used in reminders and the weekend schedule. */
    val reminderName: String
        get() = when (identifier) {
            "FP1" -> "Practice 1"
            "FP2" -> "Practice 2"
            "FP3" -> "Practice 3"
            "Q" -> "Qualifying"
            "S" -> "Sprint"
            "SQ", "SS" -> "Sprint Qualifying"
            "R" -> "Race"
            else -> name ?: "Session"
        }
}

/* ------------------------------------------------------------------- results */

@Serializable
data class SessionResultsDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "",
    val sessionName: String? = null,
    val eventName: String? = null,
    val totalLaps: JsonValue? = null,
    val results: List<ResultEntryDto> = emptyList(),
)

@Serializable
data class ResultEntryDto(
    val position: Double? = null,
    val classifiedPosition: String? = null,
    val driverNumber: String? = null,
    val abbreviation: String? = null,
    val driverId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val fullName: String? = null,
    val headshotUrl: String? = null,
    val countryCode: String? = null,
    val teamName: String? = null,
    val teamId: String? = null,
    val teamLogoUrl: String? = null,
    val teamColor: String? = null,
    val gridPosition: Double? = null,
    val status: String? = null,
    val points: Double? = null,
    val timeMs: Int? = null,
    val q1: String? = null,
    val q2: String? = null,
    val q3: String? = null,
    val q1Gap: String? = null,
    val q2Gap: String? = null,
    val q3Gap: String? = null,
) {
    val id: String get() = driverId ?: abbreviation ?: fullName ?: "unknown"

    /** "R"/"DNF" pass through; numeric positions render as integers. */
    val positionLabel: String
        get() {
            val classified = classifiedPosition
            if (!classified.isNullOrEmpty() && classified.toDoubleOrNull() == null) return classified
            position?.let { return it.roundToInt().toString() }
            return "–"
        }

    val pointsLabel: String
        get() {
            val p = points ?: return ""
            if (p <= 0.0) return ""
            return if (p % 1.0 == 0.0) p.toInt().toString() else p.toString()
        }

    /** Positive means places gained from the grid. */
    val gridDelta: Int?
        get() {
            val g = gridPosition ?: return null
            val p = position ?: return null
            if (g <= 0.0) return null
            return (g - p).roundToInt()
        }

    val isPodium: Boolean get() = (position ?: 99.0) <= 3.0

    val bestQualifyingTime: String? get() = q3 ?: q2 ?: q1

    /**
     * Race time column, ported from the iOS `ResultRow.raceTime`.
     *
     * The leader shows an absolute time; everyone else shows a gap; a
     * non-finisher shows the status text ("Accident", "+1 Lap", "DNF").
     */
    fun raceTimeLabel(winnerTimeMs: Int?): String {
        val s = status
        if (s != null && s != "Finished" && !s.startsWith("+") && timeMs == null) return s
        val ms = timeMs ?: return status ?: "–"
        if (position == 1.0) return LapTimeFormat.format(ms, leading = true)
        if (winnerTimeMs != null) return "+" + LapTimeFormat.format(ms - winnerTimeMs, leading = false)
        return LapTimeFormat.format(ms, leading = true)
    }
}

/* ----------------------------------------------------------------- standings */

@Serializable
data class DriverStandingDto(
    val position: JsonValue? = null,
    val points: JsonValue? = null,
    val wins: JsonValue? = null,
    val driverId: String? = null,
    val driverNumber: JsonValue? = null,
    val driverCode: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val nationality: String? = null,
    val dateOfBirth: String? = null,
    val teamName: String? = null,
    val teamId: String? = null,
    val teamLogoUrl: String? = null,
) {
    val id: String get() = driverId ?: "${givenName.orEmpty()}${familyName.orEmpty()}"
    val fullName: String get() = "${givenName.orEmpty()} ${familyName.orEmpty()}".trim()
}

@Serializable
data class ConstructorStandingDto(
    val position: JsonValue? = null,
    val points: JsonValue? = null,
    val wins: JsonValue? = null,
    val teamId: String? = null,
    val teamName: String? = null,
    val nationality: String? = null,
    val teamLogoUrl: String? = null,
) {
    val id: String get() = teamId ?: teamName ?: "unknown"
}

/* ------------------------------------------------------------------- drivers */

@Serializable
data class DriverDto(
    val driverId: String = "",
    val givenName: String? = null,
    val familyName: String? = null,
    val code: String? = null,
    val number: JsonValue? = null,
    val nationality: String? = null,
    val dateOfBirth: String? = null,
    val teamName: String? = null,
    val teamId: String? = null,
    val teamLogoUrl: String? = null,
    val teamColor: String? = null,
    val headshotUrl: String? = null,
    val countryCode: String? = null,
    val position: JsonValue? = null,
    val points: JsonValue? = null,
    val wins: JsonValue? = null,
) {
    val fullName: String get() = "${givenName.orEmpty()} ${familyName.orEmpty()}".trim()
    val numberString: String? get() = number?.stringValue
    val pointsString: String get() = points?.numberLabel ?: "0"
    val positionInt: Int? get() = position?.intValue
    val initials: String
        get() = code ?: listOfNotNull(givenName?.firstOrNull(), familyName?.firstOrNull())
            .joinToString("")
            .ifEmpty { "?" }
}

@Serializable
data class DriverDetailDto(
    val driverId: String = "",
    val givenName: String? = null,
    val familyName: String? = null,
    val code: String? = null,
    val number: JsonValue? = null,
    val nationality: String? = null,
    val dateOfBirth: String? = null,
    val teamName: String? = null,
    val teamId: String? = null,
    val teamLogoUrl: String? = null,
    val teamColor: String? = null,
    val headshotUrl: String? = null,
    val countryCode: String? = null,
    val position: JsonValue? = null,
    val points: JsonValue? = null,
    val wins: JsonValue? = null,
    val seasonResults: List<DriverSeasonResultDto>? = null,
) {
    val fullName: String get() = "${givenName.orEmpty()} ${familyName.orEmpty()}".trim()
    val initials: String
        get() = code ?: listOfNotNull(givenName?.firstOrNull(), familyName?.firstOrNull())
            .joinToString("")
            .ifEmpty { "?" }
}

@Serializable
data class DriverSeasonResultDto(
    val round: Int? = null,
    val raceName: String? = null,
    val position: JsonValue? = null,
    val points: JsonValue? = null,
    val grid: JsonValue? = null,
    val status: String? = null,
) {
    val positionInt: Int? get() = position?.intValue
    val pointsLabel: String get() = points?.numberLabel ?: "0"
}

/* --------------------------------------------------------------------- teams */

@Serializable
data class TeamDto(
    val position: JsonValue? = null,
    val points: JsonValue? = null,
    val wins: JsonValue? = null,
    val teamId: String? = null,
    val teamName: String? = null,
    val nationality: String? = null,
    val teamColor: String? = null,
    val teamLogoUrl: String? = null,
    val drivers: List<TeamDriverDto> = emptyList(),
) {
    val id: String get() = teamId ?: teamName ?: "unknown"
    val pointsString: String get() = points?.numberLabel ?: "0"
    val positionInt: Int? get() = position?.intValue
}

@Serializable
data class TeamDriverDto(
    val driverId: String? = null,
    val name: String? = null,
    val code: String? = null,
    val number: JsonValue? = null,
    val headshotUrl: String? = null,
    val points: JsonValue? = null,
) {
    val id: String get() = driverId ?: name ?: "unknown"
    val initials: String
        get() = code ?: name?.split(" ")?.mapNotNull { it.firstOrNull() }?.joinToString("")
            ?.take(2) ?: "?"
    val pointsValue: Double get() = points?.doubleValue ?: 0.0
    val pointsLabel: String get() = points?.numberLabel ?: "0"
}

/* ------------------------------------------------------------------ circuits */

@Serializable
data class CircuitDto(
    val circuitId: String? = null,
    val name: String? = null,
    val locality: String? = null,
    val country: String? = null,
    val lat: JsonValue? = null,
    val long: JsonValue? = null,
) {
    val id: String get() = circuitId ?: name ?: "unknown"
}

@Serializable
data class CircuitMapDto(
    val year: Int = 0,
    val round: Int = 0,
    val eventName: String? = null,
    val location: String? = null,
    val country: String? = null,
    val outline: List<TrackPointDto> = emptyList(),
    val points: List<TrackTelemetryPointDto>? = null,
    val corners: List<TrackCornerDto> = emptyList(),
    val rotation: Double = 0.0,
    val lengthMeters: Double? = null,
    val minElevation: Double? = null,
    val maxElevation: Double? = null,
    val fastestLap: CircuitFastestLapDto? = null,
)

@Serializable
data class TrackPointDto(val x: Double = 0.0, val y: Double = 0.0)

@Serializable
data class TrackTelemetryPointDto(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val speed: Double = 0.0,
    val drs: Int = 0,
    val distance: Double = 0.0,
) {
    /** DRS status codes that mean the flap is actually open. */
    val drsOpen: Boolean get() = drs == 10 || drs == 12 || drs == 14
}

@Serializable
data class TrackCornerDto(
    val number: Int = 0,
    val letter: String = "",
    val x: Double = 0.0,
    val y: Double = 0.0,
    /** Turn angle in degrees, when FastF1 provides it. */
    val angle: Double? = null,
    /** Distance along the lap (metres) at this corner. */
    val distanceMeters: Double? = null,
    /** Approximate speed (km/h) at this corner, cross-referenced from the fastest lap's trace. */
    val speed: Double? = null,
) {
    val label: String get() = "$number$letter"
    val id: String get() = label
}

@Serializable
data class CircuitFastestLapDto(
    val driver: String? = null,
    val driverName: String? = null,
    val team: String? = null,
    val teamLogoUrl: String? = null,
    val teamColor: String? = null,
    val time: String? = null,
    val compound: String? = null,
)

/* -------------------------------------------------------------------- replay */

@Serializable
data class RaceReplayDto(
    val year: Int = 0,
    val round: Int = 0,
    val eventName: String? = null,
    val totalLaps: Int = 0,
    val drivers: List<ReplayDriverDto> = emptyList(),
    val frames: List<ReplayFrameDto> = emptyList(),
)

@Serializable
data class ReplayDriverDto(
    val code: String = "",
    val driverId: String? = null,
    val fullName: String? = null,
    val teamName: String? = null,
    val teamLogoUrl: String? = null,
    val teamColor: String? = null,
    val number: String? = null,
)

@Serializable
data class ReplayFrameDto(
    val lap: Int = 0,
    val order: List<ReplayEntryDto> = emptyList(),
)

@Serializable
data class ReplayEntryDto(
    val position: Int = 0,
    val driver: String = "",
    val driverId: String? = null,
    val teamColor: String? = null,
    val teamName: String? = null,
    val teamLogoUrl: String? = null,
    val lapTimeMs: Int? = null,
    val lapTime: String? = null,
    val compound: String? = null,
    val tyreLife: JsonValue? = null,
    val gapMs: Int? = null,
    val gap: String? = null,
)

@Serializable
data class ReplayPositionsDto(
    val year: Int = 0,
    val round: Int = 0,
    val eventName: String? = null,
    val totalLaps: Int = 0,
    val drivers: List<ReplayDriverDto> = emptyList(),
    val laps: List<ReplayLapPositionsDto> = emptyList(),
)

@Serializable
data class ReplayLapPositionsDto(
    val lap: Int = 0,
    val positions: Map<String, List<List<Double>>> = emptyMap(),
)
