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
import dev.quiblo.core.database.dao.ChannelWithFavorite
import dev.quiblo.core.database.dao.FavoriteDao
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.model.MediaKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

/**
 * The guard on the ANR reported as #010.
 *
 * A Flow operator runs in its *collector's* context, so `observeBrowse`'s mapping used to
 * happen wherever it was collected — and every collector in the app is a
 * `stateIn(viewModelScope, …)`, which is the main thread. At 67,000 channels, allocating a
 * domain object per row there froze the app.
 *
 * These tests pin the *thread* rather than measuring elapsed time. A timing assertion would
 * pass on a fast machine with the bug fully reintroduced; the thread assertion fails the
 * moment the `flowOn` is removed, which is the only regression worth catching here.
 */
class ChannelRepositoryTest {

    private val channelDao: ChannelDao = mockk(relaxed = true)
    private val favoriteDao: FavoriteDao = mockk(relaxed = true)

    /** One thread with a name of its own, so work can be caught happening on it. */
    private val mappingExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, MAPPING_THREAD_NAME)
    }
    private val mappingDispatcher = mappingExecutor.asCoroutineDispatcher()

    private val repository = ChannelRepository(
        channelDao = channelDao,
        favoriteDao = favoriteDao,
        profiles = fakeProfiles(),
        browseDispatcher = mappingDispatcher,
    )

    @AfterEach
    fun tearDown() {
        mappingExecutor.shutdownNow()
    }

    @Test
    fun `maps browse rows away from the collecting thread`() = runTest {
        // `flowOn` moves everything upstream of it, so the thread the DAO's flow is
        // collected on is the thread the mapping runs on. Recording it here is what makes
        // the claim testable at all — the mapped value itself carries no evidence of where
        // it was made.
        var upstreamThread = ""
        every { channelDao.observeBrowse(any(), any(), any(), any(), any(), any()) } returns flow {
            upstreamThread = Thread.currentThread().name
            emit(listOf(row(id = 1L, name = "BBC One")))
        }

        val items = withContext(COLLECTOR) {
            repository.observeBrowse(SOURCE_ID, MediaKind.LIVE).first()
        }

        assertRanOnMappingThread(upstreamThread)
        assertEquals("BBC One", items.single().name)
    }

    @Test
    fun `maps favourites away from the collecting thread too`() = runTest {
        // Favourites is the other list that can hold every row a source has, and it was
        // missing the same guard. Asserted separately so one cannot be fixed and the other
        // quietly left behind.
        var upstreamThread = ""
        every { channelDao.observeFavorites(any(), any(), any()) } returns flow {
            upstreamThread = Thread.currentThread().name
            emit(listOf(row(id = 2L, name = "ITV")))
        }

        val items = withContext(COLLECTOR) {
            repository.observeFavorites(SOURCE_ID).first()
        }

        assertRanOnMappingThread(upstreamThread)
        assertEquals("ITV", items.single().name)
        assertEquals(true, items.single().isFavorite)
    }

    /**
     * Asserts the work happened on the mapping thread, tolerating the coroutine suffix.
     *
     * Coroutine debug mode renames the running thread to `<name> @coroutine#N`, so an exact
     * comparison passes or fails depending on whether debug mode is on rather than on
     * whether the code is correct.
     */
    private fun assertRanOnMappingThread(threadName: String) {
        assertTrue(
            threadName.startsWith(MAPPING_THREAD_NAME),
            "expected work on $MAPPING_THREAD_NAME but it ran on $threadName",
        )
    }

    @Test
    fun `carries the favourite flag through the mapping`() = runTest {
        every { channelDao.observeBrowse(any(), any(), any(), any(), any(), any()) } returns flow {
            emit(
                listOf(
                    row(id = 1L, name = "BBC One", isFavorite = true),
                    row(id = 2L, name = "BBC Two", isFavorite = false),
                ),
            )
        }

        val items = repository.observeBrowse(SOURCE_ID, MediaKind.LIVE).first()

        // Moving the mapping to another thread must not change what it produces. The join
        // is done in SQL and read out here, and this is the assertion that the `flowOn`
        // did not disturb it.
        assertEquals(listOf(true, false), items.map { it.isFavorite })
    }

    @Test
    fun `a dated source is asked for its dates, and nothing else`() = runTest {
        every { channelDao.observeHasAddedDates(SOURCE_ID) } returns flowOf(true)
        every { channelDao.observeRecentlyAdded(any(), any(), any(), any()) } returns
            flowOf(listOf(row(id = 1L, name = "New film")))

        val feed = repository.observeRecentlyAdded(SOURCE_ID, limit = 40, sinceEpochMillis = 100L).first()

        assertEquals(listOf("New film"), feed.items.map { it.name })
        assertTrue(feed.orderedByDate, "a dated source produced a row that does not claim its dates")
        verify(exactly = 0) { channelDao.observeLastInListOrder(any(), any(), any(), any()) }
    }

    @Test
    fun `an undated source falls back to the end of its own list, both kinds interleaved`() = runTest {
        every { channelDao.observeHasAddedDates(SOURCE_ID) } returns flowOf(false)
        every { channelDao.observeLastInListOrder(any(), any(), MediaKind.VOD.name, any()) } returns
            flowOf(listOf(row(id = 1L, name = "Film A"), row(id = 2L, name = "Film B")))
        every { channelDao.observeLastInListOrder(any(), any(), MediaKind.SERIES.name, any()) } returns
            flowOf(listOf(row(id = 3L, name = "Series A")))

        val feed = repository.observeRecentlyAdded(SOURCE_ID, limit = 40, sinceEpochMillis = 100L).first()

        // Interleaved, so a playlist listing every film before every series does not fill the
        // whole cap with films. And `orderedByDate` is false, which is what stops the screen
        // above from calling list order a date.
        assertEquals(listOf("Film A", "Series A", "Film B"), feed.items.map { it.name })
        assertFalse(feed.orderedByDate, "list order was presented as a date order")
    }

    private fun row(id: Long, name: String, isFavorite: Boolean = true) = ChannelWithFavorite(
        channel = ChannelEntity(
            id = id,
            sourceId = SOURCE_ID,
            name = name,
            streamUrl = "http://host.invalid/$id",
            kind = MediaKind.LIVE.name,
            groupTitle = "News",
            stableKey = "key-$id",
            sortIndex = id.toInt(),
        ),
        isFavorite = isFavorite,
    )

    private companion object {
        const val SOURCE_ID = 7L
        const val MAPPING_THREAD_NAME = "channel-mapping"

        /** Stands in for the main thread: anything that is not the mapping dispatcher. */
        val COLLECTOR = Dispatchers.IO
    }
}
