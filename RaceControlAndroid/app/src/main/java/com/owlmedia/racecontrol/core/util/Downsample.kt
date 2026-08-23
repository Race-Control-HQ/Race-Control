package com.owlmedia.racecontrol.core.util

import androidx.compose.foundation.layout.size

/**
 * Largest-Triangle-Three-Buckets downsampling.
 *
 * A fastest-lap telemetry trace is several thousand samples per channel, and
 * handing all of them to a chart is the single easiest way to make this app
 * drop frames on a mid-range phone. LTTB keeps the visually significant points
 * (peaks, braking edges) rather than naively taking every Nth sample, which
 * would smooth away exactly the detail the chart exists to show.
 */
object Downsample {

    fun lttb(xs: List<Double>, ys: List<Double>, threshold: Int): Pair<List<Double>, List<Double>> {
        val n = minOf(xs.size, ys.size)
        if (threshold >= n || threshold < 3 || n < 3) {
            return xs.take(n) to ys.take(n)
        }

        val outX = ArrayList<Double>(threshold)
        val outY = ArrayList<Double>(threshold)

        // Buckets between the mandatory first and last points.
        val every = (n - 2).toDouble() / (threshold - 2)

        var a = 0
        outX.add(xs[0]); outY.add(ys[0])

        for (i in 0 until threshold - 2) {
            // Average of the *next* bucket, used as the triangle's third vertex.
            val avgRangeStart = ((i + 1) * every).toInt() + 1
            var avgRangeEnd = ((i + 2) * every).toInt() + 1
            if (avgRangeEnd > n) avgRangeEnd = n

            var avgX = 0.0
            var avgY = 0.0
            val avgCount = (avgRangeEnd - avgRangeStart).coerceAtLeast(1)
            for (j in avgRangeStart until avgRangeEnd) {
                avgX += xs[j]
                avgY += ys[j]
            }
            avgX /= avgCount
            avgY /= avgCount

            val rangeOffs = (i * every).toInt() + 1
            var rangeTo = ((i + 1) * every).toInt() + 1
            if (rangeTo > n) rangeTo = n

            val pointAX = xs[a]
            val pointAY = ys[a]

            var maxArea = -1.0
            var maxAreaIndex = rangeOffs
            for (j in rangeOffs until rangeTo) {
                val area = kotlin.math.abs(
                    (pointAX - avgX) * (ys[j] - pointAY) - (pointAX - xs[j]) * (avgY - pointAY)
                ) * 0.5
                if (area > maxArea) {
                    maxArea = area
                    maxAreaIndex = j
                }
            }

            outX.add(xs[maxAreaIndex])
            outY.add(ys[maxAreaIndex])
            a = maxAreaIndex
        }

        outX.add(xs[n - 1]); outY.add(ys[n - 1])
        return outX to outY
    }

    /** Convenience for the common "cap a telemetry channel" case. */
    fun cap(xs: List<Double>, ys: List<Double>, max: Int = 800): Pair<List<Double>, List<Double>> =
        lttb(xs, ys, max)
}
