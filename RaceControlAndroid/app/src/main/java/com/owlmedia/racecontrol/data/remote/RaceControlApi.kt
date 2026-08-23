package com.owlmedia.racecontrol.data.remote

import com.owlmedia.racecontrol.data.remote.dto.CircuitDto
import com.owlmedia.racecontrol.data.remote.dto.CircuitMapDto
import com.owlmedia.racecontrol.data.remote.dto.CompareResponseDto
import com.owlmedia.racecontrol.data.remote.dto.ConstructorStandingDto
import com.owlmedia.racecontrol.data.remote.dto.DriverDetailDto
import com.owlmedia.racecontrol.data.remote.dto.DriverDto
import com.owlmedia.racecontrol.data.remote.dto.DriverFingerprintResponseDto
import com.owlmedia.racecontrol.data.remote.dto.DriverStandingDto
import com.owlmedia.racecontrol.data.remote.dto.FlagsResponseDto
import com.owlmedia.racecontrol.data.remote.dto.LapTimesResponseDto
import com.owlmedia.racecontrol.data.remote.dto.MiniSectorsResponseDto
import com.owlmedia.racecontrol.data.remote.dto.PenaltiesResponseDto
import com.owlmedia.racecontrol.data.remote.dto.PitStopsResponseDto
import com.owlmedia.racecontrol.data.remote.dto.QualifyingSectorsResponseDto
import com.owlmedia.racecontrol.data.remote.dto.RaceControlResponseDto
import com.owlmedia.racecontrol.data.remote.dto.RaceDriverDto
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto
import com.owlmedia.racecontrol.data.remote.dto.RaceReplayDto
import com.owlmedia.racecontrol.data.remote.dto.ReplayPositionsDto
import com.owlmedia.racecontrol.data.remote.dto.RaceTraceResponseDto
import com.owlmedia.racecontrol.data.remote.dto.ReliabilityResponseDto
import com.owlmedia.racecontrol.data.remote.dto.RetirementsResponseDto
import com.owlmedia.racecontrol.data.remote.dto.SessionResultsDto
import com.owlmedia.racecontrol.data.remote.dto.StandingsEvolutionDto
import com.owlmedia.racecontrol.data.remote.dto.StrategyResponseDto
import com.owlmedia.racecontrol.data.remote.dto.TeamDto
import com.owlmedia.racecontrol.data.remote.dto.TelemetryCompareResponseDto
import com.owlmedia.racecontrol.data.remote.dto.TelemetryResponseDto
import com.owlmedia.racecontrol.data.remote.dto.TitleScenariosResponseDto
import com.owlmedia.racecontrol.data.remote.dto.TyrePerformanceResponseDto
import com.owlmedia.racecontrol.data.remote.dto.WdcCalculatorDto
import com.owlmedia.racecontrol.data.remote.dto.WeatherResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The RaceControl FastF1 backend.
 *
 * Endpoint-for-endpoint parity with the iOS `APIClient` extension: all 22
 * routes, same paths, same query parameters.
 *
 * The base URL is user-configurable at runtime, so it is rewritten per request
 * by [BaseUrlInterceptor] rather than baked into Retrofit.
 */
interface RaceControlApi {

    @GET("api/health")
    suspend fun health(): Response<Unit>

    @GET("api/seasons")
    suspend fun seasons(): List<Int>

    @GET("api/schedule/{year}")
    suspend fun schedule(@Path("year") year: Int): List<RaceEventDto>

    @GET("api/results/{year}/{round}/{session}")
    suspend fun results(
        @Path("year") year: Int,
        @Path("round") round: Int,
        @Path("session") session: String,
    ): SessionResultsDto

    @GET("api/standings/drivers/{year}")
    suspend fun driverStandings(@Path("year") year: Int): List<DriverStandingDto>

    @GET("api/standings/constructors/{year}")
    suspend fun constructorStandings(@Path("year") year: Int): List<ConstructorStandingDto>

    @GET("api/drivers/{year}")
    suspend fun drivers(@Path("year") year: Int): List<DriverDto>

    @GET("api/drivers/{year}/{driverId}")
    suspend fun driverDetail(
        @Path("year") year: Int,
        @Path("driverId") driverId: String,
    ): DriverDetailDto

    @GET("api/teams/{year}")
    suspend fun teams(@Path("year") year: Int): List<TeamDto>

    @GET("api/teams/{year}/{teamId}")
    suspend fun teamDetail(
        @Path("year") year: Int,
        @Path("teamId") teamId: String,
    ): TeamDto

