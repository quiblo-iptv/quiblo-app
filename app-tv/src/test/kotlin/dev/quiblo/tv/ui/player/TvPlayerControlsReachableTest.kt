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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
 * Can a viewer holding only a remote reach every control the player draws?
 *
 * **This is the check that this feature most needs, and it is the one reading the code cannot
 * make.** The television player had no focusable controls at all until now — the remote drove
 * playback directly and everything past the five keys it has was smuggled onto a key that
 * already meant something else. Replacing that with buttons trades one failure for another: a
 * key map that keeps consuming the arrows leaves every button drawn, correct, and unreachable.
 * That is the hollow-feature shape arrived at from the opposite direction, and this project has
 * met it for real — twelve licence rows built non-focusable, of which only the top two could
 * ever be reached, caught by a walk exactly like this one and by nothing else.
 *
 * So the buttons are walked to with key events rather than found and clicked. `performClick` on
 * a node found by its description proves the composable exists, which was never in doubt.
 *
 * **What this cannot prove.** It composes [TvPlayerControls] directly, so it says nothing about
 * whether the screen around them ever puts focus here in the first place — that half is
 * `TvPlayerKeyMapTest`'s "the arrows belong to the controls while the controls are up", which
 * is the rule that lets focus traversal see an arrow at all. The two together cover the path;
 * neither covers it alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvPlayerControlsReachableTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the remote lands on play and pause`() {
        setUp()

        compose.onNodeWithContentDescription(PAUSE).assertIsFocused()
    }

    /**
     * Left off the play button reaches rewind and then the previous episode.
     *
     * One press per button and no more: slack in the walk would mean something between them is
     * swallowing presses, and that is the fault this is looking for rather than a detail of it.
     */
    @Test
    fun `the transport row walks left to the previous episode`() {
        setUp()

        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription(REWIND).assertIsFocused()

        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription(PREVIOUS).assertIsFocused()
    }

    @Test
    fun `the transport row walks right to the next episode`() {
        setUp()

        press(Key.DirectionRight)
        compose.onNodeWithContentDescription(FORWARD).assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithContentDescription(NEXT).assertIsFocused()
    }

    /**
     * Down from the transport lands in the options row, and this is the whole of "press down,
     * the player appears, press down again, navigate them".
     *
     * It is stated in the layout with `focusProperties` rather than left to Compose's geometric
     * search, because the two rows barely overlap — the transport is centred and the options sit
     * against the left margin — so a search would answer this from how wide the row happened to
     * come out. This is what fails if somebody takes that statement back out.
     */
    @Test
    fun `down from the transport reaches the options row`() {
        setUp()

        // Two presses since `026`: the timeline sits between the rows and is the first thing
        // below the transport. The options row is still exactly one press further down.
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        compose.onNodeWithContentDescription(SUBTITLES).assertIsFocused()
    }

    @Test
    fun `the options row walks along all of itself`() {
        setUp()
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        press(Key.DirectionRight)
        compose.onNodeWithContentDescription(AUDIO).assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithContentDescription(FIT).assertIsFocused()
    }

    /** And back up again, through the timeline, to the button the remote started on. */
    @Test
    fun `up from the options row returns to play and pause`() {
        setUp()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionRight)

        press(Key.DirectionUp)
        compose.onNodeWithContentDescription(TIMELINE).assertIsFocused()

        press(Key.DirectionUp)
        compose.onNodeWithContentDescription(PAUSE).assertIsFocused()
    }

    /**
     * The row is not a fixed five, and down still finds its first button.
     *
     * A film has no episodes either side of it and a stream can have one audio track, so the
     * options row's first button is subtitles, or audio, or picture fit depending on what is
     * playing. The `focusProperties` target is bound to whichever comes first for that reason,
     * and this is the case where naming one of the three by hand would have been wrong.
     */
    @Test
    fun `a film with one audio track still walks down to picture fit`() {
        setUp(
            state().copy(
                hasNextEpisode = false,
                hasPreviousEpisode = false,
                hasAudioChoice = false,
                hasSubtitleChoice = false,
            ),
        )

        press(Key.DirectionDown)
        press(Key.DirectionDown)

        compose.onNodeWithContentDescription(FIT).assertIsFocused()
    }

    /**
     * A live channel offers play and nothing either side of it.
     *
     * Worth its own case because the seek buttons are the ones a viewer would reach for first,
     * and a control that is drawn and does nothing on a live stream is the thing the key map
     * has always refused to be.
     */
    @Test
    fun `a live channel draws no seek and no episode buttons`() {
        setUp(state().copy(isSeekable = false, isLive = true, hasNextEpisode = false, hasPreviousEpisode = false))

        compose.onNodeWithContentDescription(PAUSE).assertIsFocused()
        compose.onNodeWithContentDescription(REWIND).assertDoesNotExist()
        compose.onNodeWithContentDescription(FORWARD).assertDoesNotExist()
        compose.onNodeWithContentDescription(NEXT).assertDoesNotExist()
        compose.onNodeWithContentDescription(PREVIOUS).assertDoesNotExist()
    }

    /**
     * Prints the walk instead of asserting it.
     *
     * A harness has to be checked before it is believed — kept for the next person, as
     * `TvBrowseScrollStabilityTest` keeps its own.
     */
    @Test
    fun diagnostics() {
        setUp()
        println("start: ${focusedLabel()}")
        listOf(Key.DirectionDown, Key.DirectionRight, Key.DirectionUp).forEach { key ->
            press(key)
            println("$key -> ${focusedLabel()}")
        }
    }

    private fun focusedLabel(): String =
        compose.onNode(isFocused())
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString()
            ?: "(nothing)"

    /**
     * Puts the remote where the screen puts it.
     *
     * Seeded by request rather than by key, because the press that opens the controls is the
     * screen's job and not this composable's — see the note on the class about what that leaves
     * uncovered. Every step after this one is a key.
     */
    private fun setUp(state: TvControlsState = state()) {
        compose.setContent { Harness(state) }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /**
     * The real controls at the panel's real geometry, never a copy of them.
     *
     * The box is the panel. `TvPlayerControls` fills whatever it is given and places the
     * transport at the centre of it, so a harness at the wrong size is not a weaker instrument
     * but a lying one — the same correction `TvDetailOpensAtTopTest` had to make when a guessed
     * 912x492 made a real fault disappear.
     */
    @Composable
    private fun Harness(state: TvControlsState) {
        val playPauseFocus = remember { FocusRequester() }

        Box(modifier = Modifier.size(width = CONTENT_WIDTH, height = CONTENT_HEIGHT)) {
            TvPlayerControls(
                state = state,
                actions = noActions,
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
        positionMillis = 60_000L,
        durationMillis = 2_400_000L,
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

        /** The spoken labels from `strings.xml`, which are also how a test finds an icon. */
        const val PAUSE = "Pause"
        const val REWIND = "Back 10 seconds"
        const val FORWARD = "Forward 10 seconds"
        const val NEXT = "Next episode"
        const val PREVIOUS = "Previous episode"
        const val SUBTITLES = "Subtitles"
        const val AUDIO = "Audio"
        const val FIT = "Picture fit: FIT"
        const val TIMELINE = "Timeline. Left and right to move through, then wait."
    }
}
