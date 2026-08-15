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

package dev.quiblo.tv.ui.search

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quiblo.feature.browse.SearchUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * At rest, is the search screen on the middle of the television?
 *
 * The screen has almost nothing on it at rest — a mark, the app's name, a field — so where that
 * block sits *is* the screen. It was centred inside the box the shell hands it, which begins under
 * the tab bar and inside the overscan padding, so the whole composition sat visibly high on a
 * panel while measuring as perfectly centred in the layout that produced it.
 *
 * **The assertion is an invariance rather than a coordinate**, and that is what makes it worth
 * having. A block centred on the panel lands in the same place however tall the bar above it is; a
 * block centred on its container moves down with the bar. So the test grows the bar under the same
 * composition and compares, which needs to know nothing about the mark's size, the field's height,
 * or the constants inside the header — none of which are the fault, and all of which a
 * coordinate-based test would have to be updated for every time somebody adjusted them.
 *
 * That matters more than it sounds: the bar has already grown once, when the profile control
 * joined the gear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvSearchRestingCentreTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the resting block ignores the height of the bar above it`() {
        // One composition, driven twice. `setContent` may only be called once on a rule, and
        // a second rule would be a second panel — so the bar is state and the test changes it,
        // which is also nearer what happens on a television: the bar grows in place.
        val bar = mutableStateOf(SHORT_BAR)
        compose.setContent { Harness(bar) }
        compose.waitForIdle()
        val underShortBar = blockTop()

        bar.value = TALL_BAR
        compose.waitForIdle()
        val underTallBar = blockTop()

        assertTrue(
            "The block rests at ${"%.1f".format(underShortBar)}px under a $SHORT_BAR bar and " +
                "${"%.1f".format(underTallBar)}px under a $TALL_BAR one. It is being centred on " +
                "the box under the bar rather than on the panel, so it sits lower the more " +
                "chrome there is above it.",
            abs(underShortBar - underTallBar) <= TOLERANCE_PX,
        )
    }

    /**
     * Above the half-way line, and not by much.
     *
     * The screen deliberately does not sit on the panel's true middle — a block that does reads
     * as low, because the bar above it is taken for the top edge of the picture and there is
     * always something below it. So the assertion is a band rather than a point: high enough to
     * be the deliberate lift, nowhere near high enough to be the old fault of centring on the
     * container, which put the block a whole bar's height up.
     *
     * A range, not the constant, on purpose. Asserting `panel * 0.46` would restate the line
     * under test and pass for any value somebody typed there; this fails if the lift disappears
     * and fails if it grows into something a viewer would call top-aligned.
     */
    @Test
    fun `the resting block sits just above the middle of the panel`() {
        // The invariance above would also be satisfied by a block pinned to the top of the
        // screen. This is the other half: it has to be near the middle.
        val bar = mutableStateOf(SHORT_BAR)
        compose.setContent { Harness(bar) }
        compose.waitForIdle()

        val top = blockTop()
        val bottom = blockBottom()
        val panel = panelHeight()
        val lift = (panel / 2f) - (top + bottom) / 2f

        assertTrue(
            "The block runs from ${"%.1f".format(top)}px to ${"%.1f".format(bottom)}px on a " +
                "${panel}px panel, so its middle sits ${"%.1f".format(lift)}px above the half-way " +
                "line. That is outside the ${MIN_LIFT * 100}%–${MAX_LIFT * 100}% of the panel " +
                "height this screen lifts by.",
            lift in panel * MIN_LIFT..panel * MAX_LIFT,
        )
    }

    /**
     * Prints the geometry rather than asserting it.
     *
     * Run with `--tests '*TvSearchRestingCentreTest.diagnostics*'`. The numbers say whether the
     * block is a few pixels off or half a screen, which is the difference between a rounding
     * question and a wrong reference rectangle.
     */
    @Test
    fun `diagnostics`() {
        val bar = mutableStateOf(SHORT_BAR)
        compose.setContent { Harness(bar) }
        compose.waitForIdle()

        val wordmark = compose.onNodeWithTag(WORDMARK_TAG).fetchSemanticsNode()
        val fieldRow = compose.onNodeWithTag(FIELD_ROW_TAG).fetchSemanticsNode()

        println("panel ${panelHeight()}px tall, middle at ${panelHeight() / 2f}")
        println("wordmark y ${wordmark.positionInRoot.y} h ${wordmark.size.height}")
        println("fieldRow y ${fieldRow.positionInRoot.y} h ${fieldRow.size.height}")
        println("block    ${blockTop()} → ${blockBottom()}, middle ${(blockTop() + blockBottom()) / 2f}")
    }

    /**
     * The top of the block, which is the top of the wordmark column above the field.
     *
     * Both edges are read off tags rather than off the text in them. The mark is a `Canvas`
     * with no semantics, so the block's top cannot be found by looking for something drawn; and
     * the name sits partway down its column, which made an earlier version of this test measure
     * from 190px below the edge it meant.
     */
    private fun blockTop(): Float =
        compose.onNodeWithTag(WORDMARK_TAG).fetchSemanticsNode().positionInRoot.y

    /** The bottom of the field row, which is the bottom of the block. */
    private fun blockBottom(): Float =
        compose.onNodeWithTag(FIELD_ROW_TAG).fetchSemanticsNode().let {
            it.positionInRoot.y + it.size.height
        }

    private fun panelHeight(): Int = compose.onRoot().fetchSemanticsNode().size.height

    /**
     * The shell as the television builds it: a bar across the top, then the screen inside the
     * overscan padding. Both are what put the container's middle below the panel's.
     */
    @Composable
    private fun Harness(barHeight: MutableState<Dp>) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(barHeight.value))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = SCREEN_PADDING, end = SCREEN_PADDING, bottom = SCREEN_PADDING),
            ) {
                TvSearchPanel(
                    // Nothing asked and no filters open: the resting shape, which is the only
                    // one this test is about.
                    state = SearchUiState(hasSource = true),
                    onOpen = { _, _ -> },
                    onQueryChange = {},
                    onSelectGenre = {},
                    onToggleIncludeHidden = {},
                    onClear = {},
                    onResultVisible = {},
                )
            }
        }
    }

    private companion object {
        /** Near enough what `TvApp`'s bar measures: its padding, a tab label and an underline. */
        val SHORT_BAR = 90.dp

        /**
         * Deliberately unlike it, and deliberately not enormous.
         *
         * Any bar the screen can actually be centred under will do, and that is the point being
         * asserted — but there is a height past which it cannot: the gap is clamped at zero, so
         * once the bar plus half the block reaches the line the screen centres on, the block
         * rests at the top of its container instead and stops being independent of the bar. That
         * clamp is correct — a negative gap would drag the field up under the bar — and it lands
         * at about 135dp here. This stays under it.
         */
        val TALL_BAR = 120.dp

        /** Overscan, as `TvApp` applies it. */
        val SCREEN_PADDING = 48.dp

        /**
         * Half a pixel of rounding, and no more.
         *
         * Deliberately tight. The fault being guarded against moved the block by half the bar's
         * height, so a tolerance loose enough to feel safe would be loose enough to miss it
         * coming back.
         */
        const val TOLERANCE_PX = 1f

        /** The lift off the half-way line, as a share of the panel. Any real lift counts. */
        const val MIN_LIFT = 0.01f

        /** And an upper bound: past this the block is not near the middle, it is near the top. */
        const val MAX_LIFT = 0.10f
    }
}
