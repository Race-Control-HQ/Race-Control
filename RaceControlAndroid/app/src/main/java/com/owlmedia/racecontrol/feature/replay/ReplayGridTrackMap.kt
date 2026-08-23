package com.owlmedia.racecontrol.feature.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlmedia.racecontrol.core.design.RcPalette
import com.owlmedia.racecontrol.core.design.RcShapes
import com.owlmedia.racecontrol.core.design.RcTheme
import com.owlmedia.racecontrol.core.design.teamColor
import com.owlmedia.racecontrol.data.remote.dto.CircuitMapDto
import com.owlmedia.racecontrol.data.remote.dto.ReplayDriverDto
import com.owlmedia.racecontrol.data.remote.dto.ReplayEntryDto
import com.owlmedia.racecontrol.data.remote.dto.ReplayLapPositionsDto
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

private data class ReplayPoint(val x: Double, val y: Double)

@Composable
internal fun ReplayGridTrackMap(
    map: CircuitMapDto,
    lapPositions: ReplayLapPositionsDto?,
    lapFraction: Float,
    order: List<ReplayEntryDto>,
    drivers: List<ReplayDriverDto>,
    modifier: Modifier = Modifier,
) {
    if (map.outline.size < 2) return

    val outline = remember(map) {
        val radians = Math.toRadians(map.rotation)
        val cosA = cos(radians)
        val sinA = sin(radians)
        map.outline.map { point ->
            ReplayPoint(
                x = point.x * cosA - point.y * sinA,
                y = point.x * sinA + point.y * cosA,
            )
        }
    }
    val driverMetadata = remember(drivers) { drivers.associateBy { it.code } }
    val orderByDriver = remember(order) { order.associate { it.driver to it.position } }
    val timingByDriver = remember(order) { order.associateBy { it.driver } }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = RcPalette.TextPrimary,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
    )
    val leader = order.firstOrNull()?.driver
    val carCount = lapPositions?.positions?.size ?: 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(RcPalette.Surface, RcShapes.Medium)
            .semantics {
                contentDescription = buildString {
                    append("Full-grid circuit replay. $carCount cars on track.")
                    leader?.let { append(" $it leads.") }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val minX = outline.minOf { it.x }
            val maxX = outline.maxOf { it.x }
            val minY = outline.minOf { it.y }
            val maxY = outline.maxOf { it.y }
            val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
            val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0
            val padding = 24.dp.toPx()
            val availableWidth = (size.width - padding * 2).coerceAtLeast(1f)
            val availableHeight = (size.height - padding * 2).coerceAtLeast(1f)
            val fit = min(availableWidth / spanX, availableHeight / spanY).toFloat()
            val offsetX = padding + (availableWidth - (spanX * fit).toFloat()) / 2f
            val offsetY = padding + (availableHeight - (spanY * fit).toFloat()) / 2f

            fun project(point: ReplayPoint) = Offset(
                x = offsetX + ((point.x - minX) * fit).toFloat(),
                y = offsetY + ((maxY - point.y) * fit).toFloat(),
            )

            val trackPath = Path()
            outline.forEachIndexed { index, point ->
                val projected = project(point)
                if (index == 0) trackPath.moveTo(projected.x, projected.y)
                else trackPath.lineTo(projected.x, projected.y)
            }
            trackPath.close()
            drawPath(
                path = trackPath,
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f),
                style = Stroke(
                    width = 12.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawPath(
                path = trackPath,
                color = RcPalette.TextTertiary.copy(alpha = 0.38f),
                style = Stroke(
                    width = 7.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            val start = project(outline.first())
            drawCircle(androidx.compose.ui.graphics.Color.White, 5.dp.toPx(), start)
            drawCircle(
                color = androidx.compose.ui.graphics.Color.Black,
                radius = 5.dp.toPx(),
                center = start,
                style = Stroke(2.dp.toPx()),
            )

            val radians = Math.toRadians(map.rotation)
            val cosA = cos(radians)
            val sinA = sin(radians)
            val cars = lapPositions?.positions?.entries
                ?.sortedByDescending { orderByDriver[it.key] ?: Int.MAX_VALUE }
                .orEmpty()

            cars.forEach { (code, samples) ->
                val timing = timingByDriver[code]
                val leaderLapMs = order.firstOrNull()?.lapTimeMs
                val gapFraction = if (timing?.gapMs != null && leaderLapMs != null && leaderLapMs > 0) {
                    timing.gapMs.toFloat() / leaderLapMs
                } else {
                    0f
                }
                val rawFraction = lapFraction - gapFraction
                val spatialFraction = rawFraction - floor(rawFraction)
                val raw = interpolate(samples, spatialFraction) ?: return@forEach
                val rotated = ReplayPoint(
                    x = raw.x * cosA - raw.y * sinA,
                    y = raw.x * sinA + raw.y * cosA,
                )
                val point = project(rotated)
                drawCircle(
                    color = teamColor(driverMetadata[code]?.teamColor),
                    radius = 7.dp.toPx(),
                    center = point,
                )
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.Black,
                    radius = 7.dp.toPx(),
                    center = point,
                    style = Stroke(1.5.dp.toPx()),
                )
                val measured = textMeasurer.measure(code, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        point.x - measured.size.width / 2f,
                        point.y - 16.dp.toPx() - measured.size.height / 2f,
                    ),
                )
            }
        }

        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Sensors,
                contentDescription = null,
                tint = RcTheme.colors.racingRedText,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = "LIVE REPLAY",
                color = RcTheme.colors.racingRedText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
        }

        if (carCount == 0) {
            Text(
                text = "Car positions aren't available for this lap.",
                color = RcTheme.colors.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
            )
        }
    }
}

private fun interpolate(samples: List<List<Double>>, fraction: Float): ReplayPoint? {
    val valid = samples.filter { it.size >= 2 && it[0].isFinite() && it[1].isFinite() }
    val first = valid.firstOrNull() ?: return null
    if (valid.size == 1) return ReplayPoint(first[0], first[1])
    val position = fraction.coerceIn(0f, 1f) * (valid.size - 1)
    val lower = floor(position).toInt().coerceIn(0, valid.lastIndex)
    val upper = (lower + 1).coerceAtMost(valid.lastIndex)
    val amount = position - lower
    return ReplayPoint(
        x = valid[lower][0] + (valid[upper][0] - valid[lower][0]) * amount,
        y = valid[lower][1] + (valid[upper][1] - valid[lower][1]) * amount,
    )
}
