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

package dev.quiblo.core.data

import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.FeedRowDao
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.SourceKind
import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.Credentials
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceReport
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * A source can be edited and stay the same source (`BUG-042`).
 *
 * The alternative was delete and re-add, which cascades away every favourite and resume point — so
 * a provider changing its address or a viewer's password cost the household its history.
 */
class SourceEditTest {

    private var row = SourceEntity(
        id = 7L,
        name = "Panel",
        kind = SourceKind.XTREAM.name,
        url = "http://old.example.invalid:8080",
        createdAtEpochMillis = 1L,
        lastRefreshedEpochMillis = 2L,
    )

    private val sourceDao: SourceDao = mockk<SourceDao>(relaxed = true).apply {
        coEvery { findById(7L) } answers { row }
        coEvery { update(any()) } answers { row = firstArg() }
    }

    private class Store(var value: Credentials?) : CredentialStore {
        override suspend fun credentials(sourceId: Long) = value
        override suspend fun put(sourceId: Long, credentials: Credentials) {
            value = credentials
        }
        override suspend fun clear(sourceId: Long) {
            value = null
        }
    }

    private val store = Store(Credentials("someone", "old-pass"))

    /** A panel that answers only to the address and password it is told to accept. */
    private inner class Panel(private val acceptsHost: String, private val acceptsPassword: String) : MediaSource {
        val asked = mutableListOf<Pair<String, String?>>()
        override val kind = SourceKind.XTREAM
        override suspend fun load(request: SourceRequest): SourceResult {
            val password = store.credentials(request.sourceId)?.password
            asked += request.location to password
            return if (acceptsHost in request.location && password == acceptsPassword) {
                SourceResult.Success(listOf(CHANNEL), SourceReport(parsedEntries = 1, skippedEntries = 0))
            } else {
                SourceResult.Failure(SourceError.Unauthorized)
            }
        }
    }

    private fun repository(panel: MediaSource) = SourceRepository(
        sourceDao = sourceDao,
        channelDao = mockk<ChannelDao>(relaxed = true),
        feedRowDao = mockk<FeedRowDao>(relaxed = true),
        mediaSources = mapOf(SourceKind.XTREAM to panel),
        credentialStore = store,
    )

    @Test
    fun `a new address and password are saved under the same source and loaded with`() = runTest {
        val panel = Panel(acceptsHost = "new.example.invalid", acceptsPassword = "new-pass")

        val outcome = repository(panel)
            .editSource(7L, "Panel", " http://new.example.invalid:8080 ", "someone", "new-pass")

        assertInstanceOf(RefreshOutcome.Success::class.java, outcome)
        assertEquals(7L, (outcome as RefreshOutcome.Success).sourceId)
        assertEquals("http://new.example.invalid:8080", row.url)
        assertEquals(Credentials("someone", "new-pass"), store.value)
        assertEquals(listOf("http://new.example.invalid:8080" to "new-pass"), panel.asked)
    }

    @Test
    fun `an empty password keeps the stored one`() = runTest {
        val panel = Panel(acceptsHost = "new.example.invalid", acceptsPassword = "old-pass")

        repository(panel).editSource(7L, "Panel", "http://new.example.invalid:8080", "someone", "")

        assertEquals(Credentials("someone", "old-pass"), store.value)
    }

    @Test
    fun `a change the panel refuses puts everything back as it was`() = runTest {
        val before = row
        val panel = Panel(acceptsHost = "old.example.invalid", acceptsPassword = "old-pass")

        val outcome = repository(panel).editSource(7L, "Renamed", "http://typo.example.invalid", "someone", "wrong")

        assertEquals(RefreshOutcome.Failure(SourceError.Unauthorized), outcome)
        assertEquals(before, row)
        assertEquals(Credentials("someone", "old-pass"), store.value)
    }

    @Test
    fun `the source is never deleted, so nothing keyed to it cascades away`() = runTest {
        val panel = Panel(acceptsHost = "new.example.invalid", acceptsPassword = "new-pass")

        repository(panel).editSource(7L, "Panel", "http://new.example.invalid", "someone", "new-pass")

        coVerify(exactly = 0) { sourceDao.deleteById(any()) }
    }

    private companion object {
        val CHANNEL = Channel(
            id = 0L,
            sourceId = 7L,
            name = "News",
            streamUrl = "xtream:live/1",
            kind = MediaKind.LIVE,
            tvgId = "news.epg",
        )
    }
}
