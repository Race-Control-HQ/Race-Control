package com.owlmedia.racecontrol.feature.drivers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.CountryFlag
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.ChartDomain
import com.owlmedia.racecontrol.core.ui.ChartPoint
import com.owlmedia.racecontrol.core.ui.ChartSeries
import com.owlmedia.racecontrol.core.ui.DriverAvatar
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcLineChart
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.core.ui.Sparkline
import com.owlmedia.racecontrol.core.ui.StatCell
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.core.util.pointsLabel
import com.owlmedia.racecontrol.data.remote.dto.DriverDetailDto
import com.owlmedia.racecontrol.data.remote.dto.DriverSeasonResultDto
import com.owlmedia.racecontrol.data.remote.dto.EvolutionPointDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DriverDetailViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<DriverDetailDto>>(UiState.Idle)
    val state: StateFlow<UiState<DriverDetailDto>> = _state.asStateFlow()

    /** This driver's cumulative points, round by round. Empty until the secondary fetch resolves. */
    private val _pointsSeries = MutableStateFlow<List<EvolutionPointDto>>(emptyList())
    val pointsSeries: StateFlow<List<EvolutionPointDto>> = _pointsSeries.asStateFlow()

    /**
     * Null until the secondary fetch resolves, or if the driver isn't in the WDC calculator's
     * data for this year (e.g. no standings on record).
     */
    private val _canWinWdc = MutableStateFlow<Boolean?>(null)
    val canWinWdc: StateFlow<Boolean?> = _canWinWdc.asStateFlow()

    fun load(year: Int, driverId: String) {
        if (_state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.driverDetail(year, driverId)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
        // Secondary lookups for the points-progression chart and the WDC badge; failure
        // shouldn't block the main screen, so they're independent best-effort fetches.
        viewModelScope.launch {
            repository.standingsEvolution(year).onSuccess { evolution ->
                _pointsSeries.value = evolution.drivers.find { it.driverId == driverId }?.series ?: emptyList()
            }
        }
        viewModelScope.launch {
            repository.wdcCalculator(year).onSuccess { wdc ->
                _canWinWdc.value = wdc.drivers.find { it.driverId == driverId }?.canWin
            }
        }
    }
}

@Composable
fun DriverDetailScreen(
    year: Int,
    driverId: String,
    onBack: () -> Unit,
    onOpenTitleDecider: () -> Unit,
    viewModel: DriverDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pointsSeries by viewModel.pointsSeries.collectAsStateWithLifecycle()
    val canWinWdc by viewModel.canWinWdc.collectAsStateWithLifecycle()
    LaunchedEffect(year, driverId) { viewModel.load(year, driverId) }

    val title = (state as? UiState.Loaded)?.value?.fullName ?: ""

    RcDetailScaffold(title = title, onBack = onBack) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, driverId) },
            modifier = modifier,
        ) { driver ->
            DriverDetailContent(
                year = year,
                driver = driver,
                pointsSeries = pointsSeries,
                canWinWdc = canWinWdc,
                onOpenTitleDecider = onOpenTitleDecider,
            )
        }
    }
}

