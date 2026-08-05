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

import androidx.compose.ui.input.key.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The remote's vocabulary, which on a television *is* the interface.
 *
 * Half of bug #009 was this map being the same whatever was playing: a film was given the
 * channel keys, so pressing up during a film jumped to whichever film happened to sit next
 * in the category. These tests hold the two modes apart.
 */
class TvPlayerKeyMapTest {

    private var zapped = 0
    private var skipped = 0
    private var playPauses = 0
    private var controlsShown = 0
    private var aspectCycles = 0

    private val actions = KeyActions(
        showControls = { controlsShown++ },
        playPause = { playPauses++ },
        skip = { skipped += it },
        zap = { zapped += it },
        cycleAspect = { aspectCycles++ },
    )

    @Test
    fun `channel keys zap on a live stream`() {
        assertTrue(press(Key.ChannelUp, isSeekable = false, canZap = true))
        assertTrue(press(Key.DirectionUp, isSeekable = false, canZap = true))
        assertTrue(press(Key.ChannelDown, isSeekable = false, canZap = true))

        // Up and channel-up both mean "the previous channel"; channel-down means the next.
        assertEquals(-1, zapped)
    }

    @Test
    fun `channel keys never zap on a film`() {
        press(Key.ChannelUp, isSeekable = true, canZap = false)
        press(Key.DirectionUp, isSeekable = true, canZap = false)
        press(Key.ChannelDown, isSeekable = true, canZap = false)

        assertEquals(0, zapped)
    }

    @Test
    fun `aspect can be reached on both kinds of content`() {
        // The controls have always announced the current mode. Until this binding existed
        // nothing could change it — a readout for a control that was not there.
        assertTrue(press(Key.Menu, isSeekable = false, canZap = true))
        assertTrue(press(Key.Info, isSeekable = true, canZap = false))

        // Up doubles as the aspect key on a film, where zapping does not apply and the key
        // is otherwise dead. The Haier's remote has no Menu key, so without this there is no
        // way to reach aspect on a film at all.
        assertTrue(press(Key.DirectionUp, isSeekable = true, canZap = false))

        assertEquals(3, aspectCycles)
    }

    @Test
    fun `up still zaps on a live stream rather than changing aspect`() {
        assertTrue(press(Key.DirectionUp, isSeekable = false, canZap = true))

        assertEquals(-1, zapped)
        assertEquals(0, aspectCycles)
    }

    @Test
    fun `left and right seek a film and are left alone on a live stream`() {
        assertTrue(press(Key.DirectionRight, isSeekable = true, canZap = false))
        assertEquals(1, skipped)

        assertFalse(press(Key.DirectionRight, isSeekable = false, canZap = true))
        assertFalse(press(Key.DirectionLeft, isSeekable = false, canZap = true))
        assertEquals(1, skipped)
    }

    @Test
    fun `centre plays and pauses whatever is on`() {
        assertTrue(press(Key.DirectionCenter, isSeekable = false, canZap = true))
        assertTrue(press(Key.Enter, isSeekable = true, canZap = false))

        assertEquals(2, playPauses)
    }

    @Test
    fun `down opens the controls in both modes`() {
        assertTrue(press(Key.DirectionDown, isSeekable = false, canZap = true))
        assertTrue(press(Key.DirectionDown, isSeekable = true, canZap = false))

        assertEquals(2, controlsShown)
    }

    @Test
    fun `a failed stream answers no key at all`() {
        // Bug #011. The transport keys all mean something about playback, and there is no
        // playback — so every one of them is left for the error screen's buttons, which
        // are the only controls on that screen that do anything.
        assertFalse(press(Key.DirectionCenter, isSeekable = true, canZap = false, hasFailed = true))
        assertFalse(press(Key.DirectionDown, isSeekable = true, canZap = false, hasFailed = true))
        assertFalse(press(Key.DirectionLeft, isSeekable = true, canZap = false, hasFailed = true))
        assertFalse(press(Key.DirectionRight, isSeekable = true, canZap = false, hasFailed = true))
        assertFalse(press(Key.DirectionUp, isSeekable = false, canZap = true, hasFailed = true))
        assertFalse(press(Key.Menu, isSeekable = true, canZap = false, hasFailed = true))

        assertEquals(0, playPauses)
        assertEquals(0, controlsShown)
        assertEquals(0, skipped)
        assertEquals(0, zapped)
        assertEquals(0, aspectCycles)
    }

    private fun press(key: Key, isSeekable: Boolean, canZap: Boolean, hasFailed: Boolean = false) =
        handleKey(
            key = key,
            isSeekable = isSeekable,
            canZap = canZap,
            actions = actions,
            hasFailed = hasFailed,
        )
}
