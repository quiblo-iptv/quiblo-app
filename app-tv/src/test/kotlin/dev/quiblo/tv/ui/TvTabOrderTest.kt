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

package dev.quiblo.tv.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Where the tabs sit, in order.
 *
 * The order is not an implementation detail here: the bar is a single focus target driven by
 * index arithmetic, `TvApp` routes on `TvTab.entries[selectedTab]`, and Back is defined as
 * "return to ordinal zero". A tab inserted in the wrong place is therefore not a cosmetic
 * problem — it silently repoints every one of those. Declaring the order once, here, is what
 * makes an accidental reshuffle a failing test rather than a surprise on a television.
 */
class TvTabOrderTest {

    @Test
    fun `the bar reads search, for you, live, movies, series, favourites`() {
        assertEquals(
            listOf(
                TvTab.SEARCH,
                TvTab.FOR_YOU,
                TvTab.LIVE,
                TvTab.MOVIES,
                TvTab.SERIES,
                TvTab.FAVOURITES,
            ),
            TvTab.entries,
        )
    }

    @Test
    fun `search is still first, because Back comes to rest there`() {
        assertEquals(0, TvTab.SEARCH.ordinal)
    }

    @Test
    fun `for you sits ahead of live and the catalogues`() {
        assertEquals(TvTab.SEARCH.ordinal + 1, TvTab.FOR_YOU.ordinal)
        assertEquals(TvTab.FOR_YOU.ordinal + 1, TvTab.LIVE.ordinal)
    }

    @Test
    fun `search is the only icon-only tab`() {
        // Everything else is a word. An icon nobody recognises on a bar of words reads as a
        // missing label, and a magnifier is the one glyph that does not need one.
        assertEquals(listOf(TvTab.SEARCH), TvTab.entries.filter { it.isIconOnly })
    }
}
