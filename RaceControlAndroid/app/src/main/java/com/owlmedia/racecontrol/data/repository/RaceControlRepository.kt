package com.owlmedia.racecontrol.data.repository

import android.content.Context
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.data.remote.ApiException
import com.owlmedia.racecontrol.data.remote.RaceControlApi
import com.owlmedia.racecontrol.data.remote.dto.ApiErrorBodyDto
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Year
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single data entry point for every screen.
 *
 * Deliberately **one** repository rather than six. The iOS app has one
 * `APIClient` for the same reason: this is a read-only client over a REST API
 * with no local database, no write path and no per-domain caching policy
 * (OkHttp handles caching uniformly). Splitting it per feature would add six
 * files of delegation and no separation that actually exists.
 *
 * Every method returns [Result]; failures are already mapped to a message the
 * UI can show, using the same wording as the iOS build.
 */
@Singleton
class RaceControlRepository @Inject constructor(
    private val api: RaceControlApi,
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun seasons(): Result<List<Int>> = call { api.seasons() }

    /**
     * Seasons, with the iOS fallback behaviour: if the server is unreachable we
     * still let the user browse, using the same static 2018..now range rather
     * than blocking every tab behind one failed call.
     */
    suspend fun seasonsOrFallback(): List<Int> =
        seasons().getOrElse {
            val current = Year.now().value
            (current downTo 2018).toList()
        }

    suspend fun schedule(year: Int): Result<List<RaceEventDto>> = call { api.schedule(year) }

    suspend fun results(year: Int, round: Int, session: String): Result<SessionResultsDto> =
        call { api.results(year, round, session) }

    suspend fun driverStandings(year: Int): Result<List<DriverStandingDto>> =
        call { api.driverStandings(year) }

    suspend fun constructorStandings(year: Int): Result<List<ConstructorStandingDto>> =
        call { api.constructorStandings(year) }

    suspend fun drivers(year: Int): Result<List<DriverDto>> = call { api.drivers(year) }

    suspend fun driverDetail(year: Int, driverId: String): Result<DriverDetailDto> =
        call { api.driverDetail(year, driverId) }

    suspend fun teams(year: Int): Result<List<TeamDto>> = call { api.teams(year) }

    suspend fun teamDetail(year: Int, teamId: String): Result<TeamDto> =
        call { api.teamDetail(year, teamId) }

    suspend fun circuits(year: Int): Result<List<CircuitDto>> = call { api.circuits(year) }

    suspend fun circuitMap(year: Int, round: Int): Result<CircuitMapDto> =
        call { api.circuitMap(year, round) }

    suspend fun replay(year: Int, round: Int): Result<RaceReplayDto> =
        call { api.replay(year, round) }

    suspend fun replayPositions(year: Int, round: Int): Result<ReplayPositionsDto> =
        call { api.replayPositions(year, round) }

    suspend fun raceTrace(year: Int, round: Int, mode: String = "median"): Result<RaceTraceResponseDto> =
        call { api.raceTrace(year, round, mode) }

    suspend fun tyrePerformance(year: Int, round: Int): Result<TyrePerformanceResponseDto> =
        call { api.tyrePerformance(year, round) }

    suspend fun pitStops(year: Int, round: Int): Result<PitStopsResponseDto> =
        call { api.pitStops(year, round) }

    suspend fun qualifyingSectors(year: Int, round: Int): Result<QualifyingSectorsResponseDto> =
        call { api.qualifyingSectors(year, round) }

    suspend fun miniSectors(
        year: Int,
        round: Int,
        session: String = "Q",
        top: Int = 10,
    ): Result<MiniSectorsResponseDto> = call { api.miniSectors(year, round, session, top) }

    suspend fun lapTimes(year: Int, round: Int): Result<LapTimesResponseDto> =
        call { api.lapTimes(year, round) }

    suspend fun strategy(year: Int, round: Int): Result<StrategyResponseDto> =
        call { api.strategy(year, round) }

    suspend fun weather(year: Int, round: Int, session: String): Result<WeatherResponseDto> =
        call { api.weather(year, round, session) }

    suspend fun raceDrivers(year: Int, round: Int): Result<List<RaceDriverDto>> =
        call { api.raceDrivers(year, round) }

    suspend fun telemetry(
        year: Int,
        round: Int,
        driver: String,
        lap: String = "fastest",
    ): Result<TelemetryResponseDto> = call { api.telemetry(year, round, driver, lap) }

    suspend fun telemetryCompare(
        year: Int,
        round: Int,
        d1: String,
        d2: String,
    ): Result<TelemetryCompareResponseDto> = call { api.telemetryCompare(year, round, d1, d2) }

    suspend fun retirements(year: Int, round: Int): Result<RetirementsResponseDto> =
        call { api.retirements(year, round) }

    suspend fun flags(year: Int, round: Int, session: String = "R"): Result<FlagsResponseDto> =
        call { api.flags(year, round, session) }

    suspend fun raceControl(
        year: Int,
        round: Int,
        session: String = "R",
    ): Result<RaceControlResponseDto> = call { api.raceControl(year, round, session) }

    suspend fun penalties(year: Int, round: Int, session: String = "R"): Result<PenaltiesResponseDto> =
        call { api.penalties(year, round, session) }

    suspend fun reliability(year: Int): Result<ReliabilityResponseDto> =
        call { api.reliability(year) }

    suspend fun compare(year: Int, d1: String, d2: String): Result<CompareResponseDto> =
        call { api.compare(year, d1, d2) }

    suspend fun standingsEvolution(year: Int): Result<StandingsEvolutionDto> =
        call { api.standingsEvolution(year) }

    suspend fun wdcCalculator(year: Int, throughRound: Int? = null): Result<WdcCalculatorDto> =
        call { api.wdcCalculator(year, throughRound) }

    suspend fun titleScenarios(
        year: Int,
        d1: String? = null,
        d2: String? = null,
        throughRound: Int? = null,
    ): Result<TitleScenariosResponseDto> = call { api.titleScenarios(year, d1, d2, throughRound) }

    suspend fun driverFingerprint(year: Int, driverId: String): Result<DriverFingerprintResponseDto> =
        call { api.driverFingerprint(year, driverId) }

    /** Unauthenticated reachability probe, used by Settings > Test Connection. */
    suspend fun health(): Result<Int> = call { api.health().code() }

    /* ------------------------------------------------------------------ */

    private suspend inline fun <T> call(crossinline block: suspend () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.success(block())
            } catch (e: HttpException) {
                Result.failure(mapHttp(e))
            } catch (e: SerializationException) {
                Result.failure(ApiException.Decoding(e))
            } catch (e: IOException) {
                Result.failure(ApiException.Transport(e))
            } catch (e: IllegalStateException) {
                // Retrofit raises this for a malformed base URL or empty body.
                Result.failure(ApiException.InvalidResponse)
            }
        }

    private fun mapHttp(e: HttpException): ApiException {
        val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val detail = raw
            ?.let { body -> runCatching { errorJson.decodeFromString<ApiErrorBodyDto>(body) }.getOrNull() }
            ?.detail
        return ApiException.Server(e.code(), detail)
    }

    /**
     * Human-readable failure text.
     *
     * Wording is copied from the iOS `APIError.errorDescription` so a user who
     * has both apps sees one vocabulary for one backend.
     */
    fun messageFor(error: Throwable): String = when (error) {
        is ApiException.InvalidResponse -> context.getString(R.string.error_invalid_response)
        is ApiException.Server -> when {
            error.status == 401 -> context.getString(R.string.error_unauthorized)
            error.detail != null -> error.detail
            else -> context.getString(R.string.error_server, error.status)
        }
        is ApiException.Decoding -> context.getString(R.string.error_decoding)
        is ApiException.Transport -> context.getString(R.string.error_unreachable)
        is IOException -> context.getString(R.string.error_unreachable)
        else -> error.message ?: context.getString(R.string.error_invalid_response)
    }
}
