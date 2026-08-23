@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.owlmedia.racecontrol.feature.standings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcTabScaffold
import com.owlmedia.racecontrol.core.ui.SeasonPickerChip
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.feature.AppState

@Composable
fun StandingsScreen(
    appState: AppState,
    onSelectYear: (Int) -> Unit,
    onOpenDriver: (String) -> Unit = {},
    viewModel: StandingsViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(appState.selectedYear, mode) {
        viewModel.load(appState.selectedYear, mode)
    }

    RcTabScaffold(
        title = stringResource(R.string.standings),
        actions = {
            SeasonPickerChip(
                seasons = appState.seasons,
                selected = appState.selectedYear,
                onSelect = onSelectYear,
            )
            Spacer(Modifier.width(Dimens.SM))
        },
    ) { modifier ->
        Column(modifier) {
            // Six peer views. iOS uses a segmented control; at this many options
            // with words like "Reliability" that would truncate badly on a
            // compact screen, so this is a Material tab row.
            val labels = listOf(
                stringResource(R.string.standings_drivers),
                stringResource(R.string.standings_teams),
                stringResource(R.string.standings_form),
                stringResource(R.string.standings_progress),
                stringResource(R.string.standings_reliability),
                stringResource(R.string.standings_wdc),
            )
            PrimaryTabRow(
                selectedTabIndex = mode.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                StandingsMode.entries.forEachIndexed { index, entry ->
                    Tab(
                        selected = mode == entry,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setMode(entry)
                        },
                        text = {
                            Text(
                                text = labels[index],
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            when (mode) {
                StandingsMode.DRIVERS -> DriversStandings(viewModel, appState)
                StandingsMode.TEAMS -> ConstructorsStandings(viewModel, appState)
                StandingsMode.FORM -> SeasonFormGuideView(viewModel, appState)
                StandingsMode.PROGRESS -> StandingsEvolutionView(viewModel, appState)
                StandingsMode.RELIABILITY -> ReliabilityView(viewModel, appState)
                StandingsMode.WDC -> WdcCalculatorView(viewModel, appState, onOpenDriver)
            }
        }
    }
}

@Composable
private fun DriversStandings(viewModel: StandingsViewModel, appState: AppState) {
    val state by viewModel.drivers.collectAsStateWithLifecycle()

    LoadableContent(
        state = state,
        onRetry = { viewModel.load(appState.selectedYear, StandingsMode.DRIVERS, force = true) },
    ) { standings ->
        if (standings.isEmpty()) {
            EmptyStateForYear(appState.selectedYear)
        } else {
            val leaderPoints = standings.firstOrNull()?.points?.doubleValue ?: 0.0
            LazyColumn(
                contentPadding = PaddingValues(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.SM),
            ) {
                itemsIndexedKeyed(standings.size) { index ->
                    val entry = standings[index]
                    StandingRow(
                        rank = entry.position?.intValue ?: (index + 1),
                        title = entry.fullName,
                        subtitle = entry.teamName.orEmpty(),
                        points = entry.points?.numberLabel ?: "0",
                        wins = entry.wins?.intValue ?: 0,
                        fraction = fractionOf(entry.points?.doubleValue, leaderPoints),
                        logoUrl = entry.teamLogoUrl,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConstructorsStandings(viewModel: StandingsViewModel, appState: AppState) {
    val state by viewModel.constructors.collectAsStateWithLifecycle()

    LoadableContent(
        state = state,
        onRetry = { viewModel.load(appState.selectedYear, StandingsMode.TEAMS, force = true) },
    ) { standings ->
        if (standings.isEmpty()) {
            EmptyStateForYear(appState.selectedYear)
        } else {
            val leaderPoints = standings.firstOrNull()?.points?.doubleValue ?: 0.0
            LazyColumn(
                contentPadding = PaddingValues(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.SM),
            ) {
                itemsIndexedKeyed(standings.size) { index ->
                    val entry = standings[index]
                    StandingRow(
                        rank = entry.position?.intValue ?: (index + 1),
                        title = entry.teamName.orEmpty(),
                        subtitle = entry.nationality.orEmpty(),
                        points = entry.points?.numberLabel ?: "0",
                        wins = entry.wins?.intValue ?: 0,
                        fraction = fractionOf(entry.points?.doubleValue, leaderPoints),
                        logoUrl = entry.teamLogoUrl,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateForYear(year: Int) {
    EmptyState(
        icon = Icons.Filled.EmojiEvents,
        title = stringResource(R.string.no_standings_title),
        message = stringResource(R.string.no_standings_message, year.toString()),
    )
}

private fun fractionOf(points: Double?, leader: Double): Float {
    if (leader <= 0.0) return 0f
    return ((points ?: 0.0) / leader).toFloat().coerceIn(0f, 1f)
}

/**
 * One championship row: rank, name, a gap-to-leader bar and the points.
 *
 * The bar is decorative (the numeric points are always shown next to it), so
 * it is hidden from TalkBack rather than announced as an unlabelled progress
 * indicator.
 */
@Composable
internal fun StandingRow(
    rank: Int,
    title: String,
    subtitle: String,
    points: String,
    wins: Int,
    fraction: Float,
    logoUrl: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.SM)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Position $rank, $title")
                    if (subtitle.isNotBlank()) append(", $subtitle")
                    append(", $points points")
                    if (wins > 0) append(", $wins wins")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) RcTheme.colors.gold else RcTheme.colors.textSecondary,
            modifier = Modifier.width(28.dp),
        )
        TeamLogo(url = logoUrl)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = RcTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            GapBar(fraction)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = points,
                style = MaterialTheme.typography.titleMedium.tabular(),
                fontWeight = FontWeight.Bold,
                color = RcTheme.colors.textPrimary,
            )
            if (wins > 0) {
                Text(
                    text = "${wins}W",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = RcTheme.colors.racingRedText,
                )
            }
        }
    }
}

@Composable
private fun GapBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RcShapes.Small)
            .background(RcTheme.colors.surfaceElevated),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RcShapes.Small)
                .background(RcTheme.colors.racingRed),
        )
    }
}

/**
 * Index-keyed items. Championship standings have no stable server id for every
 * season (older Ergast rows can repeat a driverId across teams), so position in
 * the list is the honest key here.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedKeyed(
    count: Int,
    itemContent: @Composable (Int) -> Unit,
) {
    items(count = count, key = { it }, contentType = { "standing" }) { index ->
        itemContent(index)
    }
}
