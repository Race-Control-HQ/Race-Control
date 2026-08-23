package com.owlmedia.racecontrol.data.remote.dto

import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.util.pointsLabel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* -------------------------------------------------------------- race trace */

@Serializable
data class RaceTraceResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "R",
    val eventName: String? = null,
    val available: Boolean = false,
    val mode: String = "median",
    val totalLaps: Int = 0,
    val greenFlagMedianLapMs: Int? = null,
    val yDomainMs: List<Int>? = null,
    val periods: List<FlagPeriodDto> = emptyList(),
    val drivers: List<RaceTraceDriverDto> = emptyList(),
)

@Serializable
data class RaceTraceDriverDto(
    val code: String = "",
    val driverId: String? = null,
    val fullName: String? = null,
    val teamName: String? = null,
    val teamColor: String? = null,
    val finishPosition: Int? = null,
    val retired: Boolean = false,
    val status: String? = null,
    val lapsCompleted: Int = 0,
    val laps: List<RaceTraceLapDto> = emptyList(),
)

@Serializable
data class RaceTraceLapDto(
    val lap: Int = 0,
    val deltaMs: Int = 0,
    val cumulativeMs: Int = 0,
    val lapTimeMs: Int? = null,
    val compound: String? = null,
)

/* --------------------------------------------------------- tyre degradation */

@Serializable
data class TyrePerformanceResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "R",
    val eventName: String? = null,
    val available: Boolean = false,
    val xDomain: List<Int>? = null,
    val yDomainMs: List<Int>? = null,
    val compoundBaselines: List<CompoundBaselineDto> = emptyList(),
    val stints: List<TyrePerformanceStintDto> = emptyList(),
)

@Serializable
data class CompoundBaselineDto(
    val compound: String = "",
    val slopeSecPerLap: Double = 0.0,
    val stintCount: Int = 0,
)

@Serializable
data class TyrePerformanceStintDto(
    val id: String = "",
    val driverCode: String = "",
    val driverId: String? = null,
    val fullName: String? = null,
    val teamName: String? = null,
    val teamColor: String? = null,
    val stint: Int = 0,
    val compound: String? = null,
    val freshTyre: Boolean? = null,
    val startLap: Int = 0,
    val endLap: Int = 0,
    val bestLapMs: Int = 0,
    val slopeSecPerLap: Double = 0.0,
    val points: List<TyrePerformancePointDto> = emptyList(),
    val fit: List<TyrePerformanceFitPointDto> = emptyList(),
)

@Serializable
data class TyrePerformancePointDto(
    val lap: Int = 0,
    val tyreLife: Double = 0.0,
    val lapTimeMs: Int = 0,
    val deltaMs: Int = 0,
)

@Serializable
data class TyrePerformanceFitPointDto(
    val tyreLife: Double = 0.0,
    val deltaMs: Int = 0,
)

/* ---------------------------------------------------------- pit-stop ledger */

@Serializable
data class PitStopsResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "R",
    val eventName: String? = null,
    val available: Boolean = false,
    val circuitMedianLossMs: Int? = null,
    val lossDomainMs: List<Int>? = null,
    val stops: List<PitStopLedgerItemDto> = emptyList(),
)

@Serializable
data class PitStopLedgerItemDto(
    val id: String = "",
    val driverCode: String = "",
    val driverId: String? = null,
    val fullName: String? = null,
    val teamName: String? = null,
    val teamColor: String? = null,
    val stop: Int = 0,
    val lap: Int = 0,
    val compoundIn: String? = null,
    val compoundOut: String? = null,
    val lossMs: Int = 0,
    val deltaToMedianMs: Int = 0,
    val entryPosition: Int? = null,
    val rejoinPosition: Int? = null,
    val positionsGained: Int? = null,
    val outcome: String = "HELD",
    val rivals: List<String> = emptyList(),
)

/* ----------------------------------------------- qualifying sector waterfall */

