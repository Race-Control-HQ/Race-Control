package com.owlmedia.racecontrol.feature.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.owlmedia.racecontrol.core.ui.ErrorState
import com.owlmedia.racecontrol.core.ui.LoadingIndicator
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.core.ui.UiState
import com.owlmedia.racecontrol.data.remote.dto.RaceDriverDto
import com.owlmedia.racecontrol.data.remote.dto.TelemetryTraceDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Paints the racing line by brake / coast / partial-throttle / full-throttle
 * intensity, turning already-decoded telemetry channels into a spatial read
 * on driving style: braking zones, lift-and-coast, and confidence on exit —
 * corner by corner, at a glance.
 */
@Composable
fun BrakeThrottleScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: BrakeThrottleViewModel = hiltViewModel(),
) {
    val driversState by viewModel.drivers.collectAsStateWithLifecycle()
    val traceState by viewModel.trace.collectAsStateWithLifecycle()
    val selectedCode by viewModel.selectedCode.collectAsStateWithLifecycle()

    LaunchedEffect(year, round) { viewModel.loadDrivers(year, round) }

    RcDetailScaffold(title = "Brake / Throttle", onBack = onBack) { modifier ->
        when (val drivers = driversState) {
            is UiState.Idle, is UiState.Loading -> LoadingIndicator(modifier)
            is UiState.Failed -> ErrorState(drivers.message, { viewModel.loadDrivers(year, round) }, modifier)
            is UiState.Loaded -> Column(modifier.padding(Dimens.MD)) {
                DriverChips(drivers.value, selectedCode) { viewModel.select(it, year, round) }
                Row(Modifier.height(Dimens.SM)) {}
                when (val trace = traceState) {
                    is UiState.Idle -> EmptyState(
                        icon = Icons.Filled.LocalFireDepartment,
                        title = "Pick a Driver",
                        message = "Select a driver to paint their fastest lap by brake and throttle.",
                    )
                    is UiState.Loading -> LoadingIndicator()
                    is UiState.Failed -> ErrorState(
                        message = trace.message,
                        onRetry = { selectedCode?.let { viewModel.loadTrace(it, year, round) } },
                    )
                    is UiState.Loaded -> BrakeThrottleMap(trace.value)
                }
            }
        }
    }
}

@Composable
private fun DriverChips(
    drivers: List<RaceDriverDto>,
    selectedCode: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
    ) {
        drivers.forEach { driver ->
            val code = driver.code ?: "?"
            val on = selectedCode == code
            val accent = teamColor(driver.teamColor)
            FilterChip(
                selected = on,
                onClick = { onSelect(code) },
                label = { Text(code, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent,
                    selectedLabelColor = Color.Black,
                ),
            )
        }
    }
}

