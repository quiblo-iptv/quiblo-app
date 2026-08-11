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

package dev.quiblo.tv.ui.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import dev.quiblo.feature.settings.THIRD_PARTY_LICENSES
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Can a viewer holding only a remote actually read the licences?
 *
 * **This is an obligation, not a preference.** Every third-party component in both apps is
 * Apache-2.0, which requires its notices to travel with the binary. The phone has shown them
 * since it had a settings screen; the television linked the same libraries and showed nothing,
 * so `AC-LEGAL-03` was recorded as passing while it was true of one of the two apps we ship.
 *
 * **Why it is driven with key events rather than clicked.** This project has deleted nine
 * features that existed in code and could not be reached from a remote, and the test that would
 * have caught every one of them is this one: walk to the control the way a viewer walks to it,
 * and press it the way a viewer presses it. `performClick` on a node found by text proves the
 * composable exists, which was never the thing in doubt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvLicencesReachableTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the licences open from the remote`() {
        setUpAtTheRowAbove()

        compose.onNodeWithText(SHOW).assertIsFocused()
        press(Key.DirectionCenter)

        // By name rather than by count: twelve rows that render nothing would satisfy a count.
        compose.onNodeWithText(THIRD_PARTY_LICENSES.first().name).assertIsDisplayed()
    }

    /**
     * **Every** component can be reached, walking down the way a viewer walks down.
     *
     * This is the assertion that changed the screen. The entries were not focusable at first —
     * there is nothing to press on them, so a focus stop looked like pure cost — and this test
     * could not reach the twelfth. On a television, **moving focus is how a list scrolls**: a
     * row that never takes focus is a row the remote cannot bring on screen. Twelve entries
     * would have been the two that fit and a promise about the rest, which is precisely the
     * shape of the nine features this project has deleted for being unreachable.
     *
     * Attribution nobody can read is not attribution.
     */
    @Test
    fun `the remote can walk to the last component`() {
        setUpAtTheRowAbove()
        press(Key.DirectionCenter)

        // One press per entry, and no more: if the walk needed slack, something between them
        // would be swallowing presses and that is the fault this is looking for.
        repeat(THIRD_PARTY_LICENSES.size) { press(Key.DirectionDown) }

        compose.onNodeWithText(THIRD_PARTY_LICENSES.last().name).assertIsDisplayed()
    }

    /**
     * The same press closes them again.
     *
     * Worth asserting because the label is the only thing that says which way the control will
     * go, and a control whose label stops matching what it does is how a viewer learns not to
     * trust the screen.
     */
    @Test
    fun `pressing again puts them away`() {
        setUpAtTheRowAbove()
        press(Key.DirectionCenter)

        compose.onNodeWithText(HIDE).assertIsDisplayed()
        press(Key.DirectionCenter)

        compose.onNodeWithText(SHOW).assertIsDisplayed()
        compose.onNodeWithText(THIRD_PARTY_LICENSES.first().name).assertDoesNotExist()
    }

    /**
     * Collapsed, the section costs the remote one press to walk past.
     *
     * The other half of the decision above. Twelve focus stops are the right answer for someone
     * reading the licences and the wrong one for everybody else, so they exist only while the
     * list is open — and this is what would fail if a later change left them composed and
     * merely invisible.
     */
    @Test
    fun `closed, the entries are not in the way`() {
        setUpAtTheRowAbove()

        press(Key.DirectionDown)

        compose.onNodeWithText(ROW_BELOW).assertIsFocused()
    }

    /**
     * Puts the remote on the row above the control, then walks down onto it.
     *
     * Focus is seeded by request and then *moved by key*, which is the distinction
     * `TvBrowseScrollStabilityTest` records: a focus request skips the focus search, and the
     * focus search is the part of this that can be broken by a layout. Seeding somewhere
     * harmless and arriving by D-pad exercises the path a viewer takes.
     */
    private fun setUpAtTheRowAbove() {
        compose.setContent { Harness() }
        compose.onNodeWithText(ROW_ABOVE).requestFocus()
        compose.waitForIdle()
        press(Key.DirectionDown)
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /**
     * The end of the settings screen, at the panel's real geometry.
     *
     * `aboutSection` itself rather than a copy of it — the film and series detail screens each
     * kept their own copy of one open sequence, the copies disagreed, and the same defect had to
     * be found twice. A harness that reimplements the thing under test tests the harness.
     */
    @Composable
    private fun Harness() {
        var shown by remember { mutableStateOf(false) }

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .size(width = CONTENT_WIDTH, height = CONTENT_HEIGHT)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { FocusableRow(ROW_ABOVE) }
            aboutSection(licensesShown = shown, onToggleLicenses = { shown = !shown })
            item { FocusableRow(ROW_BELOW) }
        }
    }

    /** Something for the remote to rest on, standing in for the rows around this section. */
    @Composable
    private fun FocusableRow(label: String) {
        Text(text = label, modifier = Modifier.clickable {}.padding(vertical = 12.dp))
    }

    private companion object {
        /** The Haier's scrollable area after overscan. See `TvDetailOpensAtTopTest`. */
        val CONTENT_WIDTH = 864.dp
        val CONTENT_HEIGHT = 444.dp

        const val ROW_ABOVE = "The row above"
        const val ROW_BELOW = "The row below"

        /** The chip's two labels, from `strings.xml`. */
        const val SHOW = "Show"
        const val HIDE = "Hide"
    }
}