@Serializable
data class QualifyingSectorsResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "Q",
    val eventName: String? = null,
    val available: Boolean = false,
    val poleCode: String? = null,
    val poleLapMs: Int? = null,
    val gapDomainMs: List<Int>? = null,
    val drivers: List<QualifyingSectorDriverDto> = emptyList(),
)

@Serializable
data class QualifyingSectorDriverDto(
    val code: String = "",
    val driverId: String? = null,
    val fullName: String? = null,
    val teamName: String? = null,
    val teamColor: String? = null,
    val lapMs: Int = 0,
    val gapToPoleMs: Int = 0,
    val sectorMs: List<Int> = emptyList(),
    val sectorDeltaMs: List<Int> = emptyList(),
    val idealSectorMs: List<Int> = emptyList(),
    val idealLapMs: Int = 0,
    val idealGainMs: Int = 0,
    val speedI1: Double? = null,
    val speedI2: Double? = null,
    val speedFL: Double? = null,
    val speedST: Double? = null,
)

/* -------------------------------------------------------- lap-time evolution */

@Serializable
data class LapTimesResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val eventName: String? = null,
    val totalLaps: Int = 0,
    val drivers: List<LapTimeDriverDto> = emptyList(),
)

@Serializable
data class LapTimeDriverDto(
    val code: String = "",
    val driverId: String? = null,
    val teamName: String? = null,
    val teamColor: String? = null,
    val laps: List<LapTimePointDto> = emptyList(),
)

@Serializable
data class LapTimePointDto(
    val lap: Int = 0,
    val timeMs: Int = 0,
    val compound: String? = null,
) {
    val seconds: Double get() = timeMs / 1000.0
}

/* ------------------------------------------------------------- tyre strategy */

@Serializable
data class StrategyResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val eventName: String? = null,
    val totalLaps: Int = 0,
    val drivers: List<StrategyDriverDto> = emptyList(),
)

@Serializable
data class StrategyDriverDto(
    val code: String = "",
    val driverId: String? = null,
    val teamName: String? = null,
    val teamColor: String? = null,
    val pitStops: Int = 0,
    val stints: List<StintDto> = emptyList(),
    val status: String? = null,
    val retired: Boolean = false,
)

@Serializable
data class StintDto(
    val stint: Int = 0,
    val compound: String? = null,
    val startLap: Int = 0,
    val endLap: Int = 0,
    val laps: Int = 0,
)

/* ------------------------------------------------------------------- weather */

@Serializable
data class WeatherResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "",
    val eventName: String? = null,
    val available: Boolean = false,
    val airTemp: Double? = null,
    val trackTemp: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    val windSpeed: Double? = null,
    val rainfall: Boolean? = null,
    val airTempMax: Double? = null,
    val trackTempMax: Double? = null,
    val timeline: List<WeatherSampleDto> = emptyList(),
)

@Serializable
data class WeatherSampleDto(
    val timeSeconds: Double = 0.0,
    val airTemp: Double? = null,
    val trackTemp: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    val windSpeed: Double? = null,
    val rainfall: Boolean = false,
)

/* ----------------------------------------------------- mini-sector dominance */

@Serializable
data class MiniSectorsResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "Q",
    val eventName: String? = null,
    val available: Boolean = false,
    val driverCount: Int = 0,
    val segmentCount: Int = 0,
    val outlineSourceCode: String? = null,
    val legend: List<MiniSectorLegendDto> = emptyList(),
    val segments: List<MiniSectorSegmentDto> = emptyList(),
)

@Serializable
data class MiniSectorLegendDto(
    val code: String = "",
    val teamColor: String? = null,
    val segmentsWon: Int = 0,
)

@Serializable
data class MiniSectorSegmentDto(
    val index: Int = 0,
    val startDistance: Double = 0.0,
    val endDistance: Double = 0.0,
    val points: List<List<Double>> = emptyList(),
    val winnerCode: String = "",
    val teamColor: String? = null,
    val timeMs: Int = 0,
    val gapMs: Int = 0,
)

