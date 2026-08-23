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
import dev.quiblo.core.database.entity.ChannelEntity
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
 * One film listed four times, shown once — and the two ways that can go wrong.
 *
 * A panel that carries a film in SD, HD, FHD and 4K sends four rows with one identity between
 * them. The merge predicate keeps the provider's own first listing of each identity, which is a
 * correlated subquery: a mocked DAO proves none of it, and a predicate that is nearly right
 * returns a plausible list of the wrong rows.
 *
 * The second failure is the expensive one. A row whose title cleans away to nothing carries the
 * blank identity, a hundred unrelated channels share it, and a merge that grouped on it would
 * collapse the lot into one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class MergedTitlesQueryTest {

    private lateinit var db: QuibloDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            QuibloDatabase::class.java,
        ).build()
    }

    @After
    fun close() = db.close()

    @Test
    fun `merging off shows every listing the provider sent`() = runTest {
        seed()

        assertEquals(
            listOf("Dune (2021) SD", "Dune (2021) HD", "Dune (2021) 4K", "Heat (1995)", "؟؟؟", "Unnameable"),
            browse(mergeDuplicates = 0).map { it.channel.name },
        )
    }

    @Test
    fun `merging on keeps the provider's first listing of each title`() = runTest {
        seed()

        assertEquals(
            listOf("Dune (2021) SD", "Heat (1995)", "؟؟؟", "Unnameable"),
            browse(mergeDuplicates = 1).map { it.channel.name },
        )
    }

    /**
     * A film listed once is untouched, and so is a film of the same name from another year.
     *
     * The identity is title *and* year, which is what stops the 1984 Dune and the 2021 one being
     * one film — `014`'s defect, wearing the merge setting.
     */
    @Test
    fun `two films with one title and different years are not one film`() = runTest {
        seed()
        db.channelDao().insertAll(
            listOf(channel(id = 10, name = "Dune (1984)", searchTitle = "dune", identityYear = 1984)),
        )

        assertEquals(1, browse(mergeDuplicates = 1).count { it.channel.name.startsWith("Dune (1984)") })
    }

    /** The blank identity is shared by every unnameable row, and nothing may group on it. */
    @Test
    fun `rows with no computed identity are never merged together`() = runTest {
        seed()

        val merged = browse(mergeDuplicates = 1).map { it.channel.name }
        assertEquals(listOf("؟؟؟", "Unnameable"), merged.filter { it == "؟؟؟" || it == "Unnameable" })
    }

    /** The category counts follow the list, or a shelf says four over a list showing one. */
    @Test
    fun `the category count counts what the list will show`() = runTest {
        seed()

        assertEquals(
            listOf(6),
            db.channelDao().observeCategoriesByKind(SOURCE_ID, "VOD", mergeDuplicates = 0).first()
                .map { it.itemCount },
        )
        assertEquals(
            listOf(4),
            db.channelDao().observeCategoriesByKind(SOURCE_ID, "VOD", mergeDuplicates = 1).first()
                .map { it.itemCount },
        )
    }

    private suspend fun browse(mergeDuplicates: Int) = db.channelDao().observeBrowse(
        profileId = 1L,
        sourceId = SOURCE_ID,
        kind = "VOD",
        groupTitle = null,
        query = "",
        favoritesOnly = 0,
        mergeDuplicates = mergeDuplicates,
        hiddenMask = 0,
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
                channel(id = 1, name = "Dune (2021) SD", searchTitle = "dune", identityYear = 2021),
                channel(id = 2, name = "Dune (2021) HD", searchTitle = "dune", identityYear = 2021),
                channel(id = 3, name = "Dune (2021) 4K", searchTitle = "dune", identityYear = 2021),
                channel(id = 4, name = "Heat (1995)", searchTitle = "heat", identityYear = 1995),
                // Two rows that cleaned away to nothing. They share the blank identity and must
                // still be two rows.
                channel(id = 5, name = "؟؟؟", searchTitle = ""),
                channel(id = 6, name = "Unnameable", searchTitle = ""),
            ),
        )
    }

    private fun channel(
        id: Long,
        name: String,
        searchTitle: String,
        identityYear: Int = 0,
    ) = ChannelEntity(
        id = id,
        sourceId = SOURCE_ID,
        name = name,
        streamUrl = "https://example.invalid/$id",
        kind = "VOD",
        groupTitle = "Everything",
        stableKey = "key-$id",
        sortIndex = id.toInt(),
        searchTitle = searchTitle,
        identityYear = identityYear,
    )

    private companion object {
        const val SOURCE_ID = 11L
    }
}
