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
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.MediaKind
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
 * Can a remote get **past** the category box, and back **out** of it?
 *
 * Two different questions, and the box answers them differently now. It used to be a scroller
 * a viewer walked through: focus entered at the first category and left at the last, so passing
 * it on the way to the rows underneath cost one press per category — two hundred of them on a
 * real panel. It is now shut by default, opened by a press, and closed by Back.
 *
 * So what has to hold is:
 *
 * 1. **Shut, it is one row.** One Down press and the box is behind you.
 * 2. **Open, focus is inside it.** The categories are reachable and the box does not spill focus
 *    into the settings rows underneath while it is open.
 * 3. **Back closes it**, and focus comes back to the box rather than vanishing — a screen with
 *    nothing focused is a screen a remote cannot move on.
 *
 * The reason this file exists rather than a comment asserting that `focusGroup()` handles it:
 * **this project has been wrong about television focus four times, and every one of those was a
 * confident argument rather than a measurement.** A fifth argument was not worth making.
 *
 * It composes the real [CategoryBox] between two focusables rather than a copy of its shape. The
 * copy was the earlier design here and it was the wrong one — a reproduction is free to drift
 * away from the thing it reproduces, and this box is exactly the sort of code that gets adjusted.
 * [CategoryBox] takes a list and two lambdas, so nothing is dragged in by composing it.
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

    /*
     * An activity rule, not a bare one: the box closes itself with a `BackHandler`, and that
     * needs an `OnBackPressedDispatcherOwner` above it. `ComponentActivity` is one.
     */
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the shut box is one press to pass`() {
        setContent(categoryCount = 300)

        compose.onNodeWithText(ABOVE).requestFocus()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()

        assertTrue(
            "a shut box still costs more than one press to walk past",
            compose.onNodeWithText(BELOW).isFocused(),
        )
    }

    @Test
    fun `pressing the shut box opens it onto the categories`() {
        setContent(categoryCount = 6)

        open()

        assertTrue(
            "the box did not open",
            compose.onAllNodesWithText(category(1)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the box opened but focus did not reach the first category's controls",
            compose.firstRowControl().isFocused(),
        )
    }

    /**
     * The case the reasoning cannot cover.
     *
     * A lazy list does not compose what is off screen, so at the bottom of a long list there may
     * be no next focusable child *yet* — and whether focus waits for composition or escapes the
     * group is exactly the sort of frame-timing question this project keeps guessing wrong. Focus
     * must reach the settings rows underneath rather than sticking at the end of the categories,
     * and the box must not be left standing open behind it.
     */
    @Test
    fun `walking off the end of an open box leaves it and shuts it`() {
        val count = 300
        setContent(categoryCount = count)

        open()
        // Twice per category: the pencil and the hide chip beside it, and a few spare to carry
        // focus past the last one.
        repeat(count * CONTROLS_PER_ROW + 4) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.waitForIdle()

        assertTrue(
            "focus never reached the settings rows below a long category list",
            compose.onNodeWithText(BELOW).isFocused(),
        )
        assertTrue(
            "the box was walked out of and left standing open",
            compose.onAllNodesWithText(category(1)).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `back closes the box and focus comes back to it`() {
        setContent(categoryCount = 6)

        open()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertTrue(
            "Back left the box open",
            compose.onAllNodesWithText(category(1)).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "Back closed the box and focus went nowhere — the remote is stranded",
            compose.onNodeWithText(SUMMARY).isFocused(),
        )
    }

    /**
     * The first focusable *inside* the box.
     *
     * Not the category's name — that is a label, and the things a remote lands on are the pencil
     * and the hide chip beside it. The pencil is first, and it carries the rename field's label
     * as its description.
     */
    private fun ComposeContentTestRule.firstRowControl(): SemanticsNodeInteraction =
        onAllNodesWithContentDescription(RENAME)[0]

    /** Focus the shut box and press it, which is the only way in. */
    private fun open() {
        compose.onNodeWithText(SUMMARY).requestFocus()
        compose.onNodeWithText(SUMMARY).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    private fun setContent(categoryCount: Int) {
        compose.setContent {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { FocusableRow(ABOVE) }

                item {
                    CategoryBox(
                        categories = (1..categoryCount).map {
                            Category(id = it.toLong(), sourceId = 1L, title = category(it))
                        },
                        kind = MediaKind.VOD,
                        onToggleHidden = {},
                        onRename = { _, _ -> },
                        onMove = { _, _ -> },
                    )
                }

                item { FocusableRow(BELOW) }
            }
        }
    }

    private fun category(index: Int) = "Category $index"
}

/**
 * What the shut box reads.
 *
 * The count is not asserted here — `tv_settings_category_summary` is a formatted resource and
 * this test is about focus, so it finds the box by the one part of it that does not move.
 */
private const val SUMMARY = "Press to edit"

/** `tv_settings_rename_field`, which the pencil in every category row carries as its description. */
private const val RENAME = "Rename to"

/** The pencil and the hide chip. A row costs two presses to cross, not one. */
private const val CONTROLS_PER_ROW = 2

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
