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

import dev.quiblo.core.database.dao.FeedRowDao
import dev.quiblo.core.database.entity.FeedRowEntity
import dev.quiblo.core.model.FeedRowEntry
import dev.quiblo.core.model.FeedRowId
import dev.quiblo.core.model.MediaKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the remembered rows keep, and what they let go of.
 *
 * The rankings are the easy half: one answer, replaced whole. The suggestions row is where the
 * decisions are, because it is appended to — so it needs a rule for when something stops being
 * worth keeping, and the rule has to be one a viewer would recognise as fair.
 */
class FeedRowCacheTest {

    private val dao = FakeFeedRowDao()
    private val repository = FeedRowCacheRepository(
        dao = dao,
        profiles = fakeProfiles(),
        now = { NOW },
    )

    @Test
    fun `a ranking is replaced rather than added to`() = runTest {
        repository.replace(SOURCE_ID, FeedRowId.POPULAR_MOVIES, listOf(entry("a", rank = 1), entry("b", rank = 2)))
        repository.replace(SOURCE_ID, FeedRowId.POPULAR_MOVIES, listOf(entry("c", rank = 1)))

        val row = repository.cached(SOURCE_ID).getValue(FeedRowId.POPULAR_MOVIES)

        // Not three. A ranking is one answer arrived at all at once, and last week's places do not
        // survive underneath this week's.
        assertEquals(listOf("c"), row.map { it.stableKey })
    }

    /** A place with no title behind it is remembered too — that is what an unavailable tile is. */
    @Test
    fun `a ranking remembers the places this provider cannot fill`() = runTest {
        repository.replace(
            SOURCE_ID,
            FeedRowId.POPULAR_MOVIES,
            listOf(entry("a", rank = 1), entry(stableKey = null, rank = 2, title = "Nothing Here")),
        )

        val row = repository.cached(SOURCE_ID).getValue(FeedRowId.POPULAR_MOVIES)

        assertEquals(listOf("a", null), row.map { it.stableKey })
        assertEquals("Nothing Here", row.last().title)
    }

    @Test
    fun `suggestions are appended, and what was there keeps its place`() = runTest {
        repository.appendSuggestions(listOf(suggestion("a"), suggestion("b")))
        repository.appendSuggestions(listOf(suggestion("c"), suggestion("a")))

        val row = repository.cached(SOURCE_ID).getValue(FeedRowId.YOU_MAY_LIKE)

        // "a" is not moved to the front by being suggested again, and "c" arrives behind what was
        // already there. A shelf that reshuffles itself is one nobody learns the shape of.
        assertEquals(listOf("a", "b", "c"), row.map { it.stableKey })
    }

    /**
     * The one thing that does move the row: watching a cause again.
     *
     * A second viewing is the strongest signal this app collects, and leaving a fortnight-old
     * suggestion standing in front of what that signal produces would be ignoring it.
     */
    @Test
    fun `a suggestion whose cause has been watched again is dropped`() = runTest {
        repository.appendSuggestions(listOf(suggestion("a", becauseOf = "One Piece"), suggestion("b")))

        repository.appendSuggestions(
            fresh = listOf(suggestion("c")),
            watchedSince = mapOf("One Piece" to NOW + 1),
        )

        val row = repository.cached(SOURCE_ID).getValue(FeedRowId.YOU_MAY_LIKE)

        assertEquals(listOf("b", "c"), row.map { it.stableKey })
    }

    /** And watching it *before* the suggestion was made is what caused the suggestion. */
    @Test
    fun `a cause watched before the suggestion was made is not a reason to drop it`() = runTest {
        repository.appendSuggestions(listOf(suggestion("a", becauseOf = "One Piece")))

        repository.appendSuggestions(
            fresh = emptyList(),
            watchedSince = mapOf("One Piece" to NOW - 1),
        )

        assertEquals(listOf("a"), repository.cached(SOURCE_ID).getValue(FeedRowId.YOU_MAY_LIKE).map { it.stableKey })
    }

