package com.owlmedia.racecontrol.feature.drivers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.ErrorState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.LoadingIndicator
import com.owlmedia.racecontrol.core.ui.ChartPoint
import com.owlmedia.racecontrol.core.ui.ChartSeries
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.RcLineChart
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.core.util.pointsLabel
import com.owlmedia.racecontrol.data.remote.dto.CompareDriverDto
import com.owlmedia.racecontrol.data.remote.dto.CompareResponseDto
import com.owlmedia.racecontrol.data.remote.dto.DriverDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FormLinePoint(val round: Int, val position: Int)
data class FormLineSeries(val driverId: String, val code: String, val teamColor: String?, val points: List<FormLinePoint>)

@HiltViewModel
class HeadToHeadViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _drivers = MutableStateFlow<UiState<List<DriverDto>>>(UiState.Idle)
    val drivers: StateFlow<UiState<List<DriverDto>>> = _drivers.asStateFlow()

    private val _comparison = MutableStateFlow<UiState<CompareResponseDto>?>(null)
    val comparison: StateFlow<UiState<CompareResponseDto>?> = _comparison.asStateFlow()

    private val _formLine = MutableStateFlow<UiState<List<FormLineSeries>>?>(null)
    val formLine: StateFlow<UiState<List<FormLineSeries>>?> = _formLine.asStateFlow()

    fun loadDrivers(year: Int) {
        if (_drivers.value is UiState.Loaded) return
        viewModelScope.launch {
            _drivers.value = UiState.Loading
            repository.drivers(year)
                .onSuccess { _drivers.value = UiState.Loaded(it) }
                .onFailure { _drivers.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    fun compare(year: Int, d1: String?, d2: String?) {
        if (d1 == null || d2 == null || d1 == d2) {
            _comparison.value = null
            return
        }
        viewModelScope.launch {
            _comparison.value = UiState.Loading
            repository.compare(year, d1, d2)
                .onSuccess { _comparison.value = UiState.Loaded(it) }
                .onFailure { _comparison.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    /**
     * Round-by-round finishing position for both selected drivers. There's
     * no per-round series on `/api/compare`, so this fans out one results
     * request per completed round, same v1 tradeoff the season form guide
     * makes, filtered down to the two selected drivers.
     */
    fun loadFormLine(year: Int, d1: DriverDto?, d2: DriverDto?) {
        if (d1 == null || d2 == null || d1.driverId == d2.driverId) {
            _formLine.value = null
            return
        }
        viewModelScope.launch {
            _formLine.value = UiState.Loading
            val scheduleResult = repository.schedule(year)
            val schedule = scheduleResult.getOrNull()
            if (schedule == null) {
                _formLine.value = UiState.Failed(
                    repository.messageFor(scheduleResult.exceptionOrNull() ?: Exception("Unknown error")),
                )
                return@launch
            }
            val completedRounds = schedule.filter { it.completed }.sortedBy { it.round }
            if (completedRounds.isEmpty()) {
                _formLine.value = UiState.Loaded(emptyList())
                return@launch
            }

            val pointsByDriver = mutableMapOf(d1.driverId to mutableListOf<FormLinePoint>(), d2.driverId to mutableListOf())
            coroutineScope {
                completedRounds.map { event ->
                    async { repository.results(year, event.round, "R").getOrNull()?.let { event.round to it } }
                }.awaitAll().filterNotNull().forEach { (round, response) ->
                    response.results.forEach { entry ->
                        val position = entry.position?.toInt() ?: return@forEach
                        when {
                            entry.driverId == d1.driverId || entry.abbreviation == d1.code ->
                                pointsByDriver.getValue(d1.driverId).add(FormLinePoint(round, position))
                            entry.driverId == d2.driverId || entry.abbreviation == d2.code ->
                                pointsByDriver.getValue(d2.driverId).add(FormLinePoint(round, position))
                        }
                    }
                }
            }

            val s1 = FormLineSeries(d1.driverId, d1.code ?: "?", d1.teamColor, pointsByDriver.getValue(d1.driverId).sortedBy { it.round })
            val s2 = FormLineSeries(d2.driverId, d2.code ?: "?", d2.teamColor, pointsByDriver.getValue(d2.driverId).sortedBy { it.round })
            _formLine.value = UiState.Loaded(listOf(s1, s2))
        }
    }
}

@Composable
fun HeadToHeadScreen(
    year: Int,
    onBack: () -> Unit,
    viewModel: HeadToHeadViewModel = hiltViewModel(),
) {
    val driversState by viewModel.drivers.collectAsStateWithLifecycle()
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()
    val formLine by viewModel.formLine.collectAsStateWithLifecycle()

    var driverA by remember { mutableStateOf<DriverDto?>(null) }
    var driverB by remember { mutableStateOf<DriverDto?>(null) }

    LaunchedEffect(year) { viewModel.loadDrivers(year) }
    LaunchedEffect(driverA, driverB) {
        viewModel.compare(year, driverA?.driverId, driverB?.driverId)
        viewModel.loadFormLine(year, driverA, driverB)
    }

    RcDetailScaffold(title = stringResource(R.string.head_to_head), onBack = onBack) { modifier ->
        com.owlmedia.racecontrol.core.ui.LoadableContent(
            state = driversState,
            onRetry = { viewModel.loadDrivers(year) },
            modifier = modifier,
        ) { drivers ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.MD),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SM)) {
                    DriverPicker(
                        label = stringResource(R.string.driver_a),
                        drivers = drivers,
                        selected = driverA,
                        onSelect = { driverA = it },
                        modifier = Modifier.weight(1f),
                    )
                    DriverPicker(
                        label = stringResource(R.string.driver_b),
                        drivers = drivers,
                        selected = driverB,
                        onSelect = { driverB = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                when (val state = comparison) {
                    null -> EmptyState(
                        icon = Icons.Filled.CompareArrows,
                        title = stringResource(R.string.head_to_head),
                        message = stringResource(R.string.pick_two_drivers),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is UiState.Idle, is UiState.Loading -> LoadingIndicator()
                    is UiState.Failed -> ErrorState(
                        message = state.message,
                        onRetry = {
                            viewModel.compare(year, driverA?.driverId, driverB?.driverId)
                        },
                    )
                    is UiState.Loaded -> {
                        val pair = state.value.drivers
                        if (pair.size >= 2) {
                            FormLineCard(formLine)
                            ComparisonCard(pair[0], pair[1])
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverPicker(
    label: String,
    drivers: List<DriverDto>,
    selected: DriverDto?,
    onSelect: (DriverDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected?.fullName ?: label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            drivers.forEach { driver ->
                DropdownMenuItem(
                    text = { Text(driver.fullName) },
                    onClick = {
                        onSelect(driver)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FormLineCard(formLine: UiState<List<FormLineSeries>>?) {
    val series = (formLine as? UiState.Loaded)?.value.orEmpty()
    if (formLine !is UiState.Loaded || series.all { it.points.isEmpty() }) return

    RcCard {
        Text(
            text = "FORM LINE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RcTheme.colors.textSecondary,
        )
        val chartSeries = series.map { s ->
            ChartSeries(
                id = s.driverId,
                color = com.owlmedia.racecontrol.core.design.teamColor(s.teamColor),
                points = s.points.map { ChartPoint(it.round.toDouble(), -it.position.toDouble()) },
                showPoints = true,
            )
        }
        RcLineChart(series = chartSeries, height = 140.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.MD)) {
            series.forEach { s ->
                Text(
                    text = s.code,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = com.owlmedia.racecontrol.core.design.teamColor(s.teamColor),
                )
            }
        }
    }
}

@Composable
private fun ComparisonCard(a: CompareDriverDto, b: CompareDriverDto) {
    RcCard {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = a.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RcTheme.colors.textPrimary,
                textAlign = TextAlign.Start,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = b.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RcTheme.colors.textPrimary,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(
            color = RcTheme.colors.stroke,
            modifier = Modifier.padding(vertical = Dimens.SM),
        )

        ComparisonRow(stringResource(R.string.stat_points), a.pointsLabel, b.pointsLabel)
        ComparisonRow(stringResource(R.string.stat_wins), a.wins.toString(), b.wins.toString())
        ComparisonRow(stringResource(R.string.stat_podiums), a.podiums.toString(), b.podiums.toString())
        ComparisonRow(stringResource(R.string.stat_poles), a.poles.toString(), b.poles.toString())
        ComparisonRow(
            stringResource(R.string.stat_best),
            a.bestFinish?.let { "P$it" } ?: "–",
            b.bestFinish?.let { "P$it" } ?: "–",
        )
        ComparisonRow(stringResource(R.string.stat_dnf), a.dnf.toString(), b.dnf.toString())
        HorizontalDivider(
            color = RcTheme.colors.stroke,
            modifier = Modifier.padding(vertical = Dimens.SM),
        )
        ComparisonRow(
            stringResource(R.string.h2h_race_wins),
            a.raceWinsH2h.toString(),
            b.raceWinsH2h.toString(),
        )
        ComparisonRow(
            stringResource(R.string.h2h_quali_wins),
            a.qualWinsH2h.toString(),
            b.qualWinsH2h.toString(),
        )
    }
}

/**
 * One comparison line. The winning side is emphasised with weight as well as
 * colour so the distinction survives a colour-vision deficiency.
 */
@Composable
private fun ComparisonRow(label: String, valueA: String, valueB: String) {
    val numericA = valueA.removePrefix("P").toDoubleOrNull()
    val numericB = valueB.removePrefix("P").toDoubleOrNull()
    // "Best finish" is the one row where a lower number is the better result.
    val lowerWins = label.equals("Best", ignoreCase = true)
    val aWins = when {
        numericA == null || numericB == null -> false
        lowerWins -> numericA < numericB
        else -> numericA > numericB
    }
    val bWins = when {
        numericA == null || numericB == null -> false
        lowerWins -> numericB < numericA
        else -> numericB > numericA
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = valueA,
            style = MaterialTheme.typography.bodyLarge.tabular(),
            fontWeight = if (aWins) FontWeight.Bold else FontWeight.Normal,
            color = if (aWins) RcTheme.colors.positive else RcTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = RcTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.6f),
        )
        Text(
            text = valueB,
            style = MaterialTheme.typography.bodyLarge.tabular(),
            fontWeight = if (bWins) FontWeight.Bold else FontWeight.Normal,
            color = if (bWins) RcTheme.colors.positive else RcTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
