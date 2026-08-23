package com.owlmedia.racecontrol.feature.racedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto
import com.owlmedia.racecontrol.data.remote.dto.SessionResultsDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RaceDetailViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _event = MutableStateFlow<UiState<RaceEventDto>>(UiState.Idle)
    val event: StateFlow<UiState<RaceEventDto>> = _event.asStateFlow()

    private val _results = MutableStateFlow<UiState<SessionResultsDto>>(UiState.Idle)
    val results: StateFlow<UiState<SessionResultsDto>> = _results.asStateFlow()

    private val _selectedSession = MutableStateFlow("R")
    val selectedSession: StateFlow<String> = _selectedSession.asStateFlow()

    private var resultsKey: String? = null

    /**
     * The schedule payload carries the session list and completion flag, and a
     * deep link from a notification arrives with only year+round, so the event
     * is always re-fetched rather than passed through navigation arguments.
     */
    fun loadEvent(year: Int, round: Int) {
        if (_event.value is UiState.Loaded) return
        viewModelScope.launch {
            _event.value = UiState.Loading
            repository.schedule(year)
                .onSuccess { events ->
                    val match = events.firstOrNull { it.round == round }
                    _event.value = if (match != null) {
                        UiState.Loaded(match)
                    } else {
                        UiState.Failed("Round $round was not found in the $year calendar.")
                    }
                }
                .onFailure { _event.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    fun selectSession(session: String) {
        if (_selectedSession.value == session) return
        _selectedSession.value = session
    }

    fun loadResults(year: Int, round: Int, session: String) {
        val key = "$year-$round-$session"
        if (resultsKey == key && _results.value is UiState.Loaded) return
        viewModelScope.launch {
            _results.value = UiState.Loading
            repository.results(year, round, session)
                .onSuccess {
                    _results.value = UiState.Loaded(it)
                    resultsKey = key
                }
                .onFailure { _results.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}
