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

package dev.quiblo.tv.ui.search

import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.feature.browse.SearchUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * That pressing a result opens *that* result.
 *
 * A poster hands the shell a list and a position rather than an item, because that is what
 * the player needs to zap onward from. The position is counted while the rows are built, and
 * an off-by-one in that counting is a viewer pressing one film and being shown another —
 * silently, and only for the second and third kinds, which is exactly the sort of thing
 * nobody notices from the sofa until it has shipped.
 */
class TvSearchRowsTest {

    @Test
    @DisplayName("a series is opened, not the film that shares its position in its own row")
    fun `the flat index continues across the rows`() {
        val state = SearchUiState(
            query = "fargo",
            live = listOf(channel(1L, "Fargo News", MediaKind.LIVE)),
            movies = listOf(channel(2L, "Fargo", MediaKind.VOD), channel(3L, "Fargo (1996)", MediaKind.VOD)),
            series = listOf(channel(4L, "Fargo", MediaKind.SERIES)),
        )

        val found = searchRows(state, LIVE, FILMS, SERIES)

        assertEquals(listOf(1L, 2L, 3L, 4L), found.flat.map { it.id })
        // The series row starts after one live channel and two films.
        assertEquals(3, found.rows.last().items.single().flatIndex)
        assertEquals(4L, found.flat[3].id)
    }

    @Test
    @DisplayName("a kind with nothing in it gets no heading")
    fun `an empty kind produces no row`() {
        val state = SearchUiState(
            query = "bbc",
            live = listOf(channel(1L, "BBC One", MediaKind.LIVE)),
            movies = emptyList(),
            series = listOf(channel(2L, "Bodyguard", MediaKind.SERIES)),
        )

        val found = searchRows(state, LIVE, FILMS, SERIES)

        assertEquals(listOf(LIVE, SERIES), found.rows.map { it.title })
        // Indices count results, not rows: the series is second in the flat list even though
        // the row above it is the first.
        assertEquals(1, found.rows.last().items.single().flatIndex)
    }

    @Test
    fun `nothing found is no rows at all`() {
        val found = searchRows(SearchUiState(query = "zzz"), LIVE, FILMS, SERIES)

        assertEquals(emptyList<String>(), found.rows.map { it.title })
        assertEquals(emptyList<Long>(), found.flat.map { it.id })
    }

    private fun channel(id: Long, name: String, kind: MediaKind) = Channel(
        id = id,
        sourceId = 1L,
        name = name,
        streamUrl = "http://host.invalid/$id",
        kind = kind,
    )

    private companion object {
        const val LIVE = "Live channels"
        const val FILMS = "Films"
        const val SERIES = "Series"
    }
}
