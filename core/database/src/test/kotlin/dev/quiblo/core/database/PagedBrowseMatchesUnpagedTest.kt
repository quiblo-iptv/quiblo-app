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

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.quiblo.core.common.SCRIPT_MASK_UNKNOWN
import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.database.dao.ChannelWithFavorite
import dev.quiblo.core.database.entity.CategoryOverrideEntity
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.database.entity.FavoriteEntity
import dev.quiblo.core.database.entity.ProfileEntity
import dev.quiblo.core.database.entity.SourceEntity
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
 * The browse query exists twice, and this is what stops the two drifting apart.
 *
 * Room cannot return both a `Flow<List<…>>` and a `PagingSource` from one declaration, and both
 * shapes are genuinely wanted: a flat grid must not read a catalogue to draw a screenful, and the
 * television's poster grid groups its whole answer into rows and so cannot take a paged list at
 * all. So the predicates are written out twice.
 *
 * A predicate added to one and forgotten in the other is not a build failure and not a crash. It
 * is one app hiding a writing system the other does not, or a favourites filter that works on a
 * phone and not on a television — differences nobody sees until somebody reports the wrong list.
 * Asserting the two answers are the same list, over a fixture that exercises every predicate, is
 * cheaper than remembering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class PagedBrowseMatchesUnpagedTest {

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
    fun `the two queries answer the same, with nothing filtered`() = runTest {
        seed()
        assertSameAnswer()
    }

    @Test
    fun `the two queries answer the same when a category is chosen`() = runTest {
        seed()
        assertSameAnswer(groupTitle = "Drama")
    }

    @Test
    fun `the two queries answer the same for a search term`() = runTest {
        seed()
        assertSameAnswer(query = "Dune")
    }

    @Test
    fun `the two queries answer the same when a writing system is hidden`() = runTest {
        seed()
        assertSameAnswer(hiddenMask = TitleScript.Arabic.bit)
    }

    @Test
    fun `the two queries answer the same for favourites only`() = runTest {
        seed()
        db.favoriteDao().add(
            FavoriteEntity(
                profileId = PROFILE_ID,
                sourceId = SOURCE_ID,
                stableKey = "key-2",
                favoritedAtEpochMillis = 0L,
            ),
        )

        assertSameAnswer(favoritesOnly = 1)
    }

    /**
     * A row with no computed mask reaches both, and reaches them the same way.
     *
     * Both queries deliberately let these through — an unknown mask has every bit set, so
     * filtering on it would hide the whole catalogue — and Kotlin decides afterwards. If only one
     * of them did, a catalogue mid-backfill would show different lists on the two apps.
     */
    @Test
    fun `an uncomputed row reaches both queries`() = runTest {
        seed()
        db.channelDao().insertAll(
            listOf(channel(id = 90, name = "Older row", scriptMask = SCRIPT_MASK_UNKNOWN)),
        )

        assertSameAnswer(hiddenMask = TitleScript.Arabic.bit)
    }

    @Suppress("LongParameterList")
    private suspend fun assertSameAnswer(
        groupTitle: String? = null,
        query: String = "",
        favoritesOnly: Int = 0,
        hiddenMask: Int = 0,
    ) {
        val unpaged = db.channelDao().observeBrowse(
            profileId = PROFILE_ID,
            sourceId = SOURCE_ID,
            kind = "VOD",
            groupTitle = groupTitle,
            query = query,
            favoritesOnly = favoritesOnly,
            hiddenMask = hiddenMask,
            unknownMask = SCRIPT_MASK_UNKNOWN,
        ).first()

        val source = db.channelDao().pagedBrowse(
            profileId = PROFILE_ID,
            sourceId = SOURCE_ID,
            kind = "VOD",
            groupTitle = groupTitle,
            query = query,
            favoritesOnly = favoritesOnly,
            hiddenMask = hiddenMask,
            unknownMask = SCRIPT_MASK_UNKNOWN,
        )
        // One page bigger than the fixture, so the whole answer is in it and the comparison is
        // about the predicates rather than about where a page happens to end.
        val page = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = PAGE, placeholdersEnabled = false),
        )
        val paged = (page as PagingSource.LoadResult.Page<Int, ChannelWithFavorite>).data

        assertEquals(unpaged.map { it.channel.id to it.isFavorite }, paged.map { it.channel.id to it.isFavorite })
    }

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
        db.profileDao().insert(
            ProfileEntity(
                id = PROFILE_ID,
                name = "Someone",
                createdAtEpochMillis = 0L,
            ),
        )
        db.channelDao().insertAll(
            listOf(
                channel(id = 1, name = "Dune (1984)", category = "Drama"),
                channel(id = 2, name = "Dune (2021)", category = "Drama"),
                channel(id = 3, name = "Heat", category = "Crime"),
                channel(id = 4, name = "مسلسل الاختيار", category = "Crime", scriptMask = TitleScript.Arabic.bit),
                channel(id = 5, name = "Hidden shelf", category = "Adult"),
            ),
        )
        db.categoryOverrideDao().upsert(
            CategoryOverrideEntity(
                profileId = PROFILE_ID,
                kind = "VOD",
                originalTitle = "Adult",
                isHidden = true,
            ),
        )
    }

    private fun channel(
        id: Long,
        name: String,
        category: String = "Everything",
        scriptMask: Int = TitleScript.Latin.bit,
    ) = ChannelEntity(
        id = id,
        sourceId = SOURCE_ID,
        name = name,
        streamUrl = "https://example.invalid/$id",
        kind = "VOD",
        groupTitle = category,
        stableKey = "key-$id",
        sortIndex = id.toInt(),
        scriptMask = scriptMask,
    )

    private companion object {
        const val SOURCE_ID = 7L
        const val PROFILE_ID = 1L

        /** Larger than the fixture, so a page boundary is never what the assertion is about. */
        const val PAGE = 50
    }
}
