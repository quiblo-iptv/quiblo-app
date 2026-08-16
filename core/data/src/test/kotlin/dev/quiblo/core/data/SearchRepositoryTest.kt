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

import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.ChannelWithFavorite
import dev.quiblo.core.database.dao.TitleGenreRow
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.tmdb.NO_YEAR
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
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
        coVerify(exactly = 0) { channelDao.search(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { channelDao.titlesForMetadata(any(), any()) }
    }

    @Test
    @DisplayName("one term, three answers, kept apart")
    fun `searches every kind and keeps the results separate`() = runTest {
        coEvery {
            channelDao.search(any(), SOURCE_ID, MediaKind.LIVE.name, "bbc", any(), any(), any(), any())
        } returns listOf(row(id = 1L, name = "BBC One", kind = MediaKind.LIVE))
        coEvery {
            channelDao.search(any(), SOURCE_ID, MediaKind.VOD.name, "bbc", any(), any(), any(), any())
        } returns listOf(row(id = 2L, name = "BBC Earth: A Perfect Planet", kind = MediaKind.VOD))
        coEvery {
            channelDao.search(any(), SOURCE_ID, MediaKind.SERIES.name, "bbc", any(), any(), any(), any())
        } returns emptyList()

        val results = repository.search(sourceId = SOURCE_ID, query = "bbc")

        assertEquals(listOf("BBC One"), results.live.map { it.name })
        assertEquals(listOf("BBC Earth: A Perfect Planet"), results.movies.map { it.name })
        assertEquals(emptyList<String>(), results.series.map { it.name })
        assertEquals(2, results.total)
    }

    /**
     * A genre asks the database for one genre, and reads nothing else.
     *
     * **What this asserts is an absence, and the absence is `021`.** The filter used to read
     * every film and series the source carried and clean each title in Kotlin to a cache key —
     * fifty thousand rows and four hundred thousand regex passes per press of a chip, repeated in
     * full for the next press. The cleaned key is a column now, so the join is SQLite's; what is
     * left to prove here is that this layer no longer reaches for the catalogue at all.
     *
     * The join itself is proved where a join can be proved, against real SQLite, in
     * `GenreAndScriptQueryTest`.
     */
    @Test
    @DisplayName("a genre asks one query per column and never reads the catalogue")
    fun `the genre filter is one query per kind and no catalogue pass`() = runTest {
        coEvery {
            channelDao.searchByGenre(
                any(), SOURCE_ID, MediaKind.VOD.name, "Crime", any(), any(), any(), any(), any(),
            )
        } returns listOf(row(id = 10L, name = "Fargo (1996) [FHD]", kind = MediaKind.VOD))

        val results = repository.search(
            sourceId = SOURCE_ID,
            query = "",
            options = SearchOptions(genre = "Crime"),
        )

        assertEquals(listOf("Fargo (1996) [FHD]"), results.movies.map { it.name })
        coVerify(exactly = 0) { channelDao.titlesForMetadata(any(), any()) }
        coVerify(exactly = 0) { channelDao.findAllByIds(any(), any()) }
    }

    @Test
    @DisplayName("a genre a viewer has never looked at is not offered")
    fun `the genre index lists only genres the cache actually holds`() = runTest {
        coEvery { titleMetadataDao.allGenreRows() } returns listOf(
            TitleGenreRow("fargo", MediaKind.VOD.name, NO_YEAR, "Crime\nDrama", isMiss = false),
            TitleGenreRow("speed", MediaKind.VOD.name, NO_YEAR, "Action\nCrime", isMiss = false),
            // A miss carries no genres and must not be mistaken for one.
            TitleGenreRow("some unfindable title", MediaKind.VOD.name, NO_YEAR, null, isMiss = true),
        )
        coEvery { channelDao.countDistinctTitles(SOURCE_ID) } returns 0
        coEvery { channelDao.countDescribedTitles(SOURCE_ID) } returns 0

        val index = repository.genreIndex(SOURCE_ID)

        assertEquals(listOf("Action", "Crime", "Drama"), index.genres)
    }

    /**
     * Coverage is two counts now, not a pass over the catalogue.
     *
     * Which titles are counted is SQLite's business and is proved against real SQLite in
     * `GenreAndScriptQueryTest` — that four qualities of one film are one title, and that a name
     * which cleans away to nothing is in neither half of the fraction. What is left here is the
     * arithmetic, and that opening the search screen no longer reads the catalogue to do it.
     */
    @Test
    @DisplayName("coverage is a fraction of counted titles, not a pass over the catalogue")
    fun `coverage divides the two counts and reads nothing else`() = runTest {
        coEvery { titleMetadataDao.allGenreRows() } returns listOf(
            TitleGenreRow("the matrix", MediaKind.VOD.name, 1999, "Action", isMiss = false),
        )
        coEvery { channelDao.countDistinctTitles(SOURCE_ID) } returns 2
        coEvery { channelDao.countDescribedTitles(SOURCE_ID) } returns 1

        val index = repository.genreIndex(SOURCE_ID)

        assertEquals(50, index.coveragePercent)
        coVerify(exactly = 0) { channelDao.titlesForMetadata(any(), any()) }
    }

    /** A source with nothing worth looking up reports nothing, rather than dividing by zero. */
    @Test
    fun `coverage of an empty catalogue is zero`() = runTest {
        coEvery { titleMetadataDao.allGenreRows() } returns emptyList()
        coEvery { channelDao.countDistinctTitles(SOURCE_ID) } returns 0
        coEvery { channelDao.countDescribedTitles(SOURCE_ID) } returns 0

        assertEquals(0, repository.genreIndex(SOURCE_ID).coveragePercent)
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

    /**
     * A page of results is not quietly shortened by a hidden writing system.
     *
     * The script filter used to run *after* the SQL `LIMIT`, so a term matching forty Arabic
     * titles and ten Latin ones read forty rows, threw thirty-nine of them away and showed one
     * hit for a search that had plenty. It now asks for more than it will keep.
     */
    @Test
    @DisplayName("hiding a script does not shrink a page of results")
    fun `the query overscans when a script is hidden`() = runTest {
        val mixed = (1..20).map { index ->
            val arabic = index % 2 == 0
            row(
                id = index.toLong(),
                name = if (arabic) "مسلسل $index" else "Series $index",
                kind = MediaKind.SERIES,
            )
        }
        coEvery { channelDao.search(any(), SOURCE_ID, any(), "s", any(), any(), any(), any()) } returns mixed

        val results = hidingArabic().search(
            sourceId = SOURCE_ID,
            query = "s",
            options = SearchOptions(limitPerKind = 10),
        )

        // Ten asked for, ten returned, and none of them in the hidden script.
        assertEquals(10, results.series.size)
        assertTrue(results.series.none { it.name.startsWith("مسلسل") })
        // And it asked the database for more than ten, which is the whole mechanism.
        coVerify { channelDao.search(any(), SOURCE_ID, MediaKind.SERIES.name, "s", 20, false, any(), any()) }
    }

    @Test
    @DisplayName("one toggle covers hidden categories and hidden scripts alike")
    fun `including hidden searches hidden categories and stops filtering scripts`() = runTest {
        coEvery { channelDao.search(any(), SOURCE_ID, any(), "a", any(), any(), any(), any()) } returns
            listOf(row(id = 1L, name = "مسلسل الاختيار", kind = MediaKind.SERIES))

        val results = hidingArabic()
            .search(sourceId = SOURCE_ID, query = "a", options = SearchOptions(includeHidden = true))

        assertEquals(listOf("مسلسل الاختيار"), results.series.map { it.name })
        coVerify { channelDao.search(any(), SOURCE_ID, MediaKind.SERIES.name, "a", any(), true, any(), any()) }
    }

    @Test
    @DisplayName("an ordinary search asks the database for what is not hidden")
    fun `hidden categories are excluded unless asked for`() = runTest {
        repository.search(sourceId = SOURCE_ID, query = "a")

        coVerify { channelDao.search(any(), SOURCE_ID, MediaKind.VOD.name, "a", any(), false, any(), any()) }
    }

    /**
     * **The report: "it shows series or movies, not both, and it's random."**
     *
     * It was neither random nor a display problem. `titlesForMetadata` has no `ORDER BY`, so
     * SQLite returns rowid order, and rows are inserted live-then-films-then-series — which puts
     * every film ahead of every series. One shared cap of `limit * 2` was taken from that
     * sequence *before* the split into columns, so past eighty matching films the series column
     * was empty. The fixture below is that catalogue in miniature.
     */
    @Test
    @DisplayName("a genre held by both kinds fills both columns")
    fun `the genre filter caps each kind on its own`() = runTest {
        // A hundred of each, returned in the order SQLite returns them: every film ahead of
        // every series. The cap is per query now, so neither column can starve the other
        // whatever that order is — which is `019`'s fix, kept rather than re-derived.
        coEvery {
            channelDao.searchByGenre(
                any(), SOURCE_ID, MediaKind.VOD.name, "Crime", any(), any(), any(), any(), any(),
            )
        } answers { (1..CROWD).map { row(id = it.toLong(), name = "Film $it", kind = MediaKind.VOD) } }
        coEvery {
            channelDao.searchByGenre(
                any(), SOURCE_ID, MediaKind.SERIES.name, "Crime", any(), any(), any(), any(), any(),
            )
        } answers { (1..CROWD).map { row(id = it.toLong(), name = "Series $it", kind = MediaKind.SERIES) } }

        val results = repository.search(
            sourceId = SOURCE_ID,
            query = "",
            options = SearchOptions(genre = "Crime", limitPerKind = 40),
        )

        assertEquals(40, results.movies.size)
        assertEquals(40, results.series.size)
    }

    @Test
    @DisplayName("advanced search does not pay for a live query it will not show")
    fun `live is not queried when it is switched off`() = runTest {
        repository.search(
            sourceId = SOURCE_ID,
            query = "bbc",
            options = SearchOptions(includeLive = false),
        )

        coVerify(exactly = 0) {
            channelDao.search(any(), any(), MediaKind.LIVE.name, any(), any(), any(), any(), any())
        }
        coVerify { channelDao.search(any(), any(), MediaKind.VOD.name, any(), any(), any(), any(), any()) }
    }

    /** The same repository with Arabic hidden, which is the only difference under test. */
    private fun hidingArabic() = SearchRepository(
        channelDao = channelDao,
        profiles = fakeProfiles(),
        titleMetadataDao = titleMetadataDao,
        metadataRepository = metadataRepository,
        matchDispatcher = Dispatchers.Default,
        hiddenScripts = flowOf(setOf(TitleScript.Arabic)),
    )

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

        /**
         * Enough of each kind that the old shared cap could not reach the second one.
         *
         * The cap was `limit * 2`, so a hundred films ahead of the series is comfortably past it
         * and a smaller fixture would have passed against the bug.
         */
        const val CROWD = 100
    }
}