@Composable
private fun BrakeThrottleMap(trace: TelemetryTraceDto) {
    Column {
        trace.lapTime?.let {
            Text(
                text = "${trace.code} · fastest lap · $it",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = RcTheme.colors.textSecondary,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(Dimens.SM))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(MaterialTheme.colorScheme.surface, RcShapes.Medium),
        ) {
            val n = minOf(trace.x.size, trace.y.size, trace.brake.size, trace.throttle.size)
            if (n < 2) return@Canvas

            val minX = trace.x.take(n).min()
            val maxX = trace.x.take(n).max()
            val minY = trace.y.take(n).min()
            val maxY = trace.y.take(n).max()
            val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
            val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0
            val pad = 20f
            val scale = minOf((size.width - pad * 2) / spanX.toFloat(), (size.height - pad * 2) / spanY.toFloat())
            val offX = (size.width - spanX.toFloat() * scale) / 2f
            val offY = (size.height - spanY.toFloat() * scale) / 2f
            fun project(x: Double, y: Double) = Offset(
                ((x - minX) * scale + offX).toFloat(),
                size.height - ((y - minY) * scale + offY).toFloat(),
            )

            val base = Path().apply {
                moveTo(project(trace.x[0], trace.y[0]).x, project(trace.x[0], trace.y[0]).y)
                for (i in 1 until n) {
                    val p = project(trace.x[i], trace.y[i])
                    lineTo(p.x, p.y)
                }
            }
            drawPath(base, Color.Black.copy(alpha = 0.55f), style = Stroke(width = 10f, cap = StrokeCap.Round))

            for (i in 1 until n) {
                val from = project(trace.x[i - 1], trace.y[i - 1])
                val to = project(trace.x[i], trace.y[i])
                val zone = controlZone(trace.brake[i], trace.throttle[i])
                drawLine(zone.color, from, to, strokeWidth = 5f, cap = StrokeCap.Round)
            }

            val start = project(trace.x[0], trace.y[0])
            drawCircle(Color.White, radius = 5f, center = start)
            drawCircle(Color.Black, radius = 5f, center = start, style = Stroke(width = 2f))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(Dimens.SM))
        Legend()
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.MD)) {
        LegendSwatch("Brake", ControlZone.BRAKE.color)
        LegendSwatch("Coast", ControlZone.COAST.color)
        LegendSwatch("Partial", ControlZone.PARTIAL.color)
        LegendSwatch("Full throttle", ControlZone.FULL.color)
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .height(5.dp)
                .width(14.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = RcTheme.colors.textSecondary)
    }
}

private enum class ControlZone(val color: Color) {
    BRAKE(RcPalette.RacingRed),
    COAST(RcPalette.Info),
    PARTIAL(RcPalette.Warning),
    FULL(RcPalette.Positive),
}

private fun controlZone(brake: Int, throttle: Double): ControlZone = when {
    brake > 0 -> ControlZone.BRAKE
    throttle < 10 -> ControlZone.COAST
    throttle < 90 -> ControlZone.PARTIAL
    else -> ControlZone.FULL
}

@HiltViewModel
class BrakeThrottleViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {

    private val _drivers = MutableStateFlow<UiState<List<RaceDriverDto>>>(UiState.Idle)
    val drivers: StateFlow<UiState<List<RaceDriverDto>>> = _drivers.asStateFlow()

    private val _trace = MutableStateFlow<UiState<TelemetryTraceDto>>(UiState.Idle)
    val trace: StateFlow<UiState<TelemetryTraceDto>> = _trace.asStateFlow()

    private val _selectedCode = MutableStateFlow<String?>(null)
    val selectedCode: StateFlow<String?> = _selectedCode.asStateFlow()

    private val traceCache = mutableMapOf<String, TelemetryTraceDto>()
    private var loadedKey: String? = null

    fun loadDrivers(year: Int, round: Int) {
        val key = "$year-$round"
        if (loadedKey == key && _drivers.value is UiState.Loaded) return
        loadedKey = key
        viewModelScope.launch {
            _drivers.value = UiState.Loading
            repository.raceDrivers(year, round)
                .onSuccess { drivers ->
                    _drivers.value = UiState.Loaded(drivers)
                    drivers.firstOrNull()?.code?.let { select(it, year, round) }
                }
                .onFailure { _drivers.value = UiState.Failed(repository.messageFor(it)) }
        }
    }

    fun select(code: String, year: Int, round: Int) {
        _selectedCode.value = code
        loadTrace(code, year, round)
    }

    fun loadTrace(code: String, year: Int, round: Int) {
        traceCache[code]?.let {
            _trace.value = UiState.Loaded(it)
            return
        }
        viewModelScope.launch {
            _trace.value = UiState.Loading
            repository.telemetry(year, round, code)
                .onSuccess { response ->
                    val t = response.trace
                    if (t == null) {
                        _trace.value = UiState.Failed("No telemetry available for $code in this session.")
                    } else {
                        traceCache[code] = t
                        _trace.value = UiState.Loaded(t)
                    }
                }
                .onFailure { _trace.value = UiState.Failed(repository.messageFor(it)) }
        }
    }
}
