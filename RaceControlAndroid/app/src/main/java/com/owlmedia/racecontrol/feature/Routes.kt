package com.owlmedia.racecontrol.feature

import com.owlmedia.racecontrol.feature.circuits.TrackMap
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes.
 *
 * Using serializable objects rather than string templates means a screen cannot
 * be reached with a missing or mistyped argument; the compiler enforces it.
 */
object Routes {

    /* Top-level tabs. Each is the start destination of its own nested graph so
       every tab keeps an independent back stack, as the iOS NavigationStacks do. */
    @Serializable data object RacesGraph
    @Serializable data object DriversGraph
    @Serializable data object TeamsGraph
    @Serializable data object StandingsGraph
    @Serializable data object CircuitsGraph

    @Serializable data object Races
    @Serializable data object Drivers
    @Serializable data object Teams
    @Serializable data object Standings
    @Serializable data object Circuits

    /**
     * [title] carries the race name so the app bar has something to show before
     * the schedule loads. It must have a default: the notification deep link
     * only supplies year and round, and Navigation rejects a graph whose deep
     * link omits a required argument.
     */
    @Serializable data class RaceDetail(val year: Int, val round: Int, val title: String = "")
    @Serializable data class DriverDetail(val year: Int, val driverId: String)
    @Serializable data class TeamDetail(val year: Int, val teamId: String)
    @Serializable data class HeadToHead(val year: Int)
    @Serializable data class TitleDecider(val year: Int)
    @Serializable data class CircuitDetail(
        val year: Int,
        val round: Int,
        val circuitName: String,
    )

    @Serializable data class Replay(val year: Int, val round: Int, val title: String)
    @Serializable data class Telemetry(val year: Int, val round: Int, val title: String)
    @Serializable data class BrakeThrottle(val year: Int, val round: Int, val title: String)
    @Serializable data class LapTimes(val year: Int, val round: Int, val title: String)
    @Serializable data class Strategy(val year: Int, val round: Int, val title: String)
    @Serializable data class RaceTrace(val year: Int, val round: Int, val title: String)
    @Serializable data class PositionChart(val year: Int, val round: Int, val title: String)
    @Serializable data class TyrePerformance(val year: Int, val round: Int, val title: String)
    @Serializable data class PitStops(val year: Int, val round: Int, val title: String)
    @Serializable data class PitSwimlane(val year: Int, val round: Int, val title: String)
    @Serializable data class QualifyingSectors(val year: Int, val round: Int, val title: String)
    @Serializable data class SpeedTrap(val year: Int, val round: Int, val title: String)
    @Serializable data class MiniSectors(val year: Int, val round: Int, val title: String)
    @Serializable data class Qualifying(val year: Int, val round: Int, val title: String)
    @Serializable data class QualifyingLadder(val year: Int, val round: Int, val title: String)
    @Serializable data class CornerSpeedProfile(val year: Int, val round: Int, val title: String)
    @Serializable data class TrackMap(val year: Int, val round: Int, val title: String)
    @Serializable data class Weather(val year: Int, val round: Int, val title: String)
    @Serializable data class Retirements(val year: Int, val round: Int, val title: String)
    @Serializable data class Flags(val year: Int, val round: Int, val title: String)
    @Serializable data class RaceControl(val year: Int, val round: Int, val title: String)
    @Serializable data class Penalties(val year: Int, val round: Int, val title: String)

    @Serializable data object Settings

    /** Deep link used by session reminder notifications. */
    const val RACE_DEEP_LINK = "racecontrol://race/{year}/{round}"

    fun raceDeepLink(year: Int, round: Int) = "racecontrol://race/$year/$round"
}
