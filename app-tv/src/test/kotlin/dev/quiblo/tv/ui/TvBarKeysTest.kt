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

        assertEquals(TvBarAction.Move(TvBarState(LAST, TvBarSpot.GEAR)), action)
    }

    @Test
    fun `centre on the gear opens settings`() {
        val action = tvBarAction(Key.DirectionCenter, TvBarState(LAST, TvBarSpot.GEAR), LAST)

        assertEquals(TvBarAction.OpenSettings, action)
    }

    @Test
    fun `centre on a tab enters the content instead`() {
        val action = tvBarAction(Key.DirectionCenter, TvBarState(selectedTab = 1), LAST)

        assertEquals(TvBarAction.EnterContent, action)
    }

    @Test
    fun `left comes back off the gear onto the last tab`() {
        val action = tvBarAction(Key.DirectionLeft, TvBarState(LAST, TvBarSpot.GEAR), LAST)

        assertEquals(TvBarAction.Move(TvBarState(LAST, TvBarSpot.TABS)), action)
    }

    @Test
    fun `left at the first tab is left alone`() {
        // Unconsumed so focus can leave the bar the way it came, rather than the remote
        // going dead in the corner.
        val action = tvBarAction(Key.DirectionLeft, TvBarState(selectedTab = 0), LAST)

        assertEquals(TvBarAction.Unhandled, action)
    }

    @Test
    fun `down leaves the bar from anywhere along it`() {
        assertEquals(
            TvBarAction.EnterContent,
            tvBarAction(Key.DirectionDown, TvBarState(LAST, TvBarSpot.GEAR), LAST),
        )
        assertEquals(
            TvBarAction.EnterContent,
            tvBarAction(Key.DirectionDown, TvBarState(LAST, TvBarSpot.PROFILE), LAST),
        )
        assertEquals(
            TvBarAction.EnterContent,
            tvBarAction(Key.DirectionDown, TvBarState(selectedTab = 0), LAST),
        )
    }

    /*
     * The profile control, which is the reason the gear is no longer the end of the bar.
     *
     * The gear was unreachable for the life of the app because nothing asserted that it could be
     * got to. Adding a second icon past it repeats the whole risk — every one of these is a press
     * that has to work on a real remote, and "right, right, centre" is not something anyone will
     * re-check by hand after the next change to this bar.
     */

    @Test
    fun `right off the gear reaches the profile`() {
        val action = tvBarAction(Key.DirectionRight, TvBarState(LAST, TvBarSpot.GEAR), LAST)

        assertEquals(TvBarAction.Move(TvBarState(LAST, TvBarSpot.PROFILE)), action)
    }

    @Test
    fun `centre on the profile switches profile`() {
        val action = tvBarAction(Key.DirectionCenter, TvBarState(LAST, TvBarSpot.PROFILE), LAST)

        assertEquals(TvBarAction.SwitchProfile, action)
    }

    @Test
    fun `left comes back off the profile onto the gear`() {
        val action = tvBarAction(Key.DirectionLeft, TvBarState(LAST, TvBarSpot.PROFILE), LAST)

        assertEquals(TvBarAction.Move(TvBarState(LAST, TvBarSpot.GEAR)), action)
    }

    @Test
    fun `right on the profile goes nowhere and is still consumed`() {
        // Consumed rather than unhandled: the profile is the end of the bar, and letting the
        // press fall through drops focus into the content sideways, by a route no viewer
        // asked for.
        val action = tvBarAction(Key.DirectionRight, TvBarState(LAST, TvBarSpot.PROFILE), LAST)

        assertEquals(TvBarAction.Move(TvBarState(LAST, TvBarSpot.PROFILE)), action)
    }

    @Test
    fun `the whole bar is walkable from the first tab to the profile`() {
        // The journey rather than its steps. Each assertion above holds one hop; this is the
        // one a viewer actually makes, and it is what would catch a hop that was made
        // unreachable by a change to a different one.
        var state = TvBarState(selectedTab = 0)
        repeat(LAST + 2) {
            val action = tvBarAction(Key.DirectionRight, state, LAST)
            state = (action as TvBarAction.Move).state
        }

        assertEquals(TvBarState(LAST, TvBarSpot.PROFILE), state)
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
