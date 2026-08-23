@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.owlmedia.racecontrol.feature.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.MonoFamily
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.tabular
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.TeamAccentBar
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.core.ui.TyreBadge
import com.owlmedia.racecontrol.data.remote.dto.RaceReplayDto
import com.owlmedia.racecontrol.data.remote.dto.ReplayEntryDto
import com.owlmedia.racecontrol.util.KeepScreenOn

private val SPEEDS = listOf(0.5f, 1f, 2f, 4f)

@Composable
fun ReplayScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    onOpenDriver: (String) -> Unit = {},
    viewModel: ReplayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    LaunchedEffect(year, round) { viewModel.load(year, round) }

    // Playback must not continue behind a back navigation.
    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    // Watching a replay is a "look at it without touching it" activity, so the
    // display timing out mid-race is exactly wrong. Only while playing.
    KeepScreenOn(enabled = isPlaying)

    RcDetailScaffold(title = stringResource(R.string.analysis_replay), onBack = onBack) { modifier ->
        LoadableContent(
            state = state,
            onRetry = { viewModel.load(year, round) },
            modifier = modifier,
        ) { replay ->
            if (replay.frames.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.VideocamOff,
                    title = stringResource(R.string.replay_empty_title),
                    message = stringResource(R.string.replay_empty_message),
                )
            } else {
                ReplayContent(
                    replay = replay,
                    title = title,
                    viewModel = viewModel,
                    onOpenDriver = onOpenDriver,
                )
            }
        }
    }
}

