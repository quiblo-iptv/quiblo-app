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

package dev.quiblo.feature.player

import dev.quiblo.core.data.diagnostics.AccountEvidence
import dev.quiblo.core.data.diagnostics.Diagnosis
import dev.quiblo.core.data.diagnostics.StreamEvidence
import dev.quiblo.core.data.diagnostics.StreamFault
import dev.quiblo.core.data.diagnostics.Verdict
import dev.quiblo.core.data.diagnostics.detailsLine
import dev.quiblo.core.data.diagnostics.hostOf
import dev.quiblo.core.media.FailureDetails
import dev.quiblo.core.media.PlayableItem
import dev.quiblo.core.media.PlaybackError
import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.PlaybackStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Where the player's failure meets the diagnosis (`FEAT-035`). */
class PlaybackEvidenceTest {

    private val credentialedUrl = "http://panel.example.invalid:8080/live/someone/s3cret/1234.ts"

    private fun failed(error: PlaybackError, details: FailureDetails? = FailureDetails()) = PlaybackState(
        status = PlaybackStatus.ERROR,
        item = PlayableItem(id = "k", title = "News", url = credentialedUrl, isLive = true),
        error = error,
        failure = details,
    )

    @Test
    fun `nothing failed is no evidence`() {
        assertNull(PlaybackState().streamEvidence())
    }

    @Test
    fun `a status is carried as a bad status, whatever the error was called`() {
        val evidence = failed(
            PlaybackError.PROVIDER_REFUSED,
            FailureDetails(httpStatus = 458, engineCode = "ERROR_CODE_IO_BAD_HTTP_STATUS", retries = 2),
        ).streamEvidence()

        assertEquals(
            StreamEvidence(
                StreamFault.BAD_STATUS,
                httpStatus = 458,
                engineCode = "ERROR_CODE_IO_BAD_HTTP_STATUS",
                retries = 2,
            ),
            evidence,
        )
    }

    @Test
    fun `each engine-level error keeps its kind`() {
        mapOf(
            PlaybackError.UNREACHABLE to StreamFault.UNREACHABLE,
            PlaybackError.TIMEOUT to StreamFault.TIMEOUT,
            PlaybackError.NETWORK to StreamFault.NETWORK,
            PlaybackError.UNSUPPORTED_FORMAT to StreamFault.FORMAT,
            PlaybackError.DRM_UNSUPPORTED to StreamFault.DRM,
            PlaybackError.UNKNOWN to StreamFault.OTHER,
        ).forEach { (error, fault) ->
            assertEquals(fault, failed(error).streamEvidence()?.fault, "$error")
        }
    }

    @Test
    fun `bytes and having played are carried, so Quiblo is only blamed with proof`() {
        val evidence = failed(
            PlaybackError.UNSUPPORTED_FORMAT,
            FailureDetails(bytesReceived = 188_000L, hadPlayed = false),
        ).streamEvidence()

        assertEquals(188_000L, evidence?.bytesReceived)
        assertFalse(evidence?.hadPlayed ?: true)
    }

    @Test
    fun `an error with no details is still evidence, of nothing received`() {
        assertEquals(
            StreamEvidence(StreamFault.TIMEOUT),
            failed(PlaybackError.TIMEOUT, details = null).streamEvidence(),
        )
    }

    @Test
    fun `copied details never contain the credentialed path`() {
        val diagnosis = Diagnosis(
            verdict = Verdict.UNDETERMINED,
            account = null,
            details = detailsLine(
                StreamEvidence(StreamFault.BAD_STATUS, httpStatus = 403, engineCode = "ERROR_CODE_IO_BAD_HTTP_STATUS"),
                AccountEvidence.Unavailable,
                hostOf(credentialedUrl),
            ),
            title = "News",
            atEpochMillis = 0L,
        )

        val copied = diagnosis.reportText()

        listOf("someone", "s3cret", "/live/", "1234.ts", "News").forEach { assertFalse(it in copied, it) }
        assertEquals(
            "Quiblo playback: UNDETERMINED · HTTP 403 · ERROR_CODE_IO_BAD_HTTP_STATUS · no data · " +
                "account check unavailable · host panel.example.invalid",
            copied,
        )
    }
}
