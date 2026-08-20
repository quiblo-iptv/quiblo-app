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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Does the remote survive a control disappearing from under it? — `027` #2.
 *
 * **The reported symptom is "the focus escapes me inside the player": nothing is highlighted, the
 * arrows move nothing, and the only way back is Back and then Down.** The mechanism is that this
 * transport is not a fixed row. The seek buttons and the timeline exist only once a duration has
 * arrived, the subtitle button only once the tracks have been enumerated, the episode steps only
 * once the run is known — and all of that lands a second or two into playback, while a viewer is
 * pressing things. Compose does not move focus to a neighbour when the focused node is removed; it
 * drops it, and the controls are then drawn with nobody on them.
 *
 * So the case here is deliberately the crudest form of it: put the remote on the timeline, then
 * take the timeline away. Nothing about that is contrived — a stream whose duration is re-reported
 * as zero mid-play does exactly this, and it is the same event as every other button appearing.
 *
 * **What is asserted is that the remote is still somewhere in the controls afterwards, not that it
 * is on any particular button.** Compose sometimes rehomes focus itself when a node goes, and when
 * it does that is the better outcome — the viewer keeps a place near where they were, and the host
 * deliberately leaves it alone. The fault is the case where nobody takes it: controls drawn with
 * nothing highlighted, which is what was reported from the sofa and what this fails on.
 *
 * It composes [TvPlayerControlsHost] rather than the screen, because the host is where the repair
 * lives and the screen needs a ViewModel this test has no business building.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvControlsFocusRepairTest {

    @get:Rule
    val compose = createComposeRule()

    private var isSeekable by mutableStateOf(true)

    @Test
    fun `the remote lands on play and pause when the controls appear`() {
        setUp()

        compose.onNodeWithContentDescription(PAUSE).assertIsFocused()
    }

    @Test
    fun `losing the focused control leaves the remote somewhere on the controls`() {
        setUp()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(TIMELINE).assertIsFocused()

        // The duration goes away, and with it the bar the remote was standing on.
        isSeekable = false
        compose.waitForIdle()

        compose.onNode(isFocused()).assertExists(
            "The timeline was removed from under the remote and nothing took focus, so the " +
                "controls are on screen with nothing highlighted and the arrows move nothing " +
                "(`027` #2).",
        )
    }

    /**
     * And the repair does not fight whatever is drawn over the controls.
     *
     * The track panel and the end-of-episode offer both take the remote on purpose, and a host
     * that grabbed it back would make either unusable. `ownsFocus` is what says so; this is the
     * case that fails if it is ever dropped as an unnecessary parameter.
     */
    @Test
    fun `nothing is asked for while something else owns the remote`() {
        compose.setContent { Harness(ownsFocus = false) }
        compose.waitForIdle()

        compose.onNode(isFocused()).assertDoesNotExist()
    }

    private fun setUp() {
        isSeekable = true
        compose.setContent { Harness(ownsFocus = true) }
        compose.waitForIdle()
    }

    @Composable
    private fun Harness(ownsFocus: Boolean) {
        val playPauseFocus = remember { FocusRequester() }

        Box(modifier = Modifier.size(width = CONTENT_WIDTH, height = CONTENT_HEIGHT)) {
            TvPlayerControlsHost(
                state = state(isSeekable),
                actions = noActions,
                playPauseFocus = playPauseFocus,
                ownsFocus = ownsFocus,
            )
        }
    }

    private fun state(isSeekable: Boolean) = TvControlsState(
        title = "An episode",
        isPlaying = true,
        isSeekable = isSeekable,
        isLive = false,
        positionMillis = 60_000L,
        // Zero together with `isSeekable`, because that is how a stream reports itself before the
        // duration is known — the two are one fact and the timeline is drawn from both.
        durationMillis = if (isSeekable) 2_400_000L else 0L,
        seekInterval = SeekInterval.TEN,
        aspectRatioMode = AspectRatioMode.FIT,
        hasAudioChoice = true,
        hasSubtitleChoice = true,
        hasNextEpisode = true,
        hasPreviousEpisode = true,
    )

    private val noActions = TvControlActions(
        playPause = {},
        skip = {},
        seekTo = {},
        nextEpisode = {},
        previousEpisode = {},
        openAudio = {},
        openSubtitles = {},
        cycleAspect = {},
    )

    private companion object {
        /** The Haier's usable area after overscan. See `TvDetailOpensAtTopTest`. */
        val CONTENT_WIDTH = 864.dp
        val CONTENT_HEIGHT = 444.dp

        const val PAUSE = "Pause"
        const val TIMELINE = "Timeline. Left and right to move through, then wait."
    }
}
