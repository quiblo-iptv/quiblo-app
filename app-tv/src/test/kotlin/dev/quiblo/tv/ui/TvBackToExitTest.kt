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
 * What a back press means, and what it takes to leave.
 *
 * **Leaving used to mean falling through to the system**, which *backgrounds* an activity rather
 * than finishing it. The process survived, the chosen profile survived with it, and the next
 * launch resumed straight into somebody else's favourites — which is the reported half, "so I can
 * pick profile again".
 *
 * The rule has three cases and only the first was ever written down. Held as a function over
 * state rather than as branches inside a `BackHandler` for the reason `TvBarKeysTest` gives about
 * the bar: a decision buried in a composable can only be checked by driving a whole screen, and
 * this project has shipped several rules that were right in the code and wrong in the order they
 * fired.
 */
class TvBackToExitTest {

    @Test
    fun `back from another tab walks to Search rather than leaving`() {
        val action = tvBackAction(TvBackState(selectedTab = LAST, isExitArmed = false))

        assertEquals(TvBackAction.GoToSearch, action)
    }

    @Test
    fun `and it disarms an exit somebody had already started`() {
        // A viewer who has gone somewhere else has stopped leaving. Without this, arming on
        // Search and then walking to Movies leaves the next back on Search closing the app with
        // no notice on screen at all.
        val action = tvBackAction(TvBackState(selectedTab = LAST, isExitArmed = true))

        assertEquals(TvBackAction.GoToSearch, action)
    }

    @Test
    fun `the first back on Search asks rather than closing`() {
        val action = tvBackAction(TvBackState(selectedTab = SEARCH, isExitArmed = false))

        assertEquals(TvBackAction.ArmExit, action)
    }

    @Test
    fun `the second closes`() {
        val action = tvBackAction(TvBackState(selectedTab = SEARCH, isExitArmed = true))

        assertEquals(TvBackAction.Exit, action)
    }

    private companion object {
        const val SEARCH = 0
        const val LAST = 3
    }
}
