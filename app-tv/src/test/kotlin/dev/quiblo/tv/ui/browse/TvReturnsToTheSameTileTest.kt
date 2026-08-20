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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.tv.ui.common.LocalTvReturn
import dev.quiblo.tv.ui.common.TvReturnSignal
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The newest Android Robolectric 4.16 ships an image for. See `TvBrowseScrollStabilityTest`. */
private const val ROBOLECTRIC_SDK = 34

/**
 * Does backing out of a title land on the title it was opened from? — `027` #1.
 *
 * **The reported symptom is the whole app except the series screen: search a catalogue, open the
 * fourth result, add it to favourites, come back — and be at the first result again.** The cause is
 * that the shell is removed from composition while anything is drawn over it, which is deliberate
 * and is not being changed: a catalogue left composed behind a film is one still measuring and
 * still fetching artwork. What was missing is that nothing survived it.
 *
 * The harness is that journey and nothing else. A holder keeps the shell's saved state, exactly as
 * `TvApp` does; a flag swaps the list for the screen that covered it; and the signal says the
 * second composition is a return rather than an arrival. If any of those three is dropped, this
 * fails — which is the point, because each of them alone looks like a line nobody needs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvReturnsToTheSameTileTest {

    @get:Rule
    val compose = createComposeRule()

    private val returning = TvReturnSignal()
    private var isOverlayUp by mutableStateOf(false)

    @Test
    fun `the remote comes back to the tile it left from`() {
        setUp()
        walkTo(row = 0, column = 3)

        openAndClose()

        compose.onNodeWithText(title(0, 3)).assertIsFocused()
    }

    /** And it is the tile, not merely the row: a viewer four along does not want to be first. */
    @Test
    fun `a tile in a lower row is found again too`() {
        setUp()
        walkTo(row = 1, column = 2)

        openAndClose()

        compose.onNodeWithText(title(1, 2)).assertIsFocused()
    }

    /**
     * Arriving is not returning.
     *
     * Without the signal being *consumed* this would restore on every composition of the list,
     * which is also what happens when a viewer walks along the tab bar — the content would take
     * the remote off the bar mid-walk and strand them on whichever tab they were passing. That is
     * a worse fault than the one being fixed, so it gets a case of its own.
     */
    @Test
    fun `a list composed without the signal leaves the remote alone`() {
        setUp()
        walkTo(row = 0, column = 3)

        isOverlayUp = true
        compose.waitForIdle()
        isOverlayUp = false
        compose.waitForIdle()

        compose.onNode(isFocused()).assertDoesNotExist()
    }

    private fun setUp() {
        compose.setContent { Harness() }
        compose.waitForIdle()
    }

    /**
     * Walks the remote from the first tile to the wanted one, as a viewer would.
     *
     * Pressed rather than requested, because the thing being recorded is *focus arriving*, and a
     * requester would arrange the one state the fix is supposed to observe for itself.
     */
    private fun walkTo(row: Int, column: Int) {
        compose.onNodeWithText(title(0, 0)).requestFocus()
        compose.waitForIdle()

        repeat(row) { press(Key.DirectionDown) }
        repeat(column) { press(Key.DirectionRight) }

        compose.onNodeWithText(title(row, column)).assertIsFocused()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** The journey: something opens over the shell, and back closes it. */
    private fun openAndClose() {
        isOverlayUp = true
        compose.waitForIdle()

        returning.arm()
        isOverlayUp = false
        compose.waitForIdle()
    }

    @Composable
    private fun Harness() {
        val shellState = rememberSaveableStateHolder()

        CompositionLocalProvider(LocalTvReturn provides returning) {
            Box(modifier = Modifier.size(width = PANEL_WIDTH, height = PANEL_HEIGHT)) {
                if (isOverlayUp) {
                    Text(text = OVERLAY)
                } else {
                    shellState.SaveableStateProvider("shell") {
                        TvCategoryList(
                            rows = remember { rows() },
                            ratings = emptyMap(),
                            onVisible = {},
                            onItemClick = {},
                        )
                    }
                }
            }
        }
    }

    private fun rows(): List<TvCategoryRow> = (0 until ROWS).map { row ->
        TvCategoryRow(
            title = heading(row),
            items = (0 until COLUMNS).map { column ->
                TvRowItem(
                    Channel(
                        id = (row * COLUMNS + column).toLong(),
                        sourceId = 1L,
                        name = title(row, column),
                        streamUrl = "https://example.invalid/$row/$column",
                        kind = MediaKind.VOD,
                        groupTitle = heading(row),
                    ),
                    row * COLUMNS + column,
                )
            },
        )
    }

    private fun heading(row: Int) = "Heading ${'A' + row}"

    private fun title(row: Int, column: Int) = "Title ${'A' + row}$column"

    private companion object {
        /** The panel less overscan and the tab bar, which is what a tab is given. */
        val PANEL_WIDTH = 864.dp
        val PANEL_HEIGHT = 420.dp

        const val ROWS = 2
        const val COLUMNS = 6
        const val OVERLAY = "A film"

    }
}
