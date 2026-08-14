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

import dev.quiblo.core.database.dao.ProgrammeDao
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Programme
import dev.quiblo.core.model.SourceKind
import dev.quiblo.source.api.GuideResult
import dev.quiblo.source.api.GuideSource
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * That the several different nothings are told apart.
 *
 * `refreshGuideFor` returned a `Boolean`, and five unrelated situations produced `false`: the
 * source has no guide to give, the panel is refusing this app, the panel answered with nothing,
 * the request failed, and the channel has no stream id. Every screen above drew all five as a
 * blank line, so "the guide does not work" was the only sentence anybody could write about any
 * of them — including in a bug report.
 *
 * These tests are the seam that keeps them apart. The one that matters most is the block: it is
 * the only outcome a viewer can act on, and it was the most thoroughly hidden.
 */
class GuideRepositoryTest {

    private val programmeDao: ProgrammeDao = mockk(relaxed = true)
    private val sourceDao: SourceDao = mockk(relaxed = true)

    /** What the panel will answer, swapped per test. */
    private var answer: GuideResult = GuideResult.Success(emptyList())

    /** How many times the panel was actually asked, which is the backoff's whole claim. */
    private var asks = 0

    private val panel = object : MediaSource, GuideSource {
        override val kind = SourceKind.XTREAM
        override suspend fun load(request: SourceRequest): SourceResult =
            SourceResult.Failure(SourceError.UnreachableHost)

        override suspend fun guideFor(
            request: SourceRequest,
            channelKey: String,
            providerStreamId: String,
        ): GuideResult {
            asks++
            return answer
        }
    }

    /** An M3U source: a [MediaSource] and deliberately not a [GuideSource]. */
    private val playlist = object : MediaSource {
        override val kind = SourceKind.M3U
        override suspend fun load(request: SourceRequest): SourceResult =
            SourceResult.Failure(SourceError.UnreachableHost)
    }

    private var clock = 1_000_000L

    private fun repository(vararg sources: MediaSource) = GuideRepository(
        programmeDao = programmeDao,
        sourceDao = sourceDao,
        mediaSources = sources.associateBy { it.kind },
        now = { clock },
    )

    @Test
    fun `listings that come back are stored`() = runTest {
        givenSource(SourceKind.XTREAM)
        answer = GuideResult.Success(listOf(programme()))

        assertEquals(GuideOutcome.STORED, repository(panel).refreshGuideFor(channel()))
    }

    @Test
    fun `a panel that answers with nothing is EMPTY, not a failure`() = runTest {
        givenSource(SourceKind.XTREAM)
        answer = GuideResult.Success(emptyList())

        // The commonest real case: a channel the panel carries but has no `epg_channel_id`
        // for. Nothing is wrong, and the screen should say something different from the two
        // cases where something is.
        assertEquals(GuideOutcome.EMPTY, repository(panel).refreshGuideFor(channel()))
    }

    @Test
    fun `a refusing panel is BLOCKED, and is not asked again while the backoff holds`() = runTest {
        givenSource(SourceKind.XTREAM)
        answer = GuideResult.Failure(SourceError.ProviderBlocked)
        val repository = repository(panel)

        assertEquals(GuideOutcome.BLOCKED, repository.refreshGuideFor(channel()))
        assertEquals(1, asks)

        // Still blocked, still reported as blocked, and — the part that protects the account —
        // reported without another request leaving the device.
        assertEquals(GuideOutcome.BLOCKED, repository.refreshGuideFor(channel(key = "another")))
        assertEquals(1, asks)
    }

    @Test
    fun `the backoff lets go on its own`() = runTest {
        givenSource(SourceKind.XTREAM)
        answer = GuideResult.Failure(SourceError.ProviderBlocked)
        val repository = repository(panel)
        repository.refreshGuideFor(channel())

        // A block is a state of the provider, not of the account, so waiting it out is the
        // whole recovery. Sixteen minutes: the backoff is fifteen.
        clock += 16L * 60L * 1000L
        answer = GuideResult.Success(listOf(programme()))

        assertEquals(GuideOutcome.STORED, repository.refreshGuideFor(channel()))
        assertEquals(2, asks)
    }

    @Test
    fun `another kind of failure is not mistaken for a block`() = runTest {
        givenSource(SourceKind.XTREAM)
        answer = GuideResult.Failure(SourceError.UnreachableHost)

        // A network that is down is not a provider refusing us, and telling a viewer to
        // telephone their provider about it would be wrong advice.
        assertEquals(GuideOutcome.FAILED, repository(panel).refreshGuideFor(channel()))
    }

    @Test
    fun `a source with no guide to give is UNSUPPORTED, and is never asked`() = runTest {
        givenSource(SourceKind.M3U)

        // AC-EPG-04, restated as a value rather than as an absence: an M3U playlist carries
        // no listings, which is a fact about playlists and not a fault worth a message.
        assertEquals(GuideOutcome.UNSUPPORTED, repository(playlist).refreshGuideFor(channel()))
        assertEquals(0, asks)
    }

    @Test
    fun `a channel with no stream id is UNSUPPORTED too`() = runTest {
        givenSource(SourceKind.XTREAM)

        assertEquals(
            GuideOutcome.UNSUPPORTED,
            repository(panel).refreshGuideFor(channel(streamId = null)),
        )
        assertEquals(0, asks)
    }

    private fun givenSource(kind: SourceKind) {
        coEvery { sourceDao.findById(SOURCE_ID) } returns SourceEntity(
            id = SOURCE_ID,
            name = "A source",
            kind = kind.name,
            url = "https://example.invalid",
            createdAtEpochMillis = 0L,
        )
    }

    private fun channel(key: String = "a-channel", streamId: String? = "101") = Channel(
        id = 1L,
        sourceId = SOURCE_ID,
        name = "Alpha",
        streamUrl = "https://example.invalid/101.ts",
        kind = MediaKind.LIVE,
        tvgId = key,
        providerStreamId = streamId,
    )

    private fun programme() = Programme(
        id = 0L,
        sourceId = SOURCE_ID,
        channelKey = "a-channel",
        title = "The News",
        startEpochMillis = 0L,
        endEpochMillis = 1L,
    )

    private companion object {
        const val SOURCE_ID = 5L
    }
}
