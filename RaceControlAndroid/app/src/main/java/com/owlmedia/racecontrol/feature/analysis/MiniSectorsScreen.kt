package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.MiniSectorSegmentDto
import com.owlmedia.racecontrol.data.remote.dto.MiniSectorsResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MiniSectorsViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<MiniSectorsResponseDto>>(UiState.Idle)
    val state = _state.asStateFlow()
    fun load(year: Int, round: Int, force: Boolean = false) {
        if (!force && _state.value is UiState.Loaded) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.miniSectors(year, round)
                .onSuccess { _state.value = UiState.Loaded(it) }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}

@Composable
fun MiniSectorsScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: MiniSectorsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }
    RcDetailScaffold(title = "Mini-Sectors", onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round, true) }, modifier) { data ->
            if (!data.available || data.segments.isEmpty()) {
                EmptyState(Icons.Filled.Map, "No Mini-Sectors", "No qualifying telemetry is available.")
                return@LoadableContent
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(Dimens.MD),
                verticalArrangement = Arrangement.spacedBy(Dimens.MD),
            ) {
                Text(data.eventName ?: title, color = RcTheme.colors.textSecondary)
                RcCard {
                    MiniSectorTrack(data.segments, Modifier.fillMaxWidth().height(420.dp))
                }
                data.legend.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { item ->
                            Text(
                                "● ${item.code} · ${item.segmentsWon}",
                                color = teamColor(item.teamColor),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniSectorTrack(segments: List<MiniSectorSegmentDto>, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(Dimens.SM)) {
        val points = segments.flatMap { it.points }.filter { it.size >= 2 }
        if (points.isEmpty()) return@Canvas
        val minX = points.minOf { it[0] }
        val maxX = points.maxOf { it[0] }
        val minY = points.minOf { it[1] }
        val maxY = points.maxOf { it[1] }
        val spanX = (maxX - minX).coerceAtLeast(1.0)
        val spanY = (maxY - minY).coerceAtLeast(1.0)
        val scale = minOf(size.width / spanX.toFloat(), size.height / spanY.toFloat()) * 0.92f
        val offsetX = (size.width - spanX.toFloat() * scale) / 2f
        val offsetY = (size.height - spanY.toFloat() * scale) / 2f
        segments.forEach { segment ->
            val path = Path()
            segment.points.filter { it.size >= 2 }.forEachIndexed { index, point ->
                val mapped = Offset(
                    offsetX + (point[0] - minX).toFloat() * scale,
                    size.height - offsetY - (point[1] - minY).toFloat() * scale,
                )
                if (index == 0) path.moveTo(mapped.x, mapped.y) else path.lineTo(mapped.x, mapped.y)
            }
            drawPath(
                path,
                teamColor(segment.teamColor),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