@Composable
private fun ReplayContent(
    replay: RaceReplayDto,
    title: String,
    viewModel: ReplayViewModel,
    onOpenDriver: (String) -> Unit,
) {
    val currentLap by viewModel.currentLap.collectAsStateWithLifecycle()
    val raceProgress by viewModel.raceProgress.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val circuitMap by viewModel.circuitMap.collectAsStateWithLifecycle()
    val replayPositions by viewModel.replayPositions.collectAsStateWithLifecycle()
    val isSpatialLoading by viewModel.isSpatialLoading.collectAsStateWithLifecycle()

    val frame = remember(currentLap, replay) { viewModel.frameFor(currentLap) }
    val lapPositions = remember(currentLap, replayPositions) { viewModel.positionsFor(currentLap) }

    Column(Modifier.fillMaxWidth()) {
        LapHeader(
            eventName = replay.eventName ?: title,
            currentLap = currentLap,
            totalLaps = replay.totalLaps,
        )

        val map = circuitMap
        if (map != null && map.outline.isNotEmpty()) {
            ReplayGridTrackMap(
                map = map,
                lapPositions = lapPositions,
                lapFraction = viewModel.lapFraction(),
                order = frame?.order.orEmpty(),
                drivers = replay.drivers,
                modifier = Modifier.padding(horizontal = Dimens.MD, vertical = Dimens.SM),
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.MD, vertical = Dimens.SM)
                    .height(if (isSpatialLoading) 112.dp else 64.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RcShapes.Medium,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSpatialLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Loading full-grid positions…",
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTheme.colors.textSecondary,
                            modifier = Modifier.padding(start = Dimens.SM),
                        )
                    } else {
                        Text(
                            text = "Track positions aren't available for this race.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Dimens.MD),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                items = frame?.order.orEmpty(),
                // Keying by driver rather than position is what lets Compose
                // animate a car moving up the order instead of redrawing rows
                // in place.
                key = { it.driver },
                contentType = { "replay-row" },
            ) { entry ->
                ReplayRow(
                    entry = entry,
                    previousPosition = viewModel.previousPosition(entry.driver, currentLap),
                    onOpenDriver = onOpenDriver,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        TransportControls(
            currentLap = currentLap,
            totalLaps = replay.totalLaps,
            raceProgress = raceProgress,
            isPlaying = isPlaying,
            speed = speed,
            onScrub = viewModel::scrubTo,
            onScrubProgress = {
                viewModel.stop()
                viewModel.scrubToProgress(it)
            },
            onTogglePlay = viewModel::togglePlay,
            onSpeed = viewModel::setSpeed,
        )
    }
}

@Composable
private fun LapHeader(eventName: String, currentLap: Int, totalLaps: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.MD)
            // Announced as one unit when the lap changes, rather than TalkBack
            // reading each fragment separately.
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "Lap $currentLap of $totalLaps"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = eventName,
            style = MaterialTheme.typography.bodyMedium,
            color = RcTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.lap),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = RcTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 8.dp, end = 6.dp),
            )
            Text(
                text = currentLap.toString(),
                style = MaterialTheme.typography.displaySmall.tabular(),
                fontWeight = FontWeight.Black,
                color = RcTheme.colors.textPrimary,
            )
            Text(
                text = " " + stringResource(R.string.lap_of, totalLaps),
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = RcTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun ReplayRow(
    entry: ReplayEntryDto,
    previousPosition: Int?,
    onOpenDriver: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = teamColor(entry.teamColor).legibleOnSurface()
    val movement = previousPosition?.let { it - entry.position }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RcShapes.Small)
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = Dimens.SM, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        Text(
            text = entry.position.toString(),
            style = MaterialTheme.typography.titleMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = RcTheme.colors.textPrimary,
            modifier = Modifier.width(28.dp),
        )

        MovementIndicator(movement)

        TeamAccentBar(color = accent, height = 28.dp)

        Text(
            text = entry.driver,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (entry.driverId != null) RcTheme.colors.info else RcTheme.colors.textPrimary,
            modifier = Modifier
                .width(44.dp)
                .clickable(enabled = entry.driverId != null) {
                    onOpenDriver(entry.driverId ?: return@clickable)
                },
        )

        TeamLogo(url = entry.teamLogoUrl, size = 18.dp)

        Text(
            text = entry.teamName.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = RcTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        entry.compound?.let { TyreBadge(compound = it, size = 22.dp) }

        (entry.gap ?: entry.lapTime)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.tabular(),
                fontFamily = MonoFamily,
                color = if (entry.position == 1) RcTheme.colors.racingRedText
                else RcTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MovementIndicator(movement: Int?) {
    val (icon, tint, description) = when {
        movement != null && movement > 0 -> Triple(
            Icons.Filled.ArrowDropUp,
            RcTheme.colors.positive,
            stringResource(R.string.replay_position_up),
        )
        movement != null && movement < 0 -> Triple(
            Icons.Filled.ArrowDropDown,
            RcTheme.colors.negative,
            stringResource(R.string.replay_position_down),
        )
        else -> Triple(
            Icons.Filled.Remove,
            RcTheme.colors.textTertiary,
            stringResource(R.string.replay_position_same),
        )
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun TransportControls(
    currentLap: Int,
    totalLaps: Int,
    raceProgress: Float,
    isPlaying: Boolean,
    speed: Float,
    onScrub: (Int) -> Unit,
    onScrubProgress: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onSpeed: (Float) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.MD),
            verticalArrangement = Arrangement.spacedBy(Dimens.SM),
        ) {
            Slider(
                value = raceProgress,
                onValueChange = onScrubProgress,
                valueRange = 0f..totalLaps.coerceAtLeast(1).toFloat(),
                modifier = Modifier.semantics {
                    contentDescription = "Lap $currentLap of $totalLaps"
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(
                    icon = Icons.Filled.SkipPrevious,
                    description = stringResource(R.string.replay_first_lap),
                    onClick = { onScrub(1) },
                )
                TransportButton(
                    icon = Icons.Filled.FastRewind,
                    description = stringResource(R.string.replay_back_5),
                    onClick = { onScrub(currentLap - 5) },
                )
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTogglePlay()
                    },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.PauseCircleFilled
                        else Icons.Filled.PlayCircleFilled,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.replay_pause else R.string.replay_play
                        ),
                        tint = RcTheme.colors.racingRed,
                        modifier = Modifier.size(56.dp),
                    )
                }
                TransportButton(
                    icon = Icons.Filled.FastForward,
                    description = stringResource(R.string.replay_forward_5),
                    onClick = { onScrub(currentLap + 5) },
                )
                TransportButton(
                    icon = Icons.Filled.SkipNext,
                    description = stringResource(R.string.replay_last_lap),
                    onClick = { onScrub(totalLaps) },
                )
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SPEEDS.forEachIndexed { index, value ->
                    SegmentedButton(
                        selected = speed == value,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSpeed(value)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, SPEEDS.size),
                    ) {
                        Text(if (value == 1f) "1×" else "${value}×".replace(".0", ""))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        // 48dp is the Android minimum; the iOS build uses 44.
        modifier = Modifier.size(Dimens.MinTouch),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = RcTheme.colors.textPrimary,
        )
    }
}
