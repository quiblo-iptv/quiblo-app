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
    private var tracksOpened = 0
    private var episodeSteps = 0

    private val actions = KeyActions(
        showControls = { controlsShown++ },
        playPause = { playPauses++ },
        skip = { skipped += it },
        zap = { zapped += it },
        stepEpisode = { episodeSteps += it },
        cycleAspect = { aspectCycles++ },
        openTracks = { tracksOpened++ },
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

    @Suppress("LongParameterList")
    private fun press(
        key: Key,
        isSeekable: Boolean,
        canZap: Boolean,
        hasFailed: Boolean = false,
        areControlsVisible: Boolean = false,
        isOfferingNextEpisode: Boolean = false,
        canStepEpisode: Boolean = false,
    ) = handleKey(
        key = key,
        context = KeyContext(
            isSeekable = isSeekable,
            canZap = canZap,
            hasFailed = hasFailed,
            areControlsVisible = areControlsVisible,
            isOfferingNextEpisode = isOfferingNextEpisode,
            canStepEpisode = canStepEpisode,
        ),
        actions = actions,
    )

    @Test
    fun `a remote with a subtitle key reaches the menu in one press`() {
        assertTrue(press(Key.Captions, isSeekable = true, canZap = false))
        assertEquals(1, tracksOpened)
    }

    /**
     * The rule the focusable controls stand on.
     *
     * A key consumed here is a key Compose's focus traversal never sees, so if this map kept
     * claiming the arrows once the controls were up, every button on them would be drawn and
     * unreachable — the hollow-feature shape, arrived at from the opposite direction. The
     * licence list reached that state for real by being unfocusable, and a D-pad walk is what
     * caught it; this is the same check made where it costs nothing.
     */
    @Test
    fun `the arrows belong to the controls while the controls are up`() {
        assertFalse(press(Key.DirectionDown, isSeekable = true, canZap = false, areControlsVisible = true))
        assertFalse(press(Key.DirectionUp, isSeekable = true, canZap = false, areControlsVisible = true))
        assertFalse(press(Key.DirectionLeft, isSeekable = true, canZap = false, areControlsVisible = true))
        assertFalse(press(Key.DirectionRight, isSeekable = true, canZap = false, areControlsVisible = true))

        // And nothing happened. An arrow that both moved focus and seeked would jump the film
        // ten seconds every time a viewer stepped from Rewind to Play.
        assertEquals(0, skipped)
        assertEquals(0, zapped)
        assertEquals(0, aspectCycles)
        assertEquals(0, controlsShown)
    }

    /**
     * The keys that are not arrows keep working in both states.
     *
     * A remote's play key means one thing wherever the viewer is looking, and taking it away
     * while the controls happen to be on screen would make the controls appearing — which is
     * something Down does by itself — change what the play key does.
     */
    @Test
    fun `the media keys work whether or not the controls are up`() {
        assertTrue(press(Key.MediaPlayPause, isSeekable = true, canZap = false, areControlsVisible = true))
        assertTrue(press(Key.MediaRewind, isSeekable = true, canZap = false, areControlsVisible = true))
        assertTrue(press(Key.ChannelUp, isSeekable = false, canZap = true, areControlsVisible = true))

        assertEquals(1, playPauses)
        assertEquals(-1, skipped)
        assertEquals(-1, zapped)
    }

    @Test
    fun `the episode keys step a series and are left alone on anything else`() {
        assertTrue(press(Key.MediaNext, isSeekable = true, canZap = false, canStepEpisode = true))
        assertEquals(1, episodeSteps)

        assertTrue(press(Key.MediaPrevious, isSeekable = true, canZap = false, canStepEpisode = true))
        assertEquals(0, episodeSteps)

        // A film and a channel have no episodes, so the keys are left unhandled rather than
        // silently doing nothing — the same rule seeking follows on a live stream.
        assertFalse(press(Key.MediaNext, isSeekable = true, canZap = false, canStepEpisode = false))
        assertFalse(press(Key.MediaPrevious, isSeekable = false, canZap = true, canStepEpisode = false))
        assertEquals(0, episodeSteps)
    }

    /**
     * The end of an episode belongs to the banner, the way a dead stream belongs to Try again.
     *
     * The dangerous one is the play key: an episode has finished, so pressing it does nothing
     * a viewer can see, and if this map answered it the press would never reach Play now.
     */
    @Test
    fun `the offer of a next episode answers no key at all`() {
        assertFalse(press(Key.DirectionCenter, isSeekable = true, canZap = false, isOfferingNextEpisode = true))
        assertFalse(press(Key.DirectionLeft, isSeekable = true, canZap = false, isOfferingNextEpisode = true))
        assertFalse(press(Key.DirectionDown, isSeekable = true, canZap = false, isOfferingNextEpisode = true))
        assertFalse(press(Key.MediaPlayPause, isSeekable = true, canZap = false, isOfferingNextEpisode = true))
        assertFalse(press(Key.Menu, isSeekable = true, canZap = false, isOfferingNextEpisode = true))

        assertEquals(0, playPauses)
        assertEquals(0, skipped)
        assertEquals(0, controlsShown)
        assertEquals(0, aspectCycles)
    }
}
