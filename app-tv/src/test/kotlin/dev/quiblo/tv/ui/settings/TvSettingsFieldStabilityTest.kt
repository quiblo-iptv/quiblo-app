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
import android.content.ComponentName
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.quiblo.feature.settings.TmdbCheck
import org.junit.Assert.assertEquals
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
 * Does the settings list hold still while the remote enters the metadata key?
 *
 * #021 — "opening the API-key field makes the screen shake" — is the fourth appearance of the
 * family #008 belongs to, and every member of it has the same shape: something reports bounds
 * that change every frame, and a scrollable container chases them. Last time four confident
 * diagnoses were argued rather than measured and two of them were shipped. So this is the
 * instrument first, at the panel's real geometry, one frame at a time.
 *
 * **What it can and cannot see, stated plainly, because a harness has to be believed before it
 * is trusted.** Robolectric has no IME, so nothing here types on an on-screen keyboard. What it
 * does have is the only part of a keyboard that reaches a layout — a viewport that shrinks over
 * several frames — and that is reproduced directly by shrinking the container.
 *
 * **What it found, in order.** With the viewport held still, focus arriving and characters
 * being entered both leave the list flat on every frame: inside Compose alone there is no loop.
 * With the viewport shrinking under the focused field, the list runs four items past where the
 * viewer left it and takes about a dozen frames after the resize ends to stop. So the fault is
 * an excursion driven from outside Compose, not an oscillation inside it — which is why the fix
 * is a manifest attribute and not a modifier.
 *
 * **What is still owed: the television.** #021's exit criterion asks for the harness *and* the
 * Haier, and only the first half is here.
 *
 * **What is measured is the list's own scroll position**, not a pixel landmark. A landmark was
 * the first attempt and it is wrong here: a lazy list disposes what scrolls out of view, so a
 * heading above the field stops existing at the exact moment the thing under test happens, and
 * the harness fails rather than reporting. `firstVisibleItemIndex` and its offset are the same
 * fact, always present, and cannot be confused with an item animating inside itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [ROBOLECTRIC_SDK],
    qualifiers = "sw960dp-w960dp-h540dp-land-television-xhdpi",
    application = Application::class,
)
class TvSettingsFieldStabilityTest {

    @get:Rule
    val compose = createComposeRule()

    /** The list under test, captured from the composition so its position can be read back. */
    private lateinit var listState: LazyListState

    /**
     * The height the list is given, which the test can shrink.
     *
     * This is the on-screen keyboard, expressed as the only thing about it that reaches
     * Compose: the window gets shorter. Robolectric has no IME, but an IME's effect on a
     * layout is a viewport that shrinks over several frames, and that can be reproduced
     * exactly.
     */
    private val viewportHeight = mutableStateOf(CONTENT_HEIGHT)

    /**
     * The property under test: **arriving on the key field must not leave the list moving.**
     *
     * The list is allowed to scroll once — bringing a field the viewer has just moved to into
     * view is the correct behaviour and is how they can see what they are typing. What it may
     * not do is keep moving afterwards, which is what a container chasing a target that shifts
     * as it is approached looks like, and what a viewer reports as a shake.
     *
     * So the trace is read after the arrival, not through it: once focus has landed and the
     * screen has had time to settle, every subsequent frame must report the same position.
     */
    @Test
    fun `the list is still once focus has arrived on the key field`() {
        val trace = traceAfterFocusingTheField()
        val settled = trace.first()
        val drift = trace.maxOf { abs(it - settled) }

        assertTrue(
            "The settings list moved ${"%.1f".format(drift)}px after focus arrived on the key " +
                "field. Once the field is on screen the list must not move again.\n" +
                report(trace),
            drift < STILL_THRESHOLD_PX,
        )
    }

    /**
     * The same property while the viewer is actually typing.
     *
     * A text field asks to be brought into view again on every cursor movement, not only on
     * focus — so typing is a second, longer opportunity for the same loop, and it is the state
     * the bug was reported in.
     */
    @Test
    fun `the list is still while characters are entered`() {
        val trace = traceWhileTyping()
        val settled = trace.first()
        val drift = trace.maxOf { abs(it - settled) }

        assertTrue(
            "The settings list moved ${"%.1f".format(drift)}px while characters were entered " +
                "into the key field.\n" + report(trace),
            drift < STILL_THRESHOLD_PX,
        )
    }

