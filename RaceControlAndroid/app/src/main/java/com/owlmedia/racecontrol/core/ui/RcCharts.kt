package com.owlmedia.racecontrol.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlmedia.racecontrol.core.design.RcPalette
import kotlinx.coroutines.flow.first

/**
 * A small Compose-Canvas charting layer.
 *
 * ## Why not a charting library?
 *
 * The agreed plan called for Vico. This module was written without network
 * access to a dependency resolver, so Vico's API could not be verified against
 * a real artifact, and guessing at a chart library's API produces code that
 * looks right and does not compile. Every chart this app needs (multi-series
 * lines, a sparkline, stacked horizontal bars) is a few dozen lines of Canvas,
 * and half of them (track maps, stint timelines) were going to be Canvas
 * regardless.
 *
 * Everything here is deliberately behind small composables with plain data
 * inputs, so swapping in Vico later is a change to this file only.
 */

/**
 * Applies [description] as a merged content description when non-null,
 * making a Canvas-drawn chart -- which otherwise exposes nothing to
 * accessibility services or Compose UI tests -- assertable/announced like any
 * other composable. A null description leaves the modifier untouched so
 * existing call sites are unaffected.
 */
private fun Modifier.chartSemantics(description: String?): Modifier =
    if (description == null) this else semantics(mergeDescendants = true) { contentDescription = description }

/** One line on a [RcLineChart]. */
data class ChartSeries(
    val id: String,
    val color: Color,
    val points: List<ChartPoint>,
    val strokeWidth: Dp = 2.dp,
    val showLine: Boolean = true,
    val showPoints: Boolean = false,
    val pointRadius: Dp = 3.dp,
)

data class ChartPoint(val x: Double, val y: Double)

/**
 * A translucent vertical band drawn behind the grid/series, e.g. a safety-car
 * period shaded across the laps it covered on a lap-time chart.
 */
data class ChartBand(val minX: Double, val maxX: Double, val color: Color)

data class ChartDomain(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
) {
    val spanX: Double get() = (maxX - minX).takeIf { it > 0 } ?: 1.0
    val spanY: Double get() = (maxY - minY).takeIf { it > 0 } ?: 1.0

    companion object {
        /** Domain covering every series, with a little vertical breathing room. */
        fun cover(series: List<ChartSeries>, yPadding: Double = 0.06): ChartDomain {
            val points = series.flatMap { it.points }
            if (points.isEmpty()) return ChartDomain(0.0, 1.0, 0.0, 1.0)
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val pad = (maxY - minY) * yPadding
            return ChartDomain(minX, maxX, minY - pad, maxY + pad)
        }
    }
}

/**
 * Multi-series line chart with optional y-axis labels and a draggable playhead.
 *
 * [onPlayheadChange] receiving a non-null value makes the chart interactive:
 * tapping or dragging reports the x value under the finger, which is how the
 * telemetry screen keeps its readouts, mini-map dot and charts in sync.
 */
