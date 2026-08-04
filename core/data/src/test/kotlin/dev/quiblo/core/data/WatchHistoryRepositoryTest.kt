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

import dev.quiblo.core.database.dao.ResumePositionDao
import dev.quiblo.core.database.entity.ResumePositionEntity
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WatchHistoryRepositoryTest {

    private val dao: ResumePositionDao = mockk(relaxed = true)
    private val repository = WatchHistoryRepository(dao, now = { FIXED_NOW })

    @Test
    fun `collapses a series to the episode last watched`() = runTest {
        every { dao.observeHistory(any(), any(), any()) } returns flowOf(
            listOf(
                episodeRow("s1e4", season = 1, episode = 4, watchedAt = 300L),
                episodeRow("s1e3", season = 1, episode = 3, watchedAt = 200L),
                episodeRow("s1e2", season = 1, episode = 2, watchedAt = 100L),
            ),
        )

        val history = repository.observeHistory(SOURCE_ID, MediaKind.SERIES).first()

        // One programme is one entry. Three tiles for three episodes of the same series is
        // a list of one thing pretending to be three.
        assertEquals(1, history.size)
        assertEquals("s1e4", history.single().stableKey)
        assertEquals(4, history.single().episodeNumber)
    }

    @Test
    fun `keeps different series apart while collapsing each one`() = runTest {
        every { dao.observeHistory(any(), any(), any()) } returns flowOf(
            listOf(
                episodeRow("a2", series = "series-a", watchedAt = 400L),
                episodeRow("b1", series = "series-b", watchedAt = 300L),
                episodeRow("a1", series = "series-a", watchedAt = 200L),
            ),
        )

        val history = repository.observeHistory(SOURCE_ID, MediaKind.SERIES).first()

        assertEquals(listOf("a2", "b1"), history.map { it.stableKey })
    }

    @Test
    fun `films collapse to themselves rather than to each other`() = runTest {
        // Films carry no parent key. Grouping on a null would fold the entire film history
        // into a single entry — the failure this test exists to pin down.
        every { dao.observeHistory(any(), any(), any()) } returns flowOf(
            listOf(
                filmRow("film-1", watchedAt = 200L),
                filmRow("film-2", watchedAt = 100L),
            ),
        )

        val history = repository.observeHistory(SOURCE_ID, MediaKind.VOD).first()

        assertEquals(listOf("film-1", "film-2"), history.map { it.stableKey })
    }

    @Test
    fun `stamps the write time itself rather than trusting the caller`() = runTest {
        val written = slot<ResumePositionEntity>()

        repository.saveProgress(
            HistoryEntry(
                stableKey = "film-1",
                sourceId = SOURCE_ID,
                kind = MediaKind.VOD,
                title = "Heat",
                positionMillis = 90_000L,
                // Deliberately wrong. When something was watched is not the caller's to say.
                watchedAtEpochMillis = 1L,
            ),
        )

        coVerify { dao.upsert(capture(written)) }
        assertEquals(FIXED_NOW, written.captured.updatedAtEpochMillis)
        assertEquals("VOD", written.captured.kind)
    }

    @Test
    fun `an item never played has no resume point`() = runTest {
        every { dao.observeHistory(any(), any(), any()) } returns flowOf(emptyList())

        // Zero, not null: callers seek to this, and "start at the beginning" is the right
        // answer for something nobody has watched.
        assertEquals(0L, repository.resumePosition("never-played"))
        assertNull(repository.mostRecentlyWatched(emptyList()))
    }

    private fun episodeRow(
        key: String,
        series: String = "series-a",
        season: Int = 1,
        episode: Int = 1,
        watchedAt: Long,
    ) = ResumePositionEntity(
        stableKey = key,
        positionMillis = 60_000L,
        updatedAtEpochMillis = watchedAt,
        sourceId = SOURCE_ID,
        kind = MediaKind.SERIES.name,
        title = "A Programme",
        seriesStableKey = series,
        seasonNumber = season,
        episodeNumber = episode,
    )

    private fun filmRow(key: String, watchedAt: Long) = ResumePositionEntity(
        stableKey = key,
        positionMillis = 60_000L,
        updatedAtEpochMillis = watchedAt,
        sourceId = SOURCE_ID,
        kind = MediaKind.VOD.name,
        title = "A Film",
    )

    private companion object {
        const val SOURCE_ID = 1L
        const val FIXED_NOW = 1_700_000_000_000L
    }
}
