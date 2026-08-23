package com.owlmedia.racecontrol.feature.replay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.CircuitMapDto
import com.owlmedia.racecontrol.data.remote.dto.RaceReplayDto
import com.owlmedia.racecontrol.data.remote.dto.ReplayFrameDto
import com.owlmedia.racecontrol.data.remote.dto.ReplayLapPositionsDto
import com.owlmedia.racecontrol.data.remote.dto.ReplayPositionsDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class ReplayViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<RaceReplayDto>>(UiState.Idle)
    val state: StateFlow<UiState<RaceReplayDto>> = _state.asStateFlow()

    private val _currentLap = MutableStateFlow(1)
    val currentLap: StateFlow<Int> = _currentLap.asStateFlow()

    private val _raceProgress = MutableStateFlow(0f)
    val raceProgress: StateFlow<Float> = _raceProgress.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _circuitMap = MutableStateFlow<CircuitMapDto?>(null)
    val circuitMap: StateFlow<CircuitMapDto?> = _circuitMap.asStateFlow()

    private val _replayPositions = MutableStateFlow<ReplayPositionsDto?>(null)
    val replayPositions: StateFlow<ReplayPositionsDto?> = _replayPositions.asStateFlow()

    private val _isSpatialLoading = MutableStateFlow(true)
    val isSpatialLoading: StateFlow<Boolean> = _isSpatialLoading.asStateFlow()

    private var replay: RaceReplayDto? = null
    private var framesByLap: Map<Int, ReplayFrameDto> = emptyMap()
    private var positionsByLap: Map<Int, ReplayLapPositionsDto> = emptyMap()
    private val secondsPerLap = 14f

    /**
     * The playback loop.
     *
     * A coroutine in [viewModelScope] rather than a Timer: it is cancelled
     * automatically when the ViewModel clears, and [stop] is also called when
     * the screen leaves the composition, so playback can never outlive the UI
     * the way a stray Timer would.
     */
    private var playbackJob: Job? = null

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            _isSpatialLoading.value = true
            coroutineScope {
                val replayRequest = async { repository.replay(year, round) }
                val circuitRequest = async { repository.circuitMap(year, round) }
                val positionsRequest = async { repository.replayPositions(year, round) }

                replayRequest.await()
                    .onSuccess { data ->
                        replay = data
                        framesByLap = data.frames.associateBy { it.lap }
                        _raceProgress.value =
                            ((data.frames.firstOrNull()?.lap ?: 1) - 1).coerceAtLeast(0).toFloat()
                        updateCurrentLap()
                        _state.value = UiState.Loaded(data)
                    }
                    .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }

                _circuitMap.value = circuitRequest.await().getOrNull()
                _replayPositions.value = positionsRequest.await().getOrNull()
                positionsByLap = _replayPositions.value?.laps?.associateBy { it.lap }.orEmpty()
            }
            _isSpatialLoading.value = false
        }
    }

    fun frameFor(lap: Int): ReplayFrameDto? =
        framesByLap[lap] ?: framesByLap.filterKeys { it <= lap }.maxByOrNull { it.key }?.value
            ?: framesByLap.minByOrNull { it.key }?.value

    fun positionsFor(lap: Int): ReplayLapPositionsDto? = positionsByLap[lap]

    fun lapFraction(): Float {
        val total = replay?.totalLaps ?: return 0f
        if (_raceProgress.value >= total.toFloat()) return 1f
        return _raceProgress.value - kotlin.math.floor(_raceProgress.value)
    }

    /** Position on the previous lap, used to draw the movement arrows. */
    fun previousPosition(driver: String, lap: Int): Int? {
        if (lap <= 1) return null
        val previous = framesByLap[lap - 1]
            ?: framesByLap.filterKeys { it < lap }.maxByOrNull { it.key }?.value
        return previous?.order?.firstOrNull { it.driver == driver }?.position
    }

    fun scrubTo(lap: Int) {
        val total = replay?.totalLaps ?: return
        _raceProgress.value = (lap.coerceIn(1, total) - 1).toFloat()
        updateCurrentLap()
    }

    fun scrubToProgress(progress: Float) {
        val total = replay?.totalLaps ?: return
        _raceProgress.value = progress.coerceIn(0f, total.coerceAtLeast(1).toFloat())
        updateCurrentLap()
    }

    fun togglePlay() {
        if (_isPlaying.value) stop() else play()
    }

    fun setSpeed(value: Float) {
        _speed.value = value
    }

    private fun play() {
        val total = replay?.totalLaps ?: return
        if (_raceProgress.value >= total.toFloat()) {
            _raceProgress.value = 0f
            updateCurrentLap()
        }
        _isPlaying.value = true
        startLoop()
    }

    fun stop() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun startLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var previousNanos = System.nanoTime()
            while (isActive) {
                delay(33L)
                val now = System.nanoTime()
                val elapsedSeconds =
                    ((now - previousNanos) / 1_000_000_000.0).toFloat().coerceAtMost(0.25f)
                previousNanos = now
                val total = replay?.totalLaps ?: break
                _raceProgress.value += elapsedSeconds * _speed.value / secondsPerLap
                if (_raceProgress.value >= total.toFloat()) {
                    _raceProgress.value = total.toFloat()
                    updateCurrentLap()
                    stop()
                    break
                }
                updateCurrentLap()
            }
        }
    }

    private fun updateCurrentLap() {
        val total = replay?.totalLaps?.coerceAtLeast(1) ?: 1
        _currentLap.value = (_raceProgress.value.toInt() + 1).coerceIn(1, total)
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
