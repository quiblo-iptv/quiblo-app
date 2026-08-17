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

import dev.quiblo.core.database.dao.WatchCount
import dev.quiblo.core.database.dao.WatchEventDao
import dev.quiblo.core.database.entity.WatchEventEntity
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.WatchOrigin
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The occasion log, and the four derived answers the suggestions row reads off it.
 *
 * Three of these are arithmetic nobody can check from a screen — the circular mean over a clock,
 * the strongest-origin ordering, and the retention trim — and all three shipped in `025` without
 * a test. Closing an Amendment 10 gap rather than covering new code.
 */
class WatchEventRepositoryTest {

    private val dao = FakeWatchEventDao()

    @Test
    fun `a viewing is written against this viewer, with the clock it happened on`() = runTest {
        repositoryAt(NOW).record(SOURCE, "film-1", MediaKind.VOD, "Alien", fraction = 0.8, origin = WatchOrigin.ROW)

        val row = dao.rows.single()
        assertEquals(PROFILE, row.profileId)
        assertEquals("film-1", row.stableKey)
        assertEquals(NOW, row.startedAtEpochMillis)
        assertEquals(WatchOrigin.ROW.name, row.origin)
    }

    /**
     * A fraction outside 0..1 is clamped rather than stored.
     *
     * A player reporting 1.02 at the end of a stream is not unusual, and a fraction above one
     * would make a single viewing count as more than a whole one everywhere downstream.
     */
    @Test
    fun `a fraction outside the range is clamped`() = runTest {
        val repository = repositoryAt(NOW)

        repository.record(SOURCE, "a", MediaKind.VOD, "A", fraction = 1.4, origin = WatchOrigin.ROW)
        repository.record(SOURCE, "b", MediaKind.VOD, "B", fraction = -0.2, origin = WatchOrigin.ROW)

        assertEquals(listOf(1.0, 0.0), dao.rows.map { it.fraction })
    }

    /** A log is the one table that grows with nothing on screen getting bigger. */
    @Test
    fun `writing an occasion trims anything past the retention window`() = runTest {
        dao.rows += event(stableKey = "ancient", at = NOW - TWO_YEARS)

        repositoryAt(NOW).record(SOURCE, "new", MediaKind.VOD, "New", fraction = 0.9, origin = WatchOrigin.ROW)

        assertEquals(listOf("new"), dao.rows.map { it.stableKey })
    }

    @Test
    fun `an event inside the year is kept`() = runTest {
        dao.rows += event(stableKey = "recent", at = NOW - ONE_MONTH)

        repositoryAt(NOW).record(SOURCE, "new", MediaKind.VOD, "New", fraction = 0.9, origin = WatchOrigin.ROW)

        assertEquals(setOf("recent", "new"), dao.rows.map { it.stableKey }.toSet())
    }

    /**
     * 23:00 and 01:00 average to midnight, not to midday.
     *
     * The one case a plain mean gets exactly wrong, and it is the common one: the hour somebody
     * watches television is the hour a plain average cannot represent. Midday is the single hour
     * neither of these viewings happened in.
     */
    @Test
    fun `the usual hour is a circular mean, so late night does not average to midday`() = runTest {
        dao.rows += event("film", at = atHour(23))
        dao.rows += event("film", at = atHour(1))

        val hour = repositoryAt(NOW).usualHourByStableKey(SOURCE).getValue("film")

        assertTrue(
            hour == 0 || hour == HOURS - 1,
            "23:00 and 01:00 should mean around midnight, not $hour:00.",
        )
    }

    @Test
    fun `two evening viewings mean the evening`() = runTest {
        dao.rows += event("film", at = atHour(20))
        dao.rows += event("film", at = atHour(22))

        val hour = repositoryAt(NOW).usualHourByStableKey(SOURCE).getValue("film")

        assertTrue(hour in 20..21, "Expected around 21:00, got $hour:00.")
    }

    /**
     * Searching for something once outranks resuming it four times.
     *
     * "Strongest" rather than "most recent" is the decision, and most recent is what a naive
     * implementation gives you — the last event wins, so every title a viewer ever returns to
     * ends up labelled CONTINUE and the intent that got them there is lost.
     */
    @Test
    fun `the strongest origin wins, not the latest`() = runTest {
        dao.rows += event("film", at = NOW - ONE_MONTH, origin = WatchOrigin.SEARCH)
        repeat(4) { dao.rows += event("film", at = NOW, origin = WatchOrigin.CONTINUE) }

        assertEquals(WatchOrigin.SEARCH, repositoryAt(NOW).strongestOriginByStableKey(SOURCE).getValue("film"))
    }

    @Test
    fun `a favourite outranks a row, and a row outranks a resume`() = runTest {
        dao.rows += event("a", origin = WatchOrigin.FAVOURITE)
        dao.rows += event("a", origin = WatchOrigin.ROW)
        dao.rows += event("b", origin = WatchOrigin.ROW)
        dao.rows += event("b", origin = WatchOrigin.CONTINUE)

        val origins = repositoryAt(NOW).strongestOriginByStableKey(SOURCE)

        assertEquals(WatchOrigin.FAVOURITE, origins.getValue("a"))
        assertEquals(WatchOrigin.ROW, origins.getValue("b"))
    }