    /**
     * The mechanism itself, kept as a measurement rather than as a claim.
     *
     * The two tests above hold the viewport still and are flat, which is what points here:
     * inside Compose alone there is no loop to find. What the television adds is a **window
     * that changes size while a field is focused** — the activity declared no
     * `windowSoftInputMode`, so the system chose one, which is also the likeliest reason the
     * emulator behaved and the Haier did not.
     *
     * This asserts the excursion still reproduces, which sounds backwards for a bug fix and is
     * deliberate: the fix is `adjustNothing` in the manifest, and it works by making sure this
     * scenario never occurs rather than by changing what happens during it. A test asserting
     * the scenario is now harmless would be testing nothing, and would go quietly green if
     * somebody removed the attribute. This one tells whoever changes Compose or this layout
     * that the reason for the attribute has moved.
     */
    @Test
    fun `a shrinking window drags the settings list a long way`() {
        arriveOnTheField()
        val restingIndex = listState.firstVisibleItemIndex
        val whileOpening = openTheKeyboard()
        val afterOpening = (0 until FRAMES_WATCHED).map {
            frame()
            positionOfLandmark()
        }

        // It does settle — the chase converges rather than oscillating — so the fault is not
        // an endless loop. What it is instead is an excursion: measured here, the list runs
        // four items past where the viewer left it and takes about a dozen frames after the
        // resize ends to stop. That is the lurch, and on a panel watched from three metres it
        // is what gets reported as a shake.
        val movedItems = listState.firstVisibleItemIndex - restingIndex
        assertTrue(
            "This test documents the mechanism #021 comes from, and it has stopped " +
                "reproducing: a shrinking viewport moved the list $movedItems items. If that " +
                "is now zero, Compose or the layout has changed and the manifest's " +
                "adjustNothing may no longer be carrying the fix on its own.\n" +
                "while opening:\n" + report(whileOpening) + "\nafter:\n" + report(afterOpening),
            movedItems > 0,
        )

        val settled = afterOpening.last()
        assertTrue(
            "The list never came to rest after the window stopped resizing, which would make " +
                "this an oscillation rather than an excursion — a different fault from the one " +
                "diagnosed.\nafter:\n" + report(afterOpening),
            abs(afterOpening.takeLast(FRAMES_WATCHED / 2).maxOf { it } - settled) < STILL_THRESHOLD_PX,
        )
    }

    /**
     * The fix, pinned where it lives.
     *
     * `adjustNothing` on the television activity is what stops the window resizing under the
     * settings list, and the three measurements above are the argument for it. It is one
     * manifest attribute, it is invisible in every screenshot, and deleting it would put the
     * mechanism straight back — which is exactly the kind of decision that needs a test rather
     * than a comment.
     */
    @Test
    fun `the television window does not resize under the keyboard`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val activity = ComponentName(context, "dev.quiblo.tv.TvMainActivity")
        val info = context.packageManager.getActivityInfo(activity, 0)

