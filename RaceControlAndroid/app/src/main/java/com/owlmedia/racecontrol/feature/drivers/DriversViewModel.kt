package com.owlmedia.racecontrol.feature.drivers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.local.FavoritesStore
import com.owlmedia.racecontrol.data.remote.dto.DriverDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DriversViewModel @Inject constructor(
    private val repository: RaceControlRepository,
    private val favorites: FavoritesStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<DriverDto>>>(UiState.Idle)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var loadedYear: Int? = null

    /**
     * Search and favourites are applied here rather than in the composable so
     * the work happens once per data change, not on every recomposition.
     *
     * Favourites float to the top, matching the iOS behaviour.
     */
    val state: StateFlow<UiState<List<DriverDto>>> =
        combine(_state, _query, favorites.driverIds) { state, query, favouriteIds ->
            if (state !is UiState.Loaded) return@combine state
            val filtered = state.value.filter { it.matches(query) }
            val sorted = filtered.sortedWith(
                compareByDescending<DriverDto> { it.driverId in favouriteIds }
                    .thenBy { it.positionInt ?: Int.MAX_VALUE }
            )
            UiState.Loaded(sorted, state.fromCache)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Idle)

    val favoriteIds: StateFlow<Set<String>> =
        favorites.driverIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun load(year: Int, force: Boolean = false) {
        if (!force && loadedYear == year && _state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.drivers(year)
                .onSuccess {
                    _state.value = UiState.Loaded(it)
                    loadedYear = year
                }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggleFavorite(driverId: String) {
        viewModelScope.launch { favorites.toggleDriver(driverId) }
    }
}

/** Name, three-letter code or car number: the three things people search by. */
private fun DriverDto.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return fullName.lowercase().contains(q) ||
        code?.lowercase()?.contains(q) == true ||
        numberString?.contains(q) == true ||
        teamName?.lowercase()?.contains(q) == true
}
