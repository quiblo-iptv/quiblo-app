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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * The bar with nothing highlighted on it — `022` #4, reported several times and never pinned.
 *
 * **What was reported is a symptom two steps downstream of the cause.** The gear and the profile
 * face are highlighted when the bar itself holds focus and the remote is resting on them; the tabs
 * keep their underline regardless, because that is *selection* rather than focus. So a screenshot
 * of a bar with an underlined tab and no highlighted icons is a screenshot of a shell where
 * **nothing at all has focus** — and in that state the bar's key handler never runs either, so the
 * remote appears dead until something else takes focus.
 *
 * The cause is a race, and it is narrower than it looks. `requestFocus()` **returns a boolean**;
 * it does not throw when its node exists but has not been placed yet. `TvShell` asked from a
 * `LaunchedEffect`, which runs after composition and before layout has necessarily placed
 * anything, and `tryRequestFocus` dropped that `false` on the floor. The shell leaves composition
 * entirely whenever an overlay opens, so the ask ran again on every return from Settings, a detail
 * screen or the player — and lost often enough to be reported as happening "many times".
 *
 * **A race cannot be asserted by running it and hoping**, so this forces the losing side: the
 * focusable is not in the tree on the frame the request is made. Against `tryRequestFocus` that
 * is exactly the reported state and stays that way forever; against `insistOnFocus` the next
 * frame catches it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvFocusRaceTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a bar that is not placed yet still ends up focused`() {
        var focused = false

        compose.setContent { BarPlacedLate(onFocused = { focused = it }, insist = true) }
        compose.waitForIdle()

        assertTrue("the bar never took focus, so nothing on it can be highlighted", focused)
    }

    /**
     * The same arrangement against the old call, so the test is known to be able to fail.
     *
     * Without this, "the bar ends up focused" is a sentence that would also be true of a test
     * that never forced the race at all — which is how this defect survived a whole round of
     * bar tests already.
     */
    @Test
    fun `and the swallowing version never does`() {
        var focused = false

        compose.setContent { BarPlacedLate(onFocused = { focused = it }, insist = false) }
        compose.waitForIdle()

        assertFalse("the race is no longer being forced, so the test above proves nothing", focused)
    }

    /**
     * A bar whose focusable arrives one frame after the request for it.
     *
     * That single frame is the whole of the defect. On a television it is a slow first layout
     * from cold, or a shell recomposing behind an overlay that is going away; here it is a flag
     * flipped after the first composition, which is the same thing with the timing made certain.
     */
    @Composable
    private fun BarPlacedLate(onFocused: (Boolean) -> Unit, insist: Boolean) {
        val requester = remember { FocusRequester() }
        var placed by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (insist) requester.insistOnFocus() else requester.tryRequestFocus()
        }
        LaunchedEffect(Unit) { placed = true }

        if (placed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(requester)
                    .onFocusChanged { onFocused(it.isFocused) }
                    .focusable(),
            )
        }
    }
}
