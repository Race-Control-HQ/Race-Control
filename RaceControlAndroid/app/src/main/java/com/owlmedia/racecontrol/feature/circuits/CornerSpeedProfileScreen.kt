package com.owlmedia.racecontrol.feature.circuits

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.owlmedia.racecontrol.core.design.Dimens
import com.owlmedia.racecontrol.core.design.RcPalette
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.ui.EmptyState
import com.owlmedia.racecontrol.core.ui.LoadableContent
import com.owlmedia.racecontrol.core.ui.RcDetailScaffold
import com.owlmedia.racecontrol.data.remote.dto.TrackCornerDto
import kotlin.math.cos
import kotlin.math.sin

/**
 * Corner-by-corner speed as a compact bar profile, grouped slow/medium/fast,
 * paired with the circuit outline so a corner number can be matched back to
 * where it actually sits on track. Turns corner markers into a circuit
 * "rhythm" fingerprint.
 */
@Composable
fun CornerSpeedProfileScreen(
    year: Int,
    round: Int,
    title: String,
    onBack: () -> Unit,
    viewModel: CircuitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(year, round) { viewModel.load(year, round) }

    RcDetailScaffold(title = "Corner Speed Profile", onBack = onBack) { modifier ->
        LoadableContent(state, { viewModel.load(year, round) }, modifier) { map ->
            val corners = map.corners.filter { it.speed != null }
            if (corners.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.BarChart,
                    title = "No Corner Speed Data",
                    message = "Corner speed cross-references aren't available for this circuit.",
                )
                return@LoadableContent
            }

            var selected by remember { mutableStateOf<TrackCornerDto?>(null) }

            Column(Modifier.padding(Dimens.MD)) {
                Text(
                    "Speed at each corner · km/h · tap a bar for detail",
                    style = MaterialTheme.typography.labelSmall,
                    color = RcTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(Dimens.SM))

                val maxSpeed = corners.maxOf { it.speed ?: 0.0 }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).height(180.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    corners.forEach { corner ->
                        val speed = corner.speed ?: 0.0
                        val barHeight = (speed / maxSpeed * 150).dp
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(28.dp).clickable { selected = corner },
                        ) {
                            Box(
                                Modifier
                                    .height(barHeight)
                                    .width(20.dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(tierColor(speed)),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                corner.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RcTheme.colors.textTertiary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.SM))
                Legend()

                selected?.let { corner ->
                    Spacer(Modifier.height(Dimens.SM))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RcShapes.Small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(Dimens.SM),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SM),
                    ) {
                        Text(
                            "Turn ${corner.label}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = RcTheme.colors.textPrimary,
                        )
                        corner.speed?.let {
                            Text(
                                "${it.toInt()} km/h",
                                style = MaterialTheme.typography.labelLarge,
                                color = RcTheme.colors.textSecondary,
                            )
                        }
                        corner.angle?.let {
                            Text(
                                "${kotlin.math.abs(it).toInt()}° turn",
                                style = MaterialTheme.typography.labelSmall,
                                color = RcTheme.colors.textTertiary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Dimens.MD))
                MiniMap(map.outline.map { it.x to it.y }, map.rotation, corners, selected)
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.MD)) {
        LegendSwatch("Slow (<120)", RcPalette.RacingRed)
        LegendSwatch("Medium", RcPalette.Warning)
        LegendSwatch("Fast (>200)", RcPalette.Positive)
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.height(8.dp).width(12.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = RcTheme.colors.textSecondary)
    }
}

@Composable
private fun MiniMap(
    outline: List<Pair<Double, Double>>,
    rotationDegrees: Double,
    corners: List<TrackCornerDto>,
    selected: TrackCornerDto?,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surface, RcShapes.Medium),
    ) {
        if (outline.size < 2) return@Canvas
        val rotated = outline.map { rotate(it.first, it.second, rotationDegrees) }
        val xs = rotated.map { it.first }
        val ys = rotated.map { it.second }
        val minX = xs.min(); val maxX = xs.max()
        val minY = ys.min(); val maxY = ys.max()
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

        val path = Path().apply {
            val first = project(rotated[0].first, rotated[0].second)
            moveTo(first.x, first.y)
            for (i in 1 until rotated.size) {
                val p = project(rotated[i].first, rotated[i].second)
                lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(
            path,
            RcPalette.Stroke,
            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        corners.forEach { corner ->
            val (rx, ry) = rotate(corner.x, corner.y, rotationDegrees)
            val p = project(rx, ry)
            val isSelected = selected?.id == corner.id
            val radius = if (isSelected) 9f else 6f
            drawCircle(tierColor(corner.speed ?: 0.0), radius = radius, center = p)
            if (isSelected) {
                drawCircle(Color.White, radius = radius, center = p, style = Stroke(width = 2f))
            }
        }
    }
}

private fun rotate(x: Double, y: Double, degrees: Double): Pair<Double, Double> {
    val rad = degrees * Math.PI / 180
    return (x * cos(rad) - y * sin(rad)) to (x * sin(rad) + y * cos(rad))
}

private fun tierColor(speed: Double): Color = when {
    speed < 120 -> RcPalette.RacingRed
    speed < 200 -> RcPalette.Warning
    else -> RcPalette.Positive
}
