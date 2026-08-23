package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.layout.size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.FlagPeriodDto
import com.owlmedia.racecontrol.data.remote.dto.LapTimeDriverDto
import com.owlmedia.racecontrol.data.remote.dto.RaceDriverDto
import com.owlmedia.racecontrol.data.remote.dto.TelemetryTraceDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class TelemetryViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    /** Three overlaid traces is the most that stays readable on a phone. */
    companion object {
        const val MAX_DRIVERS = 3

        /** Sentinel the backend accepts for "this driver's quickest lap of the race". */
        const val FASTEST_LAP = "fastest"
    }

    private val _drivers = MutableStateFlow<UiState<List<RaceDriverDto>>>(UiState.Idle)
    val drivers: StateFlow<UiState<List<RaceDriverDto>>> = _drivers.asStateFlow()

    private val _selected = MutableStateFlow<List<String>>(emptyList())
    val selected: StateFlow<List<String>> = _selected.asStateFlow()

    private val _traces = MutableStateFlow<List<TelemetryTraceDto>>(emptyList())
    val traces: StateFlow<List<TelemetryTraceDto>> = _traces.asStateFlow()

    private val _loadingTraces = MutableStateFlow(false)
    val loadingTraces: StateFlow<Boolean> = _loadingTraces.asStateFlow()

    private val _traceError = MutableStateFlow<String?>(null)
    val traceError: StateFlow<String?> = _traceError.asStateFlow()

    /** Distance along the lap, in metres, that the replay playhead is at. */
    private val _playhead = MutableStateFlow(0.0)
    val playhead: StateFlow<Double> = _playhead.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Badges the "Safety Car (lap 20)" banner on the currently-viewed lap. A
    // failed fetch just means no badge shows; telemetry itself still works.
    private val _flagPeriods = MutableStateFlow<List<FlagPeriodDto>>(emptyList())
    val flagPeriods: StateFlow<List<FlagPeriodDto>> = _flagPeriods.asStateFlow()

    // Reuses the lap-times endpoint purely as a source of "which laps exist for
    // this driver, with what time/compound" so the lap picker has something to
    // list; this screen doesn't otherwise touch lap-time evolution.
    private val _lapsByDriver = MutableStateFlow<Map<String, LapTimeDriverDto>>(emptyMap())
    val lapsByDriver: StateFlow<Map<String, LapTimeDriverDto>> = _lapsByDriver.asStateFlow()

    // Per-driver selected lap ("fastest" or a lap-number string, per the
    // backend's `lap` query param). Absent entries default to fastest.
    private val _selectedLaps = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedLaps: StateFlow<Map<String, String>> = _selectedLaps.asStateFlow()

    private var playbackJob: Job? = null
    private var year: Int = 0
    private var round: Int = 0

    fun loadDrivers(year: Int, round: Int) {
        this.year = year
        this.round = round
        if (_drivers.value is UiState.Loaded) return
        viewModelScope.launch {
            _drivers.value = UiState.Loading
            repository.raceDrivers(year, round)
                .onSuccess { _drivers.value = UiState.Loaded(it) }
                .onFailure { _drivers.value = UiState.Failed(repository.messageFor(it)) }
        }
        viewModelScope.launch {
            repository.flags(year, round).onSuccess { _flagPeriods.value = it.periods }
        }
        // Supplementary, like flags: a failed fetch just means the lap picker
        // only offers "Fastest"; telemetry itself still works.
        viewModelScope.launch {
            repository.lapTimes(year, round).onSuccess { data ->
                _lapsByDriver.value = data.drivers.associateBy { it.code }
            }
        }
    }

    fun toggleDriver(code: String) {
        val current = _selected.value
        _selected.value = when {
            code in current -> current - code
            current.size >= MAX_DRIVERS -> current
            else -> current + code
        }
        if (code !in _selected.value) {
            // Drop the stale lap choice so re-adding the driver later starts
            // fresh at "Fastest" instead of resurrecting an old selection.
            _selectedLaps.value = _selectedLaps.value - code
        }
        loadTraces()
    }

    /** [lap] is `"fastest"` or a lap-number string, per the backend's `lap` param. */
    fun selectLap(code: String, lap: String) {
        _selectedLaps.value = _selectedLaps.value + (code to lap)
        loadTraces()
    }

    private fun loadTraces() {
        val codes = _selected.value
        stopReplay()
        if (codes.isEmpty()) {
            _traces.value = emptyList()
            _traceError.value = null
            return
        }
        viewModelScope.launch {
            _loadingTraces.value = true
            _traceError.value = null

            // Fetched in parallel: three sequential telemetry calls against a
            // cold backend is a genuinely long wait.
            val laps = _selectedLaps.value
            val results = codes.map { code ->
                async { repository.telemetry(year, round, code, laps[code] ?: FASTEST_LAP) }
            }.awaitAll()

            val loaded = results.mapNotNull { result ->
                result.getOrNull()?.takeIf { it.available }?.trace
            }
            val firstError = results.firstOrNull { it.isFailure }?.exceptionOrNull()

            _traces.value = loaded
            _traceError.value = when {
                loaded.isNotEmpty() -> null
                firstError != null -> repository.messageFor(firstError)
                else -> null
            }
            _playhead.value = 0.0
            _loadingTraces.value = false
        }
    }

    fun setPlayhead(distance: Double) {
        val max = _traces.value.maxOfOrNull { it.maxDistance } ?: return
        _playhead.value = distance.coerceIn(0.0, max)
    }

    fun toggleReplay() {
        if (_isPlaying.value) stopReplay() else startReplay()
    }

    private fun startReplay() {
        val max = _traces.value.maxOfOrNull { it.maxDistance } ?: return
        if (max <= 0.0) return
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            if (_playhead.value >= max) _playhead.value = 0.0
            // ~8 seconds to sweep a lap, at roughly 60 steps a second.
            val step = max / (8 * 60)
            while (isActive) {
                delay(16L)
                val next = _playhead.value + step
                if (next >= max) {
                    _playhead.value = max
                    stopReplay()
                    break
                }
                _playhead.value = next
            }
        }
    }

    fun stopReplay() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopReplay()
    }
}
