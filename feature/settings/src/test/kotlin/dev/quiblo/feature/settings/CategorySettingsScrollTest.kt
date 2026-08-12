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
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
 * Are the category rows items of the settings list, not children of a nested scroller?
 *
 * The old layout put a `LazyColumn` capped at 320dp inside the settings screen's own
 * scrollable column. That gave the categories their own scroll surface: a drag could move
 * the inner list or the outer screen, and which one it moved was never obviously right.
 * Worse, scrolling the outer list to the sentinel below the category section would never
 * encounter the category items at all, because they were children of a different scrollable.
 *
 * The property under test is reachability through one list. If `performScrollToNode` on the
 * single `LazyColumn` can find both the 300th category and a sentinel placed after all
 * categories, every row is an item of the outer list and there is no inner scroller boxing
 * them in.
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
     * A sentinel placed after all 300 category items is reachable by scrolling the single
     * list. If the categories were inside a nested bounded scroller, the outer list would
     * hold only the header card (one item) and the sentinel (another), with the 300
     * categories invisible to `performScrollToNode` on the outer list entirely.
     */
    @Test
    fun `sentinel after 300 categories is reachable through the list`() {
        setContent()

        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText(SENTINEL))
    }

    /**
     * The 300th category is itself an item of the list, not hidden inside a child scroller.
     */
    @Test
    fun `the 300th category is reachable through the list`() {
        setContent()

        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText(categoryTitle(CATEGORY_COUNT)))
    }

    private fun setContent() {
        compose.setContent {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                categorySettingsItems(
                    selectedKind = MediaKind.LIVE,
                    categories = categories(),
                    onSelectKind = {},
                    onSetHidden = { _, _ -> },
                    onRenameRequest = {},
                )
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
        const val CATEGORY_COUNT = 300
        const val SENTINEL = "the section below categories"
    }
}