        assertEquals(
            "TvMainActivity must declare windowSoftInputMode=adjustNothing. Without it the " +
                "system chooses, which is how this behaved differently on the emulator and on " +
                "the television (#021).",
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            info.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
        )
    }

    /**
     * Shrinks the viewport the way a keyboard animating in does, and records each frame.
     *
     * In steps rather than in one jump, because one jump is a single relayout and could not
     * produce a chase even in principle — the fault being looked for needs a target that is
     * still moving when the container arrives at where it used to be.
     */
    private fun openTheKeyboard(): List<Float> {
        val trace = mutableListOf<Float>()
        val step = (CONTENT_HEIGHT - CONTENT_HEIGHT_WITH_KEYBOARD) / KEYBOARD_FRAMES
        repeat(KEYBOARD_FRAMES) { index ->
            viewportHeight.value = CONTENT_HEIGHT - step * (index + 1)
            frame()
            trace += positionOfLandmark()
        }
        return trace
    }

    /**
     * Prints the traces without asserting, for when one of the above goes red.
     *
     * Run with `--tests '*TvSettingsFieldStabilityTest.diagnostics*'`.
     */
    @Test
    fun `diagnostics`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Harness() }
        settle()
        println("viewport ${compose.onRoot().fetchSemanticsNode().size}")
        println("list position before focus ${positionOfLandmark()}")

        compose.onNodeWithText(ROW_ABOVE).requestFocus()
        settle()
        println("list position with focus above the field ${positionOfLandmark()}")

        val arriving = mutableListOf<Float>()
        press(Key.DirectionDown)
        repeat(FRAMES_TO_SETTLE) {
            frame()
            arriving += positionOfLandmark()
        }
        println("=== arriving on the field, one entry per frame ===")
        println(report(arriving))
        println("=== then typing ===")
        println(report(traceOfTyping()))
    }

    /** Moves focus onto the field, lets it settle, then records where the list sits. */
    private fun traceAfterFocusingTheField(): List<Float> {
        arriveOnTheField()
        return (0 until FRAMES_WATCHED).map {
            frame()
            positionOfLandmark()
        }
    }

    private fun traceWhileTyping(): List<Float> {
        arriveOnTheField()
        return traceOfTyping()
    }

    private fun traceOfTyping(): List<Float> {
        val trace = mutableListOf<Float>()
        repeat(CHARACTERS_TYPED) {
            press(Key.A)
            repeat(FRAMES_BETWEEN_KEYSTROKES) {
                frame()
                trace += positionOfLandmark()
            }
        }
        repeat(FRAMES_WATCHED) {
            frame()
            trace += positionOfLandmark()
        }
        return trace
    }

    /**
     * Puts the remote on the field the way a viewer does — from the row above, by pressing
     * down — and then waits for everything that arrival started to finish.
     *
     * By pressing rather than by asking, for the reason `TvBrowseScrollStabilityTest` records:
     * a focus search inside a lazy list is itself a scroll, and asking a node for focus skips
     * the very path being measured.
     */
    private fun arriveOnTheField() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Harness() }
        settle()
        compose.onNodeWithText(ROW_ABOVE).requestFocus()
        settle()
        press(Key.DirectionDown)
        settle()
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun settle() = repeat(FRAMES_TO_SETTLE) { frame() }

    /**
     * Where the list is, as one number.
     *
     * Index and offset combined so a trace is readable at a glance and so crossing an item
     * boundary cannot look like standing still. The multiplier only has to exceed any single
     * item's height in pixels, which on this panel nothing here comes near.
     */
    private fun positionOfLandmark(): Float =
        listState.firstVisibleItemIndex * ITEM_STRIDE + listState.firstVisibleItemScrollOffset.toFloat()

    private fun report(trace: List<Float>): String =
        "trace (px from the top of the viewport, one entry per frame):\n" +
            trace.chunked(FRAMES_BETWEEN_KEYSTROKES).joinToString(prefix = "  ", separator = " | ") {
                it.joinToString(" ") { value -> "%.1f".format(value) }
            }

    /**
     * The settings screen's shape, without its ViewModel.
     *
     * Enough rows above the field that it starts below the fold — which is the case the bug
     * was reported in and the only case where the list has anywhere to scroll to. The real
     * screen has considerably more above it than this.
     */
    @Composable
    private fun Harness() {
        listState = rememberLazyListState()
        Box(modifier = Modifier.size(width = CONTENT_WIDTH, height = viewportHeight.value)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Text(LANDMARK) }
                items(ROWS_ABOVE) { index -> FocusableRow("Filler $index") }
                item { FocusableRow(ROW_ABOVE) }
                item {
                    TmdbKeyRow(
                        currentKey = null,
                        check = TmdbCheck.Idle,
                        onSave = {},
                        onClear = {},
                    )
                }
                items(ROWS_BELOW) { index -> FocusableRow("Trailing $index") }
            }
        }
    }

    /** A row the remote can rest on, standing in for the option rows around the real field. */
    @Composable
    private fun FocusableRow(label: String) {
        Text(
            text = label,
            modifier = Modifier
                .clickable {}
                .padding(vertical = 12.dp),
        )
    }

    private companion object {
        val CONTENT_WIDTH = 864.dp
        val CONTENT_HEIGHT = 420.dp

        /**
         * What is left when the on-screen keyboard is up.
         *
         * A leanback IME takes roughly the lower half of the panel. The exact figure does not
         * matter to the property: what matters is that the viewport gets materially smaller
         * while a field below the fold is focused.
         */
        val CONTENT_HEIGHT_WITH_KEYBOARD = 220.dp

        /** Roughly the length of a keyboard's entrance animation, in frames. */
        const val KEYBOARD_FRAMES = 12

        const val LANDMARK = "Settings"

        /** Larger than any item here is tall, so index and offset combine without colliding. */
        const val ITEM_STRIDE = 10_000f
        const val ROW_ABOVE = "The row above the field"
        const val ROWS_ABOVE = 6
        const val ROWS_BELOW = 6

        const val CHARACTERS_TYPED = 8
        const val FRAMES_BETWEEN_KEYSTROKES = 3

        /** Comfortably longer than any animation this screen starts. */
        const val FRAMES_TO_SETTLE = 120

        /** Long enough that an oscillation would have to show itself. */
        const val FRAMES_WATCHED = 60

        /** Below this, movement is rounding rather than motion. See `TvBrowseScrollStabilityTest`. */
        const val STILL_THRESHOLD_PX = 0.5f
    }
}
