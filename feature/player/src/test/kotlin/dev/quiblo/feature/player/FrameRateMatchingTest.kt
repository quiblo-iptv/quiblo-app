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

package dev.quiblo.feature.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Which display mode a film plays in (`FEAT-041`). */
class FrameRateMatchingTest {

    private fun uhd(id: Int, hz: Float) = DisplayModeOption(id, 3_840, 2_160, hz)
    private fun hd(id: Int, hz: Float) = DisplayModeOption(id, 1_920, 1_080, hz)

    /** A 4K panel that offers the usual set, at 60 Hz by default — the reporter's, roughly. */
    private val sixty = uhd(1, 60f)
    private val panel = listOf(
        uhd(1, 60f),
        uhd(2, 50f),
        uhd(3, 30f),
        uhd(4, 25f),
        uhd(5, 24f),
        uhd(6, 23.976f),
    )

    @Test
    fun `a 23_976 film moves a 60 Hz panel to 23_976 Hz`() {
        assertEquals(uhd(6, 23.976f), matchingDisplayMode(23.976f, sixty, panel))
    }

    @Test
    fun `without an exact mode, 24 Hz will do for 23_976`() {
        assertEquals(uhd(5, 24f), matchingDisplayMode(23.976f, sixty, panel - uhd(6, 23.976f)))
    }

    @Test
    fun `a 25 fps film prefers 50 Hz to 25 Hz`() {
        assertEquals(uhd(2, 50f), matchingDisplayMode(25f, sixty, panel))
    }

    @Test
    fun `a rate the panel already shows evenly is left alone`() {
        assertNull(matchingDisplayMode(30f, sixty, panel))
        assertNull(matchingDisplayMode(60f, sixty, panel))
        assertNull(matchingDisplayMode(25f, uhd(2, 50f), panel))
    }

    @Test
    fun `a panel with nothing that fits is left alone`() {
        assertNull(matchingDisplayMode(23.976f, sixty, listOf(uhd(1, 60f), uhd(2, 50f))))
    }

    @Test
    fun `resolution is never given up for an even cadence`() {
        assertNull(matchingDisplayMode(23.976f, sixty, listOf(uhd(1, 60f), hd(7, 24f))))
    }

    @Test
    fun `an unknown frame rate switches nothing`() {
        assertNull(matchingDisplayMode(0f, sixty, panel))
    }
}
