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

import androidx.compose.ui.input.key.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * That the settings icon can actually be reached, and that the ends of the bar behave.
 *
 * The gear was unreachable by remote for the whole life of the television app: it was
 * focusable, the bar left the key unconsumed hoping focus would land on it, and instead the
 * focus search walked past it into the content below. Nothing caught that because nothing
 * asserted it — the icon existed, drew, and could not be got to.
 */
class TvBarKeysTest {

    @Test
    fun `right past the last tab lands on the gear`() {
        val action = tvBarAction(Key.DirectionRight, TvBarState(selectedTab = LAST), LAST)

        assertEquals(TvBarAction.Move(TvBarState(LAST, onGear = true)), action)
    }

    @Test
    fun `centre on the gear opens settings`() {
        val action = tvBarAction(Key.DirectionCenter, TvBarState(LAST, onGear = true), LAST)

        assertEquals(TvBarAction.OpenSettings, action)
    }

    @Test
    fun `centre on a tab enters the content instead`() {
        val action = tvBarAction(Key.DirectionCenter, TvBarState(selectedTab = 1), LAST)

        assertEquals(TvBarAction.EnterContent, action)
    }

    @Test
    fun `left comes back off the gear onto the last tab`() {
        val action = tvBarAction(Key.DirectionLeft, TvBarState(LAST, onGear = true), LAST)

        assertEquals(TvBarAction.Move(TvBarState(LAST, onGear = false)), action)
    }

    @Test
    fun `right on the gear goes nowhere and is still consumed`() {
        // Consumed rather than unhandled: there is nothing past the gear, and letting the
        // press fall through drops focus into the content sideways, by a route no viewer
        // asked for.
        val action = tvBarAction(Key.DirectionRight, TvBarState(LAST, onGear = true), LAST)

        assertEquals(TvBarAction.Move(TvBarState(LAST, onGear = true)), action)
    }

    @Test
    fun `left at the first tab is left alone`() {
        // Unconsumed so focus can leave the bar the way it came, rather than the remote
        // going dead in the corner.
        val action = tvBarAction(Key.DirectionLeft, TvBarState(selectedTab = 0), LAST)

        assertEquals(TvBarAction.Unhandled, action)
    }

    @Test
    fun `down leaves the bar even from the gear`() {
        assertEquals(
            TvBarAction.EnterContent,
            tvBarAction(Key.DirectionDown, TvBarState(LAST, onGear = true), LAST),
        )
        assertEquals(
            TvBarAction.EnterContent,
            tvBarAction(Key.DirectionDown, TvBarState(selectedTab = 0), LAST),
        )
    }

    @Test
    fun `right walks the tabs one at a time`() {
        val action = tvBarAction(Key.DirectionRight, TvBarState(selectedTab = 0), LAST)

        assertEquals(TvBarAction.Move(TvBarState(selectedTab = 1)), action)
    }

    private companion object {
        /** Stands in for `TvTab.entries.lastIndex` without tying the test to the tab list. */
        const val LAST = 4
    }
}