@Composable
private fun DriverDetailContent(
    year: Int,
    driver: DriverDetailDto,
    pointsSeries: List<EvolutionPointDto>,
    canWinWdc: Boolean?,
    onOpenTitleDecider: () -> Unit,
) {
    val accent = teamColor(driver.teamColor).legibleOnSurface()
    val results = driver.seasonResults.orEmpty().sortedBy { it.round ?: 0 }

    LazyColumn(
        contentPadding = PaddingValues(Dimens.MD),
        verticalArrangement = Arrangement.spacedBy(Dimens.MD),
    ) {
        item(key = "header") {
            RcCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DriverAvatar(
                        url = driver.headshotUrl,
                        initials = driver.initials,
                        accent = accent,
                        size = 76.dp,
                    )
                    Spacer(Modifier.width(Dimens.MD))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = driver.fullName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = RcTheme.colors.textPrimary,
                            )
                            if (canWinWdc != null) {
                                WdcBadge(canWin = canWinWdc, onClick = onOpenTitleDecider)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TeamLogo(url = driver.teamLogoUrl, size = 16.dp)
                            Text(
                                text = driver.teamName.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = accent,
                            )
                        }
                        val nationality = driver.nationality
                        if (!nationality.isNullOrBlank()) {
                            Text(
                                text = "${CountryFlag.flag(nationality)} $nationality",
                                style = MaterialTheme.typography.bodySmall,
                                color = RcTheme.colors.textSecondary,
                            )
                        }
                        driver.number?.stringValue?.let {
                            Text(
                                text = "#$it",
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                fontWeight = FontWeight.Bold,
                                color = RcTheme.colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }

        item(key = "stats") {
            RcCard {
                Row(Modifier.fillMaxWidth()) {
                    StatCell(
                        value = driver.points?.numberLabel ?: "0",
                        label = stringResource(R.string.stat_points),
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        value = driver.wins?.numberLabel ?: "0",
                        label = stringResource(R.string.stat_wins),
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        value = driver.position?.intValue?.toString() ?: "–",
                        label = stringResource(R.string.stat_position),
                        modifier = Modifier.weight(1f),
                        accent = accent,
                    )
                }
            }
        }

        // Cumulative championship points, round by round. Reuses the same
        // /api/standings-evolution data as the Standings tab's Progress chart, just
        // filtered to this one driver instead of the top-10.
        if (pointsSeries.size >= 2) {
            item(key = "points-progression") {
                RcCard {
                    Column {
                        SectionHeader(stringResource(R.string.points_progression))
                        val series = remember(pointsSeries) {
                            listOf(
                                ChartSeries(
                                    id = driver.driverId,
                                    color = accent,
                                    points = pointsSeries.map { ChartPoint(it.round.toDouble(), it.points) },
                                )
                            )
                        }
                        val domain = remember(series) {
                            ChartDomain.cover(series, yPadding = 0.04).copy(minY = 0.0)
                        }
                        RcLineChart(
                            series = series,
                            domain = domain,
                            height = 180.dp,
                        )
                    }
                }
            }
        }

        item(key = "fingerprint") {
            DriverFingerprintCard(year, driver.driverId, accent)
        }

        val finishes = results.mapNotNull { it.positionInt?.toFloat() }
        if (finishes.size >= 2) {
            item(key = "form") {
                RcCard {
                    SectionHeader(stringResource(R.string.season_form))
                    // Positions are "lower is better", so the sparkline is drawn
                    // inverted - a rising line means improving results.
                    Sparkline(values = finishes, color = accent, invert = true)
                }
            }
        }

        if (results.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.no_results_title),
                    message = stringResource(R.string.no_results_message),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item(key = "results-header") {
                SectionHeader(stringResource(R.string.season_results))
            }
            items(
                items = results,
                key = { it.round ?: it.hashCode() },
                contentType = { "season-result" },
            ) { result ->
                SeasonResultRow(result, accent)
            }
        }
    }
}

/**
 * "Can win" / "Can't win" title-decider status, next to the driver's name. Tapping it opens the
 * full WDC calculator (Title Decider). Uses the live calculator (no throughRound), which
 * collapses correctly to "Can win" for just the champion once a season is over, no special-casing
 * needed for past vs. current seasons.
 */
@Composable
private fun WdcBadge(canWin: Boolean, onClick: () -> Unit) {
    val color = if (canWin) RcTheme.colors.positive else RcTheme.colors.textTertiary
    Text(
        text = stringResource(if (canWin) R.string.can_win_wdc else R.string.cant_win_wdc),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RcShapes.Small)
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun SeasonResultRow(result: DriverSeasonResultDto, accent: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        Text(
            text = result.round?.let { "R$it" } ?: "–",
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = RcTheme.colors.textTertiary,
            modifier = Modifier.width(32.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = result.raceName.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = RcTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            result.status?.takeIf { it != "Finished" }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textTertiary,
                )
            }
        }
        Text(
            text = result.positionInt?.let { "P$it" } ?: "–",
            style = MaterialTheme.typography.bodyMedium.tabular(),
            fontWeight = FontWeight.SemiBold,
            color = if ((result.positionInt ?: 99) <= 3) RcTheme.colors.gold else accent,
        )
        Text(
            text = result.pointsLabel,
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = RcTheme.colors.textSecondary,
            modifier = Modifier.width(32.dp),
        )
    }
}
