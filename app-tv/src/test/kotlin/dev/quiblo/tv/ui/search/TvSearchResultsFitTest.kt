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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.feature.browse.SearchUiState
import dev.quiblo.tv.ui.browse.TvCategoryList
import dev.quiblo.tv.ui.browse.TvCategoryRow
import dev.quiblo.tv.ui.browse.TvRowItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Does a result survive being focused on the screen that has the least room for one?
 *
 * #019 reports two things on **Advanced** search that look separate and are one fault: a
 * heading that is not on screen, and titles cropped when focused. The catalogue screens hand a
 * poster row the whole panel; search keeps a field and a strip of genre chips above the
 * results and gives the row what is left. So the same row is being asked to fit into less, and
 * whether it still does is a measurement rather than an opinion.
 *
 * This composes the **real header** with the real chips at the panel's own geometry, so the
 * space left over is the space the television actually leaves — not a number somebody estimated
 * while reading the layout. A first attempt did estimate it, and the estimate was doing all the
 * work.
 *
 * A poster column is 225dp of artwork plus its label, and focus scales the whole column by 1.1
 * about its centre. What is checked is what arithmetic on constants cannot see: that after the
 * scale and the scroll that focus causes, the label is still above the bottom edge and the
 * row's heading is still below the top one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvSearchResultsFitTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a focused result keeps its whole title on screen with the filters open`() {
        compose.setContent { Harness(isAdvanced = true) }
        compose.onNodeWithText(resultTitle(0, 0)).requestFocus()
        compose.waitForIdle()

        val viewport = viewportHeight()
        val bottom = focusedVisualBottom(resultTitle(0, 0))

        assertTrue(
            "A focused result runs to ${"%.1f".format(bottom)}px in a ${viewport}px panel, so " +
                "its title is cut off. The row is taller than what Advanced search leaves for " +
                "it once the field and the genre chips are above it (#019).",
            bottom <= viewport,
        )
    }

    @Test
    fun `the heading of a focused row stays on screen with the filters open`() {
        compose.setContent { Harness(isAdvanced = true) }
        compose.onNodeWithText(resultTitle(0, 0)).requestFocus()
        compose.waitForIdle()

        val heading = compose.onNodeWithText(rowTitle(0)).fetchSemanticsNode()

        assertTrue(
            "The heading '${rowTitle(0)}' sits at ${"%.1f".format(heading.positionInRoot.y)}px " +
                "once a result under it is focused, so it has been scrolled off the top. A " +
                "viewer then cannot tell which kind they are looking at (#019).",
            heading.positionInRoot.y >= 0f,
        )
    }

    /** The same, with the filters shut — the case that has more room and should be easier. */
    @Test
    fun `a focused result keeps its whole title on screen with the filters shut`() {
        compose.setContent { Harness(isAdvanced = false) }
        compose.onNodeWithText(resultTitle(0, 0)).requestFocus()
        compose.waitForIdle()

        val viewport = viewportHeight()
        val bottom = focusedVisualBottom(resultTitle(0, 0))

        assertTrue(
            "A focused result runs to ${"%.1f".format(bottom)}px in a ${viewport}px panel.",
            bottom <= viewport,
        )
    }

    /**
     * Prints the geometry rather than asserting it.
     *
     * Run with `--tests '*TvSearchResultsFitTest.diagnostics*'`. The numbers say whether a row
     * misses by a few pixels or by a label's worth, which is the difference between trimming
     * something and rethinking the row.
     */
    @Test
    fun `diagnostics`() {
        compose.setContent { Harness(isAdvanced = true) }
        compose.waitForIdle()
        val unfocusedHeading = compose.onNodeWithText(rowTitle(0)).fetchSemanticsNode()
        val unfocused = compose.onNodeWithText(resultTitle(0, 0)).fetchSemanticsNode()

        compose.onNodeWithText(resultTitle(0, 0)).requestFocus()
        compose.waitForIdle()
        val heading = compose.onNodeWithText(rowTitle(0)).fetchSemanticsNode()
        val poster = compose.onNodeWithText(resultTitle(0, 0)).fetchSemanticsNode()

        println("panel ${viewportHeight()}px tall (${PANEL_HEIGHT} at density 2)")
        println(
            "before focus: heading y ${unfocusedHeading.positionInRoot.y}, " +
                "poster y ${unfocused.positionInRoot.y} h ${unfocused.size.height}, " +
                "bottom ${unfocused.positionInRoot.y + unfocused.size.height}",
        )
        println(
            "after focus:  heading y ${heading.positionInRoot.y}, " +
                "poster y ${poster.positionInRoot.y} h ${poster.size.height}, " +
                "layout bottom ${poster.positionInRoot.y + poster.size.height}, " +
                "visual bottom ${focusedVisualBottom(resultTitle(0, 0))}",
        )
    }

    private fun viewportHeight(): Int = compose.onRoot().fetchSemanticsNode().size.height

    /**
     * How far down the panel a focused poster actually reaches.
     *
     * **`size` is the unscaled layout box and focus scales at draw time**, so a semantics node
     * reports exactly the same rectangle focused or not. Asserting on it alone is a test that
     * cannot see the thing it was written for — which is the trap that made a first attempt at
     * this file pass while measuring nothing. The scale is applied here explicitly: the poster
     * grows about its centre, so half the gain goes below the layout box.
     */
    private fun focusedVisualBottom(text: String): Float {
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        val height = node.size.height.toFloat()
        return node.positionInRoot.y + height + height * (FOCUSED_SCALE - 1f) / 2f
    }

    /**
     * The search screen without its ViewModel: the real header, the real results list, and the
     * height the television gives the pair of them.
     */
    @Composable
    private fun Harness(isAdvanced: Boolean) {
        val state = SearchUiState(
            query = QUERY,
            live = emptyList(),
            movies = emptyList(),
            series = results(),
            genres = GENRES,
        )

        Box(modifier = Modifier.size(width = PANEL_WIDTH, height = PANEL_HEIGHT)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SearchHeader(
                    state = state,
                    // The harness is the working shape, where the centring gap is zero and
                    // this value is unused. It still has to be a real distance.
                    centreLine = PANEL_HEIGHT / 2,
                    isResting = false,
                    isAdvanced = isAdvanced,
                    onQueryChange = {},
                    onSelectGenre = {},
                    onClear = {},
                    onToggleAdvanced = {},
                )

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    TvCategoryList(
                        rows = listOf(TvCategoryRow(rowTitle(0), rowItems())),
                        ratings = emptyMap(),
                        onVisible = {},
                        onItemClick = {},
                    )
                }
            }
        }
    }

    private fun results(): List<Channel> = (0 until RESULTS).map { column ->
        Channel(
            id = column.toLong(),
            sourceId = 1L,
            name = resultTitle(0, column),
            streamUrl = "https://example.invalid/$column",
            kind = MediaKind.SERIES,
            groupTitle = rowTitle(0),
        )
    }

    private fun rowItems(): List<TvRowItem> =
        results().mapIndexed { index, channel -> TvRowItem(channel, index) }

    private fun rowTitle(row: Int) = "Heading ${'A' + row}"

    private fun resultTitle(row: Int, column: Int) = "Title ${'A' + row}$column"

    private companion object {
        /** The panel less overscan and the tab bar, which is what the screen is given. */
        val PANEL_WIDTH = 864.dp
        val PANEL_HEIGHT = 420.dp

        const val QUERY = "fargo"
        const val RESULTS = 8

        /** Enough chips that the strip is a full row, as it is on a scanned catalogue. */
        val GENRES = listOf("Drama", "Comedy", "Action", "Thriller", "Documentary", "Family")

        /** Must match `TvPosterRows`. A focused poster grows by this about its own centre. */
        const val FOCUSED_SCALE = 1.1f
    }
}
