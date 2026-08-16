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

package dev.quiblo.tv.ui.common

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * A finger reaches the top bar, and does not become a second way to lose the remote.
 *
 * **The second half is the one worth a test.** `Modifier.clickable` would have made each tab
 * tappable in one word, and would have made each tab *focusable* — which is precisely the shape
 * this bar was rebuilt to get rid of. With a focusable per tab, any event that destroyed the
 * focused element in the content below left Compose falling back to the first focusable in the
 * tree, which was a tab, which selected itself and threw the viewer onto another screen. Content
 * could silently change which tab you were on, and nothing in the build noticed.
 *
 * So [onTap] is a raw tap detector with no focus node, and this asserts both halves: that a tap
 * arrives, and that the thing tapped is still invisible to a focus search.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvTapAddsNoFocusTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a tap arrives`() {
        var taps = 0

        compose.setContent {
            Text(text = TAPPABLE, modifier = Modifier.size(120.dp).onTap { taps++ })
        }
        compose.onNodeWithText(TAPPABLE).performClick()

        assertEquals(1, taps)
    }

    /**
     * And a focus search walks straight past it.
     *
     * The arrangement is the bar in miniature: one real focus target, and beside it something a
     * finger can press. Moving down from the target must reach the *content* below, not the
     * tappable thing in between — which is what would happen if [onTap] were `clickable`.
     */
    @Test
    fun `a tap target is not a focus target`() {
        compose.setContent {
            val bar = remember { FocusRequester() }
            Column {
                Text(
                    text = BAR,
                    modifier = Modifier.size(120.dp).focusRequester(bar).focusable(),
                )
                Text(text = TAPPABLE, modifier = Modifier.size(120.dp).onTap { })
                Text(text = CONTENT, modifier = Modifier.size(120.dp).focusable())
            }
        }

        compose.onNodeWithText(BAR).requestFocus()
        compose.onNodeWithText(BAR).performKeyInput { pressKey(Key.DirectionDown) }

        compose.onNodeWithText(CONTENT).assertIsFocused()
    }

    private companion object {
        const val BAR = "Bar"
        const val TAPPABLE = "Tappable"
        const val CONTENT = "Content"
    }
}
