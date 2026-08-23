package com.owlmedia.racecontrol

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.owlmedia.racecontrol.core.design.RcPalette
import com.owlmedia.racecontrol.core.design.legibleOnSurface
import com.owlmedia.racecontrol.core.design.teamColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTest {

    @Test
    fun `parses a hash-prefixed hex`() {
        assertEquals(Color(0xFFE10600), teamColor("#E10600"))
    }

    @Test
    fun `parses a bare hex`() {
        assertEquals(Color(0xFFE10600), teamColor("E10600"))
    }

    @Test
    fun `falls back to brand red for null, blank and malformed input`() {
        assertEquals(RcPalette.RacingRed, teamColor(null))
        assertEquals(RcPalette.RacingRed, teamColor(""))
        assertEquals(RcPalette.RacingRed, teamColor("nonsense"))
    }

    @Test
    fun `lifts a near-black livery until it is readable on the dark surface`() {
        // Some team colours are almost black and vanish against #16161A.
        val nearBlack = Color(0xFF080808)
        val lifted = nearBlack.legibleOnSurface()
        assertTrue(
            "expected the colour to be lifted, got luminance ${lifted.luminance()}",
            lifted.luminance() > nearBlack.luminance(),
        )
    }

    @Test
    fun `leaves an already-bright livery alone`() {
        val bright = Color(0xFF00D2BE)
        assertEquals(bright, bright.legibleOnSurface())
    }
}