@Composable
fun RcLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    domain: ChartDomain = ChartDomain.cover(series),
    yAxisLabels: List<String> = emptyList(),
    playheadX: Double? = null,
    onPlayheadChange: ((Double) -> Unit)? = null,
    gridLines: Int = 4,
    bands: List<ChartBand> = emptyList(),
    contentDescription: String? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 10.sp, color = RcPalette.TextTertiary)

    // Reserve gutter space for axis labels so the plot never overlaps them.
    val leftGutter = with(density) { if (yAxisLabels.isEmpty()) 0.dp.toPx() else 44.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .chartSemantics(contentDescription),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(
                    if (onPlayheadChange == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(domain) {
                            detectTapGestures { offset ->
                                onPlayheadChange(xAt(offset.x, leftGutter, size.width.toFloat(), domain))
                            }
                        }.pointerInput(domain) {
                            detectDragGestures { change, _ ->
                                onPlayheadChange(
                                    xAt(change.position.x, leftGutter, size.width.toFloat(), domain)
                                )
                            }
                        }
                    }
                ),
        ) {
            val plotLeft = leftGutter
            val plotWidth = (size.width - plotLeft).coerceAtLeast(1f)
            val plotHeight = size.height

            drawBands(bands, domain, plotLeft, plotWidth, plotHeight)
            drawGrid(gridLines, plotLeft, plotWidth, plotHeight)
            drawAxisLabels(textMeasurer, yAxisLabels, labelStyle, plotHeight)

            series.forEach { s ->
                drawSeries(s, domain, plotLeft, plotWidth, plotHeight)
            }

            if (playheadX != null) {
                val x = plotLeft + ((playheadX - domain.minX) / domain.spanX).toFloat() * plotWidth
                if (x.isFinite()) {
                    drawLine(
                        color = RcPalette.TextPrimary.copy(alpha = 0.55f),
                        start = Offset(x, 0f),
                        end = Offset(x, plotHeight),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    // Dot each series where the playhead crosses it.
                    series.forEach { s ->
                        val y = s.valueAt(playheadX)
                        if (y != null) {
                            val py = plotHeight -
                                ((y - domain.minY) / domain.spanY).toFloat() * plotHeight
                            if (py.isFinite()) {
                                drawCircle(s.color, radius = 4.dp.toPx(), center = Offset(x, py))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun xAt(px: Float, gutter: Float, width: Float, domain: ChartDomain): Double {
    val plotWidth = (width - gutter).coerceAtLeast(1f)
    val fraction = ((px - gutter) / plotWidth).coerceIn(0f, 1f)
    return domain.minX + fraction * domain.spanX
}

/** Linear interpolation of a series at an arbitrary x. */
fun ChartSeries.valueAt(x: Double): Double? {
    if (points.isEmpty()) return null
    if (x <= points.first().x) return points.first().y
    if (x >= points.last().x) return points.last().y
    val index = points.binarySearch { it.x.compareTo(x) }
    if (index >= 0) return points[index].y
    val insertion = -index - 1
    val before = points[(insertion - 1).coerceAtLeast(0)]
    val after = points[insertion.coerceAtMost(points.lastIndex)]
    val span = after.x - before.x
    if (span <= 0) return before.y
    val t = (x - before.x) / span
    return before.y + t * (after.y - before.y)
}

private fun DrawScope.drawBands(
    bands: List<ChartBand>,
    domain: ChartDomain,
    left: Float,
    width: Float,
    height: Float,
) {
    bands.forEach { band ->
        val fromX = ((band.minX - domain.minX) / domain.spanX).toFloat().coerceIn(0f, 1f)
        val toX = ((band.maxX - domain.minX) / domain.spanX).toFloat().coerceIn(0f, 1f)
        if (toX <= fromX) return@forEach
        drawRect(
            color = band.color,
            topLeft = Offset(left + fromX * width, 0f),
            size = androidx.compose.ui.geometry.Size((toX - fromX) * width, height),
        )
    }
}

private fun DrawScope.drawGrid(lines: Int, left: Float, width: Float, height: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
    for (i in 0..lines) {
        val y = height * (i.toFloat() / lines)
        drawLine(
            color = RcPalette.Stroke,
            start = Offset(left, y),
            end = Offset(left + width, y),
            strokeWidth = 1f,
            pathEffect = dash,
        )
    }
}

private fun DrawScope.drawAxisLabels(
    measurer: TextMeasurer,
    labels: List<String>,
    style: TextStyle,
    height: Float,
) {
    if (labels.isEmpty()) return
    labels.forEachIndexed { index, label ->
        val fraction = index.toFloat() / (labels.size - 1).coerceAtLeast(1)
        // Labels run bottom-to-top; the first entry is the minimum.
        val y = height - fraction * height
        val result = measurer.measure(label, style)
        val clampedY = (y - result.size.height / 2f).coerceIn(0f, height - result.size.height)
        drawText(result, topLeft = Offset(0f, clampedY))
    }
}

private fun DrawScope.drawSeries(
    s: ChartSeries,
    domain: ChartDomain,
    left: Float,
    width: Float,
    height: Float,
) {
    if (s.points.isEmpty()) return
    val mapped = s.points.mapNotNull { p ->
        val x = left + ((p.x - domain.minX) / domain.spanX).toFloat() * width
        val y = height - ((p.y - domain.minY) / domain.spanY).toFloat() * height
        if (x.isFinite() && y.isFinite()) Offset(x, y) else null
    }
    if (s.showLine && mapped.size >= 2) {
        val path = Path().apply {
            moveTo(mapped.first().x, mapped.first().y)
            mapped.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = path,
            color = s.color,
            style = Stroke(width = s.strokeWidth.toPx(), cap = StrokeCap.Round),
        )
    }
    if (s.showPoints) {
        mapped.forEach { point ->
            drawCircle(color = s.color, radius = s.pointRadius.toPx(), center = point)
        }
    }
}

/**
 * Compact form line used on the driver detail screen: finishing position over
 * the season, drawn inverted so "up" means "better".
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = RcPalette.RacingRed,
    height: Dp = 48.dp,
    invert: Boolean = true,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .chartSemantics(contentDescription),
    ) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)

        val path = Path()
        values.forEachIndexed { index, raw ->
            val normalised = (raw - min) / span
            val fraction = if (invert) normalised else 1f - normalised
            val x = index * stepX
            val y = fraction * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        values.forEachIndexed { index, raw ->
            val normalised = (raw - min) / span
            val fraction = if (invert) normalised else 1f - normalised
            drawCircle(
                color = color,
                radius = 2.5.dp.toPx(),
                center = Offset(index * stepX, fraction * size.height),
            )
        }
    }
}

/** One coloured run inside a [StackedBar]: a tyre stint, or a DNF cause. */
data class BarSegment(
    val value: Float,
    val color: Color,
    val label: String? = null,
)

data class WaterfallSegment(val value: Float, val color: Color)

/** Signed horizontal waterfall centred on zero. */
@Composable
fun RcWaterfallBars(
    segments: List<WaterfallSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    contentDescription: String? = null,
) {
    Canvas(modifier.fillMaxWidth().height(height).chartSemantics(contentDescription)) {
        val extent = segments.sumOf { kotlin.math.abs(it.value.toDouble()) }.toFloat()
            .coerceAtLeast(0.001f)
        val scale = size.width / (extent * 2f)
        val centre = size.width / 2f
        drawLine(RcPalette.TextTertiary, Offset(centre, 0f), Offset(centre, size.height), 1f)
        var positive = centre
        var negative = centre
        segments.forEach { segment ->
            val width = kotlin.math.abs(segment.value) * scale
            val left = if (segment.value >= 0) positive else negative - width
            drawRect(segment.color, Offset(left, 0f), androidx.compose.ui.geometry.Size(width, size.height))
            if (segment.value >= 0) positive += width else negative -= width
        }
    }
}

/**
 * Horizontal stacked bar, used for tyre stint timelines and reliability
 * breakdowns. Segments are proportional to [BarSegment.value].
 */
@Composable
fun StackedBar(
    segments: List<BarSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    cornerRadius: Dp = 4.dp,
    gap: Dp = 1.dp,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .chartSemantics(contentDescription),
    ) {
        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return@Canvas
        val gapPx = gap.toPx()
        var x = 0f
        segments.forEach { segment ->
            val w = (segment.value / total) * size.width
            if (w > 0f) {
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(
                        width = (w - gapPx).coerceAtLeast(1f),
                        height = size.height,
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
                )
            }
            x += w
        }
    }
}
