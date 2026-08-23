@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.owlmedia.racecontrol.feature.circuits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.CountryFlag
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcTabScaffold
import com.owlmedia.racecontrol.core.ui.SeasonPickerChip
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.CircuitDto
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import com.owlmedia.racecontrol.feature.AppState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A circuit paired with the calendar event it belongs to, if there is one. */
data class CircuitEntry(
    val circuit: CircuitDto,
    val event: RaceEventDto?,
) {
    val id: String get() = circuit.id
    val isRaced: Boolean get() = event?.completed == true
}

@HiltViewModel
class CircuitsViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<CircuitEntry>>>(UiState.Idle)
    val state: StateFlow<UiState<List<CircuitEntry>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var loadedYear: Int? = null

    /**
     * Circuits are returned without a round number, so they are paired against
     * the calendar to recover ordering and the raced/upcoming status. Matching
     * is by locality and country because circuit ids differ between the two
     * upstream sources.
     */
    fun load(year: Int, force: Boolean = false) {
        if (!force && loadedYear == year && _state.value is UiState.Loaded) return
        viewModelScope.launch {
            if (force) _refreshing.value = true else _state.value = UiState.Loading

            val circuitsResult = repository.circuits(year)
            val scheduleResult = repository.schedule(year)

            circuitsResult
                .onSuccess { circuits ->
                    val events = scheduleResult.getOrDefault(emptyList())
                    val entries = circuits.map { circuit ->
                        CircuitEntry(circuit, events.matchFor(circuit))
                    }.sortedBy { it.event?.round ?: Int.MAX_VALUE }
                    _state.value = UiState.Loaded(entries)
                    loadedYear = year
                }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }

            _refreshing.value = false
        }
    }
}

private fun List<RaceEventDto>.matchFor(circuit: CircuitDto): RaceEventDto? {
    val locality = circuit.locality?.lowercase()
    val country = circuit.country?.lowercase()
    return firstOrNull { event ->
        val eventLocation = event.location?.lowercase()
        val eventCountry = event.country?.lowercase()
        (locality != null && eventLocation == locality) ||
            (locality != null && eventLocation?.contains(locality) == true) ||
            (country != null && eventCountry == country)
    }
}

@Composable
fun CircuitsScreen(
    appState: AppState,
    onSelectYear: (Int) -> Unit,
    onOpenCircuit: (year: Int, round: Int, name: String) -> Unit,
    viewModel: CircuitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    LaunchedEffect(appState.selectedYear) { viewModel.load(appState.selectedYear) }

    RcTabScaffold(
        title = stringResource(R.string.circuits),
        actions = {
            SeasonPickerChip(
                seasons = appState.seasons,
                selected = appState.selectedYear,
                onSelect = onSelectYear,
            )
            Spacer(Modifier.width(Dimens.SM))
        },
    ) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(appState.selectedYear, force = true) },
            modifier = modifier,
        ) { entries ->
            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Map,
                    title = stringResource(R.string.no_circuits_title),
                    message = stringResource(
                        R.string.no_circuits_message,
                        appState.selectedYear.toString(),
                    ),
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.load(appState.selectedYear, force = true) },
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(Dimens.MD),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SM),
                    ) {
                        items(
                            items = entries,
                            key = { it.id },
                            contentType = { "circuit" },
                        ) { entry ->
                            CircuitRow(
                                entry = entry,
                                onClick = {
                                    val round = entry.event?.round ?: return@CircuitRow
                                    onOpenCircuit(
                                        appState.selectedYear,
                                        round,
                                        entry.circuit.name.orEmpty(),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircuitRow(entry: CircuitEntry, onClick: () -> Unit) {
    val circuit = entry.circuit
    // Only a raced circuit has a track map and results worth opening; upcoming
    // venues stay non-interactive rather than leading to an empty screen.
    val enabled = entry.isRaced

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Dimens.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        Text(
            text = CountryFlag.flag(circuit.country),
            fontSize = 28.sp,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = circuit.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) RcTheme.colors.textPrimary else RcTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(circuit.locality, circuit.country).joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = RcTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusBadge(isRaced = entry.isRaced)
    }
}

@Composable
private fun StatusBadge(isRaced: Boolean) {
    val (label, color) = if (isRaced) {
        stringResource(R.string.circuit_raced) to RcTheme.colors.positive
    } else {
        stringResource(R.string.circuit_upcoming) to RcTheme.colors.textTertiary
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = Dimens.SM, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}
