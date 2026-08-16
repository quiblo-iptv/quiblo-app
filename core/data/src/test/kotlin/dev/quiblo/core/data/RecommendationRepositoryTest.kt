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
import dev.quiblo.core.database.dao.FavoriteDao
import dev.quiblo.core.database.dao.TitleFactRow
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The plumbing around [Recommender], which is where the arithmetic is tested.
 *
 * What is asserted here is the three things this class decides for itself: that it stays silent
 * rather than guessing, that hidden categories are not proposed out of, and when each watched
 * title was last played — the fact the remembered suggestions row uses to tell that a cause has
 * been watched again since it was suggested.
 */
class RecommendationRepositoryTest {

    private val history: WatchHistoryRepository = mockk()
    private val titleMetadataDao: TitleMetadataDao = mockk()
    private val channelDao: ChannelDao = mockk()

    private val watchEvents: WatchEventRepository = mockk(relaxed = true)
    private val opinions: TitleOpinionRepository = mockk(relaxed = true)
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)

    private val repository = RecommendationRepository(
        history = history,
        profiles = fakeProfiles(),
        watchEvents = watchEvents,
        opinions = opinions,
        titleMetadataDao = titleMetadataDao,
        channelDao = channelDao,
        favoriteDao = favoriteDao,
        matchDispatcher = Dispatchers.Unconfined,
        now = { NOW },
    )

    private fun watched(vararg entries: HistoryEntry) {
        every { history.observeHistory(SOURCE_ID, MediaKind.VOD, any()) } returns
            flowOf(entries.filter { it.kind == MediaKind.VOD })
        every { history.observeHistory(SOURCE_ID, MediaKind.SERIES, any()) } returns
            flowOf(entries.filter { it.kind == MediaKind.SERIES })
    }

    @Test
    fun `a profile that has watched nothing is offered nothing`() = runTest {
        watched()
        coEvery { titleMetadataDao.allFactRows() } returns listOf(factRow("dune", "Science Fiction"))

        assertTrue(repository.suggestions(SOURCE_ID).isEmpty())
    }

    @Test
    fun `a catalogue nobody has described yet is offered nothing`() = runTest {
        watched(*learned())
        coEvery { titleMetadataDao.allFactRows() } returns emptyList()

        assertTrue(repository.suggestions(SOURCE_ID).isEmpty())
        // And it does not read the catalogue to find that out: sixty thousand rows for an answer
        // already known is the cost this early return exists to avoid.
        coVerify(exactly = 0) { channelDao.titlesForMetadata(any(), any()) }
    }

    /**
     * A shelf the viewer has put away is not proposed out of.
     *
     * The popular row does the opposite deliberately — it is about what a provider carries, and
     * tidying a category away is not a claim that its films stopped existing. A suggestion is the
     * app speaking unprompted, and proposing out of a hidden shelf is the app arguing.
     */
    @Test
    fun `suggestions never come out of a hidden category`() = runTest {
        watched(*learned())
        coEvery { titleMetadataDao.allFactRows() } returns
            learned().map { factRow(it.title.lowercase(), "Animation", kind = MediaKind.SERIES) } +
            factRow("naruto", "Animation", kind = MediaKind.SERIES)
        // The hidden ones are simply not in this answer: the exclusion is the query's, and what is
        // asserted here is that this is the query asked.
        coEvery { channelDao.titlesForMetadata(SOURCE_ID, includeHidden = false) } returns
            listOf(ChannelTitle(id = 9L, name = "Naruto", kind = MediaKind.SERIES.name))

        val suggestions = repository.suggestions(SOURCE_ID)

        assertEquals(listOf(9L), suggestions.map { it.channelId })
        coVerify(exactly = 0) { channelDao.titlesForMetadata(SOURCE_ID, includeHidden = true) }
    }

    @Test
    fun `the last time each title was watched is read across both kinds`() = runTest {
        watched(
            entry("One Piece", MediaKind.SERIES, watchedAt = NOW - 100),
            entry("One Piece", MediaKind.SERIES, watchedAt = NOW - 10),
            entry("Dune", MediaKind.VOD, watchedAt = NOW - 50),
        )

        val lastWatched = repository.lastWatchedByTitle(SOURCE_ID)

        // The most recent of the two, not the first one found: this is the fact the remembered
        // suggestions row uses to decide that a cause has been watched again since.
        assertEquals(mapOf("One Piece" to NOW - 10, "Dune" to NOW - 50), lastWatched)
    }

    private fun entry(
        title: String,
        kind: MediaKind,
        watchedAt: Long = NOW,
    ) = HistoryEntry(
        stableKey = "key-$title-$watchedAt",
        sourceId = SOURCE_ID,
        kind = kind,
        title = title,
        artworkUrl = null,
        positionMillis = 110_000,
        durationMillis = 120_000,
        watchedAtEpochMillis = watchedAt,
    )

    /** Enough watched titles for the scorer to answer at all. See `Recommender`'s cold start. */
    private fun learned(): Array<HistoryEntry> =
        (1..Recommender.MINIMUM_DISTINCT_TITLES)
            .map { entry("Watched $it", MediaKind.SERIES, watchedAt = NOW - it * 1_000L) }
            .toTypedArray()

    private fun factRow(searchTitle: String, genres: String, kind: MediaKind = MediaKind.VOD) = TitleFactRow(
        searchTitle = searchTitle,
        kind = kind.name,
        year = 0,
        genres = genres,
        overview = null,
        originalLanguage = "ja",
        popularity = null,
        rating = null,
        releaseYear = null,
        runtimeMinutes = null,
    )

    private companion object {
        const val SOURCE_ID = 6L
        const val NOW = 1_780_000_000_000L
    }
}
