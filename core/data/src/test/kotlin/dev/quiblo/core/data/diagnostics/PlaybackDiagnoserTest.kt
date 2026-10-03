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

import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.model.SourceKind
import dev.quiblo.core.network.ConnectivityChecker
import dev.quiblo.source.api.AccountHealth
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Gathering the evidence (`FEAT-035`): what is asked, how often, and what is never kept.
 */
class PlaybackDiagnoserTest {

    /** An Xtream-like source that counts how often the panel was asked. */
    private class FakePanel(var answer: AccountHealth?) : MediaSource {
        var asked = 0
        override val kind = SourceKind.XTREAM
        override val checksAccount = true
        override suspend fun load(request: SourceRequest): SourceResult = error("not used")
        override suspend fun accountHealth(request: SourceRequest): AccountHealth? {
            asked++
            return answer
        }
    }

    /** A playlist: no account to ask. */
    private object Playlist : MediaSource {
        override val kind = SourceKind.M3U
        override suspend fun load(request: SourceRequest): SourceResult = error("not used")
    }

    private val sources: SourceDao = mockk<SourceDao>().apply {
        coEvery { findById(XTREAM_ID) } returns entity(XTREAM_ID, SourceKind.XTREAM)
        coEvery { findById(M3U_ID) } returns entity(M3U_ID, SourceKind.M3U)
        coEvery { findById(MISSING_ID) } returns null
    }

    private fun entity(id: Long, kind: SourceKind) = SourceEntity(
        id = id,
        name = "Synthetic",
        kind = kind.name,
        url = "http://panel.example.invalid:8080",
        createdAtEpochMillis = 0L,
    )

    private var clock = 1_000_000L
    private var online = true
    private val log = PlaybackLog()

    private fun diagnoser(panel: FakePanel) = PlaybackDiagnoser(
        sourceDao = sources,
        mediaSources = mapOf(SourceKind.XTREAM to panel, SourceKind.M3U to Playlist),
        connectivity = ConnectivityChecker { online },
        log = log,
        now = { clock },
    )

    private val refused =
        StreamEvidence(StreamFault.BAD_STATUS, httpStatus = 403, engineCode = "ERROR_CODE_IO_BAD_HTTP_STATUS")
    private val credentialedUrl = "http://panel.example.invalid:8080/live/someone/s3cret/1234.ts"

    @Test
    fun `an expired account is named, with the date the panel gave`() = runTest {
        val diagnosis = diagnoser(FakePanel(AccountHealth.Expired(42L)))
            .diagnose(XTREAM_ID, credentialedUrl, "News", refused)

        assertEquals(Verdict.SUBSCRIPTION_EXPIRED, diagnosis.verdict)
        assertEquals(AccountHealth.Expired(42L), diagnosis.account)
    }

    @Test
    fun `channel zapping asks the panel once a minute, not once a channel`() = runTest {
        val panel = FakePanel(AccountHealth.Ok(null, 2, 2))
        val diagnoser = diagnoser(panel)

        repeat(5) { diagnoser.diagnose(XTREAM_ID, credentialedUrl, "Channel $it", refused) }
        assertEquals(1, panel.asked)

        clock += 61_000L
        diagnoser.diagnose(XTREAM_ID, credentialedUrl, "Later", refused)
        assertEquals(2, panel.asked)
    }

    @Test
    fun `an offline device does not ask the panel anything`() = runTest {
        val panel = FakePanel(AccountHealth.Ok(null, 1, 2))
        online = false

        val diagnosis = diagnoser(panel).diagnose(XTREAM_ID, credentialedUrl, "News", refused)

        assertEquals(Verdict.DEVICE_OFFLINE, diagnosis.verdict)
        assertEquals(0, panel.asked)
    }

    @Test
    fun `an unanswered account check is undetermined, never a guess`() = runTest {
        val diagnosis = diagnoser(FakePanel(null)).diagnose(XTREAM_ID, credentialedUrl, "News", refused)

        assertEquals(Verdict.UNDETERMINED, diagnosis.verdict)
        assertNull(diagnosis.account)
        assertTrue("account check unavailable" in diagnosis.details, diagnosis.details)
    }

    @Test
    fun `a playlist is judged on the stream alone`() = runTest {
        val diagnosis = diagnoser(FakePanel(null)).diagnose(
            M3U_ID,
            "http://cdn.example.invalid/playlist?username=someone&password=s3cret",
            "News",
            StreamEvidence(StreamFault.BAD_STATUS, httpStatus = 401),
        )

        assertEquals(Verdict.CREDENTIALS_REJECTED, diagnosis.verdict)
    }

    @Test
    fun `a source that no longer exists blames nobody`() = runTest {
        val diagnosis = diagnoser(FakePanel(AccountHealth.Disabled))
            .diagnose(MISSING_ID, credentialedUrl, "News", refused)

        assertEquals(Verdict.UNDETERMINED, diagnosis.verdict)
    }

    @Test
    fun `the details name the host and never the credentials`() = runTest {
        val diagnosis = diagnoser(FakePanel(AccountHealth.Ok(null, 2, 2)))
            .diagnose(XTREAM_ID, credentialedUrl, "News", refused)

        assertEquals(
            "HTTP 403 · ERROR_CODE_IO_BAD_HTTP_STATUS · no data · account OK (2/2) · host panel.example.invalid",
            diagnosis.details,
        )
        assertFalse("someone" in diagnosis.details)
        assertFalse("s3cret" in diagnosis.details)
        assertFalse("/live/" in diagnosis.details)
    }

    @Test
    fun `every diagnosis is logged, newest first`() = runTest {
        val diagnoser = diagnoser(FakePanel(AccountHealth.Ok(null, 1, 2)))
        diagnoser.diagnose(XTREAM_ID, credentialedUrl, "First", refused)
        diagnoser.diagnose(XTREAM_ID, credentialedUrl, "Second", refused)

        assertEquals(listOf("Second", "First"), log.entries.value.map { it.title })
    }

    private companion object {
        const val XTREAM_ID = 1L
        const val M3U_ID = 2L
        const val MISSING_ID = 9L
    }
}