/* --------------------------------------------------------- title scenarios */

@Serializable
data class TitleScenariosResponseDto(
    val year: Int = 0,
    val throughRound: Int? = null,
    val available: Boolean = false,
    val roundsRemaining: Int = 0,
    val positions: List<Int> = emptyList(),
    val drivers: List<TitleScenarioDriverDto> = emptyList(),
    val cells: List<TitleScenarioCellDto> = emptyList(),
    val clinchText: String? = null,
    val summary: String? = null,
)

@Serializable
data class TitleScenarioDriverDto(
    val driverId: String = "",
    val code: String = "",
    val teamColor: String? = null,
    val points: Double = 0.0,
)

@Serializable
data class TitleScenarioCellDto(
    val d1Position: Int = 0,
    val d2Position: Int = 0,
    val d1Points: Double = 0.0,
    val d2Points: Double = 0.0,
    val margin: Double = 0.0,
    val outcome: String = "TIED",
)

/* ------------------------------------------------------- driver fingerprint */

@Serializable
data class DriverFingerprintResponseDto(
    val year: Int = 0,
    val driverId: String = "",
    val available: Boolean = false,
    val axes: List<FingerprintAxisDto> = emptyList(),
)

@Serializable
data class FingerprintAxisDto(
    val key: String = "",
    val label: String = "",
    val percentile: Int = 0,
    val rawValue: Double = 0.0,
)

/* ----------------------------------------------------------------- telemetry */

@Serializable
data class TelemetryResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val available: Boolean = false,
    val eventName: String? = null,
    val trace: TelemetryTraceDto? = null,
)

@Serializable
data class TelemetryCompareResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val eventName: String? = null,
    val available: Boolean = false,
    val traces: List<TelemetryTraceDto> = emptyList(),
)

@Serializable
data class TelemetryTraceDto(
    val code: String = "",
    val driverName: String? = null,
    val teamName: String? = null,
    val teamColor: String? = null,
    val lapNumber: Int? = null,
    val lapTime: String? = null,
    val lapTimeMs: Int? = null,
    val compound: String? = null,
    val distance: List<Double> = emptyList(),
    val time: List<Double>? = null,
    val speed: List<Double> = emptyList(),
    val throttle: List<Double> = emptyList(),
    val brake: List<Int> = emptyList(),
    val gear: List<Int> = emptyList(),
    val rpm: List<Int> = emptyList(),
    val drs: List<Int> = emptyList(),
    val x: List<Double> = emptyList(),
    val y: List<Double> = emptyList(),
) {
    /**
     * Nearest sample index for a distance along the lap.
     *
     * The telemetry replay needs this on every frame, and the distance array is
     * sorted, so binary search keeps it O(log n) rather than scanning ~5,000
     * samples 60 times a second.
     */
    fun indexAtDistance(target: Double): Int {
        if (distance.isEmpty()) return 0
        var low = 0
        var high = distance.lastIndex
        while (low < high) {
            val mid = (low + high) / 2
            if (distance[mid] < target) low = mid + 1 else high = mid
        }
        // Prefer whichever neighbour is genuinely closer.
        if (low > 0 && kotlin.math.abs(distance[low - 1] - target) <
            kotlin.math.abs(distance[low] - target)
        ) {
            return low - 1
        }
        return low
    }

    fun speedAt(distanceAlongLap: Double): Double? =
        speed.getOrNull(indexAtDistance(distanceAlongLap))

    fun gearAt(distanceAlongLap: Double): Int? =
        gear.getOrNull(indexAtDistance(distanceAlongLap))

    fun throttleAt(distanceAlongLap: Double): Double? =
        throttle.getOrNull(indexAtDistance(distanceAlongLap))

    fun brakeAt(distanceAlongLap: Double): Boolean? =
        brake.getOrNull(indexAtDistance(distanceAlongLap))?.let { it > 0 }

    val maxDistance: Double get() = distance.lastOrNull() ?: 0.0
}

