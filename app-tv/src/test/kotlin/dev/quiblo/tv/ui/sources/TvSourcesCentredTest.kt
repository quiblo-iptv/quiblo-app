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

package dev.quiblo.tv.ui.sources

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.feature.sources.SourcesViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
 * Is the playlist screen actually in the middle of the panel?
 *
 * **Measured against the root, never the window.** `containerSize` reported 1264 for a real
 * 1080-wide panel once already in this project, and a centring test that believes it would pass
 * against a screen visibly stuck to the left. The root node's own width is what was drawn.
 *
 * The tolerance is a couple of pixels, not zero: an odd number of pixels of leftover width
 * cannot be split evenly, and a test that demands it will fail on some panel widths and not
 * others for no reason anybody can act on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvSourcesCentredTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the add control sits in the middle of the panel`() {
        showTheScreen()

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithText(ADD).fetchSemanticsNode().boundsInRoot

        val leftGap = row.left - root.left
        val rightGap = root.right - row.right

        assertTrue(
            "Left gap $leftGap, right gap $rightGap — the column is not centred.",
            abs(leftGap - rightGap) <= TOLERANCE_PX,
        )
    }

    /** And it does not simply fill the panel, which would also be symmetrical and unreadable. */
    @Test
    fun `the column is narrower than the panel`() {
        showTheScreen()

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithText(ADD).fetchSemanticsNode().boundsInRoot

        assertTrue(
            "The column is ${row.width} of a ${root.width} panel — that is not a column.",
            row.width < root.width,
        )
    }

    private fun showTheScreen() {
        val repository = mockk<SourceRepository> {
            every { observeSources() } returns flowOf(emptyList())
        }

        compose.setContent {
            TvSourcesScreen(onBack = {}, viewModel = SourcesViewModel(repository))
        }
        compose.waitForIdle()
    }

    private companion object {
        /** From `tv_sources_add`. */
        const val ADD = "Add a playlist or account"

        const val TOLERANCE_PX = 2f
    }
}
