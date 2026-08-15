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
import dev.quiblo.core.database.dao.ChannelTitle
import dev.quiblo.core.database.dao.PopularTitleDao
import dev.quiblo.core.database.entity.PopularTitleEntity
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.tmdb.PopularTitle
import dev.quiblo.source.tmdb.TmdbAnswer
import dev.quiblo.source.tmdb.TmdbClient
import dev.quiblo.source.tmdb.TmdbKind
import dev.quiblo.source.tmdb.TmdbPopular
import dev.quiblo.source.tmdb.TmdbRefusal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * What the popular row costs, and what it does when it cannot have it.
 *
 * The interesting properties here are all about *restraint*. This project's provider account has
 * been blocked twice over requests it did not need, and a metadata service refusing is the same
 * failure with a different host — so how often this asks, and what it does with an answer it did
 * not get, matter more than the happy path.
 */
class PopularTitlesRepositoryTest {

    private val dao: PopularTitleDao = mockk(relaxed = true)
    private val channelDao: ChannelDao = mockk(relaxed = true)
    private val client: TmdbClient = mockk(relaxed = true)
    private val apiKey = MutableStateFlow<String?>("a-key")
    private val metadata: TitleMetadataRepository = mockk<TitleMetadataRepository>().apply {
        every { this@apply.apiKey } returns this@PopularTitlesRepositoryTest.apiKey
    }

    private val repository = PopularTitlesRepository(
        dao = dao,
        channelDao = channelDao,
        client = client,
        metadata = metadata,
        matchDispatcher = Dispatchers.Default,
        now = { NOW },
    )

    @Test
    @DisplayName("with no key configured, nothing is asked and no row is drawn")
    fun `no key means no request and no row`() = runTest {
        apiKey.value = null
        coEvery { dao.all() } returns emptyList()

        assertTrue(repository.popular(SOURCE_ID).isEmpty())
        coVerify(exactly = 0) { client.popular(any(), any()) }
    }

    @Test
    @DisplayName("two requests, and only when the held answer is a week old")
    fun `a fresh list is not refetched`() = runTest {
        coEvery { dao.oldestFetchedAt() } returns NOW - SIX_DAYS
        coEvery { dao.all() } returns emptyList()

        repository.popular(SOURCE_ID)

        coVerify(exactly = 0) { client.popular(any(), any()) }
    }

    @Test
    fun `a week-old list is refetched, once per catalogue`() = runTest {
        coEvery { dao.oldestFetchedAt() } returns NOW - EIGHT_DAYS
        coEvery { client.popular(any(), any()) } returns TmdbPopular.Titles(listOf(popular(1, "Dune")))
        coEvery { dao.all() } returns emptyList()

        repository.popular(SOURCE_ID)

        coVerify(exactly = 1) { client.popular(any(), TmdbKind.MOVIE) }
        coVerify(exactly = 1) { client.popular(any(), TmdbKind.SERIES) }
    }

    /**
     * A refusal is not an answer, and this one would stand for a week if it were written down.
     *
     * The same rule `TitleMetadataRepository` follows for a title, with a longer consequence: a
     * cached refusal here costs seven days of an empty row for one bad minute on the network.
     */
    @Test
    @DisplayName("a refused week leaves last week's answer standing")
    fun `a refusal is never written down`() = runTest {
        coEvery { dao.oldestFetchedAt() } returns NOW - EIGHT_DAYS
        coEvery { client.popular(any(), any()) } returns
            TmdbPopular.Refused(TmdbAnswer.Refused(TmdbRefusal.RATE_LIMITED))
        coEvery { dao.all() } returns listOf(entity(kind = MediaKind.VOD, rank = 1, title = "Dune"))
        coEvery { channelDao.titlesForMetadata(SOURCE_ID, any()) } returns
            listOf(ChannelTitle(id = 7L, name = "Dune (2021) [4K]", kind = MediaKind.VOD.name))

        val row = repository.popular(SOURCE_ID)

        coVerify(exactly = 0) { dao.replaceKind(any(), any()) }
        assertEquals(listOf(7L), row.map { it.channelId })
    }

    @Test
    @DisplayName("only what this provider actually carries reaches the row")
    fun `titles the catalogue does not have are dropped`() = runTest {
        coEvery { dao.oldestFetchedAt() } returns NOW
        coEvery { dao.all() } returns listOf(
            entity(kind = MediaKind.VOD, rank = 1, title = "Nothing Here"),
            entity(kind = MediaKind.VOD, rank = 2, title = "Dune"),
        )
        coEvery { channelDao.titlesForMetadata(SOURCE_ID, any()) } returns
            listOf(ChannelTitle(id = 7L, name = "Dune (2021) [4K]", kind = MediaKind.VOD.name))

        val row = repository.popular(SOURCE_ID)

        assertEquals(listOf(7L), row.map { it.channelId })
        // TMDB's own position, kept rather than renumbered: this is rank 2 of the popular list
        // even though it is the only tile that survived.
        assertEquals(listOf(2), row.map { it.rank })
    }

    /**
     * The cap is taken *after* the match, which is the difference between a full row and a gappy
     * one on a provider that carries none of the world's current top five.
     */
    @Test
    fun `five of each are kept, counted after the match`() = runTest {
        coEvery { dao.oldestFetchedAt() } returns NOW
        coEvery { dao.all() } returns
            (1..10).map { entity(MediaKind.VOD, it, "Film $it") } +
            (1..10).map { entity(MediaKind.SERIES, it, "Show $it") }
        // The provider carries none of the top five of either, and plenty below them.
        coEvery { channelDao.titlesForMetadata(SOURCE_ID, any()) } returns
            (6..10).map { ChannelTitle(it.toLong(), "Film $it", MediaKind.VOD.name) } +
            (6..10).map { ChannelTitle(it + 100L, "Show $it", MediaKind.SERIES.name) }

        val row = repository.popular(SOURCE_ID)

        assertEquals(5, row.count { it.kind == MediaKind.VOD })
        assertEquals(5, row.count { it.kind == MediaKind.SERIES })
        // Films first, then series, which is the order the row is drawn in.
        assertEquals(MediaKind.VOD, row.first().kind)
        assertEquals(MediaKind.SERIES, row.last().kind)
    }

    @Test
    @DisplayName("a provider carrying none of it gets no row rather than an empty one")
    fun `nothing matched is an empty answer`() = runTest {
        coEvery { dao.oldestFetchedAt() } returns NOW
        coEvery { dao.all() } returns listOf(entity(MediaKind.VOD, 1, "Dune"))
        coEvery { channelDao.titlesForMetadata(SOURCE_ID, any()) } returns emptyList()

        assertTrue(repository.popular(SOURCE_ID).isEmpty())
    }

    private fun entity(kind: MediaKind, rank: Int, title: String) = PopularTitleEntity(
        kind = kind.name,
        rank = rank,
        tmdbId = rank,
        title = title,
        year = null,
        posterUrl = null,
        fetchedAtEpochMillis = NOW,
    )

    private fun popular(rank: Int, title: String) =
        PopularTitle(rank = rank, tmdbId = rank, title = title, year = null, posterUrl = null)

    private companion object {
        const val SOURCE_ID = 3L
        const val NOW = 1_770_000_000_000L
        const val SIX_DAYS = 6L * 24 * 60 * 60 * 1000
        const val EIGHT_DAYS = 8L * 24 * 60 * 60 * 1000
    }
}
