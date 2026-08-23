@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.owlmedia.racecontrol.feature.racedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.CountryFlag
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.MonoFamily
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.DriverAvatar
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.ErrorState
import com.owlmedia.racecontrol.core.ui.GridDeltaTag
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.LoadingIndicator
import com.owlmedia.racecontrol.core.ui.PositionBadge
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.TeamAccentBar
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.core.util.formatDateLong
import com.owlmedia.racecontrol.core.util.pointsLabel
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto
import com.owlmedia.racecontrol.data.remote.dto.ResultEntryDto
import com.owlmedia.racecontrol.data.remote.dto.SessionResultsDto

@Composable
fun RaceDetailScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    onOpenAnalysis: (Any) -> Unit,
    viewModel: RaceDetailViewModel = hiltViewModel(),
) {
    val eventState by viewModel.event.collectAsStateWithLifecycle()
    val resultsState by viewModel.results.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()

    LaunchedEffect(year, round) { viewModel.loadEvent(year, round) }
    LaunchedEffect(year, round, selectedSession) {
        viewModel.loadResults(year, round, selectedSession)
    }

    // Arriving from a notification deep link there is no title yet, so fall
    // back to the event name once the schedule has loaded.
    val loadedName = (eventState as? UiState.Loaded)?.value?.displayName
    val effectiveTitle = title.ifBlank { loadedName.orEmpty() }

    RcDetailScaffold(title = effectiveTitle, onBack = onBack) { modifier ->
        LoadableContent(
            state = eventState,
            onRetry = { viewModel.loadEvent(year, round) },
            modifier = modifier,
        ) { event ->
            RaceDetailContent(
                event = event,
                title = title,
                resultsState = resultsState,
                selectedSession = selectedSession,
                onSelectSession = viewModel::selectSession,
                onRetryResults = { viewModel.loadResults(year, round, selectedSession) },
                onOpenAnalysis = onOpenAnalysis,
            )
        }
    }
}

