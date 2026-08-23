package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.QualifyingSectorDriverDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared-scale dot plot of the four speed-trap detection points (I1, I2,
 * FL, ST), one row per driver. Exposes setup tradeoffs — low-drag top speed
 * vs. cornering compromise — that lap time and sector gaps alone hide.
 */
@Composable
fun SpeedTrapScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: SpeedTrapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = "Speed Trap", onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round, force = true) }, modifier) { drivers ->
            if (drivers.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Speed,
                    title = "No Speed-Trap Data",
                    message = "No qualifying speed-trap readings are available for this session.",
                )
                return@LoadableContent
            }

            val minSpeed = drivers.flatMap { it.points }.minOf { it.speed }
            val maxSpeed = drivers.flatMap { it.points }.maxOf { it.speed }

            Column(Modifier.padding(Dimens.MD)) {
                Text(
                    "Speed at each detection point · km/h",
                    style = MaterialTheme.typography.labelSmall,
                    color = RcTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(Dimens.SM))
                Row(Modifier.fillMaxWidth().padding(start = 46.dp)) {
                    Text(
                        "${minSpeed.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RcTheme.colors.textTertiary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${maxSpeed.toInt()} km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = RcTheme.colors.textTertiary,
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(drivers, key = { it.code }) { driver ->
                        SpeedTrapRow(driver, minSpeed, maxSpeed)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedTrapRow(driver: SpeedTrapDriver, minSpeed: Double, maxSpeed: Double) {
    val color = teamColor(driver.teamColor)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = driver.code,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = RcTheme.colors.textPrimary,
            modifier = Modifier.width(42.dp),
        )
        Canvas(Modifier.weight(1f).height(28.dp)) {
            val span = (maxSpeed - minSpeed).takeIf { it > 0 } ?: 1.0
            fun xFor(speed: Double) = ((speed - minSpeed) / span * size.width).toFloat()
            val y = size.height / 2
            val xs = driver.points.map { xFor(it.speed) }
            for (i in 1 until xs.size) {
                drawLine(color.copy(alpha = 0.5f), Offset(xs[i - 1], y), Offset(xs[i], y), strokeWidth = 2f)
            }
            driver.points.forEachIndexed { i, point ->
                val radius = if (point.label == "ST") 6f else 3.5f
                drawCircle(color, radius = radius, center = Offset(xs[i], y))
                if (point.label == "ST") {
                    drawCircle(color, radius = radius, center = Offset(xs[i], y), style = Stroke(width = 2f))
                }
            }
        }
    }
}

data class SpeedTrapPoint(val label: String, val speed: Double)
data class SpeedTrapDriver(val code: String, val teamColor: String?, val points: List<SpeedTrapPoint>)

@HiltViewModel
class SpeedTrapViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<SpeedTrapDriver>>>(UiState.Idle)
    val state: StateFlow<UiState<List<SpeedTrapDriver>>> = _state.asStateFlow()

    private var loadedKey: String? = null

    fun load(year: Int, round: Int, force: Boolean = false) {
        val key = "$year-$round"
        if (!force && loadedKey == key && _state.value is UiState.Loaded) return
        loadedKey = key
        viewModelScope.launch {
            _state.value = UiState.Loading
            repository.qualifyingSectors(year, round)
                .onSuccess { response ->
                    val drivers = response.drivers.mapNotNull { d -> toSpeedTrapDriver(d) }
                        .sortedByDescending { it.points.last().speed }
                    _state.value = UiState.Loaded(drivers)
                }
                .onFailure { _state.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    private fun toSpeedTrapDriver(d: QualifyingSectorDriverDto): SpeedTrapDriver? {
        val i1 = d.speedI1 ?: return null
        val i2 = d.speedI2 ?: return null
        val fl = d.speedFL ?: return null
        val st = d.speedST ?: return null
        return SpeedTrapDriver(
            code = d.code,
            teamColor = d.teamColor,
            points = listOf(
                SpeedTrapPoint("I1", i1),
                SpeedTrapPoint("I2", i2),
                SpeedTrapPoint("FL", fl),
                SpeedTrapPoint("ST", st),
            ),
        )
    }
}
