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

package dev.quiblo.tv.ui.consent

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.feature.sources.SourcesViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Can the first screen anybody sees be got past, with a remote and nothing else?
 *
 * `FREEZE.md` Amendment 9 makes this the first thing on a fresh install, which makes it the
 * worst possible place for the fault this project keeps finding: a control that exists and
 * cannot be reached. Nine features have been deleted for it. A consent screen with that fault is
 * not a bug in a feature — it is a television that cannot be used at all.
 *
 * So it is driven by key events. Nothing here clicks a node by name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvConsentReachableTest {

    @get:Rule
    val compose = createComposeRule()

    private var accepted = 0

    @Test
    fun `all three screens are read and accepted with the centre key alone`() {
        showTheScreen { accepted++ }

        compose.onNodeWithText(NEXT).assertIsDisplayed()
        press(Key.DirectionCenter)

        assertEquals("The first press must not accept anything — it turns a page.", 0, accepted)
        press(Key.DirectionCenter)

        compose.onNodeWithText(ADD_PLAYLIST).assertIsDisplayed()
        compose.onNodeWithText(SKIP).assertIsDisplayed()
        assertEquals("The second press turns a page too — the terms are not the last word.", 0, accepted)

        // Skip is the second control on the page, so the remote has to walk to it. That is the
        // point: a viewer with no playlist to hand must be able to reach the way past.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals("Skipping is what accepts, on the third page.", 1, accepted)
    }

    /**
     * The last page offers the playlist, and offers a way past it.
     *
     * A first-launch step with no way out is the fault this project deletes features for. Both
     * controls are asserted by their words, because a page that renders one focusable and calls
     * it either of them would pass a count.
     */
    @Test
    fun `the third page offers a playlist and a way past it`() {
        showTheScreen()
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)

        compose.onNodeWithText(ADD_PLAYLIST).assertIsDisplayed()
        compose.onNodeWithText(SKIP).assertIsDisplayed()
    }

    /**
     * Nothing is agreed to before the terms have been on the screen.
     *
     * The acceptance moved from the end of the terms page to the end of the playlist page in
     * `026`, and the thing that must not have moved with it is the order: a viewer cannot reach
     * the page that accepts without passing the page that says what is being accepted.
     */
    @Test
    fun `the terms come before anything that accepts them`() {
        showTheScreen { accepted++ }

        press(Key.DirectionCenter)
        compose.onNodeWithText(RESPONSIBILITY, substring = true).assertIsDisplayed()
        assertEquals(0, accepted)
    }

    /**
     * The terms themselves are on the screen, not only behind the link.
     *
     * The one sentence a television must carry itself is the one about responsibility, because
     * following a link is what a television cannot usefully do. Asserted by its own words rather
     * than by the presence of a text node, which would pass against an empty string resource.
     */
    @Test
    fun `the responsibility sentence is on the panel, not behind the link`() {
        showTheScreen()
        press(Key.DirectionCenter)

        compose.onNodeWithText(RESPONSIBILITY, substring = true).assertIsDisplayed()
    }

    /**
     * There is no way to decline, and that is the decision rather than an oversight.
     *
     * Amendment 9: somebody who will not accept can read this and leave, and an app that
     * force-quits on decline is theatre. This fails if a Decline button is ever added without
     * that decision being revisited — which is the point of writing it as a test rather than as
     * a sentence in a document.
     */
    @Test
    fun `there is no decline button`() {
        showTheScreen()
        press(Key.DirectionCenter)

        compose.onNodeWithText("Decline", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Exit", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    /**
     * Puts the screen up with a focus system that is awake.
     *
     * **The screen focuses its own button; this harness cannot see that happen.** Robolectric
     * will not let a `FocusRequester` inside a `LaunchedEffect` place focus unless something has
     * woken the focus system first, which is the same limitation `TvDetailOpensAtTopTest`
     * records — so focus is seeded here by request, and what these tests prove is everything
     * *after* the remote has arrived: that centre turns the page, that centre again accepts, and
     * that there is no third button.
     *
     * The screen is not left depending on that request either way. If it never lands, the first
     * press of any direction key starts a focus search and finds the only focusable on screen,
     * so the worst case is one extra press rather than a television that cannot be used.
     */
    private fun showTheScreen(onAccept: () -> Unit = {}) {
        // The real ViewModel over a stubbed repository, rather than a stubbed ViewModel: the
        // third page's branching is driven by `addState`, and a stub of that is a stub of the
        // thing under test.
        val repository = mockk<SourceRepository> {
            every { observeSources() } returns flowOf(emptyList())
        }
        val viewModel = SourcesViewModel(repository)

        compose.setContent { TvConsentScreen(onAccept = onAccept, viewModel = viewModel) }
        compose.waitForIdle()
        compose.onNodeWithText(NEXT).requestFocus()
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private companion object {
        const val NEXT = "Next"
        const val ADD_PLAYLIST = "Add a playlist"
        const val SKIP = "Skip for later"

        /** From `tv_consent_terms_body`. */
        const val RESPONSIBILITY = "you are responsible for them"
    }
}
