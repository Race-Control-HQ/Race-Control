package com.owlmedia.racecontrol

import androidx.compose.foundation.layout.only
import com.owlmedia.racecontrol.feature.analysis.parseLapTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Qualifying times arrive pre-formatted, so a gap has to be parsed back out. */
class QualifyingParseTest {

    @Test
    fun `parses minutes and seconds`() {
        assertEquals(92_145L, parseLapTime("1:32.145"))
    }

    @Test
    fun `parses seconds only`() {
        assertEquals(32_145L, parseLapTime("32.145"))
    }

    @Test
    fun `parses an hour form`() {
        assertEquals(3_692_145L, parseLapTime("1:01:32.145"))
    }

    @Test
    fun `null, blank and garbage return null`() {
        assertNull(parseLapTime(null))
        assertNull(parseLapTime(""))
        assertNull(parseLapTime("no time"))
    }
}
