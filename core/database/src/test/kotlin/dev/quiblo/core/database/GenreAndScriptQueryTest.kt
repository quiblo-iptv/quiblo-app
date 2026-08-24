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

package dev.quiblo.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.quiblo.core.common.SCRIPT_MASK_UNKNOWN
import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.database.entity.TitleMetadataEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two predicates `021` moved out of Kotlin and into SQL.
 *
 * Run by SQLite rather than reasoned about, for the reason `HiddenCategorySearchTest` gives: a
 * mocked DAO proves nothing about a join, and a predicate that is *nearly* right returns a
 * plausible list of the wrong titles — which is exactly the shape of the defect that started this
 * round.
 *
 * What is being replaced is worth stating, because it is the whole reason the work happened.
 * The genre filter used to read every film and series a source carried, clean each title in
 * Kotlin to a cache key — eight regex passes each — and intersect that with the cached genres.
 * Fifty thousand rows on this project's own provider, four hundred thousand regex applications
 * per press of a genre chip, nothing kept between presses. It was reported as advanced search
 * hanging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class GenreAndScriptQueryTest {

    private lateinit var db: QuibloDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            QuibloDatabase::class.java,
        ).build()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun `a genre search returns the titles the cache files under it`() = runTest {
        seed()

        assertEquals(
            listOf("Dune (2021)"),
            genre("Science Fiction", kind = "VOD").map { it.channel.name },
        )
    }

    /**
     * A genre whose name is contained in another's must not match it.
     *
     * `genres` is one newline-separated string, so a plain `LIKE '%Drama%'` would return
     * everything filed under "Crime Drama" — and on a real cache that is most of the catalogue.
     * The query wraps both sides in newlines for exactly this, and this test is what stops that
     * detail being tidied away.
     */
    @Test
    fun `a genre does not match another genre that contains its name`() = runTest {
        seed()

        assertEquals(
            listOf("Fargo"),
            genre("Crime Drama", kind = "SERIES").map { it.channel.name },
        )
        assertEquals(emptyList<String>(), genre("Drama", kind = "SERIES").map { it.channel.name })
    }

    /** Case is the provider's business, not the viewer's: SQLite's `LIKE` is ASCII-insensitive. */
    @Test
    fun `a genre matches whatever case it is asked in`() = runTest {
        seed()

        assertEquals(listOf("Dune (2021)"), genre("science fiction", kind = "VOD").map { it.channel.name })
    }

    /**
     * The year is half the key, and joining without it is `014`'s defect wearing SQL.
     *
     * Two films called Dune are two films. A join on the cleaned title alone would file the 1984
     * one under the 2021 one's genres, which is how one cache row came to hold both films and
     * show the winner's poster for a fortnight at a time.
     */
    @Test
    fun `two films with one title are told apart by their year`() = runTest {
        seed()

        assertEquals(
            listOf("Dune (1984)"),
            genre("Adventure", kind = "VOD").map { it.channel.name },
        )
    }

    /**
     * A year narrows the same join a genre does, and either works without the other.
     *
     * Two films called Dune, one from each year, is the sharpest case this catalogue has: a year
     * filter that matched on the cleaned title alone would return both.
     */
    @Test
    fun `a year returns only the titles the service dates to it`() = runTest {
        seed()

        assertEquals(
            listOf("Dune (2021)"),
            genre(genre = "", kind = "VOD", year = 2021).map { it.channel.name },
        )
    }

    /**
     * The provider's year answers when the service gave none.
     *
     * A cache row written before `releaseYear` existed still has the year read out of the
     * provider's own title, and dropping those titles out of the filter would make a catalogue
     * look thinner the older its cache is.
     */
    @Test
    fun `a year falls back to the year in the provider's title`() = runTest {
        seed()

        assertEquals(
            listOf("Dune (1984)"),
            genre(genre = "", kind = "VOD", year = 1984).map { it.channel.name },
        )
    }

    /** A year and a genre narrow together, rather than one replacing the other. */
    @Test
    fun `a year and a genre both apply`() = runTest {
        seed()

        assertEquals(emptyList<String>(), genre("Adventure", kind = "VOD", year = 2021).map { it.channel.name })
        assertEquals(
            listOf("Dune (1984)"),
            genre("Adventure", kind = "VOD", year = 1984).map { it.channel.name },
        )
    }

    /** A cached miss is an answer about nothing, and must not put a title in a genre. */
    @Test
    fun `a cached miss files nothing under any genre`() = runTest {
        seed()

        assertEquals(emptyList<String>(), genre("Horror", kind = "VOD").map { it.channel.name })
    }

    /**
     * A blank identity is shared by every title that cleaned away to nothing.
     *
     * Joining on it would file a hundred junk rows under whatever the one blank cache row
     * happens to carry, which is the loudest possible wrong answer.
     */
    @Test
    fun `titles with no computed identity are never joined`() = runTest {
        seed()

        assertEquals(emptyList<String>(), genre("Mystery", kind = "VOD").map { it.channel.name })
    }

    @Test
    fun `hiding a writing system drops its titles from browse`() = runTest {
        seed()

        assertEquals(
            listOf("Dune (1984)", "Dune (2021)", "Cached miss", "No identity"),
            browse(hiddenMask = 0).map { it.channel.name },
        )
        assertEquals(
            listOf("Dune (1984)", "Dune (2021)", "Cached miss", "No identity"),
            browse(hiddenMask = TitleScript.Arabic.bit).map { it.channel.name },
        )
        assertEquals(emptyList<String>(), browse(hiddenMask = TitleScript.Latin.bit).map { it.channel.name })
    }

    /**
     * A row written before schema 19 is passed through, not guessed about.
     *
     * Its mask has every bit set, so a query filtering on it alone would hide the entire
     * catalogue for as long as `CatalogueIdentityBackfill` was still working — and an empty mask
     * would hide none of it. Neither is what the viewer asked for, so the query lets these rows
     * out and Kotlin decides, exactly as it did before the column existed.
     */
    @Test
    fun `a row with no computed mask survives the query and is left to Kotlin`() = runTest {
        seed()
        db.channelDao().insertAll(
            listOf(channel(id = 90, name = "Older row", kind = "VOD", scriptMask = SCRIPT_MASK_UNKNOWN)),
        )

        assertEquals(
            listOf("Older row"),
            browse(hiddenMask = TitleScript.Latin.bit).map { it.channel.name },
        )
    }

    @Test
    fun `the backfill finds only the rows that have no mask`() = runTest {
        seed()
        db.channelDao().insertAll(
            listOf(channel(id = 90, name = "Older row", kind = "VOD", scriptMask = SCRIPT_MASK_UNKNOWN)),
        )

        assertEquals(1, db.channelDao().countWithoutIdentity(SCRIPT_MASK_UNKNOWN))
        assertEquals(
            listOf("Older row"),
            db.channelDao().titlesWithoutIdentity(SCRIPT_MASK_UNKNOWN, limit = 500).map { it.name },
        )

        db.channelDao().setIdentity(id = 90, searchTitle = "older row", identityYear = 0, scriptMask = 1)

        assertEquals(0, db.channelDao().countWithoutIdentity(SCRIPT_MASK_UNKNOWN))
    }

    /** Coverage counts distinct keys, so four qualities of one film are one title to look up. */
    @Test
    fun `coverage counts titles rather than rows`() = runTest {
        seed()
        db.channelDao().insertAll(
            listOf(
                channel(id = 91, name = "Dune (2021) 4K", kind = "VOD", searchTitle = "dune", identityYear = 2021),
                channel(id = 92, name = "Dune (2021) SD", kind = "VOD", searchTitle = "dune", identityYear = 2021),
            ),
        )

        // dune/2021, dune/1984, cached miss and fargo. The blank identity is in neither half.
        assertEquals(4, db.channelDao().countDistinctTitles(SOURCE_ID))
        assertEquals(4, db.channelDao().countDescribedTitles(SOURCE_ID))
    }

    /**
     * The television's poster grid reads the top of each category, not the whole kind.
     *
     * It used to read every row of a kind — thirty thousand on a real account — and group the
     * lot in Kotlin, to draw rows about forty tiles wide. The window function caps each category
     * before any of it leaves SQLite.
     */
    @Test
    fun `each category is capped, and the cap is per category rather than shared`() = runTest {
        seed()
        db.channelDao().insertAll(
            (100..120L).map { channel(id = it, name = "Action $it", kind = "VOD", category = "Action") } +
                (200..220L).map { channel(id = it, name = "Drama $it", kind = "VOD", category = "Drama") },
        )

        val rows = categoryRows(perCategory = 5)

        // Five from each of the two crowded categories, and everything from the small one —
        // a cap per category cannot starve a category, whatever order SQLite returns rows in.
        assertEquals(5, rows.count { it.channel.groupTitle == "Action" })
        assertEquals(5, rows.count { it.channel.groupTitle == "Drama" })
        assertEquals(4, rows.count { it.channel.groupTitle == "Everything" })
    }

    /**
     * The rows come back in the provider's order, not grouped by category name.
     *
     * `ROW_NUMBER` needs its own `PARTITION BY`, and leaving the result in that order would hand
     * the screen its categories alphabetically — where the category *order* is the provider's and
     * is read from `observeCategoriesByKind`. The outer `ORDER BY` puts it back.
     */
    @Test
    fun `category rows keep the provider's own ordering`() = runTest {
        seed()

        assertEquals(
            categoryRows(perCategory = 40).map { it.channel.id },
            categoryRows(perCategory = 40).map { it.channel.id }.sorted(),
        )
    }

    @Test
    fun `a hidden writing system is not in the category rows either`() = runTest {
        seed()
        db.channelDao().insertAll(
            listOf(
                channel(
                    id = 50,
                    name = "مسلسل",
                    kind = "VOD",
                    scriptMask = TitleScript.Arabic.bit,
                ),
            ),
        )

        assertEquals(5, categoryRows(perCategory = 40, hiddenMask = 0).size)
        assertEquals(4, categoryRows(perCategory = 40, hiddenMask = TitleScript.Arabic.bit).size)
    }

    private suspend fun categoryRows(perCategory: Int, hiddenMask: Int = 0, mergeDuplicates: Int = 0) =
        db.channelDao().observeCategoryRows(
            profileId = 1L,
            sourceId = SOURCE_ID,
            kind = "VOD",
            query = "",
            perCategory = perCategory,
            mergeDuplicates = mergeDuplicates,
            hiddenMask = hiddenMask,
            unknownMask = SCRIPT_MASK_UNKNOWN,
        ).first()

    private suspend fun genre(genre: String, kind: String, year: Int = 0) = db.channelDao().searchByMetadata(
        profileId = 1L,
        sourceId = SOURCE_ID,
        kind = kind,
        genre = genre,
        year = year,
        query = "",
        limit = 40,
        includeHidden = true,
        mergeDuplicates = 0,
        hiddenMask = 0,
        unknownMask = SCRIPT_MASK_UNKNOWN,
    )

    private suspend fun browse(hiddenMask: Int, mergeDuplicates: Int = 0) = db.channelDao().observeBrowse(
        profileId = 1L,
        sourceId = SOURCE_ID,
        kind = "VOD",
        groupTitle = null,
        query = "",
        favoritesOnly = 0,
        includeHiddenCategories = 0,
        mergeDuplicates = mergeDuplicates,
        hiddenMask = hiddenMask,
        unknownMask = SCRIPT_MASK_UNKNOWN,
    ).first()

    private suspend fun seed() {
        db.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                name = "A panel",
                kind = "XTREAM",
                url = "https://example.invalid",
                createdAtEpochMillis = 0L,
            ),
        )
        db.channelDao().insertAll(
            listOf(
                channel(id = 1, name = "Dune (1984)", kind = "VOD", searchTitle = "dune", identityYear = 1984),
                channel(id = 2, name = "Dune (2021)", kind = "VOD", searchTitle = "dune", identityYear = 2021),
                channel(id = 3, name = "Cached miss", kind = "VOD", searchTitle = "cached miss"),
                // Cleaned away to nothing, like a bare language tag. Shares its key with every
                // other such row, which is why nothing may join on it.
                channel(id = 4, name = "No identity", kind = "VOD", searchTitle = ""),
                channel(id = 5, name = "Fargo", kind = "SERIES", searchTitle = "fargo"),
            ),
        )
        listOf(
            metadata(
                searchTitle = "dune",
                kind = "VOD",
                year = 2021,
                releaseYear = 2021,
                genres = "Science Fiction\nDrama",
            ),
            // No `releaseYear`: an older cache row, from before the service was asked for one.
            metadata(searchTitle = "dune", kind = "VOD", year = 1984, genres = "Adventure"),
            metadata(searchTitle = "cached miss", kind = "VOD", genres = "Horror", isMiss = true),
            // The blank key, carrying a genre nothing may be filed under by accident.
            metadata(searchTitle = "", kind = "VOD", genres = "Mystery"),
            metadata(searchTitle = "fargo", kind = "SERIES", genres = "Crime Drama"),
        ).forEach { db.titleMetadataDao().upsert(it) }
    }

    @Suppress("LongParameterList")
    private fun channel(
        id: Long,
        name: String,
        kind: String,
        searchTitle: String = "",
        identityYear: Int = 0,
        scriptMask: Int = TitleScript.Latin.bit,
        category: String = "Everything",
    ) = ChannelEntity(
        id = id,
        sourceId = SOURCE_ID,
        name = name,
        streamUrl = "https://example.invalid/$id",
        kind = kind,
        groupTitle = category,
        stableKey = "key-$id",
        sortIndex = id.toInt(),
        searchTitle = searchTitle,
        identityYear = identityYear,
        scriptMask = scriptMask,
    )

    @Suppress("LongParameterList")
    private fun metadata(
        searchTitle: String,
        kind: String,
        year: Int = 0,
        releaseYear: Int? = null,
        genres: String,
        isMiss: Boolean = false,
    ) = TitleMetadataEntity(
        searchTitle = searchTitle,
        kind = kind,
        year = year,
        releaseYear = releaseYear,
        genres = genres,
        isMiss = isMiss,
        fetchedAtEpochMillis = 0L,
    )

    private companion object {
        const val SOURCE_ID = 7L
    }
}
