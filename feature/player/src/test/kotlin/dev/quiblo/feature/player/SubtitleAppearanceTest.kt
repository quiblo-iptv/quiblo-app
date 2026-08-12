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

import dev.quiblo.core.model.SubtitleColor
import dev.quiblo.core.model.SubtitleOpacity
import dev.quiblo.core.model.SubtitleStyle
import dev.quiblo.core.model.SubtitleTextSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * What each appearance row does to the stored style (INC-F11).
 *
 * The rows themselves are drawn by two apps and cannot be tested here. What can be — and what
 * would break silently — is the mapping: which row changes which field, and what "Match system"
 * means when a viewer goes back to it.
 */
class SubtitleAppearanceTest {

    private val custom = SubtitleStyle(
        matchSystem = false,
        textSize = SubtitleTextSize.LARGE,
        textColor = SubtitleColor.YELLOW,
        background = SubtitleColor.BLACK,
        backgroundOpacity = SubtitleOpacity.LIGHT,
    )

    @Test
    fun `picking a size sets the size and stops following the system`() {
        val next = SubtitleStyle().withChoice(TrackMenuKind.SUBTITLE_SIZE, "LARGE")

        assertEquals(SubtitleTextSize.LARGE, next?.textSize)
        assertFalse(next!!.matchSystem)
    }

    @Test
    fun `picking a colour leaves the size alone`() {
        val next = custom.withChoice(TrackMenuKind.SUBTITLE_TEXT_COLOUR, "WHITE")

        assertEquals(SubtitleColor.WHITE, next?.textColor)
        assertEquals(SubtitleTextSize.LARGE, next?.textSize)
    }

    @Test
    @DisplayName("the background is one choice and sets both fields the renderer needs")
    fun `background rows set colour and opacity together`() {
        val none = custom.withChoice(TrackMenuKind.SUBTITLE_BACKGROUND, BACKGROUND_NONE)
        assertEquals(SubtitleColor.TRANSPARENT, none?.background)
        assertEquals(SubtitleOpacity.NONE, none?.backgroundOpacity)

        val solid = custom.withChoice(TrackMenuKind.SUBTITLE_BACKGROUND, BACKGROUND_SOLID)
        assertEquals(SubtitleColor.BLACK, solid?.background)
        assertEquals(SubtitleOpacity.SOLID, solid?.backgroundOpacity)
    }

    @Test
    @DisplayName("Match system forgets what was chosen rather than hiding it behind a flag")
    fun `match system returns the defaults`() {
        val next = custom.withChoice(TrackMenuKind.SUBTITLE_SIZE, null)

        assertEquals(SubtitleStyle(), next)
        assertTrue(next!!.matchSystem)
    }

    @Test
    fun `a value this build does not know changes nothing`() {
        // The shape a stored choice takes after an entry is renamed, and the shape a caller
        // mistake takes. Both answer the same way: leave the style as it is.
        assertNull(custom.withChoice(TrackMenuKind.SUBTITLE_SIZE, "ENORMOUS"))
        assertNull(custom.withChoice(TrackMenuKind.SUBTITLE_BACKGROUND, "background_plaid"))
        assertNull(custom.withChoice(TrackMenuKind.SUBTITLES, "WHITE"))
    }
}
