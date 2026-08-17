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

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.AspectRatioMode
import dev.quiblo.core.model.SeekInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Can the timeline be reached, used, and left, with a remote and nothing else?
 *
 * `TvScrubStateTest` proves the arithmetic. This proves the part the arithmetic cannot: that a
 * viewer can get onto the bar, that left and right reach it rather than moving focus off it,
 * that up and down still leave — a timeline a remote cannot get off is worse than no timeline —
 * and that a live stream, which has nothing to scrub, does not grow one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvTimelineFocusTest {

    @get:Rule
    val compose = createComposeRule()

    private var seekedTo = mutableListOf<Long>()

    @Test
    fun `down from play and pause lands on the timeline`() {
        setUp()

        press(Key.DirectionDown)

        compose.onNodeWithContentDescription(TIMELINE).assertIsFocused()
    }

    /** And back off it again. A bar that swallows up is a player a viewer is stuck in. */
    @Test
    fun `up and down still leave the timeline`() {
        setUp()
        press(Key.DirectionDown)

        press(Key.DirectionUp)
        compose.onNodeWithContentDescription(PAUSE).assertIsFocused()

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription(SUBTITLES).assertIsFocused()
    }

    /**
     * Left and right belong to the scrub, not to the focus search.
     *
     * Without the keys being consumed, a press at either end of the film walks focus sideways to
     * whichever rectangle the geometric search finds — which on this screen is a button in a row
     * the viewer was not aiming at.
     */
    @Test
    fun `left and right stay on the timeline`() {
        setUp()
        press(Key.DirectionDown)

        press(Key.DirectionRight)
        compose.onNodeWithContentDescription(TIMELINE).assertIsFocused()

        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription(TIMELINE).assertIsFocused()
    }

    /**
     * A run of presses produces one seek, and it is the sum of them.
     *
     * The seek buttons produce one per press, which is the behaviour this replaces: eight
     * presses were eight re-buffers. Waited for by the clock rather than asserted immediately,
     * because the commit is deliberately delayed — an assertion that passes without the wait is
     * an assertion that would also pass with the delay removed.
     */
    @Test
    fun `a run of presses becomes one seek at the end of it`() {
        setUp()
        press(Key.DirectionDown)

        repeat(3) { press(Key.DirectionRight) }
        assertTrue("Nothing may be sent while the viewer is still pressing.", seekedTo.isEmpty())

        compose.waitUntil(COMMIT_TIMEOUT_MILLIS) { seekedTo.isNotEmpty() }

        assertEquals("One seek, not three.", 1, seekedTo.size)
        assertEquals(POSITION + 3 * STEP_MILLIS, seekedTo.single())
    }

    /** Live has nothing to scrub, so down goes where it always went. */
    @Test
    fun `a live stream has no timeline to focus`() {
        setUp(state().copy(isSeekable = false, isLive = true, durationMillis = 0L))

        compose.onNodeWithContentDescription(TIMELINE).assertDoesNotExist()

        press(Key.DirectionDown)
        compose.onNodeWithContentDescription(SUBTITLES).assertIsFocused()
    }

    private fun setUp(state: TvControlsState = state()) {
        compose.setContent { Harness(state) }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** The real controls at the panel's real geometry. See `TvPlayerControlsReachableTest`. */
    @Composable
    private fun Harness(state: TvControlsState) {
        val playPauseFocus = remember { FocusRequester() }

        Box(modifier = Modifier.size(width = CONTENT_WIDTH, height = CONTENT_HEIGHT)) {
            TvPlayerControls(
                state = state,
                actions = TvControlActions(
                    playPause = {},
                    skip = {},
                    seekTo = { seekedTo += it },
                    nextEpisode = {},
                    previousEpisode = {},
                    openAudio = {},
                    openSubtitles = {},
                    cycleAspect = {},
                ),
                playPauseFocus = playPauseFocus,
            )
        }

        LaunchedEffect(Unit) { runCatching { playPauseFocus.requestFocus() } }
    }

    private fun state() = TvControlsState(
        title = "An episode",
        isPlaying = true,
        isSeekable = true,
        isLive = false,
        positionMillis = POSITION,
        durationMillis = 2_400_000L,
        seekInterval = SeekInterval.TEN,
        aspectRatioMode = AspectRatioMode.FIT,
        hasAudioChoice = true,
        hasSubtitleChoice = true,
        hasNextEpisode = true,
        hasPreviousEpisode = true,
    )

    private companion object {
        /** The Haier's usable area after overscan. See `TvDetailOpensAtTopTest`. */
        val CONTENT_WIDTH = 864.dp
        val CONTENT_HEIGHT = 444.dp

        const val POSITION = 60_000L

        /** `SeekInterval.TEN`, in milliseconds — the first presses of a run are worth exactly it. */
        const val STEP_MILLIS = 10_000L

        /** Comfortably past the commit delay, without being a test that sits there. */
        const val COMMIT_TIMEOUT_MILLIS = 5_000L

        const val TIMELINE = "Timeline. Left and right to move through, then wait."
        const val PAUSE = "Pause"
        const val SUBTITLES = "Subtitles"
    }
}
