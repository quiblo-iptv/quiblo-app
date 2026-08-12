/*
 * Quiblo — a free, open source IPTV player.
 * Copyright (C) 2026 The Quiblo Authors
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

package dev.quiblo.source.xtream.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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

/**
 * The subtitle list a panel may attach to a film, in whatever shape it sends it.
 *
 * There is no agreement between panels about this field. Some omit it, some send an empty array,
 * some send an array of URL strings, some an array of objects — and the object's key for the URL
 * is `url` on one panel and `link`, `file` or `src` on the next. All of it is read, and anything
 * unrecognisable is dropped rather than failing the response: a film with an odd subtitle entry
 * is still a film, and losing its plot and its cover over one is not a trade worth making.
 */
internal object FlexibleSubtitleListSerializer : KSerializer<List<XtreamSubtitle>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleSubtitles", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<XtreamSubtitle> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyList()
        val array = jsonDecoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { it.toSubtitle() }
    }

    override fun serialize(encoder: Encoder, value: List<XtreamSubtitle>) {
        encoder.encodeString("")
    }

    private fun JsonElement.toSubtitle(): XtreamSubtitle? = when (this) {
        // A bare entry is a URL, so it has to be a string and it has to look like one. A number
        // in the array is a panel sending an id where a location belongs, and an id loads nothing.
        is JsonPrimitive ->
            contentOrNull
                ?.trim()
                ?.takeIf { isString && (it.contains('/') || it.contains('.')) }
                ?.let { XtreamSubtitle(url = it) }
        is JsonObject -> pick(URL_KEYS)?.let {
            XtreamSubtitle(url = it, language = pick(LANGUAGE_KEYS), label = pick(LABEL_KEYS))
        }

        else -> null
    }

    private fun JsonObject.pick(keys: List<String>): String? = keys
        .firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private val URL_KEYS = listOf("url", "link", "file", "src", "path")
    private val LANGUAGE_KEYS = listOf("language", "lang", "iso639")
    private val LABEL_KEYS = listOf("label", "title", "name")
}

/** One subtitle entry as a panel described it. Nothing here is trusted to be present. */
internal data class XtreamSubtitle(
    val url: String,
    val language: String? = null,
    val label: String? = null,
)