    @GET("api/circuits/{year}")
    suspend fun circuits(@Path("year") year: Int): List<CircuitDto>

    @GET("api/circuit/{year}/{round}")
    suspend fun circuitMap(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): CircuitMapDto

    @GET("api/replay/{year}/{round}")
    suspend fun replay(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): RaceReplayDto

    @GET("api/replay-positions/{year}/{round}")
    suspend fun replayPositions(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): ReplayPositionsDto

    @GET("api/race-trace/{year}/{round}")
    suspend fun raceTrace(
        @Path("year") year: Int,
        @Path("round") round: Int,
        @Query("mode") mode: String = "median",
    ): RaceTraceResponseDto

    @GET("api/tyre-performance/{year}/{round}")
    suspend fun tyrePerformance(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): TyrePerformanceResponseDto

    @GET("api/pit-stops/{year}/{round}")
    suspend fun pitStops(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): PitStopsResponseDto

    @GET("api/qualifying-sectors/{year}/{round}")
    suspend fun qualifyingSectors(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): QualifyingSectorsResponseDto

    @GET("api/minisectors/{year}/{round}")
    suspend fun miniSectors(
        @Path("year") year: Int,
        @Path("round") round: Int,
        @Query("session") session: String = "Q",
        @Query("top") top: Int = 10,
    ): MiniSectorsResponseDto

    @GET("api/laptimes/{year}/{round}")
    suspend fun lapTimes(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): LapTimesResponseDto

    @GET("api/strategy/{year}/{round}")
    suspend fun strategy(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): StrategyResponseDto

    @GET("api/weather/{year}/{round}/{session}")
    suspend fun weather(
        @Path("year") year: Int,
        @Path("round") round: Int,
        @Path("session") session: String,
    ): WeatherResponseDto

    @GET("api/racedrivers/{year}/{round}")
    suspend fun raceDrivers(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): List<RaceDriverDto>

    @GET("api/telemetry/{year}/{round}/{driver}")
    suspend fun telemetry(
        @Path("year") year: Int,
        @Path("round") round: Int,
        @Path("driver") driver: String,
        @Query("lap") lap: String = "fastest",
    ): TelemetryResponseDto

    @GET("api/telemetry-compare/{year}/{round}")
    suspend fun telemetryCompare(
        @Path("year") year: Int,
        @Path("round") round: Int,
        @Query("d1") d1: String,
        @Query("d2") d2: String,
    ): TelemetryCompareResponseDto

    @GET("api/retirements/{year}/{round}")
    suspend fun retirements(
        @Path("year") year: Int,
        @Path("round") round: Int,
    ): RetirementsResponseDto

    @GET("api/flags/{year}/{round}")
    suspend fun flags(
        @Path("year") year: Int,
        @Path("round") round: Int,
        @Query("session") session: String = "R",
    ): FlagsResponseDto

    @GET("api/racecontrol/{year}/{round}")
    suspend fun raceControl(
        @Path("year") year: Int,
        @Path("round") round: Int,
        @Query("session") session: String = "R",
    ): RaceControlResponseDto

    @GET("api/penalties/{year}/{round}")
    suspend fun penalties(
        @Path("year") year: Int,
        @Path("round") round: Int,
        @Query("session") session: String = "R",
    ): PenaltiesResponseDto

    @GET("api/reliability/{year}")
    suspend fun reliability(@Path("year") year: Int): ReliabilityResponseDto

    @GET("api/compare/{year}/{d1}/{d2}")
    suspend fun compare(
        @Path("year") year: Int,
        @Path("d1") d1: String,
        @Path("d2") d2: String,
    ): CompareResponseDto

    @GET("api/standings-evolution/{year}")
    suspend fun standingsEvolution(@Path("year") year: Int): StandingsEvolutionDto

    @GET("api/wdc-calculator/{year}")
    suspend fun wdcCalculator(
        @Path("year") year: Int,
        @Query("through_round") throughRound: Int? = null,
    ): WdcCalculatorDto

    @GET("api/title-scenarios/{year}")
    suspend fun titleScenarios(
        @Path("year") year: Int,
        @Query("d1") d1: String? = null,
        @Query("d2") d2: String? = null,
        @Query("through_round") throughRound: Int? = null,
    ): TitleScenariosResponseDto

    @GET("api/driver-fingerprint/{year}/{driverId}")
    suspend fun driverFingerprint(
        @Path("year") year: Int,
        @Path("driverId") driverId: String,
    ): DriverFingerprintResponseDto
}
