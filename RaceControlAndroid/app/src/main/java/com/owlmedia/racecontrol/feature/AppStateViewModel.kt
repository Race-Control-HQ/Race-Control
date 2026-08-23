package com.owlmedia.racecontrol.feature

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Year
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-wide state: the available seasons and the one currently selected, shared
 * by every tab. The counterpart of the iOS `AppState` environment object.
 *
 * The selected year is kept in [SavedStateHandle] so it survives both rotation
 * and process death, neither of which the iOS app has to think about.
 */
@HiltViewModel
class AppStateViewModel @Inject constructor(
    private val repository: RaceControlRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private companion object {
        const val KEY_YEAR = "selected_year"
    }

    private val _state = MutableStateFlow(
        AppState(selectedYear = savedState[KEY_YEAR] ?: Year.now().value)
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        loadSeasons()
    }

    fun loadSeasons() {
        if (_state.value.seasons.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingSeasons = true) }
            // seasonsOrFallback never fails: an unreachable server still lets
            // the user browse, exactly as the iOS AppState fallback does.
            val years = repository.seasonsOrFallback()
            val latest = years.firstOrNull() ?: Year.now().value
            val restored: Int? = savedState[KEY_YEAR]
            val selected = restored?.takeIf { it in years } ?: latest
            savedState[KEY_YEAR] = selected
            _state.update {
                it.copy(seasons = years, selectedYear = selected, loadingSeasons = false)
            }
        }
    }

    fun selectYear(year: Int) {
        savedState[KEY_YEAR] = year
        _state.update { it.copy(selectedYear = year) }
    }
}

data class AppState(
    val seasons: List<Int> = emptyList(),
    val selectedYear: Int = Year.now().value,
    val loadingSeasons: Boolean = false,
)
