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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.quiblo.feature.browse.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Search keeps one shape: the field top left, and the live switch whether or not filters are open. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvSearchTopLeftTest {

    @get:Rule
    val compose = createComposeRule()

    private var toggledLive: Boolean? = null

    private fun show(state: SearchUiState) = compose.setContent {
        TvSearchPanel(
            state = state,
            onOpen = { _, _ -> },
            onQueryChange = {},
            onSelectGenre = {},
            onSelectYear = {},
            onToggleIncludeHidden = {},
            onToggleIncludeLive = { toggledLive = it },
            onToggleAdvanced = {},
            onClear = {},
            onResultVisible = {},
        )
    }

    @Test
    fun `with nothing asked the field already rests at the top left`() {
        show(SearchUiState(hasSource = true))
        compose.waitForIdle()

        val field = compose.onNodeWithText("Search").fetchSemanticsNode().boundsInRoot
        assertEquals("The field has been moved off the top.", true, field.top < 60f)
        assertEquals("The field has been moved off the left.", true, field.left < 60f)
    }

    @Test
    fun `a plain search can switch live channels off`() {
        show(SearchUiState(hasSource = true, includeLive = true))

        compose.onNodeWithText("Include TV").assertIsDisplayed().performClick()

        assertEquals(false, toggledLive)
    }

    @Test
    fun `include hidden still waits for Advanced`() {
        show(SearchUiState(hasSource = true))

        compose.onAllNodesWithText("Include hidden").assertCountEquals(0)
    }
}
