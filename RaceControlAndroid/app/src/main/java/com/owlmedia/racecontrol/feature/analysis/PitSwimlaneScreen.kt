package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcPalette
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.PitStopLedgerItemDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pit stops aligned on a shared lap axis, with position before/after the
 * stop and whether it was an undercut, overcut, or held position — turning
 * strategy into visible cause and effect instead of a ledger of times.
 */
@Composable
fun PitSwimlaneScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: PitSwimlaneViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = "Pit Swimlane", onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round, force = true) }, modifier) { data ->
            if (data.stops.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.CallSplit,
                    title = "No Pit-Stop Data",
                    message = "No timed pit-lane transits are available for this race.",
                )
                return@LoadableContent
            }

            var selected by remember { mutableStateOf<PitStopLedgerItemDto?>(null) }

            Column(Modifier.padding(Dimens.MD)) {
                Text(
                    text = "Lap ${data.minLap}–${data.maxLap} · entry → rejoin position · tap a stop for detail",
                    style = MaterialTheme.typography.labelSmall,
                    color = RcTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(Dimens.SM))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.SM)) {
                    items(data.stops, key = { it.id }) { stop ->
                        SwimlaneRow(stop, data.minLap, data.maxLap) { selected = stop }
                    }
                }

                Spacer(Modifier.height(Dimens.SM))
                Row(Modifier.fillMaxWidth().padding(start = 60.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("L${data.minLap}", style = MaterialTheme.typography.labelSmall, color = RcTheme.colors.textTertiary)
                    Text(
                        "L${(data.minLap + data.maxLap) / 2}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RcTheme.colors.textTertiary,
                    )
                    Text("L${data.maxLap}", style = MaterialTheme.typography.labelSmall, color = RcTheme.colors.textTertiary)
                }

                selected?.let { stop ->
                    Spacer(Modifier.height(Dimens.SM))
                    DetailCard(stop)
                }
            }
        }
    }
}

@Composable
private fun SwimlaneRow(
    stop: PitStopLedgerItemDto,
    minLap: Int,
    maxLap: Int,
    onClick: () -> Unit,
) {
    val span = (maxLap - minLap).coerceAtLeast(1)
    val pitFraction = (stop.lap - minLap).toFloat() / span

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        Text(
            text = stop.driverCode,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = RcTheme.colors.textPrimary,
            modifier = Modifier.width(42.dp),
        )

        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).height(20.dp)) {
            val pitX = maxWidth * pitFraction
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RcShapes.Small)
                    .background(RcPalette.SurfaceElevated),
            )
            Box(
                Modifier
                    .width(pitX.coerceAtLeast(2.dp))
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RcShapes.Small)
                    .background(teamColor(stop.teamColor).copy(alpha = 0.6f)),
            )
            Box(
                Modifier
                    .width((maxWidth - pitX).coerceAtLeast(2.dp))
                    .height(4.dp)
                    .align(Alignment.CenterEnd)
                    .clip(RcShapes.Small)
                    .background(RcPalette.Info.copy(alpha = 0.6f)),
            )
            Box(
                Modifier
                    .padding(start = pitX)
                    .width(2.dp)
                    .height(20.dp)
                    .background(RcPalette.RacingRed),
            )
        }

        val gained = stop.positionsGained ?: 0
        val badgeColor = when {
            gained > 0 -> RcTheme.colors.positive
            gained < 0 -> RcTheme.colors.racingRedText
            else -> RcTheme.colors.textSecondary
        }
        Text(
            text = "P${stop.entryPosition ?: "–"} → P${stop.rejoinPosition ?: "–"}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = badgeColor,
            modifier = Modifier.width(76.dp),
        )
    }
}

@Composable
private fun DetailCard(stop: PitStopLedgerItemDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RcShapes.Small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Dimens.SM),
    ) {
        Text(
            text = "${stop.driverCode} · Stop ${stop.stop} · Lap ${stop.lap}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = RcTheme.colors.textPrimary,
        )
        val lossSeconds = stop.lossMs / 1000.0
        Text(
            text = "Pit-lane loss %.1fs · %s · P%s → P%s".format(
                lossSeconds,
                stop.outcome.lowercase().replaceFirstChar { it.uppercase() },
                stop.entryPosition?.toString() ?: "–",
                stop.rejoinPosition?.toString() ?: "–",
            ),
            style = MaterialTheme.typography.labelSmall,
            color = RcTheme.colors.textSecondary,
        )
        if (stop.rivals.isNotEmpty()) {
            Text(
                text = "Rival window: ${stop.rivals.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = RcTheme.colors.textTertiary,
            )
        }
    }
}

data class PitSwimlaneData(
    val stops: List<PitStopLedgerItemDto>,
    val minLap: Int,
    val maxLap: Int,
)

@HiltViewModel
class PitSwimlaneViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<PitSwimlaneData>>(UiState.Idle)
    val state: StateFlow<UiState<PitSwimlaneData>> = _state.asStateFlow()

    private var loadedKey: String? = null

    fun load(year: Int, round: Int, force: Boolean = false) {
        val key = "$year-$round"
        if (!force && loadedKey == key && _state.value is UiState.Loaded) return
        loadedKey = key
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.pitStops(year, round)
                .onSuccess { response ->
                    val stops = response.stops.sortedBy { it.lap }
                    val laps = stops.map { it.lap }
                    val minLap = ((laps.minOrNull() ?: 1) - 1).coerceAtLeast(0)
                    val maxLap = (laps.maxOrNull() ?: 1) + 1
                    _state.value = UiState.Loaded(PitSwimlaneData(stops, minLap, maxLap))
                }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}
