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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.AutoNextDelay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * The countdown, driven a second at a time against a clock the test owns.
 *
 * **This is the one thing in the feature that acts on nobody's press.** Everything else waits
 * for a viewer; this starts an episode by itself, in a room where nobody may be looking, so the
 * cases worth holding are the ones where it should *not*: the setting turned off, and a viewer
 * who said Stop. A countdown that ignores either would play a whole series unattended, and the
 * fault would be invisible until somebody looked at their history.
 *
 * `mainClock.autoAdvance = false` is what makes a five-second wait a five-step assertion rather
 * than five real seconds — the method `TvBrowseScrollStabilityTest` records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvNextEpisodeBannerTest {

    @get:Rule
    val compose = createComposeRule()

    private var played = 0
    private var stopped = 0

    @Test
    fun `the count is shown and falls a second at a time`() {
        setUp(AutoNextDelay.FIVE)

        compose.onNodeWithText("Next episode in 5").assertIsDisplayed()

        advancePastOneSecond()
        compose.onNodeWithText("Next episode in 4").assertIsDisplayed()

        advancePastOneSecond()
        compose.onNodeWithText("Next episode in 3").assertIsDisplayed()
    }

    @Test
    fun `reaching zero starts the next episode, once`() {
        setUp(AutoNextDelay.THREE)

        repeat(3) { advancePastOneSecond() }
        assertEquals(1, played)

        // And it does not keep firing. The effect that reaches zero re-runs on every
        // recomposition unless it stops there, which is how one countdown becomes a loop that
        // asks for the next episode several times and races the screen replacing itself.
        repeat(3) { advancePastOneSecond() }
        assertEquals(1, played)
    }

    /**
     * Off means the offer stands and nothing happens on its own.
     *
     * Not a separate switch beside a delay, because "do not do this" and "do it after ten
     * seconds" are the same question. What has to be true is that the banner still appears —
     * the next episode is worth offering however the viewer wants it started.
     */
    @Test
    fun `off offers the episode and never starts it`() {
        setUp(AutoNextDelay.OFF)

        compose.onNodeWithText("Next episode").assertIsDisplayed()

        repeat(20) { advancePastOneSecond() }
        assertEquals(0, played)
    }

    @Test
    fun `stop ends the countdown before it fires`() {
        setUp(AutoNextDelay.FIVE)

        advancePastOneSecond()
        // Left of Play now, which is where the remote lands.
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)

        assertEquals(1, stopped)

        repeat(10) { advancePastOneSecond() }
        assertEquals(0, played)
    }

    /** One press of OK is the whole of "I want the next one now". */
    @Test
    fun `the remote lands on play now`() {
        setUp(AutoNextDelay.FIVE)

        compose.onNodeWithText(PLAY_NOW).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(1, played)
    }

    private fun setUp(delaySetting: AutoNextDelay) {
        compose.mainClock.autoAdvance = false
        compose.setContent { Harness(delaySetting) }
        compose.waitForIdle()
    }

    /**
     * A tick, and a little more than a tick.
     *
     * `advanceTimeBy` moves in whole 16ms frames, so asking for exactly 1,000ms lands on 992 —
     * short of the delay, which then never resumes and makes a working countdown look frozen.
     * That cost a run here; the extra hundred milliseconds is what stops it costing another.
     */
    private fun advancePastOneSecond() {
        compose.mainClock.advanceTimeBy(1_100L)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /**
     * The real banner at the panel's geometry, hidden by Stop the way the screen hides it.
     *
     * The visibility is state here rather than a constant because Stop's whole job is to take
     * the banner away, and a harness that left it on screen would let a countdown carry on
     * counting behind an assertion that it had not.
     */
    @Composable
    private fun Harness(delaySetting: AutoNextDelay) {
        var visible by remember { mutableStateOf(true) }

        Box(modifier = Modifier.size(width = CONTENT_WIDTH, height = CONTENT_HEIGHT)) {
            TvNextEpisodeBanner(
                isVisible = visible,
                delaySetting = delaySetting,
                episodeLabel = "S1 E2 — The next one",
                onPlayNext = { played++ },
                onStop = {
                    stopped++
                    visible = false
                },
            )
        }
    }

    private companion object {
        /** The Haier's usable area after overscan. See `TvDetailOpensAtTopTest`. */
        val CONTENT_WIDTH = 864.dp
        val CONTENT_HEIGHT = 444.dp

        const val PLAY_NOW = "Play now"
    }
}
