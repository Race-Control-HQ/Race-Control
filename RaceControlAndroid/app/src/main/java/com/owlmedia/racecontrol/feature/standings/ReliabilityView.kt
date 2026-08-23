package com.owlmedia.racecontrol.feature.standings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.owlmedia.racecontrol.core.ui.BarSegment
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.SectionHeader
import com.owlmedia.racecontrol.core.ui.StackedBar
import com.owlmedia.racecontrol.core.ui.TeamLogo
import com.owlmedia.racecontrol.feature.AppState

/** Common shape for a driver or team reliability row, so sorting and the
 * detail dialog don't need to branch on which one it is. */
data class ReliabilityEntry(
    val id: String,
    val title: String,
    val finished: Int,
    val mechanical: Int,
    val accident: Int,
    val disqualified: Int,
    val other: Int,
    val starts: Int,
    val finishRate: Double,
    val logoUrl: String? = null,
)

private enum class ReliabilitySort(val label: String) {
    FINISH_RATE("Finish Rate"),
    MECHANICAL("Mechanical"),
    ACCIDENT("Accident"),
    STARTS("Starts");

    fun value(e: ReliabilityEntry): Double = when (this) {
        FINISH_RATE -> e.finishRate
        MECHANICAL -> e.mechanical.toDouble()
        ACCIDENT -> e.accident.toDouble()
        STARTS -> e.starts.toDouble()
    }
}

/**
 * Season finish rate with a DNF breakdown by cause, for drivers and teams.
 * Both lists share one sort control so the two rankings stay comparable, and
 * each row opens a detail dialog with the full breakdown.
 */
@Composable
fun ReliabilityView(viewModel: StandingsViewModel, appState: AppState) {
    val state by viewModel.reliability.collectAsStateWithLifecycle()
    var sort by remember { mutableStateOf(ReliabilitySort.FINISH_RATE) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ReliabilityEntry?>(null) }

    LoadableContent(
        state = state,
        onRetry = { viewModel.load(appState.selectedYear, StandingsMode.RELIABILITY, force = true) },
    ) { data ->
        if (data.drivers.isEmpty() && data.teams.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Build,
                title = stringResource(R.string.no_standings_title),
                message = stringResource(
                    R.string.no_standings_message,
                    appState.selectedYear.toString(),
                ),
            )
            return@LoadableContent
        }

        val driverEntries = data.drivers.map {
            ReliabilityEntry("driver-${it.driverId}", it.name, it.finished, it.mechanical, it.accident, it.disqualified, it.other, it.starts, it.finishRate)
        }.sortedByDescending { sort.value(it) }
        val teamEntries = data.teams.map {
            ReliabilityEntry("team-${it.teamId}", it.teamName ?: it.teamId, it.finished, it.mechanical, it.accident, it.disqualified, it.other, it.starts, it.finishRate, it.teamLogoUrl)
        }.sortedByDescending { sort.value(it) }

        LazyColumn(
            contentPadding = PaddingValues(Dimens.MD),
            verticalArrangement = Arrangement.spacedBy(Dimens.SM),
        ) {
            item(key = "legend") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CauseLegend()
                    Box {
                        Row(
                            modifier = Modifier.clickable { sortMenuOpen = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.SwapVert, contentDescription = null, tint = RcTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                            Text(sort.label, style = MaterialTheme.typography.labelSmall, color = RcTheme.colors.textSecondary)
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            ReliabilitySort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { sort = option; sortMenuOpen = false },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "drivers-header") {
                SectionHeader(stringResource(R.string.standings_drivers))
            }
            items(items = driverEntries, key = { it.id }, contentType = { "reliability" }) { entry ->
                ReliabilityRow(entry = entry, onClick = { selected = entry })
            }

            item(key = "teams-header") {
                SectionHeader(stringResource(R.string.standings_teams))
            }
            items(items = teamEntries, key = { it.id }, contentType = { "reliability" }) { entry ->
                ReliabilityRow(entry = entry, onClick = { selected = entry })
            }
        }
    }

    selected?.let { entry ->
        ReliabilityDetailDialog(entry = entry, onDismiss = { selected = null })
    }
}

@Composable
private fun ReliabilityDetailDialog(entry: ReliabilityEntry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(entry.title) },
        text = {
            Column {
                Text(
                    "${(entry.finishRate * 100).toInt()}% finish rate over ${entry.starts} starts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RcTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(Dimens.SM))
                DetailRow("Finished", entry.finished, RcTheme.colors.positive)
                DetailRow("Mechanical", entry.mechanical, RcTheme.colors.warning)
                DetailRow("Accident", entry.accident, RcTheme.colors.negative)
                DetailRow("Disqualified", entry.disqualified, RcTheme.colors.racingRedText)
                DetailRow("Other", entry.other, RcTheme.colors.textTertiary)
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RcTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RcTheme.colors.textPrimary)
    }
}

@Composable
private fun CauseLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SM)) {
        LegendDot(RcTheme.colors.positive, stringResource(R.string.cause_finished))
        LegendDot(RcTheme.colors.warning, stringResource(R.string.cause_mechanical))
        LegendDot(RcTheme.colors.negative, stringResource(R.string.cause_accident))
        LegendDot(RcTheme.colors.textTertiary, stringResource(R.string.cause_other))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = RcTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ReliabilityRow(entry: ReliabilityEntry, onClick: () -> Unit) {
    val ratePercent = (entry.finishRate * 100).toInt()
    val segments = listOf(
        BarSegment(entry.finished.toFloat(), RcTheme.colors.positive),
        BarSegment(entry.mechanical.toFloat(), RcTheme.colors.warning),
        BarSegment(entry.accident.toFloat(), RcTheme.colors.negative),
        // Disqualifications are rare; folding them into "other" keeps the bar
        // readable rather than adding a fifth colour for a one-race sliver.
        BarSegment((entry.disqualified + entry.other).toFloat(), RcTheme.colors.textTertiary),
    ).filter { it.value > 0f }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Medium)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.SM)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("${entry.title}, finish rate $ratePercent percent")
                    append(", finished ${entry.finished} of ${entry.starts} starts")
                    if (entry.mechanical > 0) append(", ${entry.mechanical} mechanical")
                    if (entry.accident > 0) append(", ${entry.accident} accident")
                    if (entry.disqualified > 0) append(", ${entry.disqualified} disqualified")
                    if (entry.other > 0) append(", ${entry.other} other")
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(url = entry.logoUrl, size = 20.dp, modifier = Modifier.padding(end = 6.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = RcTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$ratePercent%",
                style = MaterialTheme.typography.titleMedium.tabular(),
                fontWeight = FontWeight.Bold,
                color = RcTheme.colors.textPrimary,
            )
        }
        Spacer(Modifier.height(6.dp))
        StackedBar(segments = segments, height = 16.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${entry.finished} / ${entry.starts}",
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = RcTheme.colors.textTertiary,
        )
    }
}
