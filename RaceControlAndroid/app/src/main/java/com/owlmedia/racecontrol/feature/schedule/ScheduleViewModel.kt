package com.owlmedia.racecontrol.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<RaceEventDto>>>(UiState.Idle)
    val state: StateFlow<UiState<List<RaceEventDto>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var loadedYear: Int? = null

    fun load(year: Int, force: Boolean = false) {
        if (!force && loadedYear == year && _state.value is UiState.Loaded) return
        viewModelScope.launch {
            if (force) _refreshing.value = true else _state.value = UiState.Loading
            repository.schedule(year)
                .onSuccess {
                    _state.value = UiState.Loaded(it)
                    loadedYear = year
                }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
            _refreshing.value = false
        }
    }
}
