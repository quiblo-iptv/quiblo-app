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

package dev.quiblo.feature.settings

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.MediaKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Are the category rows inside a bounded inner scroller, not items of the outer settings list?
 *
 * The layout puts a LazyColumn capped at 420dp inside the settings screen's own scrollable
 * column. That gives the categories their own scroll surface within a fixed-height box,
 * keeping the outer list short and predictable regardless of how many categories a source
 * has. The box has a visible border so the two scroll regions read as distinct.
 *
 * The property under test is containment. If the outer list can reach a sentinel placed
 * after the category card without scrolling through 300 individual category rows, the
 * categories are inside the inner scroller and not items of the outer list. The sentinel
 * is reachable because the outer list holds only the card (one item) and the sentinel
 * (another), not 300 separate rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    // A bare Application rather than QuibloApplication: that one starts Koin, and Koin's
    // global context outlives a Robolectric test, so the second test in the class would fail
    // on an already-started container. Nothing under test here is injected.
    application = Application::class,
)
class CategorySettingsScrollTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * There are exactly two scrolling surfaces: the settings list, and the category box.
     *
     * **This is the property the panel asked for**, and it is the one an earlier version of this
     * file asserted the opposite of. A flat settings screen has one scroller and the categories
     * spread through it; this shape has two, and the second is bounded.
     *
     * Asserting the count rather than the geometry is deliberate — heights are a design decision
     * that will change, and "how many things scroll" is the thing a viewer's drag actually meets.
     */
    @Test
    fun `the screen has two scrollers, the outer list and the category box`() {
        setContent()

        compose.onAllNodes(hasScrollAction()).assertCountEquals(2)
    }

    /**
     * The settings list reaches what is below the categories without walking through them.
     *
     * With three hundred categories inside the box, the outer list holds a handful of items. If
     * the rows were its own items again, this scroll would travel through all three hundred.
     */
    @Test
    fun `the outer list reaches the section below the categories`() {
        setContent()

        compose.onAllNodes(hasScrollAction())[OUTER]
            .performScrollToNode(hasText(SENTINEL))
        compose.onNodeWithText(SENTINEL).assertIsDisplayed()
    }

    /**
     * And the box itself still reaches its own last row.
     *
     * A bounded list that cannot be scrolled to its end would be the fault this shape is most
     * likely to introduce: the categories beyond the box's height would simply be unreachable,
     * which is worse than the ambiguous drag it replaced.
     */
    @Test
    fun `the category box reaches its own last category`() {
        setContent()

        // `performScrollToNode` throws if the node is not reachable in that scrollable, so
        // reaching it is the assertion. `assertIsDisplayed` deliberately is not used here:
        // whether the box is on screen depends on where the *outer* list is sitting, which is
        // the property the test above covers and not this one.
        compose.onAllNodes(hasScrollAction())[INNER]
            .performScrollToNode(hasText(categoryTitle(CATEGORY_COUNT)))
        compose.onNodeWithText(categoryTitle(CATEGORY_COUNT)).assertExists()
    }

    private fun setContent() {
        compose.setContent {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    CategorySettingsCard(
                        selectedKind = MediaKind.LIVE,
                        categories = categories(),
                        onSelectKind = {},
                        onSetHidden = { _, _ -> },
                        onRename = { _, _ -> },
                    )
                }
                item { Text(SENTINEL) }
            }
        }
    }

    private fun categories(): List<Category> = (1..CATEGORY_COUNT).map { index ->
        Category(
            id = index.toLong(),
            sourceId = 1L,
            title = categoryTitle(index),
            itemCount = index,
        )
    }

    private fun categoryTitle(index: Int) = "Category $index"

    private companion object {
        /** Depth-first semantics order: the settings list is composed before the box inside it. */
        const val OUTER = 0
        const val INNER = 1

        const val CATEGORY_COUNT = 300
        const val SENTINEL = "the section below categories"
    }
}
