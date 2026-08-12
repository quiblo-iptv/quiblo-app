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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

private const val ABOVE = "the control above the box"
private const val BELOW = "the heading below the box"

/**
 * Can a remote get **out** of the category box at both ends?
 *
 * `INC-F8` was rebuilt to put the category list back inside a fixed-height scroller, at the
 * tester's request. On a phone that is a drag; on a television it is a focus problem before it
 * is a layout problem, and the failure mode is the worst one this app has: **a region the D-pad
 * enters and cannot leave**. `AC-TV-01` and `AC-TV-03` both fail on it, and a viewer's only way
 * out is to kill the app.
 *
 * The reason this file exists rather than a comment asserting that `focusGroup()` handles it:
 * **this project has been wrong about television focus four times, and every one of those was a
 * confident argument rather than a measurement.** #021 is still open on this very screen and
 * #026 was found on the settings button beside it. A fifth argument was not worth making.
 *
 * What is reproduced here is the *shape* — an outer scrollable list, a bounded inner scrollable
 * marked `focusGroup()`, and a focusable above and below it — rather than the settings screen
 * itself, which would drag Koin and a database in for a question about focus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    // A bare Application rather than QuibloTvApplication: that one starts Koin, and Koin's
    // global context outlives a Robolectric test, so the second test in this class failed on
    // an already-started container. Nothing under test here is injected.
    application = Application::class,
)
class TvCategoryBoxFocusEscapeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `up from the first category leaves the box`() {
        setContent(categoryCount = 6)

        compose.onNodeWithText(category(1)).requestFocus()
        compose.onNodeWithText(category(1)).performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()

        assertTrue(
            "focus did not leave the box upward — a remote entering it can never go back",
            compose.onNodeWithText(ABOVE).isFocused(),
        )
    }

    @Test
    fun `down from the last category leaves the box`() {
        val count = 6
        setContent(categoryCount = count)

        compose.onNodeWithText(category(count)).requestFocus()
        compose.onNodeWithText(category(count)).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()

        assertTrue(
            "focus did not leave the box downward — the rest of Settings is unreachable",
            compose.onNodeWithText(BELOW).isFocused(),
        )
    }

    /**
     * The same, with more categories than the box can compose at once.
     *
     * **This is the case the reasoning cannot cover.** A lazy list does not compose what is off
     * screen, so at the bottom of a long list there may be no next focusable child *yet* — and
     * whether focus escapes or waits for composition is exactly the sort of frame-timing
     * question this project keeps guessing wrong.
     */
    @Test
    fun `down from the last of many categories still leaves the box`() {
        val count = 300
        setContent(categoryCount = count)

        // Walk to the end rather than asking for the last node: asking a node for focus skips
        // the focus *search*, and inside a lazy list that search is itself the scroll — the
        // lesson `TvBrowseScrollStabilityTest` records.
        compose.onNodeWithText(category(1)).requestFocus()
        repeat(count + 2) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.waitForIdle()

        assertTrue(
            "focus never reached the heading below a long category list",
            compose.onNodeWithText(BELOW).isFocused(),
        )
    }

    private fun setContent(categoryCount: Int) {
        compose.setContent {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { FocusableRow(ABOVE) }

                item {
                    val shape = RoundedCornerShape(8.dp)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 420.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                            .clip(shape)
                            .focusGroup(),
                    ) {
                        items(items = (1..categoryCount).toList(), key = { it }) { index ->
                            FocusableRow(category(index))
                        }
                    }
                }

                item { FocusableRow(BELOW) }
            }
        }
    }

    private fun category(index: Int) = "Category $index"
}

/** A focusable row that says what it is, so a focus assertion can name it. */
@Composable
private fun FocusableRow(label: String) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {},
    )
}

/** Whether this node currently holds focus, read rather than asserted so the message can say why. */
private fun SemanticsNodeInteraction.isFocused(): Boolean =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true
