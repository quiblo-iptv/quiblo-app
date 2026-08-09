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
import dev.quiblo.core.database.dao.ChannelWithFavorite
import dev.quiblo.core.database.dao.TitleGenreRow
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.model.MediaKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * What a search asks the database, and what it declines to ask.
 *
 * Two things are worth pinning down here and neither is visible from the screen. The first is
 * that an empty box costs nothing: a viewer who has opened search and typed nothing must not
 * have set 67,000 rows moving. The second is the genre filter's title matching, which happens
 * in Kotlin against a cache keyed by a *cleaned* title — a rule that can drift silently, and
 * whose failure looks like a filter that simply finds nothing.
 */
class SearchRepositoryTest {

    private val channelDao: ChannelDao = mockk(relaxed = true)
    private val titleMetadataDao: TitleMetadataDao = mockk(relaxed = true)
    private val metadataRepository: TitleMetadataRepository = mockk<TitleMetadataRepository>().apply {
        every { isEnabled } returns true
    }

    private val repository = SearchRepository(
        channelDao = channelDao,
        profiles = fakeProfiles(),
        titleMetadataDao = titleMetadataDao,
        metadataRepository = metadataRepository,
        // Unconfined would let assertions read state mid-flight; the default pool is what
        // ships, and nothing here is timing-dependent.
        matchDispatcher = Dispatchers.Default,
    )

    @Test
    @DisplayName("an empty search box is not a request for the whole catalogue")
    fun `a blank term with no genre asks the database nothing`() = runTest {
        val results = repository.search(sourceId = SOURCE_ID, query = "   ")

        assertTrue(results.isEmpty)
        coVerify(exactly = 0) { channelDao.search(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { channelDao.titlesForMetadata(any()) }
    }

    @Test
    @DisplayName("one term, three answers, kept apart")
    fun `searches every kind and keeps the results separate`() = runTest {
        coEvery { channelDao.search(any(), SOURCE_ID, MediaKind.LIVE.name, "bbc", any()) } returns
            listOf(row(id = 1L, name = "BBC One", kind = MediaKind.LIVE))
        coEvery { channelDao.search(any(), SOURCE_ID, MediaKind.VOD.name, "bbc", any()) } returns
            listOf(row(id = 2L, name = "BBC Earth: A Perfect Planet", kind = MediaKind.VOD))
        coEvery { channelDao.search(any(), SOURCE_ID, MediaKind.SERIES.name, "bbc", any()) } returns
            emptyList()

        val results = repository.search(sourceId = SOURCE_ID, query = "bbc")

        assertEquals(listOf("BBC One"), results.live.map { it.name })
        assertEquals(listOf("BBC Earth: A Perfect Planet"), results.movies.map { it.name })
        assertEquals(emptyList<String>(), results.series.map { it.name })
        assertEquals(2, results.total)
    }

    @Test
    @DisplayName("a genre finds a film whose title carries a year and a quality tag")
    fun `the genre filter matches through the cleaned title`() = runTest {
        coEvery { titleMetadataDao.allGenreRows() } returns listOf(
            TitleGenreRow(searchTitle = "fargo", kind = MediaKind.VOD.name, genres = "Crime\nDrama", isMiss = false),
            TitleGenreRow(searchTitle = "speed", kind = MediaKind.VOD.name, genres = "Action", isMiss = false),
        )
        coEvery { channelDao.titlesForMetadata(SOURCE_ID) } returns listOf(
            // As a panel actually writes them: a year in brackets and a quality marker, none
            // of which the cache key has.
            ChannelTitle(id = 10L, name = "Fargo (1996) [FHD]", kind = MediaKind.VOD.name),
            ChannelTitle(id = 11L, name = "Speed", kind = MediaKind.VOD.name),
        )
        coEvery { channelDao.findAllByIds(any(), listOf(10L)) } returns
            listOf(row(id = 10L, name = "Fargo (1996) [FHD]", kind = MediaKind.VOD))

        val results = repository.search(sourceId = SOURCE_ID, query = "", genre = "Crime")

        assertEquals(listOf("Fargo (1996) [FHD]"), results.movies.map { it.name })
        // The rows are read for the chosen ids only. Reading them all and filtering afterwards
        // would be the whole browse working set for a screenful of answers.
        coVerify(exactly = 1) { channelDao.findAllByIds(any(), listOf(10L)) }
    }

    @Test
    @DisplayName("a genre a viewer has never looked at is not offered")
    fun `the genre index lists only genres the cache actually holds`() = runTest {
        coEvery { titleMetadataDao.allGenreRows() } returns listOf(
            TitleGenreRow("fargo", MediaKind.VOD.name, "Crime\nDrama", isMiss = false),
            TitleGenreRow("speed", MediaKind.VOD.name, "Action\nCrime", isMiss = false),
            // A miss carries no genres and must not be mistaken for one.
            TitleGenreRow("some unfindable title", MediaKind.VOD.name, null, isMiss = true),
        )
        coEvery { channelDao.titlesForMetadata(SOURCE_ID) } returns emptyList()

        val index = repository.genreIndex(SOURCE_ID)

        assertEquals(listOf("Action", "Crime", "Drama"), index.genres)
    }

    @Test
    @DisplayName("four copies of one film are one title to look up, not four")
    fun `coverage counts distinct cleaned titles rather than rows`() = runTest {
        coEvery { titleMetadataDao.allGenreRows() } returns listOf(
            TitleGenreRow("the matrix", MediaKind.VOD.name, "Action", isMiss = false),
        )
        coEvery { channelDao.titlesForMetadata(SOURCE_ID) } returns listOf(
            ChannelTitle(id = 1L, name = "The Matrix (1999) [4K]", kind = MediaKind.VOD.name),
            ChannelTitle(id = 2L, name = "The Matrix (1999) [FHD]", kind = MediaKind.VOD.name),
            ChannelTitle(id = 3L, name = "The Matrix (1999) [SD]", kind = MediaKind.VOD.name),
            ChannelTitle(id = 4L, name = "Heat (1995)", kind = MediaKind.VOD.name),
            // Cleans away to nothing, so it can never be looked up and is excluded from both
            // halves — in the denominator it would cap the figure below 100% for ever.
            ChannelTitle(id = 5L, name = "|AR|", kind = MediaKind.VOD.name),
        )

        val index = repository.genreIndex(SOURCE_ID)

        // One of the two titles that can be looked up is described.
        assertEquals(50, index.coveragePercent)
    }

    @Test
    @DisplayName("no key is a different emptiness from no genres")
    fun `the genre index says so when metadata is switched off`() = runTest {
        every { metadataRepository.isEnabled } returns false

        val index = repository.genreIndex(SOURCE_ID)

        assertTrue(index.isMetadataDisabled)
        assertEquals(emptyList<String>(), index.genres)
        coVerify(exactly = 0) { titleMetadataDao.allGenreRows() }
    }

    private fun row(id: Long, name: String, kind: MediaKind) = ChannelWithFavorite(
        channel = ChannelEntity(
            id = id,
            sourceId = SOURCE_ID,
            name = name,
            streamUrl = "http://host.invalid/$id",
            kind = kind.name,
            groupTitle = "Group",
            stableKey = "key-$id",
            sortIndex = id.toInt(),
        ),
        isFavorite = false,
    )

    private companion object {
        const val SOURCE_ID = 3L
    }
}
