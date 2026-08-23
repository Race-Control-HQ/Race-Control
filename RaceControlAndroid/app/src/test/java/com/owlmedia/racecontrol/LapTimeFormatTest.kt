package com.owlmedia.racecontrol

import com.owlmedia.racecontrol.core.util.LapTimeFormat
import com.owlmedia.racecontrol.core.util.pointsLabel
import com.owlmedia.racecontrol.data.remote.dto.ResultEntryDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Formatting must match the iOS build exactly; the two apps show the same race
 * and any difference would be read as one of them being wrong.
 */
class LapTimeFormatTest {

    @Test
    fun `leading format includes minutes`() {
        // 1:32.145
        assertEquals("1:32.145", LapTimeFormat.format(92_145, leading = true))
    }

    @Test
    fun `gap format folds minutes into seconds`() {
        // A lap down can be over a minute; F1 timing shows 92.145, not 1:32.145.
        assertEquals("92.145", LapTimeFormat.format(92_145, leading = false))
    }

    @Test
    fun `sub-minute leading time omits the minute field`() {
        assertEquals("32.145", LapTimeFormat.format(32_145, leading = true))
    }

    @Test
    fun `winner shows an absolute time`() {
        val winner = ResultEntryDto(position = 1.0, timeMs = 5_401_234, status = "Finished")
        assertEquals("90:01.234", winner.raceTimeLabel(winnerTimeMs = 5_401_234))
    }

    @Test
    fun `others show a gap to the winner`() {
        val second = ResultEntryDto(position = 2.0, timeMs = 5_403_456, status = "Finished")
        assertEquals("+2.222", second.raceTimeLabel(winnerTimeMs = 5_401_234))
    }

    @Test
    fun `a non-finisher shows its status`() {
        val dnf = ResultEntryDto(position = 18.0, timeMs = null, status = "Accident")
        assertEquals("Accident", dnf.raceTimeLabel(winnerTimeMs = 5_401_234))
    }

    @Test
    fun `a lapped finisher keeps the plus-lap status`() {
        val lapped = ResultEntryDto(position = 15.0, timeMs = null, status = "+1 Lap")
        assertEquals("+1 Lap", lapped.raceTimeLabel(winnerTimeMs = 5_401_234))
    }

    @Test
    fun `grid delta is positive when places are gained`() {
        val gained = ResultEntryDto(position = 3.0, gridPosition = 10.0)
        assertEquals(7, gained.gridDelta)
    }

    @Test
    fun `grid delta is negative when places are lost`() {
        val lost = ResultEntryDto(position = 12.0, gridPosition = 4.0)
        assertEquals(-8, lost.gridDelta)
    }

    @Test
    fun `a pit-lane start has no meaningful delta`() {
        val pitLane = ResultEntryDto(position = 12.0, gridPosition = 0.0)
        assertEquals(null, pitLane.gridDelta)
    }

    @Test
    fun `a non-numeric classified position passes through`() {
        val retired = ResultEntryDto(position = 20.0, classifiedPosition = "R")
        assertEquals("R", retired.positionLabel)
    }

    @Test
    fun `zero points render as an empty label`() {
        assertEquals("", ResultEntryDto(points = 0.0).pointsLabel)
        assertEquals("25", ResultEntryDto(points = 25.0).pointsLabel)
    }
}