/* ---------------------------------------------------------------- flags/SC */

@Serializable
data class FlagsResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "",
    val eventName: String? = null,
    val totalLaps: Int = 0,
    val events: List<FlagEventDto> = emptyList(),
    val periods: List<FlagPeriodDto> = emptyList(),
)

/** One raw race-control message: the full timeline, for the detail view. */
@Serializable
data class FlagEventDto(
    val time: String? = null,
    val lap: Int? = null,
    val category: String? = null,
    val flag: String? = null,
    val status: String? = null,
    val scope: String? = null,
    val sector: Int? = null,
    val driverNumber: Int? = null,
    val driverCode: String? = null,
    val message: String? = null,
)

/** A collapsed yellow/red/safety-car span over an inclusive lap range. */
@Serializable
data class FlagPeriodDto(
    val type: String = "",
    val startLap: Int = 0,
    val endLap: Int = 0,
    val reason: String? = null,
) {
    val periodType: FlagPeriodType get() = FlagPeriodType.from(type)

    /** True when [lap] falls inside this period's inclusive lap range. */
    fun contains(lap: Int): Boolean = lap in startLap..endLap
}

enum class FlagPeriodType {
    YELLOW, DOUBLE_YELLOW, RED, SAFETY_CAR, VIRTUAL_SAFETY_CAR, UNKNOWN;

    companion object {
        fun from(raw: String?): FlagPeriodType = when (raw?.uppercase()) {
            "YELLOW" -> YELLOW
            "DOUBLE_YELLOW" -> DOUBLE_YELLOW
            "RED" -> RED
            "SC" -> SAFETY_CAR
            "VSC" -> VIRTUAL_SAFETY_CAR
            else -> UNKNOWN
        }
    }
}

/**
 * The period (if any) covering [lap]. Used to band a lap-time chart and to
 * badge the telemetry screen with "Safety Car (lap 20)" for the lap on screen.
 */
fun List<FlagPeriodDto>.periodContaining(lap: Int?): FlagPeriodDto? =
    lap?.let { l -> firstOrNull { it.contains(l) } }

/* ---------------------------------------------------------- race control log */

/**
 * The complete, unfiltered race-control message log: [FlagsResponseDto] only
 * carries FLAG/SAFETY-CAR category messages, this carries every category
 * (DRS, car events, stewards' investigations/penalties, etc.) so the two
 * screens are complementary rather than duplicating each other.
 */
@Serializable
data class RaceControlResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "",
    val eventName: String? = null,
    val totalLaps: Int = 0,
    val messages: List<RaceControlMessageDto> = emptyList(),
)

@Serializable
data class RaceControlMessageDto(
    val time: String? = null,
    val lap: Int? = null,
    val category: String? = null,
    val flag: String? = null,
    val status: String? = null,
    val scope: String? = null,
    val sector: Int? = null,
    val driverNumber: String? = null,
    val driverCode: String? = null,
    val message: String? = null,
) {
    val categoryType: RaceControlCategory get() = RaceControlCategory.from(category)
}

enum class RaceControlCategory {
    FLAG, SAFETY_CAR, DRS, CAR_EVENT, OTHER;

    companion object {
        fun from(raw: String?): RaceControlCategory = when (raw) {
            "Flag" -> FLAG
            "SafetyCar" -> SAFETY_CAR
            "Drs" -> DRS
            "CarEvent" -> CAR_EVENT
            else -> OTHER
        }
    }
}

/* ------------------------------------------------------------------ penalties */

/**
 * Stewards' penalties and reprimands for a session: a focused subset of what
 * [RaceControlResponseDto] carries, with driver/team identity already resolved
 * server-side so the row can render without a lookup.
 */
