package com.owlmedia.racecontrol.feature.standings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto
import com.owlmedia.racecontrol.data.remote.dto.WdcDriverDto
import com.owlmedia.racecontrol.feature.AppState
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Who can still mathematically win the drivers' championship.
 *
 * Mirrors the web/iOS "WDC calculator": for every driver, the ceiling is
 * their current points plus every remaining point on offer (race + sprint),
 * assuming they win everything left and the leader scores nothing else. That
 * is a best case, not a forecast. The caption under the header exists
 * because a user found the raw numbers confusing without it.
 */
@Composable
fun WdcCalculatorView(
    viewModel: StandingsViewModel,
    appState: AppState,
    onOpenDriver: (String) -> Unit = {},
) {
    val state by viewModel.wdc.collectAsStateWithLifecycle()
    val throughRound by viewModel.wdcThroughRound.collectAsStateWithLifecycle()
    val completedRounds by viewModel.wdcCompletedRounds.collectAsStateWithLifecycle()

    LoadableContent(
        state = state,
        onRetry = { viewModel.load(appState.selectedYear, StandingsMode.WDC, force = true) },
    ) { data ->
        if (data.drivers.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.EmojiEvents,
                title = stringResource(R.string.no_standings_title),
                message = stringResource(R.string.no_standings_message, appState.selectedYear.toString()),
            )
            return@LoadableContent
        }

        val canWinCount = data.drivers.count { it.canWin }

        LazyColumn(
            contentPadding = PaddingValues(Dimens.MD),
            verticalArrangement = Arrangement.spacedBy(Dimens.SM),
        ) {
            if (completedRounds.isNotEmpty()) {
                item(key = "wdc-time-machine") {
                    WdcTimeMachine(
                        completedRounds = completedRounds,
                        throughRound = throughRound,
                        onThroughRoundChange = viewModel::setWdcThroughRound,
                    )
                }
            }
            item(key = "wdc-header") {
                WdcHeader(
                    decided = data.decided,
                    roundsRemaining = data.roundsRemaining,
                    maxRemainingPoints = data.maxRemainingPoints,
                    canWinCount = canWinCount,
                    notes = data.notes,
                )
            }
            item(key = "title-scenarios") {
                TitleScenarioMatrix(appState.selectedYear, throughRound)
            }
            items(
                items = data.drivers,
                key = { it.driverId ?: it.fullName },
                contentType = { "wdc-driver" },
            ) { driver ->
                WdcDriverRow(driver = driver, onOpenDriver = onOpenDriver)
            }
        }
    }
}

/**
 * "Time machine" round scrubber. Once a season is over, the live calculator degenerates to
 * "only the leader can win" for every round, which isn't interesting: this lets a user scrub
 * back to any completed round and see the calculator as it stood then, using that round's
 * actual cumulative points rather than final-season standings.
 *
 * The slider's valid positions are 1..lastCompletedRound, plus one extra stop past the end
 * that represents live standings (throughRound = null). [onThroughRoundChange] only fires when
 * the drag ends, not on every intermediate value, so scrubbing does not spam the network.
 */
@Composable
private fun WdcTimeMachine(
    completedRounds: List<RaceEventDto>,
    throughRound: Int?,
    onThroughRoundChange: (Int?) -> Unit,
) {
    val lastRound = completedRounds.maxOf { it.round }
    val liveStop = (lastRound + 1).toFloat()
    val committedValue = throughRound?.toFloat() ?: liveStop

    var sliderValue by remember(committedValue) { mutableFloatStateOf(committedValue) }
    val displayedRound = sliderValue.roundToInt()
    val isLive = displayedRound > lastRound

    val label = if (isLive) {
        "Viewing: live standings"
    } else {
        val event = completedRounds.find { it.round == displayedRound }
        "Viewing: as of Round $displayedRound (${event?.displayName ?: "Round $displayedRound"})"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.SM),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RcTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (!isLive) {
                TextButton(onClick = {
                    sliderValue = liveStop
                    onThroughRoundChange(null)
                }) {
                    Text("Jump to live")
                }
            }
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val round = sliderValue.roundToInt()
                onThroughRoundChange(if (round > lastRound) null else round)
            },
            valueRange = 1f..liveStop,
            steps = (liveStop.toInt() - 1) - 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (isLive) {
                        "Round scrubber, live standings"
                    } else {
                        "Round scrubber, round $displayedRound of $lastRound"
                    }
                },
        )
        WdcRoundTicks(
            completedRounds = completedRounds,
            displayedRound = displayedRound,
            isLive = isLive,
            onSelectRound = { round ->
                sliderValue = round.toFloat()
                onThroughRoundChange(round)
            },
            onSelectLive = {
                sliderValue = liveStop
                onThroughRoundChange(null)
            },
        )
    }
}

/**
 * Per-round tick marks below the slider. With only a handful of rounds run so far, the
 * slider's few steps are spread across the whole track, so a small drag jumps a
 * disproportionately large visual gap between adjacent rounds. Showing every round's number
 * makes that discreteness explicit (it's a timeline of specific races, not a continuous
 * scale) and gives a precise tap target instead of trying to land a drag exactly on it.
 * Labels thin out once there are too many rounds to print legibly, but the current round and
 * both ends stay labelled. Includes one extra tick for "Live", mirroring the slider's own
 * extra stop past the last completed round.
 */
