package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.FlagStyle
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.TyreCompound
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.teamColor
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
import com.owlmedia.racecontrol.data.remote.dto.FlagPeriodDto
import com.owlmedia.racecontrol.data.remote.dto.RaceReplayDto
import com.owlmedia.racecontrol.data.remote.dto.RaceTraceResponseDto
import com.owlmedia.racecontrol.data.remote.dto.StrategyResponseDto
import com.owlmedia.racecontrol.data.remote.dto.TyrePerformanceResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RaceTraceViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<RaceTraceResponseDto>>(UiState.Idle)
    val state = _state.asStateFlow()
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected = _selected.asStateFlow()
    private val _mode = MutableStateFlow("median")
    val mode = _mode.asStateFlow()

    fun load(year: Int, round: Int, force: Boolean = false) {
        if (!force && _state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.raceTrace(year, round, _mode.value)
                .onSuccess {
                    _state.value = UiState.Loaded(it)
                    if (_selected.value.isEmpty()) {
                        _selected.value = it.drivers.take(5).map { driver -> driver.code }.toSet()
                    }
                }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    fun setMode(value: String, year: Int, round: Int) {
        if (_mode.value == value) return
        _mode.value = value
        load(year, round, force = true)
    }

    fun toggle(code: String) {
        _selected.value = if (code in _selected.value) _selected.value - code else _selected.value + code
    }
}

@Composable
fun RaceTraceScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: RaceTraceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = stringResource(R.string.analysis_race_trace), onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round, true) }, modifier) { data ->
            if (!data.available || data.drivers.isEmpty()) {
                EmptyState(Icons.Filled.ShowChart, stringResource(R.string.no_race_trace_title),
                    stringResource(R.string.no_race_trace_message))
                return@LoadableContent
            }
            val series = remember(data, selected) {
                data.drivers.filter { it.code in selected }.map { driver ->
                    ChartSeries(
                        id = driver.code,
                        color = teamColor(driver.teamColor).legibleOnSurface(),
                        points = driver.laps.map { ChartPoint(it.lap.toDouble(), it.deltaMs / 1000.0) },
                    )
                }
            }
            val domain = data.yDomainMs?.takeIf { it.size == 2 }?.let {
                ChartDomain(0.0, data.totalLaps.toDouble(), it[0] / 1000.0, it[1] / 1000.0)
            } ?: ChartDomain.cover(series)
            AnalysisChartColumn(data.eventName ?: title) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SM)) {
                    listOf("median" to "Field median", "leader" to "Leader").forEach { (value, label) ->
                        FilterChip(
                            selected = mode == value,
                            onClick = { viewModel.setMode(value, year, round) },
                            label = { Text(label) },
                        )
                    }
                }
                RcCard {
                    RcLineChart(
                        series = series,
                        domain = domain,
                        height = 280.dp,
                        bands = data.periods.map(::flagBand),
                    )
                }
                DriverChips(
                    codes = data.drivers.map { it.code to it.teamColor },
                    selected = selected,
                    onToggle = viewModel::toggle,
                )
                Text(
                    "Vertical distance between lines is the real gap.",
                    color = RcTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

data class PositionChartData(
    val replay: RaceReplayDto,
    val strategy: StrategyResponseDto,
    val flags: List<FlagPeriodDto>,
)

@HiltViewModel
class PositionChartViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PositionChartData>>(UiState.Idle)
    val state = _state.asStateFlow()
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected = _selected.asStateFlow()

    fun load(year: Int, round: Int, force: Boolean = false) {
        if (!force && _state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching {
                coroutineScope {
                    val replay = async { repository.replay(year, round).getOrThrow() }
                    val strategy = async { repository.strategy(year, round).getOrThrow() }
                    val flags = async { repository.flags(year, round).getOrThrow().periods }
                    PositionChartData(replay.await(), strategy.await(), flags.await())
                }
            }.onSuccess {
                _state.value = UiState.Loaded(it)
                if (_selected.value.isEmpty()) _selected.value = it.replay.drivers.take(5).map { d -> d.code }.toSet()
            }.onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    fun toggle(code: String) {
        _selected.value = if (code in _selected.value) _selected.value - code else _selected.value + code
    }
}

@Composable
fun PositionChartScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: PositionChartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = stringResource(R.string.analysis_position_chart), onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round, true) }, modifier) { data ->
            if (data.replay.frames.isEmpty()) {
                EmptyState(Icons.Filled.ShowChart, stringResource(R.string.no_position_data_title),
                    stringResource(R.string.no_position_data_message))
                return@LoadableContent
            }
            val negative = RcTheme.colors.negative
            val series = remember(data, selected, negative) {
                val lines = data.replay.drivers.filter { it.code in selected }.map { driver ->
                    ChartSeries(
                        driver.code,
                        teamColor(driver.teamColor).legibleOnSurface(),
                        data.replay.frames.mapNotNull { frame ->
                            frame.order.firstOrNull { it.driver == driver.code }
                                ?.let { ChartPoint(frame.lap.toDouble(), -it.position.toDouble()) }
                        },
                    )
                }
                val pitMarkers = data.strategy.drivers.filter { it.code in selected }.flatMap { driver ->
                    driver.stints.drop(1).mapNotNull { stint ->
                        positionAt(data.replay, driver.code, stint.startLap)?.let { position ->
                            ChartSeries(
                                "pit-${driver.code}-${stint.startLap}",
                                androidx.compose.ui.graphics.Color.White,
                                listOf(ChartPoint(stint.startLap.toDouble(), -position.toDouble())),
                                showLine = false,
                                showPoints = true,
                                pointRadius = 4.dp,
                            )
                        }
                    }
                }
                val retirements = data.strategy.drivers.filter { it.retired && it.code in selected }.mapNotNull { driver ->
                    val last = data.replay.frames.lastOrNull { frame -> frame.order.any { it.driver == driver.code } }
                    last?.order?.firstOrNull { it.driver == driver.code }?.let { entry ->
                        ChartSeries(
                            "retired-${driver.code}",
                            negative,
                            listOf(ChartPoint(last.lap.toDouble(), -entry.position.toDouble())),
                            showLine = false,
                            showPoints = true,
                            pointRadius = 5.dp,
                        )
                    }
                }
                lines + pitMarkers + retirements
            }
            val driverCount = data.replay.drivers.size.coerceAtLeast(20)
            AnalysisChartColumn(data.replay.eventName ?: title) {
                RcCard {
                    RcLineChart(
                        series = series,
                        domain = ChartDomain(1.0, data.replay.totalLaps.toDouble(), -driverCount.toDouble(), -1.0),
                        yAxisLabels = listOf("P$driverCount", "P15", "P10", "P5", "P1"),
                        height = 300.dp,
                        bands = data.flags.map(::flagBand),
                    )
                }
                DriverChips(
                    data.replay.drivers.map { it.code to it.teamColor },
                    selected,
                    viewModel::toggle,
                )
                Text(
                    "White dots are pit stops; red dots are retirements.",
                    color = RcTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun positionAt(replay: RaceReplayDto, code: String, lap: Int): Int? =
    replay.frames.firstOrNull { it.lap == lap }?.order?.firstOrNull { it.driver == code }?.position

@HiltViewModel
class TyrePerformanceViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<TyrePerformanceResponseDto>>(UiState.Idle)
    val state = _state.asStateFlow()
    private val _compounds = MutableStateFlow<Set<String>>(emptySet())
    val compounds = _compounds.asStateFlow()

    fun load(year: Int, round: Int, force: Boolean = false) {
        if (!force && _state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.tyrePerformance(year, round)
                .onSuccess {
                    _state.value = UiState.Loaded(it)
                    if (_compounds.value.isEmpty()) {
                        _compounds.value = it.stints.mapNotNull { stint -> stint.compound }.toSet()
                    }
                }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    fun toggle(compound: String) {
        _compounds.value = if (compound in _compounds.value) {
            _compounds.value - compound
        } else {
            _compounds.value + compound
        }
    }
}

@Composable
fun TyrePerformanceScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: TyrePerformanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val compounds by viewModel.compounds.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = stringResource(R.string.analysis_tyre_performance), onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round, true) }, modifier) { data ->
            if (!data.available || data.stints.isEmpty()) {
                EmptyState(Icons.Filled.ShowChart, stringResource(R.string.no_tyre_data_title),
                    stringResource(R.string.no_tyre_data_message))
                return@LoadableContent
            }
            val series = remember(data, compounds) {
                data.stints.filter { it.compound in compounds }.flatMap { stint ->
                    val color = TyreCompound.color(stint.compound)
                    listOf(
                        ChartSeries(
                            "${stint.id}-points",
                            color.copy(alpha = 0.55f),
                            stint.points.map { ChartPoint(it.tyreLife, it.deltaMs / 1000.0) },
                            showLine = false,
                            showPoints = true,
                        ),
                        ChartSeries(
                            "${stint.id}-fit",
                            color,
                            stint.fit.map { ChartPoint(it.tyreLife, it.deltaMs / 1000.0) },
                            strokeWidth = 2.dp,
                        ),
                    )
                }
            }
            val domain = data.xDomain?.takeIf { it.size == 2 }?.let { x ->
                data.yDomainMs?.takeIf { it.size == 2 }?.let { y ->
                    ChartDomain(x[0].toDouble(), x[1].toDouble(), y[0] / 1000.0, y[1] / 1000.0)
                }
            } ?: ChartDomain.cover(series)
            AnalysisChartColumn(data.eventName ?: title) {
                RcCard { RcLineChart(series = series, domain = domain, height = 300.dp) }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
                ) {
                    data.stints.mapNotNull { it.compound }.distinct().forEach { compound ->
                        FilterChip(
                            selected = compound in compounds,
                            onClick = { viewModel.toggle(compound) },
                            label = { Text(compound) },
                        )
                    }
                }
                data.compoundBaselines.forEach { baseline ->
                    Text(
                        "${baseline.compound}: ${"%.3f".format(baseline.slopeSecPerLap)} s/lap",
                        color = RcTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisChartColumn(
    eventName: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.padding(Dimens.MD),
        verticalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        Text(eventName, color = RcTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

@Composable
private fun DriverChips(
    codes: List<Pair<String, String?>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        codes.forEach { (code, color) ->
            FilterChip(
                selected = code in selected,
                onClick = { onToggle(code) },
                label = { Text(code) },
                leadingIcon = {
                    Text("●", color = teamColor(color).legibleOnSurface())
                },
            )
        }
    }
}

private fun flagBand(period: FlagPeriodDto) = ChartBand(
    minX = period.startLap - 0.5,
    maxX = period.endLap + 0.5,
    color = FlagStyle.color(period.periodType).copy(alpha = 0.16f),
)
