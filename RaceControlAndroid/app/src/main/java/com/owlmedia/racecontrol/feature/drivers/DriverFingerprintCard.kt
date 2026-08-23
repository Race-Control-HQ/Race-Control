package com.owlmedia.racecontrol.feature.drivers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcPalette
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.RcCard
import com.owlmedia.racecontrol.data.remote.dto.DriverFingerprintResponseDto
import com.owlmedia.racecontrol.data.repository.RaceControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DriverFingerprintViewModel @Inject constructor(
    private val repository: RaceControlRepository,
) : ViewModel() {
    private val _data = MutableStateFlow<DriverFingerprintResponseDto?>(null)
    val data = _data.asStateFlow()
    fun load(year: Int, driverId: String) {
        if (_data.value?.year == year && _data.value?.driverId == driverId) return
        viewModelScope.launch { repository.driverFingerprint(year, driverId).onSuccess { _data.value = it } }
    }
}

@Composable
fun DriverFingerprintCard(
    year: Int,
    driverId: String,
    accent: Color,
    viewModel: DriverFingerprintViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    LaunchedEffect(year, driverId) { viewModel.load(year, driverId) }
    val value = data ?: return
    if (!value.available || value.axes.size != 6) return
    RcCard {
        Column(Modifier.padding(Dimens.SM)) {
            Text("SEASON FINGERPRINT", color = RcTheme.colors.textSecondary)
            Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                val centre = Offset(size.width / 2, size.height / 2)
                val radius = minOf(size.width, size.height) * 0.38f
                fun point(index: Int, scale: Float): Offset {
                    val angle = index.toDouble() / value.axes.size * PI * 2 - PI / 2
                    return Offset(
                        centre.x + cos(angle).toFloat() * radius * scale,
                        centre.y + sin(angle).toFloat() * radius * scale,
                    )
                }
                for (ring in 1..4) {
                    val path = Path()
                    value.axes.indices.forEach { index ->
                        val p = point(index, ring / 4f)
                        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    path.close()
                    drawPath(path, RcPalette.Stroke, style = Stroke(1f))
                }
                val shape = Path()
                value.axes.forEachIndexed { index, axis ->
                    val p = point(index, axis.percentile / 100f)
                    if (index == 0) shape.moveTo(p.x, p.y) else shape.lineTo(p.x, p.y)
                }
                shape.close()
                drawPath(shape, accent.copy(alpha = 0.28f))
                drawPath(shape, accent, style = Stroke(2.dp.toPx()))
            }
            Text(
                value.axes.joinToString(" · ") { "${it.label} ${it.percentile}" },
                color = RcTheme.colors.textTertiary,
            )
        }
    }
}
