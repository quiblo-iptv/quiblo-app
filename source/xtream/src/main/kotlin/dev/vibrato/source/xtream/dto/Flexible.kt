/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.vibrato.source.xtream.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Serializers that accept whatever a panel actually sends.
 *
 * AC-XT-06 exists because Xtream panels are wildly inconsistent: `stream_id` arrives as
 * `123` from one panel and `"123"` from another, `exp_date` can be a number, a numeric
 * string, `null`, or the empty string, and booleans show up as `true`, `"true"`, `1` and
 * `"1"`. The correct posture is never to trust a field's declared type
 * (docs/PLAN.md §5), so every scalar goes through one of these.
 */

/** Reads the raw scalar text of the next element, or null for anything non-scalar. */
private fun Decoder.scalarOrNull(): String? {
    val jsonDecoder = this as? JsonDecoder ?: return null
    return when (val element = jsonDecoder.decodeJsonElement()) {
        is JsonNull -> null
        is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        // An object or array where a scalar was expected. Treated as absent rather than
        // as a parse failure: one odd field must not lose the whole response.
        else -> null
    }
}

internal object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? = decoder.scalarOrNull()

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value.orEmpty())
    }
}

internal object FlexibleIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.STRING)

    // Some panels send "1.0" where an integer is expected.
    override fun deserialize(decoder: Decoder): Int? =
        decoder.scalarOrNull()?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

    override fun serialize(encoder: Encoder, value: Int?) {
        encoder.encodeString(value?.toString().orEmpty())
    }
}

internal object FlexibleLongSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Long? =
        decoder.scalarOrNull()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

    override fun serialize(encoder: Encoder, value: Long?) {
        encoder.encodeString(value?.toString().orEmpty())
    }
}

internal object FlexibleBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Boolean? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> element.booleanOrNull ?: when (element.contentOrNull?.trim()?.lowercase()) {
                "1", "true", "yes", "active" -> true
                "0", "false", "no" -> false
                else -> null
            }

            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        encoder.encodeString(value?.toString().orEmpty())
    }
}
