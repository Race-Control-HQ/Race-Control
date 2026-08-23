package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.design.FlagStyle
import com.owlmedia.racecontrol.core.ui.ChartBand
import com.owlmedia.racecontrol.core.ui.ChartDomain
import com.owlmedia.racecontrol.core.ui.ChartPoint
import com.owlmedia.racecontrol.core.ui.ChartSeries
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.RcLineChart
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.core.util.LapTimeFormat
import com.owlmedia.racecontrol.data.remote.dto.FlagPeriodDto
import com.owlmedia.racecontrol.data.remote.dto.LapTimeDriverDto
import com.owlmedia.racecontrol.data.remote.dto.LapTimesResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LapTimesViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<LapTimesResponseDto>>(UiState.Idle)
    val state: StateFlow<UiState<LapTimesResponseDto>> = _state.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _hideOutliers = MutableStateFlow(true)
    val hideOutliers: StateFlow<Boolean> = _hideOutliers.asStateFlow()

    // Flag periods only band the chart; they are not the primary data for this
    // screen, so a failed fetch is silently ignored rather than surfacing a
    // second error state alongside the lap-time one.
    private val _flagPeriods = MutableStateFlow<List<FlagPeriodDto>>(emptyList())
    val flagPeriods: StateFlow<List<FlagPeriodDto>> = _flagPeriods.asStateFlow()

    fun load(year: Int, round: Int) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.lapTimes(year, round)
                .onSuccess { data ->
                    _state.value = UiState.Loaded(data)
                    // Three lines is about the limit of what reads on a phone;
                    // starting with the whole field is unreadable noise.
                    _selected.value = data.drivers.take(3).map { it.code }.toSet()
                }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
        viewModelScope.launch {
            repository.flags(year, round).onSuccess { _flagPeriods.value = it.periods }
        }
    }

    fun toggle(code: String) {
        _selected.value = if (code in _selected.value) {
            _selected.value - code
        } else {
            _selected.value + code
        }
    }

    fun selectAll(drivers: List<LapTimeDriverDto>) {
        _selected.value = drivers.map { it.code }.toSet()
    }

    fun selectNone() {
        _selected.value = emptySet()
    }

    fun setHideOutliers(value: Boolean) {
        _hideOutliers.value = value
    }
}

/**
 * Safety-car laps and in-laps are many seconds slower than a green-flag lap and
 * would flatten the whole chart into a line at the bottom. Cutting at 107% of
 * the driver's own median keeps the racing laps legible, the same threshold
 * the iOS build uses.
 */
private fun outlierCutoff(driver: LapTimeDriverDto): Double {
    val times = driver.laps.map { it.seconds }.sorted()
    if (times.isEmpty()) return Double.MAX_VALUE
    val median = times[times.size / 2]
    return median * 1.07
}

@Composable
fun LapTimesScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: LapTimesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val hideOutliers by viewModel.hideOutliers.collectAsStateWithLifecycle()
    val flagPeriods by viewModel.flagPeriods.collectAsStateWithLifecycle()

    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = stringResource(R.string.analysis_lap_times), onBack = onBack) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { data ->
            if (data.drivers.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.ShowChart,
                    title = stringResource(R.string.no_lap_data_title),
                    message = stringResource(R.string.no_lap_data_message),
                )
                return@LoadableContent
            }

            val visible = remember(data, selected, hideOutliers) {
                data.drivers
                    .filter { it.code in selected }
                    .map { driver ->
                        val cutoff = if (hideOutliers) outlierCutoff(driver) else Double.MAX_VALUE
                        ChartSeries(
                            id = driver.code,
                            color = teamColor(driver.teamColor).legibleOnSurface(),
                            points = driver.laps
                                .filter { it.seconds <= cutoff }
                                .map { ChartPoint(it.lap.toDouble(), it.seconds) },
                        )
                    }
                    .filter { it.points.isNotEmpty() }
            }

            val domain = remember(visible) { ChartDomain.cover(visible) }
            val axisLabels = remember(domain) {
                (0..4).map { index ->
                    val value = domain.minY + domain.spanY * (index / 4.0)
                    LapTimeFormat.fromSeconds(value)
                }
            }
            // Widened half a lap either side so a one-lap period reads as a
            // visible band rather than a hairline between two grid columns.
            val bands = remember(flagPeriods) {
                flagPeriods.map { period ->
                    ChartBand(
                        minX = period.startLap - 0.5,
                        maxX = period.endLap + 0.5,
                        color = FlagStyle.color(period.periodType).copy(alpha = 0.16f),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.SM),
            ) {
                Text(
                    text = data.eventName ?: title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RcTheme.colors.textSecondary,
                )

                RcCard {
                    if (visible.isEmpty()) {
                        Text(
                            text = stringResource(R.string.telemetry_pick_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = RcTheme.colors.textTertiary,
                        )
                    } else {
                        RcLineChart(
                            series = visible,
                            domain = domain,
                            yAxisLabels = axisLabels,
                            height = 260.dp,
                            bands = bands,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = hideOutliers,
                        onCheckedChange = viewModel::setHideOutliers,
                    )
                    Text(
                        text = stringResource(R.string.hide_outliers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RcTheme.colors.textSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Dimens.SM),
                    )
                    TextButton(onClick = { viewModel.selectAll(data.drivers) }) {
                        Text(stringResource(R.string.select_all))
                    }
                    TextButton(onClick = viewModel::selectNone) {
                        Text(stringResource(R.string.select_none))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
                ) {
                    data.drivers.forEach { driver ->
                        val accent = teamColor(driver.teamColor).legibleOnSurface()
                        FilterChip(
                            selected = driver.code in selected,
                            onClick = { viewModel.toggle(driver.code) },
                            label = { Text(driver.code) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.3f),
                                selectedLabelColor = RcTheme.colors.textPrimary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
