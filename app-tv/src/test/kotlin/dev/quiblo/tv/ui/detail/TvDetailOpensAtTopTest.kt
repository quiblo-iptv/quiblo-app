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

package dev.quiblo.tv.ui.detail

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/**
 * Does a detail screen open at its top, with the whole poster on screen?
 *
 * #015 — "the detail screen opens already scrolled: artwork cropped from the top" — was fixed
 * once, shipped in v0.2.6, and **rejected on the Haier on 2026-08-11**: the poster still loses a
 * strip off its top edge. This is the instrument that should have existed the first time.
 *
 * **What the panel says, measured rather than described.** On the television the poster is drawn
 * 458px tall where 160dp of width at 2:3 is 480px, and its bottom edge lands exactly where a
 * 22px scroll predicts. 22px at this density is **11dp**, and the top row of the poster reaches
 * full width immediately instead of ramping in over the 10dp corner radius — so the missing
 * strip is the viewport cutting the artwork off, not the artwork being drawn short.
 *
 * **Why the first fix did not hold.** It ordered `requestFocus()` before `scrollToItem(0)` on the
 * reasoning that two scrolls at the same priority resolve in favour of the later call. They are
 * not both calls: the focus request *queues* a bring-into-view that resolves on a later frame, so
 * the reset happens first and the bring-into-view overwrites it. Ordering cannot fix that, which
 * is why this asserts the position over a stretch of frames rather than on one.
 *
 * **What is measured is the list's own scroll position**, for the reason
 * `TvSettingsFieldStabilityTest` records: a lazy list disposes what leaves the viewport, so a
 * pixel landmark stops existing exactly when the thing under test happens.
 *
 * **What this harness cannot see, and it matters.** It does not reproduce the 22px. Run against
 * the shipped code — the single `scrollToItem(0)` the panel rejected — all three of these tests
 * pass. So this is a guard on the property, not a reproduction of the fault, and it must not be
 * read as proof that the fix works.
 *
 * The likeliest reason is the one `TvBrowseScrollStabilityTest` already warns about: **asking a
 * node for focus skips the focus search, and inside a lazy list the focus search is itself a
 * scroll.** Robolectric will not let a `FocusRequester` inside a `LaunchedEffect` place focus
 * unless the focus system has been woken first, and every way of waking it here arrives by
 * request rather than by search — so the very path that scrolls is the one the harness cannot
 * enter. That is a hypothesis, written down as one.
 *
 * **What actually establishes this fix is the panel**, and the evidence is asymmetric rather than
 * absolute: the film screen and the series screen share a header and an open sequence, and only
 * the lazy one is cropped. The bounds are quoted in `openDetailScreen`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvDetailOpensAtTopTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var listState: LazyListState

    /** Bumped to trigger the open, once the composition exists and focus works. */
    private val opened = mutableIntStateOf(0)

    /**
     * The property the viewer actually cares about: **the screen opens at its top and stays
     * there** until they press something.
     *
     * Watched over many frames rather than checked once. The bring-into-view that produced #015
     * lands a frame or more after the composition settles, so a single assertion taken too early
     * passes against a screen that is about to scroll — which is the shape of the first fix
     * being believed.
     */
    @Test
    fun `a series screen opens at the top and stays there`() {
        val trace = traceAfterOpening()

        assertTrue(
            "The detail screen did not stay at its top after opening. Anything above zero is " +
                "the poster's top edge being cut off by the viewport — 11dp of it on the " +
                "television (#015).\n" + report(trace),
            trace.all { it == 0 },
        )
    }

    /**
     * The same screen, opened the way a viewer returning from an episode opens it.
     *
     * This case wants the opposite thing and must keep wanting it: #020 asks for the cursor to
     * come back to the episode that was being watched, and that means the list is *supposed* to
     * be scrolled. A fix for #015 that pins the top would take #020 away silently, and the two
     * were built in the same wave by the same hands.
     */
    @Test
    fun `returning to an episode still scrolls to it`() {
        openAt(episodeCursor = EPISODE_RETURNED_TO)
        settle()

        assertTrue(
            "Opening with an episode cursor must scroll to that episode — this is #020, and it " +
                "is the case a fix for #015 is most likely to break. The list sat at " +
                "${positionOf()} instead.",
            positionOf() > 0,
        )
    }

    /**
     * Focus still lands on the first action; only the scroll was ever wrong.
     *
     * Worth its own test because the cheapest way to stop a bring-into-view is to stop
     * requesting focus, and that would trade a cropped poster for a screen the remote cannot
     * drive — the fault #015's own notes warn against.
     */
    @Test
    fun `focus still lands on the first action`() {
        openAt(episodeCursor = null)
        settle()

        compose.onNodeWithText(FIRST_ACTION).assertIsFocused()
    }

    private fun traceAfterOpening(): List<Int> {
        openAt(episodeCursor = null)
        settle()
        return (0 until FRAMES_WATCHED).map {
            frame()
            positionOf()
        }
    }

    /**
     * Opens the screen the way the app does, with a focus system that is actually alive.
     *
     * The composition is put up first and given focus somewhere harmless, then the open is
     * triggered. Without that seeding, `FocusRequester.requestFocus()` from a `LaunchedEffect`
     * silently does nothing under Robolectric — and a harness where focus never lands cannot
     * produce a bring-into-view, so the property under test passes for the one reason that
     * proves nothing. The seed is a header button so nothing has scrolled before the
     * measurement starts.
     */
    private fun openAt(episodeCursor: Int?) {
        compose.mainClock.autoAdvance = false
        compose.setContent { Harness(episodeCursor) }
        settle()
        compose.onNodeWithText(SEED_ACTION).requestFocus()
        settle()
        check(positionOf() == 0) { "the seed itself scrolled the list to ${positionOf()}" }
        opened.value++
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun settle() = repeat(FRAMES_TO_SETTLE) { frame() }

    /** Index and offset as one number, so crossing an item boundary cannot look like stillness. */
    private fun positionOf(): Int =
        listState.firstVisibleItemIndex * ITEM_STRIDE + listState.firstVisibleItemScrollOffset

    private fun report(trace: List<Int>): String =
        "trace (scroll position, one entry per frame):\n  " +
            trace.chunked(FRAMES_PER_LINE).joinToString(separator = "\n  ") { line ->
                line.joinToString(" ")
            }

    /**
     * The series screen's shape, at the panel's real geometry.
     *
     * The real composables where they carry the sizes that matter — the artwork and the action
     * buttons are what the bring-into-view is aiming at — and stand-ins for the rest, so this
     * needs no ViewModel and no Koin graph to run.
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Harness(episodeCursor: Int?) {
        listState = rememberLazyListState()
        val firstAction = remember { FocusRequester() }
        val cursor = remember { FocusRequester() }

        LaunchedEffect(opened.value) {
            if (opened.value == 0) return@LaunchedEffect
            openDetailScreen(
                listState = listState,
                firstAction = firstAction,
                episodeCursor = cursor.takeIf { episodeCursor != null },
                episodeIndex = episodeCursor?.let { LEADING_ITEMS + it },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.size(width = CONTENT_WIDTH, height = CONTENT_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") {
                Row(horizontalArrangement = Arrangement.spacedBy(DETAIL_COLUMN_GAP)) {
                    DetailArtwork(url = null)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DetailTitle(TITLE)
                        DetailFacts(rating = 8.2, ageRating = "12", genres = listOf("Drama"))
                        DetailOverview(overview = OVERVIEW, isEnriching = false)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 14.dp).focusGroup(),
                        ) {
                            DetailButton(
                                label = FIRST_ACTION,
                                onClick = {},
                                isPrimary = true,
                                modifier = Modifier.focusRequester(firstAction),
                            )
                            DetailButton(label = SEED_ACTION, onClick = {})
                        }
                    }
                }
            }

            item(key = "seasons") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SEASONS) { index -> Chip("Season ${index + 1}") }
                }
            }

            items(EPISODES) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (index == EPISODE_RETURNED_TO) {
                                Modifier.focusRequester(cursor)
                            } else {
                                Modifier
                            },
                        )
                        .clickable {}
                        .padding(vertical = 12.dp)
                        .background(Color.Transparent),
                ) {
                    Text("S1 E${index + 1}")
                }
            }
        }
    }

    @Composable
    private fun Chip(label: String) {
        Text(text = label, modifier = Modifier.clickable {}.padding(8.dp))
    }

    private companion object {
        /**
         * The viewport the television actually gives this list, read off the panel rather than
         * derived — `uiautomator` reports the scrollable at `[96,96][1824,984]` on a 1920x1080
         * screen at 2x, which is 864x444dp after the 48dp of overscan padding on every side.
         *
         * Worth stating because the first version of this harness guessed 912x492 and the extra
         * 48dp of height was enough to make the bug disappear: the header fitted, the
         * bring-into-view had nothing to do, and the test went green against code the panel was
         * failing. A harness at the wrong geometry is not a weaker instrument, it is a lying one.
         */
        val CONTENT_WIDTH = 864.dp
        val CONTENT_HEIGHT = 444.dp

        const val TITLE = "A series with a name long enough to wrap onto its second line"
        const val OVERVIEW =
            "A plot of the length a panel actually supplies, which is several lines and is " +
                "clamped to three by the shared overview composable rather than by this text."
        const val FIRST_ACTION = "Play"

        /** Where focus is parked to wake the focus system up, before the open is triggered. */
        const val SEED_ACTION = "Add to favourites"

        const val SEASONS = 5
        const val EPISODES = 30

        /** The header, plus the season strip. See `TvSeriesScreen`. */
        const val LEADING_ITEMS = 2
        const val EPISODE_RETURNED_TO = 12

        const val ITEM_STRIDE = 10_000
        const val FRAMES_TO_SETTLE = 120
        const val FRAMES_WATCHED = 60
        const val FRAMES_PER_LINE = 20
    }
}
