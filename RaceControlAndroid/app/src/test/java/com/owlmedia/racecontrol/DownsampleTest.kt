package com.owlmedia.racecontrol

import androidx.compose.foundation.layout.size
import com.owlmedia.racecontrol.core.util.Downsample
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownsampleTest {

    @Test
    fun `short series is returned untouched`() {
        val xs = listOf(0.0, 1.0, 2.0)
        val ys = listOf(5.0, 6.0, 7.0)
        val (outX, outY) = Downsample.lttb(xs, ys, threshold = 100)
        assertEquals(xs, outX)
        assertEquals(ys, outY)
    }

    @Test
    fun `downsamples to the requested size`() {
        val xs = (0 until 5000).map { it.toDouble() }
        val ys = xs.map { sin(it / 50.0) * 100 }
        val (outX, outY) = Downsample.lttb(xs, ys, threshold = 800)
        assertEquals(800, outX.size)
        assertEquals(800, outY.size)
    }

    @Test
    fun `endpoints are always preserved`() {
        val xs = (0 until 1000).map { it.toDouble() }
        val ys = xs.map { it * 2 }
        val (outX, outY) = Downsample.lttb(xs, ys, threshold = 50)
        assertEquals(xs.first(), outX.first(), 0.0001)
        assertEquals(xs.last(), outX.last(), 0.0001)
        assertEquals(ys.first(), outY.first(), 0.0001)
        assertEquals(ys.last(), outY.last(), 0.0001)
    }

    @Test
    fun `keeps the peak of a spike rather than averaging it away`() {
        // A braking event is a narrow spike; naive every-Nth sampling loses it.
        val xs = (0 until 1000).map { it.toDouble() }
        val ys = xs.map { if (it.toInt() == 500) 300.0 else 100.0 }
        val (_, outY) = Downsample.lttb(xs, ys, threshold = 100)
        assertTrue("the 300 peak should survive downsampling", outY.contains(300.0))
    }

    @Test
    fun `x values stay monotonically increasing`() {
        val xs = (0 until 2000).map { it.toDouble() }
        val ys = xs.map { sin(it / 30.0) }
        val (outX, _) = Downsample.lttb(xs, ys, threshold = 200)
        outX.zipWithNext { a, b ->
            assertTrue("x must not go backwards: $a then $b", b > a)
        }
    }
}
