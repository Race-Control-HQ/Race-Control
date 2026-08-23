package com.owlmedia.racecontrol.feature.standings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.ConstructorStandingDto
import com.owlmedia.racecontrol.data.remote.dto.DriverStandingDto
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto
import com.owlmedia.racecontrol.data.remote.dto.ReliabilityResponseDto
import com.owlmedia.racecontrol.data.remote.dto.ResultEntryDto
import com.owlmedia.racecontrol.data.remote.dto.StandingsEvolutionDto
import com.owlmedia.racecontrol.data.remote.dto.WdcCalculatorDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StandingsMode { DRIVERS, TEAMS, FORM, PROGRESS, RELIABILITY, WDC }

/** One driver's row in the season form guide: their result per completed round. */
data class SeasonFormDriverRow(
    val id: String,
    val name: String,
    val code: String,
    val cells: Map<Int, ResultEntryDto>,
)

data class SeasonFormGuide(
    val rounds: List<RaceEventDto>,
    val drivers: List<SeasonFormDriverRow>,
)

data class FormGuideSelection(
    val driverName: String,
    val roundName: String,
    val resultLabel: String,
)

@HiltViewModel
class StandingsViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _mode = MutableStateFlow(StandingsMode.DRIVERS)
    val mode: StateFlow<StandingsMode> = _mode.asStateFlow()

    private val _drivers = MutableStateFlow<UiState<List<DriverStandingDto>>>(UiState.Idle)
    val drivers: StateFlow<UiState<List<DriverStandingDto>>> = _drivers.asStateFlow()

    private val _constructors = MutableStateFlow<UiState<List<ConstructorStandingDto>>>(UiState.Idle)
    val constructors: StateFlow<UiState<List<ConstructorStandingDto>>> = _constructors.asStateFlow()

    private val _evolution = MutableStateFlow<UiState<StandingsEvolutionDto>>(UiState.Idle)
    val evolution: StateFlow<UiState<StandingsEvolutionDto>> = _evolution.asStateFlow()

    private val _reliability = MutableStateFlow<UiState<ReliabilityResponseDto>>(UiState.Idle)
    val reliability: StateFlow<UiState<ReliabilityResponseDto>> = _reliability.asStateFlow()

    private val _wdc = MutableStateFlow<UiState<WdcCalculatorDto>>(UiState.Idle)
    val wdc: StateFlow<UiState<WdcCalculatorDto>> = _wdc.asStateFlow()

    private val _formGuide = MutableStateFlow<UiState<SeasonFormGuide>>(UiState.Idle)
    val formGuide: StateFlow<UiState<SeasonFormGuide>> = _formGuide.asStateFlow()

    private val _formGuideSelection = MutableStateFlow<FormGuideSelection?>(null)
    val formGuideSelection: StateFlow<FormGuideSelection?> = _formGuideSelection.asStateFlow()
    private var formGuideYear: Int? = null

    /** Selected round for the WDC "time machine" scrubber. Null means live standings. */
    private val _wdcThroughRound = MutableStateFlow<Int?>(null)
    val wdcThroughRound: StateFlow<Int?> = _wdcThroughRound.asStateFlow()

    /** Completed rounds for the current year, used to build the scrubber's range/labels. */
    private val _wdcCompletedRounds = MutableStateFlow<List<RaceEventDto>>(emptyList())
    val wdcCompletedRounds: StateFlow<List<RaceEventDto>> = _wdcCompletedRounds.asStateFlow()

    private var loadedYear: Int? = null
    private var wdcScheduleYear: Int? = null

    /** Which year we've already fired the WDC time-machine cache warm-up for. */
    private var wdcWarmedYear: Int? = null

    fun setMode(value: StandingsMode) {
        _mode.value = value
    }

    /**
     * Only the visible mode is fetched. Progress and Reliability are expensive
     * server-side (they replay the whole season), so loading all four up front
     * would make opening the tab noticeably slower for data most users never
     * look at.
     */
    fun load(year: Int, mode: StandingsMode, force: Boolean = false) {
        if (loadedYear != year) {
            _drivers.value = UiState.Idle
            _constructors.value = UiState.Idle
            _evolution.value = UiState.Idle
            _reliability.value = UiState.Idle
            _wdc.value = UiState.Idle
            _wdcThroughRound.value = null
            _wdcCompletedRounds.value = emptyList()
            _formGuide.value = UiState.Idle
            _formGuideSelection.value = null
            loadedYear = year
        }
        when (mode) {
            StandingsMode.DRIVERS -> loadDrivers(year, force)
            StandingsMode.TEAMS -> loadConstructors(year, force)
            StandingsMode.FORM -> loadFormGuide(year, force)
            StandingsMode.PROGRESS -> loadEvolution(year, force)
            StandingsMode.RELIABILITY -> loadReliability(year, force)
            StandingsMode.WDC -> loadWdc(year, force)
        }
    }

    /**
     * Builds the season form guide client-side from per-round race results —
     * there's no season-aggregate endpoint for finishing position (only for
     * cumulative points, via standings-evolution), so this fans out one
     * request per completed round, same tradeoff the iOS/web builds make.
     */
    private fun loadFormGuide(year: Int, force: Boolean) {
        if (!force && formGuideYear == year && _formGuide.value is UiState.Loaded) return
        formGuideYear = year
        viewModelScope.launch {
            _formGuide.value = UiState.Loading
            val scheduleResult = repository.schedule(year)
            val schedule = scheduleResult.getOrNull()
            if (schedule == null) {
                _formGuide.value = UiState.Failed(
                    repository.messageFor(scheduleResult.exceptionOrNull() ?: Exception("Unknown error")),
                )
                return@launch
            }
            val completedRounds = schedule.filter { it.completed }.sortedBy { it.round }
            if (completedRounds.isEmpty()) {
                _formGuide.value = UiState.Loaded(SeasonFormGuide(emptyList(), emptyList()))
                return@launch
            }

            val resultsByRound: Map<Int, Map<String, ResultEntryDto>> = coroutineScope {
                completedRounds.map { event ->
                    async {
                        val response = repository.results(year, event.round, "R").getOrNull()
                        val byDriver = response?.results.orEmpty().associateBy {
                            it.driverId ?: it.abbreviation ?: it.fullName ?: UUID.randomUUID().toString()
                        }
                        event.round to byDriver
                    }
                }.awaitAll().toMap()
            }

            val rosterOrder = LinkedHashMap<String, Pair<String, String>>()
            for (event in completedRounds) {
                val byDriver = resultsByRound[event.round] ?: continue
                for ((key, entry) in byDriver) {
                    if (!rosterOrder.containsKey(key)) {
                        rosterOrder[key] = (entry.fullName ?: entry.abbreviation ?: key) to
                            (entry.abbreviation ?: key.take(3).uppercase())
                    }
                }
            }

            val standingsOrder = repository.driverStandings(year).getOrNull()
                ?.mapNotNull { it.driverId }
                .orEmpty()
            val orderedKeys = rosterOrder.keys.sortedWith(
                compareBy(
                    { key -> standingsOrder.indexOf(key).let { if (it < 0) Int.MAX_VALUE else it } },
                    { key -> key },
                ),
            )

            val drivers = orderedKeys.map { key ->
                val (name, code) = rosterOrder.getValue(key)
                val cells = completedRounds.mapNotNull { event ->
                    resultsByRound[event.round]?.get(key)?.let { event.round to it }
                }.toMap()
                SeasonFormDriverRow(id = key, name = name, code = code, cells = cells)
            }

            _formGuide.value = UiState.Loaded(SeasonFormGuide(completedRounds, drivers))
        }
    }

    fun selectFormCell(driver: SeasonFormDriverRow, round: RaceEventDto, entry: ResultEntryDto?) {
        val resultLabel = when {
            entry == null -> "No result"
            entry.status != null && !isFinishStatus(entry.status) -> entry.status
            else -> "P${entry.positionLabel}"
        }
        _formGuideSelection.value = FormGuideSelection(driver.name, round.displayName, resultLabel)
    }

    private fun loadDrivers(year: Int, force: Boolean) {
        if (!force && _drivers.value is UiState.Loaded) return
        viewModelScope.launch {
            _drivers.value = UiState.Loading
            repository.driverStandings(year)
                .onSuccess { _drivers.value = UiState.Loaded(it) }
                .onFailure { _drivers.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    private fun loadConstructors(year: Int, force: Boolean) {
        if (!force && _constructors.value is UiState.Loaded) return
        viewModelScope.launch {
            _constructors.value = UiState.Loading
            repository.constructorStandings(year)
                .onSuccess { _constructors.value = UiState.Loaded(it) }
                .onFailure { _constructors.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    private fun loadEvolution(year: Int, force: Boolean) {
        if (!force && _evolution.value is UiState.Loaded) return
        viewModelScope.launch {
            _evolution.value = UiState.Loading
            repository.standingsEvolution(year)
                .onSuccess { _evolution.value = UiState.Loaded(it) }
                .onFailure { _evolution.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    private fun loadReliability(year: Int, force: Boolean) {
        if (!force && _reliability.value is UiState.Loaded) return
        viewModelScope.launch {
            _reliability.value = UiState.Loading
            repository.reliability(year)
                .onSuccess { _reliability.value = UiState.Loaded(it) }
                .onFailure { _reliability.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    private fun loadWdc(year: Int, force: Boolean) {
        if (!force && _wdc.value is UiState.Loaded) return
        viewModelScope.launch {
            _wdc.value = UiState.Loading
            repository.wdcCalculator(year, _wdcThroughRound.value)
                .onSuccess { _wdc.value = UiState.Loaded(it) }
                .onFailure { _wdc.value = UiState.Failed(repository.messageFor(it)) }
        }
        loadWdcSchedule(year)
    }

    /**
     * Best-effort fetch of the season schedule so the "time machine" scrubber knows which
     * rounds have actually completed. Failure here shouldn't block the WDC calculator itself,
     * so errors are swallowed and the scrubber simply stays hidden.
     */
    private fun loadWdcSchedule(year: Int, force: Boolean = false) {
        if (!force && wdcScheduleYear == year) return
        wdcScheduleYear = year
        viewModelScope.launch {
            repository.schedule(year)
                .onSuccess { events ->
                    val completed = events.filter { it.completed }
                    _wdcCompletedRounds.value = completed
                    warmWdcTimeMachineCache(year, completed)
                }
                .onFailure { _wdcCompletedRounds.value = emptyList() }
        }
    }

    /**
     * Fires a background, best-effort request for a historical round as soon as we know the
     * season has one, so the backend's shared per-season computation happens during page load
     * instead of during the user's first slider drag. The backend caches that computation per
     * year regardless of which round is requested, so warming any single round makes the
     * *entire* scrubber fast, not just that one round. The result is discarded, this is purely
     * to warm the server-side cache.
     */
    private fun warmWdcTimeMachineCache(year: Int, completedRounds: List<RaceEventDto>) {
        if (wdcWarmedYear == year) return
        val lastRound = completedRounds.maxOfOrNull { it.round } ?: return
        wdcWarmedYear = year
        viewModelScope.launch {
            repository.wdcCalculator(year, lastRound)
        }
    }

    /**
     * Moves the WDC calculator's "time machine" to a different round, or back to live
     * standings when [round] is null. Always force-reloads since the round, not the year,
     * changed underneath the same cached state.
     */
    fun setWdcThroughRound(round: Int?) {
        if (_wdcThroughRound.value == round) return
        _wdcThroughRound.value = round
        val year = loadedYear ?: return
        loadWdc(year, force = true)
    }
}

/** True for a classified finish; false for DNF/DNS/DSQ/DNQ-style statuses. */
fun isFinishStatus(status: String): Boolean {
    val s = status.lowercase()
    return s.isEmpty() || s == "finished" || s.startsWith("+")
}
