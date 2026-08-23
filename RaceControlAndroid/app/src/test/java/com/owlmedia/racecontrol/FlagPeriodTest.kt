package com.owlmedia.racecontrol

import com.owlmedia.racecontrol.data.remote.dto.FlagPeriodDto
import com.owlmedia.racecontrol.data.remote.dto.FlagPeriodType
import com.owlmedia.racecontrol.data.remote.dto.periodContaining
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lap-range containment drives both the lap-times chart bands and the
 * telemetry "Safety Car (lap 20)" badge, so it must agree with the iOS
 * `FlagPeriod.contains(lap:)` behaviour: inclusive at both ends, first match
 * wins if periods somehow overlap.
 */
class FlagPeriodTest {

    private val doubleYellow = FlagPeriodDto(
        type = "DOUBLE_YELLOW",
        startLap = 17,
        endLap = 19,
        reason = "DOUBLE YELLOW FLAG IN TRACK SECTOR 5",
    )
    private val safetyCar = FlagPeriodDto(
        type = "SC",
        startLap = 20,
        endLap = 23,
        reason = "SAFETY CAR DEPLOYED",
    )
    private val periods = listOf(doubleYellow, safetyCar)

    @Test
    fun `a lap at the start of a period is contained`() {
        assertEquals(true, doubleYellow.contains(17))
    }

    @Test
    fun `a lap at the end of a period is contained`() {
        assertEquals(true, doubleYellow.contains(19))
    }

    @Test
    fun `a lap outside the range is not contained`() {
        assertEquals(false, doubleYellow.contains(16))
        assertEquals(false, doubleYellow.contains(20))
    }

    @Test
    fun `periodContaining finds the matching period in a list`() {
        assertEquals(safetyCar, periods.periodContaining(21))
        assertEquals(doubleYellow, periods.periodContaining(17))
    }

    @Test
    fun `periodContaining returns null for a green-flag lap`() {
        assertNull(periods.periodContaining(5))
    }

    @Test
    fun `periodContaining returns null for a null lap`() {
        assertNull(periods.periodContaining(null))
    }

    @Test
    fun `unknown backend type strings map to UNKNOWN rather than crashing`() {
        assertEquals(FlagPeriodType.UNKNOWN, FlagPeriodDto(type = "RAIN").periodType)
    }

    @Test
    fun `known type strings parse regardless of case`() {
        assertEquals(FlagPeriodType.SAFETY_CAR, FlagPeriodDto(type = "sc").periodType)
        assertEquals(FlagPeriodType.VIRTUAL_SAFETY_CAR, FlagPeriodDto(type = "VSC").periodType)
    }
}
