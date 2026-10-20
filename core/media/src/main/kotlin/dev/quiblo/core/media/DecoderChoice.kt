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

package dev.quiblo.core.media

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException

/*
 * Which video decoder the engine may use, and what to do when the one it wants will not start
 * (`BUG-063`), as plain functions in the style of `PlaybackDecisions`.
 *
 * The engine is told to fall back to the next decoder that claims a format when one fails to
 * start, and on a television box the next one is the platform's software decoder. For the codecs
 * a television box can only play in hardware, that fallback was not a safety net but a slideshow:
 * a hardware decoder still held by the instance before it — after a seek, or a quick change of
 * film — was passed over without a word, and the film played on the CPU at a fraction of its rate.
 */

/**
 * The decoders the engine may try for [mimeType], in its own order.
 *
 * For [HARDWARE_ONLY_MIME_TYPES], software decoders are left out whenever a hardware one claims the
 * format, unless [softwareAllowed]. Everything else — H.264, MPEG-2, every audio format — is passed
 * through untouched, fallback included: those are what the fallback was turned on for.
 *
 * @param isSoftwareOnly reads Media3's own verdict on a decoder, so this stays testable without one.
 */
internal fun <T> decodersFor(
    mimeType: String,
    decoders: List<T>,
    softwareAllowed: Boolean,
    isSoftwareOnly: (T) -> Boolean,
): List<T> {
    if (softwareAllowed || mimeType !in HARDWARE_ONLY_MIME_TYPES) return decoders
    val hardware = decoders.filterNot(isSoftwareOnly)
    return hardware.ifEmpty { decoders }
}

/** What to do when no decoder the engine was allowed would start. */
internal sealed interface DecoderInitStep {

    /** Try the hardware decoder again after [delayMillis]; it is usually free by then. */
    data class RetryHardware(val delayMillis: Long) : DecoderInitStep

    /** Let the software decoder in and prepare again: a slow picture beats an error. */
    data object AllowSoftware : DecoderInitStep

    /** Not a failure this decision handles. */
    data object NotMine : DecoderInitStep
}

/**
 * The next step after the engine failed with [errorCode].
 *
 * @param softwareAllowed whether software decoders were already allowed for this load.
 * @param hardwareRetriesSoFar hardware retries already made for this item.
 */
internal fun afterDecoderInitFailure(
    errorCode: Int,
    softwareAllowed: Boolean,
    hardwareRetriesSoFar: Int,
): DecoderInitStep = when {
    errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED || softwareAllowed -> DecoderInitStep.NotMine
    hardwareRetriesSoFar < HARDWARE_DECODER_RETRIES -> DecoderInitStep.RetryHardware(HARDWARE_DECODER_RETRY_MILLIS)
    else -> DecoderInitStep.AllowSoftware
}

/**
 * Codecs a television box decodes in hardware or not at all.
 *
 * The same four another Media3 player on Android TV settled on for the same symptom
 * (NuvioTV #3968). H.264 is not among them: a software decoder keeps up with it at 1080p on most
 * boxes, and an H.264 stream whose header the vendor's decoder rejects is exactly what the
 * fallback exists for.
 */
internal val HARDWARE_ONLY_MIME_TYPES = setOf(
    MimeTypes.VIDEO_H265,
    VP9_MIME_TYPE,
    MimeTypes.VIDEO_AV1,
    MimeTypes.VIDEO_DOLBY_VISION,
)

/** VP9, as Media3 names it (`MimeTypes.VIDEO_VP9`, which is marked unstable where the others are not). */
private const val VP9_MIME_TYPE = "video/x-vnd.on2.vp9"

/** Hardware retries per item before software is let in. */
internal const val HARDWARE_DECODER_RETRIES = 1

/** Long enough for the decoder before this one to be released. */
internal const val HARDWARE_DECODER_RETRY_MILLIS = 1_000L
