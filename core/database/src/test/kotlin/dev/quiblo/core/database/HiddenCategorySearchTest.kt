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
import dev.quiblo.core.database.entity.CategoryOverrideEntity
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.database.entity.SourceEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Does hiding a category actually hide it — from a search, and from the genre filter's read?
 *
 * It did not. `CategoryRepository` filtered the *category list*, so a hidden category vanished
 * from the chips and went on answering every search underneath them; the DAO query that would
 * have told anyone which titles were hidden existed and was called from nowhere at all.
 *
 * Run by SQLite rather than reasoned about, for the reason `RecentlyAddedQueryTest` gives: the
 * whole of this behaviour is one correlated subquery, a mocked DAO proves none of it, and a
 * predicate that is nearly right returns a plausible list of the wrong titles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class HiddenCategorySearchTest {

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
    fun `a search does not return titles from a hidden category`() = runTest {
        seed()
        hide(kind = "VOD", category = "Arabic films")

        assertEquals(listOf("Dune shown"), search(kind = "VOD").map { it.channel.name })
    }

    @Test
    fun `the toggle brings the hidden category back`() = runTest {
        seed()
        hide(kind = "VOD", category = "Arabic films")

        assertEquals(
            listOf("Dune shown", "Dune hidden"),
            search(kind = "VOD", includeHidden = true).map { it.channel.name },
        )
    }

    /**
     * Hiding is per kind, and the override table is keyed that way.
     *
     * A provider that calls a live category and a film category the same thing is ordinary —
     * "Sports" is both — and hiding one must not take the other with it. The subquery correlates
     * on `kind` for this, and a subquery that forgot to would pass every other test on this page.
     */
    @Test
    fun `hiding a category of one kind leaves the same name under another alone`() = runTest {
        seed()
        db.channelDao().insertAll(
            listOf(
                channel(id = 10, name = "Dune sports", kind = "LIVE", category = "Arabic films"),
            ),
        )
        hide(kind = "VOD", category = "Arabic films")

        assertEquals(listOf("Dune sports"), search(kind = "LIVE").map { it.channel.name })
    }

    /** An override row that exists but is not hiding anything must not hide anything. */
    @Test
    fun `a renamed but visible category is still searched`() = runTest {
        seed()
        db.categoryOverrideDao().upsert(
            CategoryOverrideEntity(
                kind = "VOD",
                originalTitle = "Arabic films",
                customName = "Films in Arabic",
                isHidden = false,
            ),
        )

        assertEquals(2, search(kind = "VOD").size)
    }

    /** The genre filter reads its own list of titles, and it obeys the same rule. */
    @Test
    fun `the genre filter's title list drops hidden categories too`() = runTest {
        seed()
        hide(kind = "VOD", category = "Arabic films")

        assertEquals(
            listOf("Dune shown"),
            db.channelDao().titlesForMetadata(SOURCE_ID, includeHidden = false).map { it.name },
        )
        assertEquals(
            2,
            db.channelDao().titlesForMetadata(SOURCE_ID, includeHidden = true).size,
        )
    }

    private suspend fun search(kind: String, includeHidden: Boolean = false) =
        db.channelDao().search(
            profileId = 1L,
            sourceId = SOURCE_ID,
            kind = kind,
            query = "Dune",
            limit = 40,
            includeHidden = includeHidden,
            // Nothing hidden by writing system here: this test is about categories, and the
            // two filters are deliberately independent.
            hiddenMask = 0,
            unknownMask = SCRIPT_MASK_UNKNOWN,
        )

    private suspend fun hide(kind: String, category: String) {
        db.categoryOverrideDao().upsert(
            CategoryOverrideEntity(kind = kind, originalTitle = category, isHidden = true),
        )
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
        db.channelDao().insertAll(
            listOf(
                channel(id = 1, name = "Dune shown", kind = "VOD", category = "English films"),
                channel(id = 2, name = "Dune hidden", kind = "VOD", category = "Arabic films"),
            ),
        )
    }

    private fun channel(id: Long, name: String, kind: String, category: String) = ChannelEntity(
        id = id,
        sourceId = SOURCE_ID,
        name = name,
        streamUrl = "https://example.invalid/$id",
        kind = kind,
        groupTitle = category,
        stableKey = "key-$id",
        sortIndex = id.toInt(),
    )

    private companion object {
        const val SOURCE_ID = 7L
    }
}
