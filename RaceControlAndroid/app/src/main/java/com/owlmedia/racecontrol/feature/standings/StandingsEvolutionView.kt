package com.owlmedia.racecontrol.feature.standings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.R
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.ChartDomain
import com.owlmedia.racecontrol.core.ui.ChartPoint
import com.owlmedia.racecontrol.core.ui.ChartSeries
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcLineChart
import com.owlmedia.racecontrol.feature.AppState

/**
 * Cumulative championship points by round.
 *
 * Only the top ten are drawn: twenty overlapping lines on a phone-width chart
 * is noise, and the interesting question ("who is actually in this") is
 * answered by the leaders.
 */
@Composable
fun StandingsEvolutionView(viewModel: StandingsViewModel, appState: AppState) {
    val state by viewModel.evolution.collectAsStateWithLifecycle()

    LoadableContent(
        state = state,
        onRetry = { viewModel.load(appState.selectedYear, StandingsMode.PROGRESS, force = true) },
    ) { data ->
        if (data.drivers.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.ShowChart,
                title = stringResource(R.string.no_standings_title),
                message = stringResource(
                    R.string.no_standings_message,
                    appState.selectedYear.toString(),
                ),
            )
            return@LoadableContent
        }

        val top = remember(data) { data.drivers.sortedByDescending { it.points }.take(10) }

        val series = remember(top) {
            top.map { driver ->
                ChartSeries(
                    id = driver.driverId,
                    color = teamColor(driver.teamColor).legibleOnSurface(),
                    points = driver.series.map { ChartPoint(it.round.toDouble(), it.points) },
                )
            }
        }
        // Championship points are cumulative and start at zero, so the domain is
        // clamped at 0 - the default padding would otherwise render a negative
        // axis label, which is meaningless here.
        val domain = remember(series) {
            ChartDomain.cover(series, yPadding = 0.04).copy(minY = 0.0)
        }
        val axisLabels = remember(domain) {
            val steps = 4
            (0..steps).map { index ->
                val value = domain.minY + domain.spanY * (index.toDouble() / steps)
                value.toInt().toString()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.MD),
            verticalArrangement = Arrangement.spacedBy(Dimens.MD),
        ) {
            RcCard {
                RcLineChart(
                    series = series,
                    domain = domain,
                    yAxisLabels = axisLabels,
                    height = 240.dp,
                    contentDescription = "Championship progression chart",
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.MD),
            ) {
                top.forEach { driver ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(teamColor(driver.teamColor).legibleOnSurface())
                        )
                        Text(
                            text = driver.code ?: driver.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = RcTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
