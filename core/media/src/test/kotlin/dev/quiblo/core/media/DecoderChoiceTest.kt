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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Which video decoder the engine may use, and what follows when it will not start (`BUG-063`). */
class DecoderChoiceTest {

    /** A decoder as the selector sees it: a name, and Media3's verdict on it. */
    private data class Decoder(val name: String, val softwareOnly: Boolean)

    private val hardware = Decoder("c2.amlogic.hevc.decoder", softwareOnly = false)
    private val software = Decoder("c2.android.hevc.decoder", softwareOnly = true)

    private fun choose(mimeType: String, decoders: List<Decoder>, softwareAllowed: Boolean = false) =
        decodersFor(mimeType, decoders, softwareAllowed) { it.softwareOnly }

    @Nested
    inner class WhichDecoders {

        @Test
        fun `HEVC is hardware only when the box has a hardware decoder`() {
            assertEquals(listOf(hardware), choose(MimeTypes.VIDEO_H265, listOf(hardware, software)))
        }

        @Test
        fun `every hardware-only codec is treated the same`() {
            HARDWARE_ONLY_MIME_TYPES.forEach { mimeType ->
                assertEquals(listOf(hardware), choose(mimeType, listOf(hardware, software)), mimeType)
            }
        }

        @Test
        fun `software is let in once allowed`() {
            assertEquals(
                listOf(hardware, software),
                choose(MimeTypes.VIDEO_H265, listOf(hardware, software), softwareAllowed = true),
            )
        }

        @Test
        fun `a box with no hardware decoder keeps the software one`() {
            assertEquals(listOf(software), choose(MimeTypes.VIDEO_H265, listOf(software)))
        }

        @Test
        fun `H264 and audio keep their fallback`() {
            val avc = listOf(Decoder("OMX.amlogic.avc.decoder.awesome", false), Decoder("c2.android.avc.decoder", true))
            assertEquals(avc, choose(MimeTypes.VIDEO_H264, avc))
            val aac = listOf(Decoder("c2.android.aac.decoder", true))
            assertEquals(aac, choose(MimeTypes.AUDIO_AAC, aac))
        }
    }

    @Nested
    inner class WhenTheDecoderWillNotStart {

        private val initFailed = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED

        @Test
        fun `the hardware decoder is tried again first`() {
            assertEquals(
                DecoderInitStep.RetryHardware(HARDWARE_DECODER_RETRY_MILLIS),
                afterDecoderInitFailure(initFailed, softwareAllowed = false, hardwareRetriesSoFar = 0),
            )
        }

        @Test
        fun `then software is let in`() {
            assertEquals(
                DecoderInitStep.AllowSoftware,
                afterDecoderInitFailure(
                    initFailed,
                    softwareAllowed = false,
                    hardwareRetriesSoFar = HARDWARE_DECODER_RETRIES,
                ),
            )
        }

        @Test
        fun `a failure with software already allowed is an ordinary failure`() {
            assertEquals(
                DecoderInitStep.NotMine,
                afterDecoderInitFailure(initFailed, softwareAllowed = true, hardwareRetriesSoFar = 0),
            )
        }

        @Test
        fun `any other failure is not this decision's`() {
            assertEquals(
                DecoderInitStep.NotMine,
                afterDecoderInitFailure(
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    softwareAllowed = false,
                    hardwareRetriesSoFar = 0,
                ),
            )
        }
    }
}
