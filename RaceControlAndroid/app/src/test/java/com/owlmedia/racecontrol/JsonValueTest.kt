package com.owlmedia.racecontrol

import com.owlmedia.racecontrol.data.remote.JsonValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The backend mixes FastF1 (numbers) and Ergast/Jolpica (strings) for the same
 * fields. These are the shapes that actually appear across 2018-present.
 */
class JsonValueTest {

    @Serializable
    private data class Holder(val position: JsonValue? = null)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes an integer`() {
        val holder = json.decodeFromString<Holder>("""{"position": 3}""")
        assertEquals(3, holder.position?.intValue)
        assertEquals("3", holder.position?.stringValue)
        assertEquals("3", holder.position?.numberLabel)
    }

    @Test
    fun `decodes a numeric string`() {
        val holder = json.decodeFromString<Holder>("""{"position": "3"}""")
        assertEquals(3, holder.position?.intValue)
        assertEquals("3", holder.position?.stringValue)
    }

    @Test
    fun `decodes a double and drops the trailing zero`() {
        val holder = json.decodeFromString<Holder>("""{"position": 25.0}""")
        assertEquals(25, holder.position?.intValue)
        assertEquals("25", holder.position?.numberLabel)
    }

    @Test
    fun `keeps a genuine fraction`() {
        val holder = json.decodeFromString<Holder>("""{"position": 12.5}""")
        assertEquals("12.5", holder.position?.numberLabel)
    }

    @Test
    fun `decodes null`() {
        // position is JsonValue? (nullable), so kotlinx.serialization intercepts a
        // JSON `null` literal structurally before JsonValueSerializer.deserialize()
        // ever runs - the result is Kotlin `null`, not the JsonValue.Null sentinel.
        // JsonValue.Null is still reachable for a genuinely present-but-unparsable
        // element (e.g. the backend sending `{}` for this field), just not this case.
        val holder = json.decodeFromString<Holder>("""{"position": null}""")
        assertNull(holder.position)
        assertNull(holder.position?.intValue)
    }

    @Test
    fun `a non-numeric string does not become a number`() {
        val holder = json.decodeFromString<Holder>("""{"position": "R"}""")
        assertEquals("R", holder.position?.stringValue)
        assertNull(holder.position?.intValue)
    }

    @Test
    fun `a missing key is null rather than a failure`() {
        val holder = json.decodeFromString<Holder>("""{}""")
        assertNull(holder.position)
    }
}
