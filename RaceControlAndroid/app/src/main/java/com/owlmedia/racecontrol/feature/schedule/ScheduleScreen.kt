@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.owlmedia.racecontrol.feature.schedule

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.CountryFlag
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcTabScaffold
import com.owlmedia.racecontrol.core.ui.SeasonPickerChip
import com.owlmedia.racecontrol.core.util.formatDateMedium
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto
import com.owlmedia.racecontrol.feature.AppState

@Composable
fun ScheduleScreen(
    appState: AppState,
    onSelectYear: (Int) -> Unit,
    onOpenRace: (year: Int, round: Int, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()

    LaunchedEffect(appState.selectedYear) { viewModel.load(appState.selectedYear) }

    RcTabScaffold(
        title = stringResource(R.string.season_title, appState.selectedYear.toString()),
        actions = {
            SeasonPickerChip(
                seasons = appState.seasons,
                selected = appState.selectedYear,
                onSelect = onSelectYear,
            )
            Spacer(Modifier.width(Dimens.SM))
            // iOS puts the gear top-left. On Android the leading slot belongs to
            // navigation, so settings moves into the trailing overflow menu.
            OverflowMenu(onOpenSettings = onOpenSettings)
        },
    ) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(appState.selectedYear, force = true) },
            modifier = modifier,
        ) { events ->
            if (events.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.CalendarMonth,
                    title = stringResource(R.string.no_races_title),
                    message = stringResource(
                        R.string.no_races_message,
                        appState.selectedYear.toString(),
                    ),
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { viewModel.load(appState.selectedYear, force = true) },
                ) {
                    ScheduleList(events = events, onOpenRace = onOpenRace)
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(onOpenSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.settings_open),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
        }
    }
}

@Composable
private fun ScheduleList(
    events: List<RaceEventDto>,
    onOpenRace: (Int, Int, String) -> Unit,
) {
    val upNext = remember(events) { events.firstOrNull { !it.completed } }

    LazyColumn(
        contentPadding = PaddingValues(Dimens.MD),
        verticalArrangement = Arrangement.spacedBy(Dimens.MD),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (upNext != null) {
            item(key = "up-next") {
                UpNextBanner(
                    event = upNext,
                    onClick = { onOpenRace(upNext.year, upNext.round, upNext.displayName) },
                )
            }
        }
        items(
            items = events,
            key = { it.id },
            contentType = { "race" },
        ) { event ->
            RaceRow(
                event = event,
                onClick = { onOpenRace(event.year, event.round, event.displayName) },
            )
        }
    }
}

@Composable
private fun UpNextBanner(event: RaceEventDto, onClick: () -> Unit) {
    val gradient = Brush.linearGradient(
        listOf(RcTheme.colors.racingRed, RcTheme.colors.racingRedDim),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Large)
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(Dimens.MD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.up_next),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = event.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
            )
            event.parsedDate?.let {
                Text(
                    text = it.formatDateMedium(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                )
            }
        }
        Text(
            text = CountryFlag.flag(event.country, event.countryCodeOrNull()),
            fontSize = 40.sp,
            // The country already appears in the text above; TalkBack reading
            // the flag as "regional indicator" adds nothing.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun RaceRow(event: RaceEventDto, onClick: () -> Unit) {
    RcCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(44.dp),
            ) {
                Text(
                    text = stringResource(R.string.round_short, event.round),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = RcTheme.colors.textSecondary,
                )
                Text(
                    text = CountryFlag.flag(event.country, event.countryCodeOrNull()),
                    fontSize = 26.sp,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }

            Spacer(Modifier.width(Dimens.MD))

            Column(Modifier.weight(1f)) {
                Text(
                    text = event.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = RcTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = event.location ?: event.country.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RcTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
                ) {
                    event.parsedDate?.let {
                        Text(
                            text = it.formatDateMedium(),
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTheme.colors.textTertiary,
                        )
                    }
                    if (event.isSprintWeekend) {
                        SprintBadge()
                    }
                }
            }

            if (event.completed) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.race_completed),
                    tint = RcTheme.colors.positive.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SprintBadge() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(RcTheme.colors.warning.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.sprint),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RcTheme.colors.warning,
        )
    }
}

/** The schedule payload has no country code field; flags fall back to the name. */
private fun RaceEventDto.countryCodeOrNull(): String? = null