@Serializable
data class PenaltiesResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val session: String = "",
    val eventName: String? = null,
    val penalties: List<PenaltyDto> = emptyList(),
)

@Serializable
data class PenaltyDto(
    val time: String? = null,
    val lap: Int? = null,
    val type: String = "",
    val value: String? = null,
    val reason: String? = null,
    val message: String? = null,
    val driverCode: String? = null,
    val driverName: String? = null,
    val driverId: String? = null,
    val teamName: String? = null,
    val teamLogoUrl: String? = null,
    val teamColor: String? = null,
) {
    val penaltyType: PenaltyType get() = PenaltyType.from(type)
}

enum class PenaltyType {
    TIME, STOP_AND_GO, DRIVE_THROUGH, GRID, REPRIMAND, DISQUALIFICATION;

    companion object {
        fun from(raw: String?): PenaltyType = when (raw) {
            "Time Penalty" -> TIME
            "Stop & Go Penalty" -> STOP_AND_GO
            "Drive Through Penalty" -> DRIVE_THROUGH
            "Grid Penalty" -> GRID
            "Reprimand" -> REPRIMAND
            "Disqualification" -> DISQUALIFICATION
            else -> TIME
        }
    }
}

/* ------------------------------------------------------- race driver pickers */

@Serializable
data class RaceDriverDto(
    val code: String? = null,
    val driverId: String? = null,
    val fullName: String? = null,
    val teamName: String? = null,
    val teamColor: String? = null,
    val number: String? = null,
) {
    val id: String get() = code ?: driverId ?: fullName ?: "unknown"
}

/* --------------------------------------------------------------- retirements */

@Serializable
data class RetirementsResponseDto(
    val year: Int = 0,
    val round: Int = 0,
    val eventName: String? = null,
    val retirements: List<RetirementDto> = emptyList(),
)

@Serializable
data class RetirementDto(
    val driver: String? = null,
    val fullName: String? = null,
    val driverId: String? = null,
    val teamName: String? = null,
    val teamId: String? = null,
    val teamLogoUrl: String? = null,
    val teamColor: String? = null,
    val status: String? = null,
    val classifiedPosition: String? = null,
    val lapsCompleted: Int? = null,
) {
    val id: String get() = driver ?: driverId ?: "unknown"

    /** Buckets the free-text status into the four causes the iOS app shows. */
    val cause: RetirementCause
        get() {
            val s = status.orEmpty().lowercase()
            return when {
                s.contains("disqualif") -> RetirementCause.DISQUALIFIED
                s.contains("accident") || s.contains("collision") ||
                    s.contains("spun") || s.contains("damage") -> RetirementCause.ACCIDENT
                s.contains("engine") || s.contains("gearbox") || s.contains("hydraulic") ||
                    s.contains("brake") || s.contains("suspension") || s.contains("power") ||
                    s.contains("transmission") || s.contains("electrical") ||
                    s.contains("mechanical") || s.contains("overheating") ||
                    s.contains("wheel") || s.contains("puncture") ||
                    s.contains("fuel") || s.contains("oil") ||
                    s.contains("water") || s.contains("clutch") ||
                    s.contains("driveshaft") || s.contains("exhaust") ||
                    s.contains("turbo") || s.contains("battery") ||
                    s.contains("radiator") || s.contains("throttle") ||
                    s.contains("differential") || s.contains("steering") ||
                    s.contains("tyre") || s.contains("retired") -> RetirementCause.MECHANICAL
                else -> RetirementCause.OTHER
            }
        }
}

enum class RetirementCause { MECHANICAL, ACCIDENT, DISQUALIFIED, OTHER }

/* --------------------------------------------------------------- reliability */

@Serializable
data class ReliabilityResponseDto(
    val year: Int = 0,
    val races: Int = 0,
    val drivers: List<ReliabilityDriverDto> = emptyList(),
    val teams: List<ReliabilityTeamDto> = emptyList(),
)

