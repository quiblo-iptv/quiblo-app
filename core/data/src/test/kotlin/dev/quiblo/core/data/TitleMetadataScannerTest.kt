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

import dev.quiblo.core.database.DurabilityCheckpoint
import dev.quiblo.core.database.dao.CachedTitleKey
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.ChannelTitle
import dev.quiblo.core.database.dao.TitleFactRow
import dev.quiblo.core.database.dao.TitleFilterRow
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.entity.TitleMetadataEntity
import dev.quiblo.core.datastore.TmdbKeyStore
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.source.tmdb.TmdbAnswer
import dev.quiblo.source.tmdb.TmdbClient
import dev.quiblo.source.tmdb.TmdbRefusal
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The catalogue scan, which is the one thing in this app that issues tens of thousands of
 * requests on purpose.
 *
 * These are about restraint rather than throughput. A scan that asks about titles it already
 * knows is an hour wasted; a scan that keeps asking after the service has refused is a user's
 * own key being hammered; and a scan that writes a refusal down as an answer poisons the
 * cache for a fortnight — which is the defect this feature nearly shipped with.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TitleMetadataScannerTest {

    private val channelDao: ChannelDao = mockk(relaxed = true)
    private val metadataDao = RecordingMetadataDao()
    private val client: TmdbClient = mockk()
    private val checkpoint = CountingCheckpoint()

    private val keyStore: TmdbKeyStore = mockk<TmdbKeyStore>().apply {
        every { apiKey } returns MutableStateFlow("a-key")
    }

    private val repository = TitleMetadataRepository(
        client = client,
        keyStore = keyStore,
        dao = metadataDao,
        now = { FIXED_NOW },
    )

    @Test
    @DisplayName("four copies of one film are one lookup, not four")
    fun `duplicate quality variants collapse to a single request`() = runTest {
        catalogue(
            title(1, "The Matrix (1999) [4K]"),
            title(2, "The Matrix (1999) [FHD]"),
            title(3, "The Matrix (1999) SD"),
            title(4, "Heat (1995)"),
        )
        var asked = 0
        coEvery { client.summary(any(), any(), any(), any()) } answers {
            asked++
            FOUND
        }

        val state = scanAndAwait()

        assertEquals(2, asked, "the same film was looked up more than once")
        assertEquals(MetadataScanState.Finished(total = 2, found = 2, missing = 0), state)
    }

    @Test
    @DisplayName("a dated title and an undated one are two titles, and both are asked")
    fun `an undated wording does not collapse into a dated one`() = runTest {
        catalogue(title(1, "The Matrix"), title(2, "The Matrix (1999)"))
        val asked = mutableListOf<String>()
        coEvery { client.summary(any(), any(), any(), any()) } answers {
            asked += secondArg<String>()
            FOUND
        }

        scanAndAwait()

        // Before #024 these were one work item and the scan picked the wording that still
        // carried its year. They are two now, and the extra request is the point rather than
        // a cost that slipped in: the scan cannot know that an undated "The Matrix" is the
        // 1999 one, and the alternative to asking twice is filing both under whichever
        // answer came back first. That is the false merge this whole item exists to end, and
        // it is the failure that shows a viewer the wrong film rather than a slower scan.
        assertEquals(listOf("The Matrix", "The Matrix (1999)"), asked)
    }

    @Test
    @DisplayName("a scan interrupted half way starts again half way")
    fun `titles already cached are not asked about again`() = runTest {
        catalogue(title(1, "The Matrix (1999)"), title(2, "Heat (1995)"))
        metadataDao.put("the matrix", MediaKind.VOD, year = 1999, fetchedAt = FIXED_NOW)
        val asked = mutableListOf<String>()
        coEvery { client.summary(any(), any(), any(), any()) } answers {
            asked += secondArg<String>()
            FOUND
        }

        val state = scanAndAwait()

        assertEquals(listOf("Heat (1995)"), asked)
        assertEquals(MetadataScanState.Finished(total = 1, found = 1, missing = 0), state)
    }

    @Test
    @DisplayName("an answer older than the cache's fortnight is still not re-scanned")
    fun `a stale row is treated as known`() = runTest {
        catalogue(title(1, "The Matrix (1999)"))
        metadataDao.put("the matrix", MediaKind.VOD, year = 1999, fetchedAt = FIXED_NOW - FIFTEEN_DAYS_MILLIS)
        coEvery { client.summary(any(), any(), any(), any()) } returns FOUND

        // This assertion used to be the opposite, and inverting it is the fix for an hour of
        // scanning that appeared to evaporate across a restart. A wrong clock — a television
        // that boots before it syncs the time, an emulator resumed from an old snapshot — ages
        // every row at once, and under the old rule that handed the whole catalogue back as
        // work to redo. A title that has been asked about stays asked about; the fortnight still
        // governs when a single title is refetched for a screen that opens it.
        assertEquals(MetadataScanState.Finished(total = 0, found = 0, missing = 0), scanAndAwait())
    }

    @Test
    @DisplayName("what has been learned is pushed to the disk during the scan and at its end")
    fun `the scan checkpoints its work`() = runTest {
        catalogue(title(1, "A Film"), title(2, "B Film"))
        coEvery { client.summary(any(), any(), any(), any()) } returns FOUND

        scanAndAwait()

        // Committed is not the same as written: Room keeps commits in a side log that a power
        // cut can still take back, and this scan is an hour long.
        assertEquals(1, checkpoint.flushes, "the finished scan was never pushed to the disk")
    }

    @Test
    @DisplayName("a cancelled scan still pushes what it managed to learn")
    fun `cancelling checkpoints rather than dropping the work`() = runTest {
        catalogue(title(1, "A Film"), title(2, "B Film"))
        // The second title never answers, so the scan is genuinely in flight when it is
        // cancelled rather than cancelled before it began.
        val inFlight = CompletableDeferred<Unit>()
        var answered = 0
        coEvery { client.summary(any(), any(), any(), any()) } coAnswers {
            if (answered++ == 0) {
                FOUND
            } else {
                inFlight.await()
                FOUND
            }
        }

        val scanner = scanner(workers = 1)
        scanner.start(SOURCE_ID)
        runCurrent()
        scanner.cancel()
        advanceUntilIdle()

        // Cancelling is exactly when somebody is leaving, and on a television leaving often
        // means switching it off at the wall.
        assertTrue(checkpoint.flushes > 0, "a cancelled scan left its answers in the log")
    }

    @Test
    @DisplayName("a title that cleans away to nothing is never sent")
    fun `unsearchable titles cost no requests`() = runTest {
        // A name written entirely in a non-Latin script, and a bare language tag. Neither can
        // match anything in an English-language search, so the request is not made at all.
        catalogue(title(1, "مسلسل الاختيار"), title(2, "|AR| [1080p] HD"))
        var asked = 0
        coEvery { client.summary(any(), any(), any(), any()) } answers {
            asked++
            FOUND
        }

        val state = scanAndAwait()

        assertEquals(0, asked)
        assertEquals(MetadataScanState.Finished(total = 0, found = 0, missing = 0), state)
    }

    @Test
    @DisplayName("a title the service holds nothing for is remembered as a miss")
    fun `a genuine no match is counted and cached`() = runTest {
        catalogue(title(1, "Some Local Broadcast"))
        coEvery { client.summary(any(), any(), any(), any()) } returns TmdbAnswer.NoMatch

        val state = scanAndAwait()

        assertEquals(MetadataScanState.Finished(total = 1, found = 0, missing = 1), state)
        assertTrue(metadataDao.rows.values.single().isMiss, "a miss was not written down")
    }

    @Test
    @DisplayName("a rate limit stops the scan instead of writing thousands of false misses")
    fun `a refusal ends the scan and caches nothing`() = runTest {
        catalogue(title(1, "A Film"), title(2, "B Film"), title(3, "C Film"), title(4, "D Film"))
        coEvery { client.summary(any(), any(), any(), any()) } returns RATE_LIMITED

        val state = scanAndAwait()

        assertTrue(state is MetadataScanState.Stopped, "expected a stop, got $state")
        assertEquals(ScanRefusal.RATE_LIMITED, (state as MetadataScanState.Stopped).reason)
        // The half that matters: nothing was learned, so nothing was written down. Cached as
        // misses, these would have the search screen reporting a described catalogue with no
        // genres in it until the TTL expired a fortnight later.
        assertTrue(metadataDao.rows.isEmpty(), "a refusal was cached as an answer")
    }

    @Test
    @DisplayName("what was found before a refusal is kept, and counted")
    fun `a refusal part way through keeps the work already done`() = runTest {
        catalogue(title(1, "First Film"), title(2, "Second Film"))
        var call = 0
        coEvery { client.summary(any(), any(), any(), any()) } answers {
            call++
            if (call == 1) FOUND else UNAVAILABLE
        }

        // One worker, so "first" and "second" mean what they say.
        val state = scanAndAwait(workers = 1)

        assertTrue(state is MetadataScanState.Stopped, "expected a stop, got $state")
        assertEquals(1, (state as MetadataScanState.Stopped).found)
        assertEquals(1, metadataDao.rows.size, "the answer received before the refusal was lost")
    }

    @Test
    fun `a scan does nothing at all without a key`() = runTest {
        every { keyStore.apiKey } returns MutableStateFlow(null)
        catalogue(title(1, "The Matrix (1999)"))

        val scanner = scanner()
        scanner.start(SOURCE_ID)
        advanceUntilIdle()

        assertEquals(MetadataScanState.Idle, scanner.state.value)
    }

    @Test
    fun `a second start while one is running is ignored`() = runTest {
        catalogue(title(1, "A Film"), title(2, "B Film"))
        coEvery { client.summary(any(), any(), any(), any()) } returns FOUND

        val scanner = scanner()
        scanner.start(SOURCE_ID)
        scanner.start(SOURCE_ID)
        advanceUntilIdle()

        assertEquals(MetadataScanState.Finished(total = 2, found = 2, missing = 0), scanner.state.value)
    }

    private fun catalogue(vararg titles: ChannelTitle) {
        coEvery { channelDao.titlesForMetadata(SOURCE_ID, any()) } returns titles.toList()
    }

    private fun title(id: Long, name: String, kind: MediaKind = MediaKind.VOD) =
        ChannelTitle(id = id, name = name, kind = kind.name)

    private fun TestScope.scanAndAwait(workers: Int = DEFAULT_WORKERS): MetadataScanState {
        val scanner = scanner(workers)
        scanner.start(SOURCE_ID)
        advanceUntilIdle()
        return scanner.state.value
    }

    private fun TestScope.scanner(workers: Int = DEFAULT_WORKERS) = TitleMetadataScanner(
        channelDao = channelDao,
        metadataRepository = repository,
        // The test's own scheduler, so `advanceUntilIdle` drives the scan to completion
        // without any waiting in real time.
        checkpoint = checkpoint,
        scope = CoroutineScope(coroutineContext),
        workers = workers,
    )

    /** Counts the flushes, since when they happen is the whole of what is being asserted. */
    private class CountingCheckpoint : DurabilityCheckpoint {
        var flushes = 0
            private set

        override suspend fun flush() {
            flushes++
        }
    }

    /** A cache that can be inspected, since what is *not* written is the point here. */
    private class RecordingMetadataDao : TitleMetadataDao {
        val rows = mutableMapOf<Triple<String, String, Int>, TitleMetadataEntity>()

        override suspend fun find(searchTitle: String, kind: String, year: Int): TitleMetadataEntity? =
            rows[Triple(searchTitle, kind, year)]

        override suspend fun allFilterRows(): List<TitleFilterRow> = rows.values.map {
            TitleFilterRow(it.searchTitle, it.kind, it.year, it.releaseYear, it.genres, it.isMiss)
        }

        override suspend fun allFactRows(): List<TitleFactRow> = rows.values.filterNot { it.isMiss }.map {
            TitleFactRow(
                searchTitle = it.searchTitle,
                kind = it.kind,
                year = it.year,
                genres = it.genres,
                overview = it.overview,
                originalLanguage = it.originalLanguage,
                popularity = it.popularity,
                rating = it.rating,
                releaseYear = it.releaseYear,
                runtimeMinutes = it.runtimeMinutes,
            )
        }

        override suspend fun allKeys(): List<CachedTitleKey> = rows.values.map {
            CachedTitleKey(it.searchTitle, it.kind, it.year, it.fetchedAtEpochMillis)
        }

        override suspend fun upsert(entity: TitleMetadataEntity) {
            rows[Triple(entity.searchTitle, entity.kind, entity.year)] = entity
        }

        override suspend fun count(): Int = rows.size

        override suspend fun clear() = rows.clear()

        fun put(searchTitle: String, kind: MediaKind, year: Int, fetchedAt: Long) {
            rows[Triple(searchTitle, kind.name, year)] = TitleMetadataEntity(
                searchTitle = searchTitle,
                kind = kind.name,
                year = year,
                fetchedAtEpochMillis = fetchedAt,
                rating = 8.0,
            )
        }
    }

    private companion object {
        const val SOURCE_ID = 5L
        const val FIXED_NOW = 1_000_000_000L
        const val FIFTEEN_DAYS_MILLIS = 15L * 24 * 60 * 60 * 1000
        const val DEFAULT_WORKERS = 4

        val FOUND = TmdbAnswer.Found(
            TitleMetadata(rating = 8.2, genres = listOf("Action"), isPartial = true),
        )
        val RATE_LIMITED = TmdbAnswer.Refused(TmdbRefusal.RATE_LIMITED)
        val UNAVAILABLE = TmdbAnswer.Refused(TmdbRefusal.UNAVAILABLE)
    }
}
