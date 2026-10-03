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

package dev.quiblo.core.data.diagnostics

import dev.quiblo.source.api.AccountHealth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * What a diagnosis may say about where a stream was (AC-XT-04): the host, and nothing else.
 *
 * Every URL here is synthetic, in the shapes real ones take (AC-LEGAL-04).
 */
class DiagnosisDetailsTest {

    @Test
    fun `an xtream path loses its username and password`() {
        assertEquals("panel.example.invalid", hostOf("http://panel.example.invalid:8080/live/someone/s3cret/1.ts"))
    }

    @Test
    fun `an m3u query loses its credentials`() {
        assertEquals(
            "cdn.example.invalid",
            hostOf("https://cdn.example.invalid/get.php?username=someone&password=s3cret"),
        )
    }

    @Test
    fun `userinfo in the authority is not part of the host`() {
        assertEquals("cdn.example.invalid", hostOf("http://someone:s3cret@cdn.example.invalid/x.m3u8"))
    }

    @Test
    fun `something that is not a url names no host`() {
        assertNull(hostOf("not a url at all"))
    }

    @Test
    fun `the details line never carries a path`() {
        val line = detailsLine(
            StreamEvidence(StreamFault.TIMEOUT, retries = 2),
            AccountEvidence.Answered(AccountHealth.ServerError(503)),
            hostOf("http://panel.example.invalid/series/someone/s3cret/9.mkv"),
        )

        assertEquals(
            "no engine error (load timed out) · no data · 2 retries · panel HTTP 503 · host panel.example.invalid",
            line,
        )
        listOf("someone", "s3cret", "/series/", ".mkv").forEach { assertFalse(it in line, it) }
    }

    @Test
    fun `a playlist says there was no account to check`() {
        val line = detailsLine(
            StreamEvidence(
                StreamFault.BAD_STATUS,
                httpStatus = 404,
                engineCode = "ERROR_CODE_IO_BAD_HTTP_STATUS",
                retries = 1,
            ),
            AccountEvidence.NotApplicable,
            null,
        )

        assertEquals("HTTP 404 · ERROR_CODE_IO_BAD_HTTP_STATUS · no data · 1 retry · no account check (playlist)", line)
    }
}