    @Test
    fun `a suggestion the catalogue no longer carries is dropped`() = runTest {
        repository.appendSuggestions(listOf(suggestion("a"), suggestion("b")))

        repository.appendSuggestions(fresh = emptyList(), stillInCatalogue = setOf("a"))

        assertEquals(listOf("a"), repository.cached(SOURCE_ID).getValue(FeedRowId.YOU_MAY_LIKE).map { it.stableKey })
    }

    @Test
    fun `the appended row does not grow past its cap`() = runTest {
        repository.appendSuggestions((1..5).map { suggestion("held-$it") })

        repository.appendSuggestions(
            fresh = (1..5).map { suggestion("new-$it") },
            stillInCatalogue = (1..5).map { "held-$it" }.toSet(),
            limit = 6,
        )

        val row = repository.cached(SOURCE_ID).getValue(FeedRowId.YOU_MAY_LIKE)

        assertEquals(6, row.size)
        // What was already there survives; the cap falls on what is arriving.
        assertTrue(row.take(5).all { it.stableKey?.startsWith("held-") == true })
    }

    @Test
    fun `positions are rewritten so a row is always a run from zero`() = runTest {
        repository.appendSuggestions(listOf(suggestion("a"), suggestion("b"), suggestion("c")))
        repository.appendSuggestions(fresh = emptyList(), stillInCatalogue = setOf("a", "c"))

        assertEquals(listOf(0, 1), repository.cached(SOURCE_ID).getValue(FeedRowId.YOU_MAY_LIKE).map { it.position })
    }

    private suspend fun FeedRowCacheRepository.appendSuggestions(
        fresh: List<FeedRowEntry>,
        watchedSince: Map<String, Long> = emptyMap(),
        stillInCatalogue: Set<String>? = null,
        limit: Int = 20,
    ) = append(
        sourceId = SOURCE_ID,
        rowId = FeedRowId.YOU_MAY_LIKE,
        fresh = fresh,
        watchedSince = watchedSince,
        // Everything held and everything arriving, unless a test is saying otherwise.
        stillInCatalogue = stillInCatalogue
            ?: (dao.rows.mapNotNull { it.stableKey } + fresh.mapNotNull { it.stableKey }).toSet(),
        limit = limit,
    )

    private fun entry(
        stableKey: String?,
        rank: Int? = null,
        title: String = stableKey.orEmpty(),
        becauseOf: String? = null,
        rowId: FeedRowId = FeedRowId.POPULAR_MOVIES,
    ) = FeedRowEntry(
        rowId = rowId,
        position = 0,
        stableKey = stableKey,
        title = title,
        kind = MediaKind.VOD,
        rank = rank,
        becauseOf = becauseOf,
    )

    private fun suggestion(stableKey: String, becauseOf: String = "Something") =
        entry(stableKey, rowId = FeedRowId.YOU_MAY_LIKE, becauseOf = becauseOf)

    /**
     * The table, in memory.
     *
     * A fake rather than a mock: every assertion here is about what a *sequence* of writes leaves
     * behind, and a mock would have each test restating the answer it is checking.
     */
    private class FakeFeedRowDao : FeedRowDao {

        var rows: List<FeedRowEntity> = emptyList()

        override suspend fun rowsFor(profileId: Long, sourceId: Long): List<FeedRowEntity> =
            rows.filter { it.profileId == profileId && it.sourceId == sourceId }
                .sortedWith(compareBy({ it.rowId }, { it.position }))

        override suspend fun clearRow(profileId: Long, sourceId: Long, rowId: String) {
            rows = rows.filterNot { it.profileId == profileId && it.sourceId == sourceId && it.rowId == rowId }
        }

        override suspend fun insertAll(entries: List<FeedRowEntity>) {
            rows = rows + entries
        }

        override suspend fun clearForSource(sourceId: Long) {
            rows = rows.filterNot { it.sourceId == sourceId }
        }
    }

    private companion object {
        const val SOURCE_ID = 5L
        const val NOW = 1_780_000_000_000L
    }
}
