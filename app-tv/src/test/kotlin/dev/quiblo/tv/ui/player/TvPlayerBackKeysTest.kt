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

package dev.quiblo.tv.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What back means inside the player — `BUG-032`.
 *
 * **The returning fault was "Back does nothing while the controls are up".** One earlier fix
 * skipped hide and exited at once (wrong product). Another kept hide-then-exit in a
 * `BackHandler` only and dropped the key preview some remotes need. These cases pin the
 * hierarchy and the consume/exit split so neither regression can land silently again.
 */
class TvPlayerBackKeysTest {

    @Test
    fun `track menu closes before the controls are touched`() {
        val action = tvPlayerBackAction(
            TvPlayerBackState(
                trackMenuOpen = true,
                controlsVisible = true,
                isOfferingNextEpisode = false,
            ),
        )

        assertEquals(TvPlayerBackAction.CloseTrackMenu, action)
        assertTrue(tvPlayerBackConsumesKey(action))
    }

    @Test
    fun `controls visible means hide, not exit — AC-TV-06`() {
        val action = tvPlayerBackAction(
            TvPlayerBackState(
                trackMenuOpen = false,
                controlsVisible = true,
                isOfferingNextEpisode = false,
            ),
        )

        assertEquals(TvPlayerBackAction.HideControls, action)
        assertTrue(tvPlayerBackConsumesKey(action))
    }

    @Test
    fun `controls do not steal back from the next-episode offer`() {
        // Offer replaces the controls on screen; if both flags were ever true, menu and
        // controls still win first, but with controls down the offer is what back dismisses.
        val action = tvPlayerBackAction(
            TvPlayerBackState(
                trackMenuOpen = false,
                controlsVisible = false,
                isOfferingNextEpisode = true,
            ),
        )

        assertEquals(TvPlayerBackAction.DismissNextEpisode, action)
        assertTrue(tvPlayerBackConsumesKey(action))
    }

    @Test
    fun `nothing on top exits playback`() {
        val action = tvPlayerBackAction(
            TvPlayerBackState(
                trackMenuOpen = false,
                controlsVisible = false,
                isOfferingNextEpisode = false,
            ),
        )

        assertEquals(TvPlayerBackAction.Exit, action)
        assertFalse(tvPlayerBackConsumesKey(action))
    }
}
