package com.owlmedia.racecontrol.feature.racedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.feature.Routes
import com.owlmedia.racecontrol.feature.circuits.TrackMap

/**
 * Turns a completed race into a hub of deep-dives.
 *
 * A three-column grid inside an already-scrolling column, so this is a plain
 * Column of Rows rather than a LazyVerticalGrid: nesting a lazy grid in a
 * scrollable parent is both an error and unnecessary for ten fixed items.
 */
@Composable
fun RaceAnalysisGrid(
    year: Int,
    round: Int,
    title: String,
    onOpen: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(
        AnalysisTile(R.string.analysis_replay, Icons.Filled.PlayCircleFilled, RcTheme.colors.racingRed, Routes.Replay(year, round, title)),
        AnalysisTile(R.string.analysis_telemetry, Icons.Filled.MonitorHeart, RcTheme.colors.info, Routes.Telemetry(year, round, title)),
        AnalysisTile(R.string.analysis_brake_throttle, Icons.Filled.LocalFireDepartment, RcTheme.colors.racingRed, Routes.BrakeThrottle(year, round, title)),
        AnalysisTile(R.string.analysis_lap_times, Icons.Filled.ShowChart, RcTheme.colors.positive, Routes.LapTimes(year, round, title)),
        AnalysisTile(R.string.analysis_strategy, Icons.Filled.Timeline, RcTheme.colors.warning, Routes.Strategy(year, round, title)),
        AnalysisTile(R.string.analysis_race_trace, Icons.Filled.ShowChart, RcTheme.colors.positive, Routes.RaceTrace(year, round, title)),
        AnalysisTile(R.string.analysis_position_chart, Icons.Filled.Timeline, RcTheme.colors.info, Routes.PositionChart(year, round, title)),
        AnalysisTile(R.string.analysis_tyre_performance, Icons.Filled.ShowChart, RcTheme.colors.warning, Routes.TyrePerformance(year, round, title)),
        AnalysisTile(R.string.analysis_pit_stops, Icons.Filled.Timeline, RcTheme.colors.racingRed, Routes.PitStops(year, round, title)),
        AnalysisTile(R.string.analysis_pit_swimlane, Icons.Filled.CallSplit, RcTheme.colors.info, Routes.PitSwimlane(year, round, title)),
        AnalysisTile(R.string.analysis_qualifying_sectors, Icons.Filled.ShowChart, RcTheme.colors.info, Routes.QualifyingSectors(year, round, title)),
        AnalysisTile(R.string.analysis_speed_trap, Icons.Filled.Bolt, RcTheme.colors.positive, Routes.SpeedTrap(year, round, title)),
        AnalysisTile(R.string.analysis_minisectors, Icons.Filled.Map, RcTheme.colors.positive, Routes.MiniSectors(year, round, title)),
        AnalysisTile(R.string.analysis_qualifying, Icons.Filled.Timer, RcTheme.colors.info, Routes.Qualifying(year, round, title)),
        AnalysisTile(R.string.analysis_qualifying_ladder, Icons.Filled.Timeline, RcTheme.colors.warning, Routes.QualifyingLadder(year, round, title)),
        AnalysisTile(R.string.analysis_track_map, Icons.Filled.Map, RcTheme.colors.textSecondary, Routes.TrackMap(year, round, title)),
        AnalysisTile(R.string.analysis_corner_speeds, Icons.Filled.ShowChart, RcTheme.colors.positive, Routes.CornerSpeedProfile(year, round, title)),
        AnalysisTile(R.string.analysis_weather, Icons.Filled.Cloud, RcTheme.colors.info, Routes.Weather(year, round, title)),
        AnalysisTile(R.string.analysis_retirements, Icons.Filled.WarningAmber, RcTheme.colors.negative, Routes.Retirements(year, round, title)),
        AnalysisTile(R.string.analysis_flags, Icons.Filled.Flag, RcTheme.colors.warning, Routes.Flags(year, round, title)),
        AnalysisTile(R.string.analysis_penalties, Icons.Filled.Block, RcTheme.colors.racingRed, Routes.Penalties(year, round, title)),
        AnalysisTile(R.string.analysis_race_control, Icons.Filled.Article, RcTheme.colors.info, Routes.RaceControl(year, round, title)),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.analysis))
        tiles.chunked(3).forEach { rowTiles ->
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.SM),
            ) {
                rowTiles.forEach { tile ->
                    AnalysisTileCard(
                        tile = tile,
                        onClick = { onOpen(tile.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps the last row's tiles the same width as a full row.
                repeat(3 - rowTiles.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class AnalysisTile(
    val labelRes: Int,
    val icon: ImageVector,
    val tint: Color,
    val route: Any,
)

@Composable
private fun AnalysisTileCard(
    tile: AnalysisTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(tile.labelRes)
    Column(
        modifier = modifier
            .clip(RcShapes.Medium)
            .background(tile.tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            // 72dp exceeds the 48dp minimum; defaultMinSize rather than height
            // so the tile grows with large font settings instead of clipping.
            .defaultMinSize(minHeight = 72.dp)
            .padding(Dimens.SM),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = null,
            tint = tile.tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tile.tint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
