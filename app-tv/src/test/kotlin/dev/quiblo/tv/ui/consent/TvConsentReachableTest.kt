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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
    fun `both screens are read and accepted with the centre key alone`() {
        showTheScreen { accepted++ }

        compose.onNodeWithText(NEXT).assertIsDisplayed()
        press(Key.DirectionCenter)

        compose.onNodeWithText(START).assertIsDisplayed()
        assertEquals("The first press must not accept anything — it turns a page.", 0, accepted)

        press(Key.DirectionCenter)
        assertEquals("The second press is the acceptance.", 1, accepted)
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
        compose.setContent { TvConsentScreen(onAccept = onAccept) }
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
        const val START = "Start watching"

        /** From `tv_consent_terms_body`. */
        const val RESPONSIBILITY = "you are responsible for them"
    }
}