@Serializable
data class ReliabilityDriverDto(
    val driverId: String = "",
    val name: String = "",
    val teamId: String? = null,
    val finished: Int = 0,
    val mechanical: Int = 0,
    val accident: Int = 0,
    val disqualified: Int = 0,
    val other: Int = 0,
    val dnf: Int = 0,
    val starts: Int = 0,
    val finishRate: Double = 0.0,
)

@Serializable
data class ReliabilityTeamDto(
    val teamId: String = "",
    val teamName: String? = null,
    val teamLogoUrl: String? = null,
    val finished: Int = 0,
    val mechanical: Int = 0,
    val accident: Int = 0,
    val disqualified: Int = 0,
    val other: Int = 0,
    val dnf: Int = 0,
    val starts: Int = 0,
    val finishRate: Double = 0.0,
)

/* -------------------------------------------------------- standings evolution */

@Serializable
data class StandingsEvolutionDto(
    val year: Int = 0,
    val rounds: List<Int> = emptyList(),
    val drivers: List<EvolutionDriverDto> = emptyList(),
)

@Serializable
data class EvolutionDriverDto(
    val driverId: String = "",
    val name: String = "",
    val code: String? = null,
    val teamName: String? = null,
    val teamColor: String? = null,
    val points: Double = 0.0,
    val series: List<EvolutionPointDto> = emptyList(),
)

@Serializable
data class EvolutionPointDto(
    val round: Int = 0,
    val points: Double = 0.0,
)

/* ---------------------------------------------------------- wdc calculator */

@Serializable
data class WdcCalculatorDto(
    val year: Int = 0,
    val roundsRemaining: Int = 0,
    val sprintRoundsRemaining: Int = 0,
    val maxRemainingPoints: Int = 0,
    val leaderPoints: Double = 0.0,
    val decided: Boolean = false,
    val drivers: List<WdcDriverDto> = emptyList(),
    val throughRound: Int? = null,
    val roundsInSeason: Int = 0,
    // Known scoring approximations (no countback tie-break; flat modern
    // sprint-points scale applied to historical seasons). Absent/empty on
    // older cached responses -- default keeps decoding safe either way.
    val notes: List<String> = emptyList(),
)

@Serializable
data class WdcDriverDto(
    val position: Int? = null,
    val driverId: String? = null,
    val driverCode: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val teamName: String? = null,
    val teamId: String? = null,
    val teamLogoUrl: String? = null,
    val teamColor: String? = null,
    val headshotUrl: String? = null,
    val points: Double = 0.0,
    val maxPoints: Double = 0.0,
    val pointsBehindLeader: Double = 0.0,
    val canWin: Boolean = false,
) {
    val fullName: String get() = "${givenName.orEmpty()} ${familyName.orEmpty()}".trim()
}

/* ------------------------------------------------------------- head-to-head */

@Serializable
data class CompareResponseDto(
    val year: Int = 0,
    val drivers: List<CompareDriverDto> = emptyList(),
)

@Serializable
data class CompareDriverDto(
    val driverId: String = "",
    val name: String = "",
    val teamName: String? = null,
    val teamId: String? = null,
    val teamLogoUrl: String? = null,
    val points: Double = 0.0,
    val wins: Int = 0,
    val podiums: Int = 0,
    val poles: Int = 0,
    val bestFinish: Int? = null,
    val dnf: Int = 0,
    // The backend uses snake-ish keys for just these two fields.
    @SerialName("raceWins_h2h") val raceWinsH2h: Int = 0,
    @SerialName("qualWins_h2h") val qualWinsH2h: Int = 0,
) {
    val pointsLabel: String
        get() = if (points % 1.0 == 0.0) points.toInt().toString() else points.toString()
}

/** Error body the FastAPI backend returns alongside a non-2xx status. */
@Serializable
data class ApiErrorBodyDto(val detail: String? = null)
