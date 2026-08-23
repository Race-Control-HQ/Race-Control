package com.owlmedia.racecontrol.feature.circuits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
import com.owlmedia.racecontrol.data.remote.dto.CircuitMapDto
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Circuit outline drawn on a Compose Canvas.
 *
 * The backend supplies raw positional telemetry in an arbitrary coordinate
 * space plus a rotation, so the map is rotated to the orientation the track is
 * usually shown in, then normalised into the available box.
 *
 * **That order matters.** Fitting first and rotating afterwards (the obvious
 * way to write this) makes the track spill outside its frame, because rotating
 * a shape that already fills the box pushes its extremities past the edges. So
 * the rotation is applied to the coordinates up front and the bounds are
 * measured from the *rotated* points.
 *
 * When speed telemetry is available the trace is coloured by speed and DRS
 * zones are highlighted, matching the iOS `TrackMapRich` view. Pinch to zoom
 * and drag to pan.
 */
@Composable
fun TrackMap(
    map: CircuitMapDto,
    modifier: Modifier = Modifier,
    height: Dp = 280.dp,
    showCorners: Boolean = true,
    colorBySpeed: Boolean = true,
    contentDescriptionText: String? = null,
) {
    if (map.outline.isEmpty() && map.points.orEmpty().isEmpty()) return

    val geometry = remember(map) { buildGeometry(map) }
    if (geometry == null) return

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cornerLabelStyle = remember(density) {
        TextStyle(fontSize = 9.sp, color = RcPalette.TextSecondary)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Zoom and pan must not draw over the card that contains this.
            .clipToBounds()
            .semantics {
                contentDescriptionText?.let { contentDescription = it }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val padding = 20f
            val availableWidth = (size.width - padding * 2).coerceAtLeast(1f)
            val availableHeight = (size.height - padding * 2).coerceAtLeast(1f)

            val bounds = geometry.bounds
            val spanX = (bounds.maxX - bounds.minX).takeIf { it > 0 } ?: 1.0
            val spanY = (bounds.maxY - bounds.minY).takeIf { it > 0 } ?: 1.0

            // Uniform scale keeps the circuit's real proportions.
            val fit = min(availableWidth / spanX, availableHeight / spanY).toFloat()

            // Centre whatever slack the aspect-ratio difference leaves over,
            // so the track sits in the middle of the frame rather than hugging
            // one edge.
            val drawnWidth = (spanX * fit).toFloat()
            val drawnHeight = (spanY * fit).toFloat()
            val originX = padding + (availableWidth - drawnWidth) / 2f
            val originY = padding + (availableHeight - drawnHeight) / 2f

            fun project(x: Double, y: Double): Offset = Offset(
                originX + ((x - bounds.minX) * fit).toFloat(),
                // Track Y grows "up"; canvas Y grows down.
                originY + ((bounds.maxY - y) * fit).toFloat(),
            )

            val centre = Offset(size.width / 2f, size.height / 2f)

            translate(offset.x, offset.y) {
                scale(scale, pivot = centre) {
                    if (geometry.trace.isNotEmpty() && colorBySpeed) {
                        drawSpeedTrace(geometry.trace, geometry.speedRange, ::project)
                    } else {
                        drawOutline(geometry.outline, ::project)
                    }

                    if (showCorners) {
                        drawCorners(
                            corners = geometry.corners,
                            project = ::project,
                            measurer = textMeasurer,
                            style = cornerLabelStyle,
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ geometry */

private data class Pt(val x: Double, val y: Double)

private data class TracePoint(
    val x: Double,
    val y: Double,
    val speed: Double,
    val drsOpen: Boolean,
)

private data class CornerPoint(val x: Double, val y: Double, val label: String)

private data class Bounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
)

private data class TrackGeometry(
    val outline: List<Pt>,
    val trace: List<TracePoint>,
    val corners: List<CornerPoint>,
    val bounds: Bounds,
    val speedRange: Pair<Double, Double>,
)

/**
 * Applies the backend's rotation to every coordinate and measures the result.
 *
 * The angle is negated because the rotation is expressed for a Y-up coordinate
 * space while the canvas draws Y-down; without the flip the track would come
 * out mirrored about its rotation.
 */
private fun buildGeometry(map: CircuitMapDto): TrackGeometry? {
    val rawOutline = map.outline
    val rawTrace = map.points.orEmpty()
    if (rawOutline.isEmpty() && rawTrace.isEmpty()) return null

    // Rotate about the centre of the source data so the shape stays put.
    val sourceXs = if (rawTrace.isNotEmpty()) rawTrace.map { it.x } else rawOutline.map { it.x }
    val sourceYs = if (rawTrace.isNotEmpty()) rawTrace.map { it.y } else rawOutline.map { it.y }
    val pivotX = (sourceXs.min() + sourceXs.max()) / 2.0
    val pivotY = (sourceYs.min() + sourceYs.max()) / 2.0

    val radians = Math.toRadians(-map.rotation)
    val cosA = cos(radians)
    val sinA = sin(radians)

    fun rotate(x: Double, y: Double): Pt {
        val dx = x - pivotX
        val dy = y - pivotY
        return Pt(
            pivotX + dx * cosA - dy * sinA,
            pivotY + dx * sinA + dy * cosA,
        )
    }

    val outline = rawOutline.map { rotate(it.x, it.y) }
    val trace = rawTrace.map {
        val p = rotate(it.x, it.y)
        TracePoint(p.x, p.y, it.speed, it.drsOpen)
    }
    // Corner markers sit just off the racing line, so they are included in the
    // bounds - otherwise a marker near the edge would be clipped.
    val corners = map.corners.map {
        val p = rotate(it.x, it.y)
        CornerPoint(p.x, p.y, it.label)
    }

    val xs = buildList {
        outline.forEach { add(it.x) }
        trace.forEach { add(it.x) }
        corners.forEach { add(it.x) }
    }
    val ys = buildList {
        outline.forEach { add(it.y) }
        trace.forEach { add(it.y) }
        corners.forEach { add(it.y) }
    }
    if (xs.isEmpty() || ys.isEmpty()) return null

    val speedRange = if (trace.isEmpty()) {
        0.0 to 1.0
    } else {
        trace.minOf { it.speed } to trace.maxOf { it.speed }
    }

    return TrackGeometry(
        outline = outline,
        trace = trace,
        corners = corners,
        bounds = Bounds(xs.min(), xs.max(), ys.min(), ys.max()),
        speedRange = speedRange,
    )
}

/* ------------------------------------------------------------------ drawing */

private fun DrawScope.drawOutline(
    outline: List<Pt>,
    project: (Double, Double) -> Offset,
) {
    if (outline.size < 2) return
    val path = Path()
    outline.forEachIndexed { index, point ->
        val p = project(point.x, point.y)
        if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(
        path = path,
        color = RcPalette.TextPrimary,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * Speed-coloured trace: slow corners in red through to fast straights in green,
 * with DRS zones picked out in the F1 blue.
 *
 * Drawn as segments rather than one path because each needs its own colour.
 */
private fun DrawScope.drawSpeedTrace(
    points: List<TracePoint>,
    speedRange: Pair<Double, Double>,
    project: (Double, Double) -> Offset,
) {
    val (minSpeed, maxSpeed) = speedRange
    val span = (maxSpeed - minSpeed).takeIf { it > 0 } ?: 1.0
    val stroke = 3.dp.toPx()

    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        val t = ((a.speed - minSpeed) / span).toFloat().coerceIn(0f, 1f)
        drawLine(
            color = if (a.drsOpen) RcPalette.Info else speedColor(t),
            start = project(a.x, a.y),
            end = project(b.x, b.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Red (slow) -> amber -> green (fast). */
private fun speedColor(t: Float): Color = if (t < 0.5f) {
    lerpColor(RcPalette.Negative, RcPalette.Warning, t * 2f)
} else {
    lerpColor(RcPalette.Warning, RcPalette.Positive, (t - 0.5f) * 2f)
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * clamped,
        green = a.green + (b.green - a.green) * clamped,
        blue = a.blue + (b.blue - a.blue) * clamped,
        alpha = 1f,
    )
}

private fun DrawScope.drawCorners(
    corners: List<CornerPoint>,
    project: (Double, Double) -> Offset,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    corners.forEach { corner ->
        val p = project(corner.x, corner.y)
        drawCircle(color = RcPalette.SurfaceElevated, radius = 8.dp.toPx(), center = p)
        val measured = measurer.measure(corner.label, style)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                p.x - measured.size.width / 2f,
                p.y - measured.size.height / 2f,
            ),
        )
    }
}

/** Kept for callers that want the raw span without redrawing. */
internal fun CircuitMapDto.lengthKilometres(): Double? =
    lengthMeters?.let { max(it, 0.0) / 1000.0 }
