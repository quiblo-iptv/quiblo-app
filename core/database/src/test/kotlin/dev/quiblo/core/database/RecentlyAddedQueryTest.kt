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
 * What `observeRecentlyAdded` actually returns, run by SQLite rather than reasoned about.
 *
 * Every claim the Recently Added tab makes is a clause in one query — newest first, films and
 * series only, nothing without a date, capped — and a mocked DAO can prove none of them. This
 * is the same argument [MigrationTest] makes about migrations, and it is the same class of
 * failure: a query that is nearly right shows a plausible list of the wrong titles, which is
 * indistinguishable from a correct one unless somebody knows the catalogue by heart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class RecentlyAddedQueryTest {

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
    fun `returns films and series newest first, and nothing else`() = runTest {
        seedSource()
        db.channelDao().insertAll(
            listOf(
                channel(id = 1, name = "Old film", kind = "VOD", addedAt = 100L),
                channel(id = 2, name = "New series", kind = "SERIES", addedAt = 300L),
                channel(id = 3, name = "Middle film", kind = "VOD", addedAt = 200L),
                // A live channel with a date on it, which no real panel sends. It is here
                // because the kind filter is the clause that stops the tab from becoming a
                // list of television channels the day a panel starts sending one.
                channel(id = 4, name = "A channel", kind = "LIVE", addedAt = 999L),
                // No date: an M3U row, or a panel that omitted the field. Absent rather than
                // last — an undated title is not an old one.
                channel(id = 5, name = "Undated film", kind = "VOD", addedAt = null),
            ),
        )

        val rows = recents(limit = 40)

        assertEquals(
            listOf("New series", "Middle film", "Old film"),
            rows.map { it.channel.name },
        )
    }

    @Test
    fun `keeps only as many as it was asked for, from the newest end`() = runTest {
        seedSource()
        db.channelDao().insertAll(
            (1..5).map { channel(id = it.toLong(), name = "Film $it", kind = "VOD", addedAt = it * 100L) },
        )

        val rows = recents(limit = 2)

        // The cap takes the top of the list, not an arbitrary two of it. A LIMIT applied
        // before the ORDER BY would still return two rows and would still look reasonable.
        assertEquals(listOf("Film 5", "Film 4"), rows.map { it.channel.name })
    }

    @Test
    fun `a source with no dates at all returns nothing`() = runTest {
        seedSource()
        db.channelDao().insertAll(
            listOf(
                channel(id = 1, name = "A film", kind = "VOD", addedAt = null),
                channel(id = 2, name = "A series", kind = "SERIES", addedAt = null),
            ),
        )

        val rows = recents(limit = 40)

        // Empty from this query, and `observeHasAddedDates` is what tells the layer above to
        // ask the other one rather than to believe this emptiness.
        assertEquals(emptyList<String>(), rows.map { it.channel.name })
        assertEquals(false, db.channelDao().observeHasAddedDates(SOURCE_ID).first())
    }

    @Test
    fun `the window excludes anything older than it, however new the catalogue is otherwise`() = runTest {
        seedSource()
        db.channelDao().insertAll(
            listOf(
                channel(id = 1, name = "Last week", kind = "VOD", addedAt = NOW - 7L * DAY),
                channel(id = 2, name = "Last year", kind = "VOD", addedAt = NOW - 400L * DAY),
                channel(id = 3, name = "Yesterday", kind = "SERIES", addedAt = NOW - DAY),
            ),
        )

        val rows = recents(limit = 40, since = NOW - 30L * DAY)

        // A service that added forty films last March is not a service with forty new films.
        assertEquals(listOf("Yesterday", "Last week"), rows.map { it.channel.name })
    }

    @Test
    fun `one dated row is enough to make a source a dated source`() = runTest {
        seedSource()
        db.channelDao().insertAll(
            listOf(
                channel(id = 1, name = "Undated", kind = "VOD", addedAt = null),
                channel(id = 2, name = "Dated", kind = "VOD", addedAt = NOW),
            ),
        )

        // Panels omit the field on individual rows. That is not the same as a playlist which
        // carries no dates at all, and only the second should fall back to list order.
        assertEquals(true, db.channelDao().observeHasAddedDates(SOURCE_ID).first())
    }

    @Test
    fun `the fallback takes the end of the provider's own list, one kind at a time`() = runTest {
        seedSource()
        db.channelDao().insertAll(
            (1..4).map { channel(id = it.toLong(), name = "Film $it", kind = "VOD", addedAt = null) } +
                (5..8).map { channel(id = it.toLong(), name = "Series $it", kind = "SERIES", addedAt = null) },
        )

        val films = db.channelDao()
            .observeLastInListOrder(profileId = 1L, sourceId = SOURCE_ID, kind = "VOD", limit = 2)
            .first()
        val series = db.channelDao()
            .observeLastInListOrder(profileId = 1L, sourceId = SOURCE_ID, kind = "SERIES", limit = 2)
            .first()

        // The end of the list first, and per kind — a single query across both would return
        // four series and no films on this catalogue, which is every catalogue that lists its
        // films first.
        assertEquals(listOf("Film 4", "Film 3"), films.map { it.channel.name })
        assertEquals(listOf("Series 8", "Series 7"), series.map { it.channel.name })
    }

    private suspend fun recents(limit: Int, since: Long = 0L) = db.channelDao()
        .observeRecentlyAdded(
            profileId = 1L,
            sourceId = SOURCE_ID,
            sinceEpochMillis = since,
            limit = limit,
            mergeDuplicates = 0,
        )
        .first()

    private suspend fun seedSource() {
        db.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                name = "A panel",
                kind = "XTREAM",
                url = "https://example.invalid",
                createdAtEpochMillis = 0L,
            ),
        )
    }

    private fun channel(id: Long, name: String, kind: String, addedAt: Long?) = ChannelEntity(
        id = id,
        sourceId = SOURCE_ID,
        name = name,
        streamUrl = "https://example.invalid/$id",
        kind = kind,
        groupTitle = "Everything",
        stableKey = "key-$id",
        sortIndex = id.toInt(),
        addedAtEpochMillis = addedAt,
    )

    private companion object {
        const val SOURCE_ID = 1L
        const val DAY = 24L * 60 * 60 * 1000
        const val NOW = 1_700_000_000_000L
    }
}
