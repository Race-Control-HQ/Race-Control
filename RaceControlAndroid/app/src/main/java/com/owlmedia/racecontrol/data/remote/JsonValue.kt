package com.owlmedia.racecontrol.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A permissive JSON scalar.
 *
 * The backend mixes sources: FastF1 emits numbers where Ergast/Jolpica emits
 * the same field as a string (`position`, `points`, `wins`, driver numbers...).
 * The iOS app absorbs this with a `JSONValue` enum; this is the direct
 * equivalent, so the UI can never crash on a type mismatch.
 */
@Serializable(with = JsonValueSerializer::class)
sealed interface JsonValue {

    @Serializable data class Str(val value: String) : JsonValue
    @Serializable data class Num(val value: Double, val isInteger: Boolean) : JsonValue
    @Serializable data class Bool(val value: Boolean) : JsonValue
    @Serializable data object Null : JsonValue

    val stringValue: String?
        get() = when (this) {
            is Str -> value
            is Num -> if (isInteger) value.toLong().toString() else value.toString()
            is Bool -> value.toString()
            Null -> null
        }

    val intValue: Int?
        get() = when (this) {
            is Num -> value.toInt()
            is Str -> value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
            else -> null
        }

    val doubleValue: Double?
        get() = when (this) {
            is Num -> value
            is Str -> value.toDoubleOrNull()
            else -> null
        }

    /** Formats numbers without a trailing ".0", matching the iOS `numberLabel`. */
    val numberLabel: String?
        get() {
            val d = doubleValue ?: return stringValue
            return if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
        }
}

object JsonValueSerializer : KSerializer<JsonValue> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JsonValue", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): JsonValue {
        val input = decoder as? JsonDecoder
            ?: return JsonValue.Str(decoder.decodeString())
        val element = input.decodeJsonElement()
        if (element is JsonNull) return JsonValue.Null
        val primitive = runCatching { element.jsonPrimitive }.getOrNull()
            ?: return JsonValue.Null

        // Order matters and mirrors the iOS decoder: bool, int, double, string.
        // `isString` guards against "1" being silently read as a number, which
        // would lose the distinction the backend actually makes.
        if (!primitive.isString) {
            primitive.booleanOrNull?.let { return JsonValue.Bool(it) }
            primitive.intOrNull?.let { return JsonValue.Num(it.toDouble(), isInteger = true) }
            primitive.doubleOrNull?.let { return JsonValue.Num(it, isInteger = false) }
        }
        return JsonValue.Str(primitive.content)
    }

    override fun serialize(encoder: Encoder, value: JsonValue) {
        val output = encoder as? JsonEncoder
        if (output == null) {
            encoder.encodeString(value.stringValue.orEmpty())
            return
        }
        val element = when (value) {
            is JsonValue.Str -> JsonPrimitive(value.value)
            is JsonValue.Num ->
                if (value.isInteger) JsonPrimitive(value.value.toLong()) else JsonPrimitive(value.value)
            is JsonValue.Bool -> JsonPrimitive(value.value)
            JsonValue.Null -> JsonNull
        }
        output.encodeJsonElement(element)
    }
}