@Composable
private fun WdcRoundTicks(
    completedRounds: List<RaceEventDto>,
    displayedRound: Int,
    isLive: Boolean,
    onSelectRound: (Int) -> Unit,
    onSelectLive: () -> Unit,
) {
    val labelStride = if (completedRounds.size > 15) {
        ceil(completedRounds.size / 12.0).toInt()
    } else {
        1
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        completedRounds.forEachIndexed { index, event ->
            val isCurrent = !isLive && event.round == displayedRound
            val isPast = event.round < displayedRound
            val showLabel = isCurrent || index == 0 || index == completedRounds.lastIndex || index % labelStride == 0
            WdcRoundTick(
                label = event.round.toString(),
                isCurrent = isCurrent,
                isPast = isPast,
                showLabel = showLabel,
                contentDescription = "Round ${event.round}, ${event.displayName}",
                onClick = { onSelectRound(event.round) },
            )
        }
        WdcRoundTick(
            label = "Live",
            isCurrent = isLive,
            isPast = false,
            showLabel = true,
            contentDescription = "Live standings",
            onClick = onSelectLive,
        )
    }
}

@Composable
private fun RowScope.WdcRoundTick(
    label: String,
    isCurrent: Boolean,
    isPast: Boolean,
    showLabel: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (isCurrent) 8.dp else 5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCurrent -> RcTheme.colors.racingRed
                        isPast -> RcTheme.colors.racingRed.copy(alpha = 0.5f)
                        else -> RcTheme.colors.surfaceElevated
                    },
                ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (showLabel) label else "",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) RcTheme.colors.racingRed else RcTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun WdcHeader(
    decided: Boolean,
    roundsRemaining: Int,
    maxRemainingPoints: Int,
    canWinCount: Int,
    notes: List<String> = emptyList(),
) {
    val statusText = when {
        decided && roundsRemaining == 0 -> "Season complete: the title is decided."
        decided -> "Mathematically settled: only the leader can still win."
        else -> {
            val roundsWord = if (roundsRemaining == 1) "round" else "rounds"
            val driversWord = if (canWinCount == 1) "driver" else "drivers"
            "$roundsRemaining $roundsWord left · up to $maxRemainingPoints points still on offer · " +
                "$canWinCount $driversWord can still win"
        }
    }

    Column(modifier = Modifier.padding(bottom = Dimens.XS)) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = RcTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "\"Can win\" is the best-case ceiling (winning every remaining session while the " +
                "leader scores nothing else), not a realistic forecast.",
            style = MaterialTheme.typography.bodySmall,
            color = RcTheme.colors.textTertiary,
        )
        notes.forEach { note ->
            Text(
                text = "• $note",
                style = MaterialTheme.typography.bodySmall,
                color = RcTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun WdcDriverRow(
    driver: WdcDriverDto,
    onOpenDriver: (String) -> Unit,
) {
    val behindLabel = if (driver.pointsBehindLeader == 0.0) "Leader" else "-${formatPoints(driver.pointsBehindLeader)}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.SM)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Position ${driver.position ?: 0}, ${driver.fullName}")
                    if (!driver.teamName.isNullOrBlank()) append(", ${driver.teamName}")
                    append(", ${formatPoints(driver.points)} points")
                    append(", ${if (driver.pointsBehindLeader == 0.0) "leader" else "${formatPoints(driver.pointsBehindLeader)} behind leader"}")
                    append(", max possible ${formatPoints(driver.maxPoints)} points")
                    append(if (driver.canWin) ", can still win" else ", cannot win")
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (driver.position ?: 0).toString(),
                style = MaterialTheme.typography.titleMedium.tabular(),
                fontWeight = FontWeight.Bold,
                color = if ((driver.position ?: 0) <= 3) RcTheme.colors.gold else RcTheme.colors.textSecondary,
                modifier = Modifier.width(28.dp),
            )
            TeamLogo(url = driver.teamLogoUrl, modifier = Modifier.padding(end = Dimens.SM))
            Column(Modifier.weight(1f)) {
                Text(
                    text = driver.fullName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (driver.driverId != null) RcTheme.colors.info else RcTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = driver.driverId != null) {
                        onOpenDriver(driver.driverId ?: return@clickable)
                    },
                )
                if (!driver.teamName.isNullOrBlank()) {
                    Text(
                        text = driver.teamName,
                        style = MaterialTheme.typography.bodySmall,
                        color = RcTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CanWinPill(canWin = driver.canWin)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.MD)) {
            WdcStat(label = "Points", value = formatPoints(driver.points))
            WdcStat(label = "Behind", value = behindLabel)
            WdcStat(label = "Max possible", value = formatPoints(driver.maxPoints))
        }
    }
}

@Composable
private fun WdcStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = RcTheme.colors.textTertiary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.tabular(),
            fontWeight = FontWeight.Bold,
            color = RcTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun CanWinPill(canWin: Boolean) {
    val background = if (canWin) RcTheme.colors.positive.copy(alpha = 0.2f) else RcTheme.colors.surfaceElevated
    val textColor = if (canWin) RcTheme.colors.positive else RcTheme.colors.textTertiary
    val label = if (canWin) "Can win" else "Can't win"

    Box(
        modifier = Modifier
            .clip(RcShapes.Small)
            .background(background)
            .padding(horizontal = Dimens.SM, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

/** Points without a trailing ".0": F1 points are integers apart from half-points races. */
private fun formatPoints(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
