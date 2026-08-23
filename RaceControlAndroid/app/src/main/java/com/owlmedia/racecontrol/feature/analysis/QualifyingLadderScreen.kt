package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.ChartPoint
import com.owlmedia.racecontrol.core.ui.ChartSeries
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.RcLineChart
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.ResultEntryDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Q1 -> Q2 -> Q3 as narrowing lanes: each driver's gap to the segment's best
 * time, carried forward until elimination. The shape of the lines tells the
 * session story before a number is read.
 */
@Composable
fun QualifyingLadderScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: QualifyingLadderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = "Qualifying Ladder", onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round, force = true) }, modifier) { drivers ->
            if (drivers.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Timer,
                    title = "No Qualifying Data",
                    message = "No qualifying session times are available for this race.",
                )
                return@LoadableContent
            }

            val series = drivers.map { d ->
                ChartSeries(
                    id = d.code,
                    color = teamColor(d.teamColor),
                    points = d.points.map { ChartPoint(it.column.toDouble(), -it.value) },
                    showPoints = true,
                )
            }

            Column(Modifier.padding(Dimens.MD)) {
                Text(
                    "Gap to session best · higher is faster · line ends at elimination",
                    style = MaterialTheme.typography.labelSmall,
                    color = RcTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(Dimens.SM))
                RcLineChart(series = series, height = 260.dp)
                Spacer(Modifier.height(Dimens.MD))

                val q3 = drivers.filter { it.eliminatedAfter == null }.sortedBy { it.finalPosition ?: 99 }
                val outQ2 = drivers.filter { it.eliminatedAfter == 2 }
                val outQ1 = drivers.filter { it.eliminatedAfter == 1 }

                LadderSection("Reached Q3", q3) { "P${it.finalPosition ?: "–"}" }
                LadderSection("Out in Q2", outQ2) { null }
                LadderSection("Out in Q1", outQ1) { null }
            }
        }
    }
}

@Composable
private fun LadderSection(label: String, drivers: List<LadderDriver>, suffix: (LadderDriver) -> String?) {
    if (drivers.isEmpty()) return
    Column(Modifier.padding(bottom = Dimens.SM)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = RcTheme.colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SM)) {
            items(drivers, key = { it.code }) { d ->
                val text = suffix(d)?.let { "${d.code} $it" } ?: d.code
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = teamColor(d.teamColor),
                )
            }
        }
    }
}

data class LadderPoint(val column: Int, val value: Double)
data class LadderDriver(
    val code: String,
    val teamColor: String?,
    val points: List<LadderPoint>,
    val finalPosition: Int?,
    val eliminatedAfter: Int?,
)

@HiltViewModel
class QualifyingLadderViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<LadderDriver>>>(UiState.Idle)
    val state: StateFlow<UiState<List<LadderDriver>>> = _state.asStateFlow()

    private var loadedKey: String? = null

    fun load(year: Int, round: Int, force: Boolean = false) {
        val key = "$year-$round"
        if (!force && loadedKey == key && _state.value is UiState.Loaded) return
        loadedKey = key
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.results(year, round, "Q")
                .onSuccess { response ->
                    _state.value = UiState.Loaded(buildLadder(response.results))
                }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    private fun buildLadder(rows: List<ResultEntryDto>): List<LadderDriver> {
        val q1Times = rows.mapNotNull { secondsFrom(it.q1) }
        val q2Times = rows.mapNotNull { secondsFrom(it.q2) }
        val q3Times = rows.mapNotNull { secondsFrom(it.q3) }
        val bestQ1 = q1Times.minOrNull()
        val bestQ2 = q2Times.minOrNull()
        val bestQ3 = q3Times.minOrNull()

        return rows.mapNotNull { row ->
            val points = mutableListOf<LadderPoint>()
            secondsFrom(row.q1)?.let { t -> bestQ1?.let { best -> points.add(LadderPoint(1, t - best)) } }
            secondsFrom(row.q2)?.let { t -> bestQ2?.let { best -> points.add(LadderPoint(2, t - best)) } }
            secondsFrom(row.q3)?.let { t -> bestQ3?.let { best -> points.add(LadderPoint(3, t - best)) } }
            if (points.isEmpty() || row.abbreviation == null) return@mapNotNull null
            val eliminatedAfter = if (points.size < 3) points.lastOrNull()?.column else null
            val finalPosition = if (points.size == 3) row.position?.toInt() else null
            LadderDriver(row.abbreviation, row.teamColor, points, finalPosition, eliminatedAfter)
        }
    }

    /** Parses a "m:ss.mmm" lap-time string into seconds. */
    private fun secondsFrom(text: String?): Double? {
        if (text.isNullOrEmpty()) return null
        val parts = text.split(":")
        return if (parts.size == 2) {
            val minutes = parts[0].toDoubleOrNull() ?: return null
            val seconds = parts[1].toDoubleOrNull() ?: return null
            minutes * 60 + seconds
        } else {
            text.toDoubleOrNull()
        }
    }
}
