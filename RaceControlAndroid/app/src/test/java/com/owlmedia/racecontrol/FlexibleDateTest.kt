package com.owlmedia.racecontrol

import com.owlmedia.racecontrol.core.util.FlexibleDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/**
 * FastF1 emits several date shapes depending on the upstream. Every one of
 * these appears somewhere in 2018-present, and a parse failure means a session
 * silently disappears from the schedule and gets no reminder.
 */
class FlexibleDateTest {

    @Test
    fun `parses an offset datetime`() {
        val parsed = FlexibleDate.parse("2026-07-05T14:00:00+02:00")
        assertNotNull(parsed)
        assertEquals(14, parsed!!.hour)
    }

    @Test
    fun `parses fractional seconds`() {
        assertNotNull(FlexibleDate.parse("2026-07-05T14:00:00.000+02:00"))
    }

    @Test
    fun `parses a Zulu instant`() {
        assertNotNull(FlexibleDate.parse("2026-07-05T12:00:00Z"))
    }

    @Test
    fun `treats a naive datetime as UTC, matching iOS`() {
        val parsed = FlexibleDate.parse("2026-07-05T14:00:00")
        assertNotNull(parsed)
        assertEquals(ZoneId.of("UTC"), parsed!!.zone)
        assertEquals(14, parsed.hour)
    }

    @Test
    fun `parses a bare date`() {
        val parsed = FlexibleDate.parse("2026-07-05")
        assertNotNull(parsed)
        assertEquals(0, parsed!!.hour)
    }

    @Test
    fun `null and blank return null rather than throwing`() {
        assertNull(FlexibleDate.parse(null))
        assertNull(FlexibleDate.parse(""))
        assertNull(FlexibleDate.parse("   "))
    }

    @Test
    fun `garbage returns null rather than throwing`() {
        assertNull(FlexibleDate.parse("not a date"))
    }
}
