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
import dev.quiblo.core.database.entity.FavoriteEntity
import dev.quiblo.core.database.entity.ProfileEntity
import dev.quiblo.core.database.entity.ResumePositionEntity
import dev.quiblo.core.database.entity.SourceEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a scheduled sync does to a catalogue somebody is not watching.
 *
 * The refresh a person presses replaces the whole source, which gives every channel a new id. That
 * is tolerable while they are looking at it. Doing it every four days, unattended, is not: the
 * arrival dates the recently-added row is built from are destroyed with the ids — and on an M3U
 * playlist the provider supplies no date at all, so the only date there has ever been is when
 * *we* first saw the title. A rebuild would declare the whole catalogue new, every four days,
 * forever.
 *
 * Run by SQLite rather than reasoned about, for the reason [MigrationTest] gives: a merge that is
 * nearly right leaves a plausible catalogue of subtly wrong rows, which nobody can tell from a
 * correct one by looking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class CatalogueMergeTest {

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
    fun `a title the provider still carries keeps its id`() = runTest {
        seed()
        db.channelDao().mergeForSource(SOURCE_ID, listOf(arriving("key-1", name = "Dune (2021) 4K")), NOW)

        val row = db.channelDao().findByStableKey(SOURCE_ID, "key-1")

        assertEquals(1L, row?.id)
        // The mutable half is rewritten: this is still a sync.
        assertEquals("Dune (2021) 4K", row?.name)
    }

    @Test
    fun `and the date it first appeared, when the provider supplies none`() = runTest {
        seed()
        db.channelDao().mergeForSource(SOURCE_ID, listOf(arriving("key-1", addedAt = null)), NOW)

        assertEquals(FIRST_SEEN, db.channelDao().findByStableKey(SOURCE_ID, "key-1")?.addedAtEpochMillis)
    }

    /**
     * The whole reason an M3U playlist has a recently-added row at all.
     *
     * The provider dates nothing, so a title that has never been seen before is dated now — and a
     * title that was here last time keeps the date it was first seen. Get this wrong in either
     * direction and the row is either always empty or always the entire catalogue.
     */
    @Test
    fun `a title that has just arrived is dated now`() = runTest {
        seed()
        db.channelDao().mergeForSource(
            SOURCE_ID,
            listOf(arriving("key-1", addedAt = null), arriving("key-new", addedAt = null)),
            NOW,
        )

        assertEquals(FIRST_SEEN, db.channelDao().findByStableKey(SOURCE_ID, "key-1")?.addedAtEpochMillis)
        assertEquals(NOW, db.channelDao().findByStableKey(SOURCE_ID, "key-new")?.addedAtEpochMillis)
    }

    @Test
    fun `the provider's own date wins over both`() = runTest {
        seed()
        db.channelDao().mergeForSource(SOURCE_ID, listOf(arriving("key-1", addedAt = PROVIDER_DATE)), NOW)

        assertEquals(PROVIDER_DATE, db.channelDao().findByStableKey(SOURCE_ID, "key-1")?.addedAtEpochMillis)
    }

    @Test
    fun `a title the provider has dropped is removed`() = runTest {
        seed()
        db.channelDao().mergeForSource(SOURCE_ID, listOf(arriving("key-1")), NOW)

        // A catalogue that only ever grows is not a catalogue: a viewer would keep meeting titles
        // their provider stopped carrying, and pressing one plays nothing.
        assertNull(db.channelDao().findByStableKey(SOURCE_ID, "key-2"))
        assertNotNull(db.channelDao().findByStableKey(SOURCE_ID, "key-1"))
    }

    /**
     * And the promise every refresh has always made, kept by a path that never deletes.
     *
     * Favourites and resume points are keyed by the provider's stable identity precisely so they
     * survive a rebuild. This asserts it for the merge as well, because "it survives the other
     * path" is not evidence about this one.
     */
    @Test
    fun `a favourite and a resume point survive it`() = runTest {
        seed()
        db.profileDao().insert(ProfileEntity(id = PROFILE_ID, name = "A viewer", createdAtEpochMillis = 0))
        db.favoriteDao().add(
            FavoriteEntity(
                profileId = PROFILE_ID,
                sourceId = SOURCE_ID,
                stableKey = "key-1",
                favoritedAtEpochMillis = 5,
            ),
        )
        db.resumePositionDao().upsert(
            ResumePositionEntity(
                stableKey = "key-1",
                profileId = PROFILE_ID,
                positionMillis = 90_000,
                updatedAtEpochMillis = 5,
                sourceId = SOURCE_ID,
                kind = "VOD",
                title = "Dune",
            ),
        )

        db.channelDao().mergeForSource(SOURCE_ID, listOf(arriving("key-1")), NOW)

        assertEquals(true, db.favoriteDao().isFavorite(PROFILE_ID, SOURCE_ID, "key-1"))
        assertEquals(90_000L, db.resumePositionDao().positionFor(PROFILE_ID, "key-1"))
    }

    @Test
    fun `a first sync of an empty source inserts everything`() = runTest {
        db.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                name = "A panel",
                kind = "M3U",
                url = "http://host.invalid",
                createdAtEpochMillis = 0,
            ),
        )

        db.channelDao().mergeForSource(SOURCE_ID, (1..3).map { arriving("key-$it", addedAt = null) }, NOW)

        assertEquals(3, db.channelDao().countForSource(SOURCE_ID))
    }

    /**
     * `FEAT-031`: an unchanged row is not rewritten.
     *
     * `insertAll` replaces on conflict, so writing a row back identical is a real write — the page
     * is dirtied and every index on `channels` is updated, and there are five. On a large account
     * almost every row is identical almost every time, so a sync that added two films used to cost
     * a rewrite of the whole table.
     *
     * Asserted through SQLite's own `total_changes()`, which counts rows actually inserted,
     * updated or deleted on this connection. Comparing the stored row before and after would
     * prove nothing: a row written back identically *is* identical afterwards, which is exactly
     * the case this is meant to catch.
     */
    @Test
    fun `a row that has not changed is not written`() = runTest {
        seed()
        val before = totalChanges()

        db.channelDao().mergeForSource(SOURCE_ID, listOf(stored(id = 1, stableKey = "key-1")), NOW)

        // One delete, for key-2, which this sync no longer carries. Nothing for key-1.
        assertEquals(1L, totalChanges() - before)
    }

    @Test
    fun `a row that has changed is still written`() = runTest {
        seed()
        val before = totalChanges()

        db.channelDao().mergeForSource(
            SOURCE_ID,
            listOf(stored(id = 1, stableKey = "key-1").copy(name = "A new name"), stored(id = 2, stableKey = "key-2")),
            NOW,
        )

        assertEquals("A new name", db.channelDao().findByStableKey(SOURCE_ID, "key-1")?.name)
        assertEquals(1L, totalChanges() - before)
    }

    /**
     * Rows inserted, updated or deleted on this connection since it was opened.
     *
     * Read straight off the support database rather than through `RoomDatabase.query`, which
     * refuses to run on the test's own thread.
     */
    private fun totalChanges(): Long =
        db.openHelper.writableDatabase.query("SELECT total_changes()").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private suspend fun seed() {
        db.sourceDao().insert(
            SourceEntity(
                id = SOURCE_ID,
                name = "A panel",
                kind = "M3U",
                url = "http://host.invalid",
                createdAtEpochMillis = 0,
            ),
        )
        db.channelDao().insertAll(
            listOf(
                stored(id = 1, stableKey = "key-1"),
                stored(id = 2, stableKey = "key-2"),
            ),
        )
    }

    private fun stored(id: Long, stableKey: String) = ChannelEntity(
        id = id,
        sourceId = SOURCE_ID,
        name = "Dune",
        streamUrl = "http://host.invalid/$stableKey",
        kind = "VOD",
        groupTitle = "Films",
        stableKey = stableKey,
        sortIndex = id.toInt(),
        addedAtEpochMillis = FIRST_SEEN,
    )

    /** A row as a parser hands it over: no id, and whatever date the provider sent. */
    private fun arriving(
        stableKey: String,
        name: String = "Dune",
        addedAt: Long? = null,
    ) = ChannelEntity(
        sourceId = SOURCE_ID,
        name = name,
        streamUrl = "http://host.invalid/$stableKey",
        kind = "VOD",
        groupTitle = "Films",
        stableKey = stableKey,
        sortIndex = 0,
        addedAtEpochMillis = addedAt,
    )

    private companion object {
        const val SOURCE_ID = 1L
        const val PROFILE_ID = 7L
        const val FIRST_SEEN = 1_000L
        const val PROVIDER_DATE = 2_000L
        const val NOW = 9_000L
    }
}
