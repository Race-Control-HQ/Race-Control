package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcPalette
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.BarSegment
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.RcWaterfallBars
import com.owlmedia.racecontrol.core.ui.StackedBar
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.core.ui.WaterfallSegment
import com.owlmedia.racecontrol.core.util.LapTimeFormat
import com.owlmedia.racecontrol.data.remote.dto.PitStopsResponseDto
import com.owlmedia.racecontrol.data.remote.dto.QualifyingSectorsResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PitStopsViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PitStopsResponseDto>>(UiState.Idle)
    val state = _state.asStateFlow()
    fun load(year: Int, round: Int, force: Boolean = false) {
        if (!force && _state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.pitStops(year, round)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

@Composable
fun PitStopsScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: PitStopsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }
    RcDetailScaffold(title = "Pit Stops", onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round, true) }, modifier) { data ->
            if (!data.available || data.stops.isEmpty()) {
                EmptyState(Icons.Filled.ShowChart, "No Pit-Stop Data", "No timed pit-lane transits are available.")
                return@LoadableContent
            }
            LazyColumn(
                contentPadding = PaddingValues(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.SM),
            ) {
                item {
                    Text(
                        "${data.eventName ?: title} · circuit median " +
                            "${LapTimeFormat.format(data.circuitMedianLossMs ?: 0, false)}s",
                        color = RcTheme.colors.textSecondary,
                    )
                }
                items(data.stops, key = { it.id }) { stop ->
                    RcCard {
                        Column(Modifier.padding(Dimens.SM), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${stop.driverCode} · lap ${stop.lap}", style = MaterialTheme.typography.titleSmall)
                                Text(stop.outcome, color = teamColor(stop.teamColor))
                            }
                            StackedBar(
                                listOf(BarSegment(stop.lossMs.toFloat(), teamColor(stop.teamColor))),
                                height = 16.dp,
                            )
                            Text(
                                "${stop.lossMs / 1000.0}s · P${stop.entryPosition ?: "–"} → " +
                                    "P${stop.rejoinPosition ?: "–"}" +
                                    if (stop.rivals.isEmpty()) "" else " · ${stop.rivals.joinToString()}",
                                color = RcTheme.colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class QualifyingSectorsViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<QualifyingSectorsResponseDto>>(UiState.Idle)
    val state = _state.asStateFlow()
    fun load(year: Int, round: Int, force: Boolean = false) {
        if (!force && _state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.qualifyingSectors(year, round)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

@Composable
fun QualifyingSectorsScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: QualifyingSectorsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }
    RcDetailScaffold(title = "Sector Waterfall", onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round, true) }, modifier) { data ->
            if (!data.available || data.drivers.isEmpty()) {
                EmptyState(Icons.Filled.ShowChart, "No Sector Data", "No complete qualifying sectors are available.")
                return@LoadableContent
            }
            LazyColumn(
                contentPadding = PaddingValues(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.SM),
            ) {
                item {
                    Text(
                        "Sector contribution against ${data.poleCode ?: "pole"}; left of centre is faster.",
                        color = RcTheme.colors.textSecondary,
                    )
                }
                items(data.drivers, key = { it.code }) { driver ->
                    RcCard {
                        Column(Modifier.padding(Dimens.SM), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(driver.code, color = teamColor(driver.teamColor), style = MaterialTheme.typography.titleSmall)
                                Text(LapTimeFormat.format(driver.lapMs, true))
                            }
                            RcWaterfallBars(
                                driver.sectorDeltaMs.mapIndexed { index, value ->
                                    WaterfallSegment(
                                        value.toFloat(),
                                        listOf(RcPalette.FlagVirtualSafetyCar, RcPalette.Info, RcPalette.Warning)[index.coerceAtMost(2)],
                                    )
                                },
                            )
                            Text(
                                "S1/S2/S3 ${driver.sectorDeltaMs.joinToString(" / ") { "%+.3f".format(it / 1000.0) }} · " +
                                    "ideal ${LapTimeFormat.format(driver.idealLapMs, true)} · " +
                                    "${driver.speedST?.let { "%.0f km/h".format(it) } ?: "no speed trap"}",
                                color = RcTheme.colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
