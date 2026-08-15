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

package dev.quiblo.tv.ui.browse

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * The newest Android Robolectric 4.16 ships an image for.
 *
 * Deliberately not `compileSdk`: this module compiles against 37, for which no image exists,
 * and nothing under test here is sensitive to the platform version — it is Compose's own
 * layout, focus and animation, which are library code.
 */
private const val ROBOLECTRIC_SDK = 34

/**
 * Does the catalogue hold still while the remote walks along a row?
 *
 * #008 — "the screen wobbles and shakes while scrolling" — survived four confident diagnoses
 * because every one of them was reasoned out rather than measured, and the single measurement
 * that was taken came from a screen recording that had silently truncated. This is the
 * missing instrument: it drives the real composables on the JVM, one frame at a time, at the
 * cadence Android repeats a held D-pad, and reads back where the screen actually is.
 *
 * Two things it does that a first attempt got wrong, both of which hid the fault:
 *
 * - **It presses keys.** Asking a node for focus skips the focus search, and on a lazy row
 *   the focus search is itself a scroll — the next poster does not exist until something
 *   scrolls it into being. That is one of the two scrolls per press.
 * - **It does not wait.** A press every three frames is roughly Android's key repeat. Letting
 *   each animation finish before the next press is a test of something nobody does.
 *
 * What it watches is a **row title**: it carries no focus animation of its own, so any
 * movement of it is the list underneath it moving. This is the video method — bands of pixels
 * that should be still — done where it can be run in CI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    // A bare Application rather than QuibloTvApplication: that one starts Koin, and Koin's
    // global context outlives a Robolectric test, so the second test in the class would fail
    // on an already-started container. Nothing under test here is injected.
    application = Application::class,
)
class TvBrowseScrollStabilityTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The first row, which Mahmoud confirmed on the television does **not** wobble.
     *
     * Here as a control. If a change ever makes this one shake too, the cause is somewhere
     * other than where the other tests are looking.
     */
    @Test
    fun `walking along the first row leaves the catalogue still`() {
        assertHoldsStill(rowIndex = 0)
    }

    /** The reported bug, in the row it was reported in. */
    @Test
    fun `walking along the second row leaves the catalogue still`() {
        assertHoldsStill(rowIndex = 1)
    }

    @Test
    fun `walking along a row further down leaves the catalogue still`() {
        assertHoldsStill(rowIndex = 3)
    }

    /**
     * The same row again, with the Recently Added tab's kind badge on every poster.
     *
     * The badge is an overlay inside the artwork rather than anything in the column beneath it,
     * precisely so a tile's measured bounds do not change — and "precisely so" is worth nothing
     * without this. #008 was four wrong analyses of a wobble whose cause was a focused tile
     * whose reported rectangle grew, and a label placed a line lower would reintroduce it.
     */
    @Test
    fun `a poster carrying a kind badge is no less still`() {
        assertHoldsStill(rowIndex = 1, showKindBadge = true)
    }

    /**
     * The same, for the two shapes the For You tab added.
     *
     * **The rank is an overlay and the caption's line is reserved for the whole row**, and both
     * of those were chosen for exactly this reason. A numeral standing outside the poster would
     * make a tile's width depend on how many digits its number has; a caption drawn only on the
     * tiles that have one would make a tile's height depend on its own content. Either is a
     * focused tile whose reported rectangle differs from its neighbour's, which is #008.
     *
     * The note inside `TvPoster` says a label that grows a line will start the loop again. This
     * is the test that note points at.
     */
    @Test
    fun `a ranked row is no less still`() {
        assertHoldsStill(rowIndex = 1, style = TvRowStyle.RANKED)
    }

    @Test
    fun `a row carrying captions is no less still`() {
        assertHoldsStill(rowIndex = 1, captioned = true)
    }

    /** And the case the reservation exists for: one tile in the row has no caption of its own. */
    @Test
    fun `a captioned row with a gap in it is no less still`() {
        assertHoldsStill(rowIndex = 1, captioned = true, captionGapEvery = 3)
    }

    /**
     * The property under test: **moving along a row must not move the catalogue at all.**
     *
     * Not "must not move much", and not "must not change direction" — must not move. Left and
     * right is a horizontal gesture, every poster in a row sits at exactly the same height as
     * every other, and so there is nothing for the vertical list to do about any of it. Any
     * vertical movement at all is the list reacting to something it should not be able to see.
     *
     * Stated this way it is also the assertion that distinguishes the two rows the way the
     * television does: the first row already passes it, and before the fix the second row
     * misses it by about six dp, arriving in a twitch on each of the first several presses.
     */
    private fun assertHoldsStill(
        rowIndex: Int,
        showKindBadge: Boolean = false,
        style: TvRowStyle = TvRowStyle.POSTER,
        captioned: Boolean = false,
        captionGapEvery: Int = 0,
    ) {
        val trace = traceWhileWalkingAlong(
            rowIndex,
            showKindBadge = showKindBadge,
            rows = catalogue(style = style, captioned = captioned, captionGapEvery = captionGapEvery),
        )
        val settled = trace.first()
        val drift = trace.maxOf { abs(it - settled) }

        assertTrue(
            "Row ${rowIndex + 1} moved ${"%.1f".format(drift)}px vertically while the remote " +
                "walked along it. Pressing left and right must not scroll the catalogue.\n" +
                report(trace),
            drift < STILL_THRESHOLD_PX,
        )
    }

    /**
     * Holds RIGHT along [rowIndex] and records where that row's title sits on every frame,
     * then keeps recording after the key is released.
     */
    private fun traceWhileWalkingAlong(
        rowIndex: Int,
        showKindBadge: Boolean = false,
        rows: List<TvCategoryRow> = catalogue(),
        alsoRecord: () -> Unit = {},
    ): List<Float> {
        compose.mainClock.autoAdvance = false
        compose.setContent { Harness(rows, showKindBadge = showKindBadge) }
        settle()
        walkFocusDownTo(rowIndex)

        val trace = mutableListOf<Float>()
        repeat(PRESSES_HELD) {
            pressRight()
            repeat(FRAMES_BETWEEN_PRESSES) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                trace += positionOf(rowTitle(rowIndex))
                alsoRecord()
            }
        }
        repeat(FRAMES_AFTER_RELEASE) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            trace += positionOf(rowTitle(rowIndex))
            alsoRecord()
        }
        return trace
    }

    /**
     * Prints geometry and traces rather than asserting anything.
     *
     * Kept in the source because a harness has to be checked before it is believed — a
     * measurement taken in the wrong state is what cost this bug two of its wrong answers.
     * Run with `--tests '*TvBrowseScrollStabilityTest.diagnostics*'` and read the numbers.
     */
    @Test
    fun `diagnostics for the first row`() = printDiagnostics(rowIndex = 0)

    @Test
    fun `diagnostics for the second row`() = printDiagnostics(rowIndex = 1)

    private fun printDiagnostics(rowIndex: Int) {
        val horizontal = mutableListOf<Float>()
        val trace = traceWhileWalkingAlong(rowIndex) {
            // A poster the remote has not reached yet, so nothing is scaling it: its x is the
            // row's scroll offset and nothing else. NaN while it is still uncomposed.
            val nodes = compose.onAllNodesWithText(posterTitle(rowIndex, HORIZONTAL_LANDMARK))
                .fetchSemanticsNodes()
            horizontal += nodes.firstOrNull()?.positionInRoot?.x ?: Float.NaN
        }
        println("viewport ${compose.onRoot().fetchSemanticsNode().size}")
        (0 until ROWS).forEach { row ->
            val nodes = compose.onAllNodesWithText(rowTitle(row)).fetchSemanticsNodes()
            println("  row $row title ${nodes.joinToString { "${it.positionInRoot}" }}")
        }
        println("=== row $rowIndex, one group of $FRAMES_BETWEEN_PRESSES frames per press ===")
        println("vertical, the row title's y:")
        println(report(trace))
        println("horizontal, poster $HORIZONTAL_LANDMARK's x:")
        println(report(horizontal))
    }

    /**
     * A real D-pad press, not a focus request.
     *
     * The difference matters: pressing right runs a focus search, which on a lazy row has to
     * scroll a poster that is not composed yet into existence before it can be focused. That
     * is a scroll of its own, on top of the one that brings the newly focused poster into
     * view, and it is exactly what asking a node for focus skips.
     */
    private fun pressRight() = press(Key.DirectionRight)

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
    }

    /**
     * Walks focus down to [rowIndex] by pressing down, one row at a time.
     *
     * By pressing rather than by asking, because a row below the fold does not exist yet:
     * nothing can be asked for focus until a focus search has scrolled it into being. That is
     * also how a viewer gets there.
     */
    private fun walkFocusDownTo(rowIndex: Int) {
        compose.onNodeWithText(posterTitle(0, 0)).requestFocus()
        settle()
        repeat(rowIndex) {
            press(Key.DirectionDown)
            settle()
        }
    }

    /** Runs long enough for any animation started so far to finish. */
    private fun settle() {
        repeat(FRAMES_TO_SETTLE) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun positionOf(text: String): Float =
        compose.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    private fun report(trace: List<Float>): String =
        "trace (px from the top of the viewport, one entry per frame):\n" +
            trace.chunked(FRAMES_BETWEEN_PRESSES).joinToString(prefix = "  ", separator = " | ") {
                it.joinToString(" ") { value -> "%.1f".format(value) }
            }

    /**
     * The content area the television actually gives this screen.
     *
     * A 960x540dp panel, less the 48dp overscan margin on each side, and less the tab bar
     * above and the margin below. Sizing it here rather than composing the whole shell keeps
     * the test free of Koin, and the only number that has to be right is that a category row
     * is taller than the space it is given — true of the real screen by a wide margin, and
     * the reason every row but the first has to be scrolled to.
     */
    @Composable
    private fun Harness(rows: List<TvCategoryRow>, showKindBadge: Boolean = false) {
        Box(modifier = Modifier.size(width = CONTENT_WIDTH, height = CONTENT_HEIGHT)) {
            TvCategoryList(
                rows = rows,
                ratings = emptyMap(),
                onVisible = {},
                onItemClick = {},
                showKindBadge = showKindBadge,
            )
        }
    }

    /**
     * Enough rows to push the lower ones off screen, and enough posters in each that the
     * remote can walk well past the right edge of the viewport — which is where a row starts
     * scrolling at all. Artwork is deliberately absent so nothing here touches the network: a
     * poster with no logo draws its placeholder.
     */
    private fun catalogue(
        style: TvRowStyle = TvRowStyle.POSTER,
        captioned: Boolean = false,
        /** Every nth tile gets no caption, so a row can be walked across a gap in one. */
        captionGapEvery: Int = 0,
    ): List<TvCategoryRow> = (0 until ROWS).map { row ->
        TvCategoryRow(
            title = rowTitle(row),
            style = style,
            items = (0 until POSTERS_PER_ROW).map { column ->
                val index = row * POSTERS_PER_ROW + column
                TvRowItem(
                    channel = Channel(
                        id = index.toLong(),
                        sourceId = 1L,
                        name = posterTitle(row, column),
                        streamUrl = "https://example.invalid/${row}_$column",
                        kind = MediaKind.VOD,
                        groupTitle = rowTitle(row),
                    ),
                    flatIndex = index,
                    rank = if (style == TvRowStyle.RANKED) column + 1 else null,
                    caption = when {
                        !captioned -> null
                        captionGapEvery > 0 && column % captionGapEvery == 0 -> null
                        else -> "Because you watched ${posterTitle(row, 0)}"
                    },
                )
            },
        )
    }

    private fun rowTitle(row: Int) = "Category ${'A' + row}"

    private fun posterTitle(row: Int, column: Int) = "Poster ${'A' + row}$column"

    private companion object {
        val CONTENT_WIDTH = 864.dp
        val CONTENT_HEIGHT = 420.dp

        const val HORIZONTAL_LANDMARK = 12

        const val ROWS = 6
        const val POSTERS_PER_ROW = 24

        /** Android repeats a held D-pad about every 50ms, which is three frames at 60fps. */
        const val FRAMES_BETWEEN_PRESSES = 3

        /** Far enough along that the row has been scrolling for most of them. */
        const val PRESSES_HELD = 16

        /** Comfortably longer than any animation this screen starts. */
        const val FRAMES_TO_SETTLE = 120
        const val FRAMES_AFTER_RELEASE = 90

        /**
         * Below this, movement is rounding rather than motion.
         *
         * Half a pixel: the wobble measured on the television was three to four dp, which at
         * this panel's density is six to eight pixels, so there is an order of magnitude
         * between what this ignores and what it is looking for.
         */
        const val STILL_THRESHOLD_PX = 0.5f
    }
}