    /** A row written by a version that had an origin this one does not is not a crash. */
    @Test
    fun `an origin the enum no longer has falls back rather than throwing`() = runTest {
        dao.rows += event("film").copy(origin = "TELEPATHY")

        assertEquals(WatchOrigin.ROW, repositoryAt(NOW).strongestOriginByStableKey(SOURCE).getValue("film"))
    }

    @Test
    fun `plays are read by the provider's own identity`() = runTest {
        dao.rows += event("film", fraction = 0.9)
        dao.rows += event("film", fraction = 0.9)
        dao.rows += event("other", fraction = 0.9)

        assertEquals(mapOf("film" to 2, "other" to 1), repositoryAt(NOW).playsByStableKey(SOURCE))
    }

    /**
     * Opening something and closing it is not a viewing.
     *
     * Without the quarter-fraction floor, a title somebody keeps bouncing off becomes their
     * most-watched — which is the opposite of what the row would then suggest more of.
     */
    @Test
    fun `a title bounced off does not count as watched`() = runTest {
        dao.rows += event("bounced", fraction = 0.05)
        dao.rows += event("bounced", fraction = 0.10)

        assertEquals(emptyMap<String, Int>(), repositoryAt(NOW).playsByStableKey(SOURCE))
        assertEquals(0, repositoryAt(NOW).distinctTitlesWatched(SOURCE))
    }

    @Test
    fun `forgetting a title removes its occasions and leaves the rest`() = runTest {
        dao.rows += event("forgotten")
        dao.rows += event("kept")

        repositoryAt(NOW).forget("forgotten")

        assertEquals(listOf("kept"), dao.rows.map { it.stableKey })
    }

    private fun repositoryAt(millis: Long) = WatchEventRepository(dao, fakeProfiles(PROFILE), now = { millis })

    private fun event(
        stableKey: String,
        at: Long = NOW,
        fraction: Double = 0.9,
        origin: WatchOrigin = WatchOrigin.ROW,
    ) = WatchEventEntity(
        profileId = PROFILE,
        sourceId = SOURCE,
        stableKey = stableKey,
        kind = MediaKind.VOD.name,
        title = stableKey,
        startedAtEpochMillis = at,
        fraction = fraction,
        origin = origin.name,
    )

    /** An instant at [hour] local time, since the repository reads the device's own clock. */
    private fun atHour(hour: Int): Long = Calendar.getInstance(TimeZone.getDefault()).apply {
        timeInMillis = NOW
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * The table, not a mock of it.
     *
     * `playCounts` and `distinctTitles` are SQL in production, and the fraction floor lives in
     * that SQL — so the fake applies the same floor. A relaxed mock returning empty lists would
     * make every assertion here pass without the repository doing anything.
     */
    private class FakeWatchEventDao : WatchEventDao {
        val rows = mutableListOf<WatchEventEntity>()

        override suspend fun insert(event: WatchEventEntity) {
            rows += event
        }

        override suspend fun recent(profileId: Long, sourceId: Long, limit: Int): List<WatchEventEntity> =
            rows.filter { it.profileId == profileId && it.sourceId == sourceId }
                .sortedByDescending { it.startedAtEpochMillis }
                .take(limit)

        override suspend fun playCounts(
            profileId: Long,
            sourceId: Long,
            minimumFraction: Double,
        ): List<WatchCount> = counted(profileId, sourceId, minimumFraction)
            .groupBy { it.stableKey }
            .map { (key, events) ->
                WatchCount(
                    stableKey = key,
                    plays = events.size,
                    lastStartedAtEpochMillis = events.maxOf { it.startedAtEpochMillis },
                )
            }

        override suspend fun distinctTitles(profileId: Long, sourceId: Long, minimumFraction: Double): Int =
            counted(profileId, sourceId, minimumFraction).map { it.stableKey }.distinct().size

        override suspend fun deleteOlderThan(before: Long) {
            rows.removeAll { it.startedAtEpochMillis < before }
        }

        override suspend fun deleteFor(profileId: Long, stableKey: String) {
            rows.removeAll { it.profileId == profileId && it.stableKey == stableKey }
        }

        private fun counted(profileId: Long, sourceId: Long, minimumFraction: Double) =
            rows.filter {
                it.profileId == profileId && it.sourceId == sourceId && it.fraction >= minimumFraction
            }
    }

    private companion object {
        const val PROFILE = 1L
        const val SOURCE = 7L
        const val NOW = 1_760_000_000_000L
        const val ONE_MONTH = 30L * 24 * 60 * 60 * 1000
        const val TWO_YEARS = 730L * 24 * 60 * 60 * 1000
        const val HOURS = 24
    }
}