@Composable
private fun RaceDetailContent(
    event: RaceEventDto,
    title: String,
    resultsState: UiState<SessionResultsDto>,
    selectedSession: String,
    onSelectSession: (String) -> Unit,
    onRetryResults: () -> Unit,
    onOpenAnalysis: (Any) -> Unit,
) {
    val sessions = remember(event) { event.resultSessions() }
    val results = (resultsState as? UiState.Loaded)?.value
    val winnerTimeMs = results?.results?.firstOrNull()?.timeMs

    LazyColumn(
        contentPadding = PaddingValues(Dimens.MD),
        verticalArrangement = Arrangement.spacedBy(Dimens.MD),
    ) {
        item(key = "header") { RaceHeaderCard(event) }

        if (event.sessions.isNotEmpty()) {
            item(key = "weekend") { WeekendScheduleCard(event) }
        }

        if (event.completed) {
            item(key = "analysis") {
                RaceAnalysisGrid(
                    year = event.year,
                    round = event.round,
                    title = event.name ?: title,
                    onOpen = onOpenAnalysis,
                )
            }
        }

        if (sessions.size > 1) {
            item(key = "sessions") {
                SessionTabs(
                    sessions = sessions,
                    selected = selectedSession,
                    onSelect = onSelectSession,
                )
            }
        }

        when (resultsState) {
            is UiState.Idle, is UiState.Loading -> item(key = "results-loading") {
                com.owlmedia.racecontrol.core.ui.LoadingIndicator(
                    modifier = Modifier.heightIn(min = 240.dp)
                )
            }
            is UiState.Failed -> item(key = "results-error") {
                com.owlmedia.racecontrol.core.ui.ErrorState(
                    message = resultsState.message,
                    onRetry = onRetryResults,
                    modifier = Modifier.heightIn(min = 240.dp),
                )
            }
            is UiState.Loaded -> {
                val entries = resultsState.value.results
                if (entries.isEmpty()) {
                    item(key = "results-empty") {
                        EmptyState(
                            icon = Icons.Filled.FormatListNumbered,
                            title = stringResource(R.string.no_results_title),
                            message = stringResource(R.string.no_results_message),
                            modifier = Modifier.heightIn(min = 240.dp),
                        )
                    }
                } else {
                    val isQualifying = resultsState.value.session == "Q" ||
                        resultsState.value.session == "SQ"
                    items(
                        items = entries,
                        key = { it.id },
                        contentType = { "result" },
                    ) { entry ->
                        ResultRow(
                            entry = entry,
                            isQualifying = isQualifying,
                            winnerTimeMs = winnerTimeMs,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RaceHeaderCard(event: RaceEventDto) {
    RcCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = CountryFlag.flag(event.country),
                fontSize = 44.sp,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Spacer(Modifier.width(Dimens.MD))
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.officialName ?: event.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = RcTheme.colors.textPrimary,
                )
                Text(
                    text = listOfNotNull(event.location, event.country).joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RcTheme.colors.textSecondary,
                )
                event.parsedDate?.let {
                    Text(
                        text = it.formatDateLong(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RcTheme.colors.textTertiary,
                    )
                }
            }
        }
    }
}

/**
 * Session selector.
 *
 * iOS uses a segmented control, but a sprint weekend can offer six sessions and
 * Material's segmented buttons neither scroll nor read well past about five,
 * so this is a scrollable tab row, which is the Material pattern for a variable
 * number of peer views.
 */
@Composable
private fun SessionTabs(
    sessions: List<SessionTab>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val selectedIndex = sessions.indexOfFirst { it.id == selected }.coerceAtLeast(0)

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.background,
        edgePadding = 0.dp,
    ) {
        sessions.forEachIndexed { index, session ->
            Tab(
                selected = index == selectedIndex,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(session.id)
                },
                text = {
                    Text(
                        text = session.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

data class SessionTab(val label: String, val id: String)

/**
 * Sessions that produce a classification worth showing, Race first.
 *
 * Ported from the iOS `resultSessions` computed property.
 */
private fun RaceEventDto.resultSessions(): List<SessionTab> {
    val out = mutableListOf<SessionTab>()
    sessions.forEach { session ->
        val id = session.identifier ?: return@forEach
        val name = session.name ?: return@forEach
        when (id) {
            "R" -> out += SessionTab("Race", "R")
            "Q" -> out += SessionTab("Quali", "Q")
            "S" -> out += SessionTab("Sprint", "S")
            "SQ", "SS" -> out += SessionTab("Sprint Q", id)
            "FP1", "FP2", "FP3" -> out += SessionTab(name.replace("Practice", "FP"), id)
        }
    }
    return out.sortedByDescending { it.id == "R" }
}

@Composable
private fun ResultRow(
    entry: ResultEntryDto,
    isQualifying: Boolean,
    winnerTimeMs: Int?,
) {
    val accent = teamColor(entry.teamColor)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Dimens.SM, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        PositionBadge(text = entry.positionLabel, highlight = entry.isPodium)
        TeamAccentBar(color = accent.legibleOnSurface())
        DriverAvatar(
            url = entry.headshotUrl,
            initials = entry.abbreviation ?: "?",
            accent = accent.legibleOnSurface(),
            size = 40.dp,
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = entry.fullName ?: entry.abbreviation ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RcTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TeamLogo(url = entry.teamLogoUrl, size = 14.dp)
                Text(
                    text = entry.teamName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = RcTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (isQualifying) {
                Text(
                    text = entry.bestQualifyingTime ?: "–",
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = RcTheme.colors.textPrimary,
                )
            } else {
                Text(
                    text = entry.raceTimeLabel(winnerTimeMs),
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    fontFamily = MonoFamily,
                    color = RcTheme.colors.textPrimary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    entry.gridDelta?.let { GridDeltaTag(delta = it) }
                    if (entry.pointsLabel.isNotEmpty()) {
                        Text(
                            text = "+${entry.pointsLabel}",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            fontWeight = FontWeight.Bold,
                            color = RcTheme.colors.racingRedText,
                        )
                    }
                }
            }
        }
    }
}
