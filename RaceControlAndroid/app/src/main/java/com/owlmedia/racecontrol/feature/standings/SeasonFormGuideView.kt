package com.owlmedia.racecontrol.feature.standings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.owlmedia.racecontrol.core.design.RcPalette
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.data.remote.dto.RaceEventDto
import com.owlmedia.racecontrol.data.remote.dto.ResultEntryDto
import com.owlmedia.racecontrol.feature.AppState

/**
 * Driver x round finishing-position heatmap for the season. Tap a cell for
 * that driver's result in that round.
 */
@Composable
fun SeasonFormGuideView(viewModel: StandingsViewModel, appState: AppState) {
    val state by viewModel.formGuide.collectAsStateWithLifecycle()
    val selection by viewModel.formGuideSelection.collectAsStateWithLifecycle()

    LoadableContent(
        state = state,
        onRetry = { viewModel.load(appState.selectedYear, StandingsMode.FORM, force = true) },
    ) { data ->
        if (data.rounds.isEmpty() || data.drivers.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.GridView,
                title = stringResource(R.string.no_standings_title),
                message = stringResource(
                    R.string.no_standings_message,
                    appState.selectedYear.toString(),
                ),
            )
            return@LoadableContent
        }

        Column(Modifier.fillMaxWidth().padding(Dimens.MD)) {
            Text(
                text = "Finishing position by round · tap a cell for detail",
                style = MaterialTheme.typography.labelSmall,
                color = RcTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(Dimens.SM))
            FormGuideGrid(
                rounds = data.rounds,
                drivers = data.drivers,
                onSelect = viewModel::selectFormCell,
            )
            selection?.let { sel ->
                Spacer(Modifier.height(Dimens.SM))
                SelectionBar(sel)
            }
            Spacer(Modifier.height(Dimens.SM))
            FormLegend()
        }
    }
}

private val RowHeight = 32.dp
private val CellWidth = 34.dp
private val NameColumnWidth = 52.dp

@Composable
private fun FormGuideGrid(
    rounds: List<RaceEventDto>,
    drivers: List<SeasonFormDriverRow>,
    onSelect: (SeasonFormDriverRow, RaceEventDto, ResultEntryDto?) -> Unit,
) {
    val scrollState = rememberScrollState()
    val roundLabels = remember(rounds) { roundShortLabels(rounds) }
    Row(Modifier.fillMaxWidth()) {
        // Fixed leading column: driver codes, doesn't scroll horizontally.
        Column(Modifier.width(NameColumnWidth)) {
            Spacer(Modifier.height(RowHeight))
            drivers.forEach { driver ->
                Box(Modifier.height(RowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = driver.code,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RcTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }

        Column(Modifier.horizontalScroll(scrollState)) {
            Row {
                rounds.forEach { round ->
                    Box(
                        modifier = Modifier.width(CellWidth).height(RowHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = roundLabels[round.round] ?: "R${round.round}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RcTheme.colors.textTertiary,
                        )
                    }
                }
            }
            drivers.forEach { driver ->
                Row {
                    rounds.forEach { round ->
                        val entry = driver.cells[round.round]
                        FormCell(
                            entry = entry,
                            onClick = { onSelect(driver, round, entry) },
                            driverName = driver.name,
                            roundName = round.displayName,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormCell(
    entry: ResultEntryDto?,
    driverName: String,
    roundName: String,
    onClick: () -> Unit,
) {
    val tier = FormTier.of(entry)
    Box(
        modifier = Modifier
            .width(CellWidth)
            .height(RowHeight)
            .padding(1.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(tier.background)
            .clickable(onClickLabel = "$driverName, $roundName") { onClick() }
            .semantics { contentDescription = "$driverName, $roundName, ${tier.accessibilityLabel(entry)}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tier.label(entry),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tier.textColor,
        )
    }
}

@Composable
private fun SelectionBar(selection: FormGuideSelection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Dimens.SM)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        Text(
            text = "${selection.driverName} · ${selection.roundName} · ${selection.resultLabel}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = RcTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FormLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.MD),
    ) {
        LegendSwatch("P1–3", FormTier.Podium.background)
        LegendSwatch("P4–10", FormTier.Points.background)
        LegendSwatch("P11+", FormTier.OutsidePoints.background)
        LegendSwatch("DNF/DSQ", FormTier.Dnf.background)
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = RcTheme.colors.textSecondary)
    }
}

private sealed class FormTier(val background: Color, val textColor: Color) {
    data object Podium : FormTier(RcPalette.Gold, Color.Black)
    data object Points : FormTier(RcPalette.Positive.copy(alpha = 0.55f), Color.White)
    data object OutsidePoints : FormTier(RcPalette.SurfaceElevated, RcPalette.TextPrimary)
    data object Dnf : FormTier(RcPalette.Negative.copy(alpha = 0.65f), Color.White)
    data object Unknown : FormTier(RcPalette.Surface, RcPalette.TextTertiary)

    fun label(entry: ResultEntryDto?): String {
        if (entry == null) return "–"
        val status = entry.status
        if (status != null && !isFinishStatus(status)) return shortStatusCode(status)
        return entry.positionLabel
    }

    fun accessibilityLabel(entry: ResultEntryDto?): String {
        if (entry == null) return "no result"
        val status = entry.status
        if (status != null && !isFinishStatus(status)) return status
        return "P${entry.positionLabel}"
    }

    companion object {
        fun of(entry: ResultEntryDto?): FormTier {
            if (entry == null) return Unknown
            val status = entry.status
            if (status != null && !isFinishStatus(status)) return Dnf
            val position = entry.position ?: entry.classifiedPosition?.toDoubleOrNull() ?: return Unknown
            return when {
                position <= 3 -> Podium
                position <= 10 -> Points
                else -> OutsidePoints
            }
        }

        private fun shortStatusCode(status: String): String {
            val s = status.lowercase()
            return when {
                s.contains("disqualified") -> "DSQ"
                s.contains("did not start") -> "DNS"
                s.contains("did not qualify") -> "DNQ"
                else -> "DNF"
            }
        }
    }
}

/**
 * Short column labels for each round, de-duplicated across the whole season:
 * a plain 3-letter prefix collides often (Montréal/Monte Carlo, United
 * States/United Kingdom, Australia/Austria all share a prefix), so widen the
 * colliding label until it's unique, falling back to the round number if it
 * still can't be disambiguated.
 */
private fun roundShortLabels(rounds: List<RaceEventDto>): Map<Int, String> {
    val used = mutableSetOf<String>()
    val labels = mutableMapOf<Int, String>()
    for (round in rounds) {
        val source = (round.location ?: round.country ?: round.name ?: "R${round.round}").filter { it.isLetter() }
        var length = minOf(3, source.length)
        var label = if (length > 0) source.take(length).uppercase() else "R${round.round}"
        while (used.contains(label) && length < source.length) {
            length += 1
            label = source.take(length).uppercase()
        }
        if (used.contains(label)) label = "R${round.round}"
        used.add(label)
        labels[round.round] = label
    }
    return labels
}
