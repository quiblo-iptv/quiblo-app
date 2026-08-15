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

package dev.quiblo.tv.ui.common

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus, tolerating there being nothing to focus.
 *
 * A content area can legitimately hold no focusable at all — an empty catalogue, a spinner, a
 * line of explanatory text — and asking one for focus must leave focus where it is rather than
 * bring the app down.
 *
 * **It swallows two different failures, and knowing which is which is `022` #4.** An unattached
 * [FocusRequester] throws, which the `runCatching` covers; a requester that is attached but whose
 * node is *not placed yet* returns `false` instead, and that one is discarded here without
 * anybody noticing. For a screen that may have nothing to focus both outcomes are the same and
 * this is right. For a caller that knows its focusable exists and is only early, the `false` is
 * the whole story — see [insistOnFocus].
 *
 * One copy, in the package both the screens and the panels can see. There were two, in files far
 * enough apart that nobody noticed the second one being written.
 */
fun FocusRequester.tryRequestFocus() {
    runCatching { requestFocus() }
}

/**
 * Asks for focus, and keeps asking until the node it belongs to exists.
 *
 * **`022` #4, and it is a race, which is why it was reported as happening "many times" rather
 * than always.**
 *
 * `requestFocus()` **returns a boolean** — it does not throw when the node exists but has not been
 * placed. `TvShell` asked its top bar for focus from a `LaunchedEffect`, which runs after
 * composition and before layout has necessarily placed anything, and [tryRequestFocus] dropped
 * the `false` on the floor. When it lost that race nothing on the screen had focus at all: the
 * gear and the profile face drew unhighlighted, the tabs kept only their selected underline —
 * which is *selection*, not focus, and is exactly what the reported screenshot shows — and the
 * bar's key handler never fired, so the remote appeared dead.
 *
 * The window is widest coming back from an overlay. The shell leaves composition entirely while
 * Settings, a detail screen or the player is up, so this runs again on every return.
 *
 * A frame between attempts rather than a busy loop, because what is being waited for *is* a
 * frame: the node is placed during layout, and nothing else this coroutine could do would bring
 * that forward. Bounded, so a caller that genuinely has nothing focusable gives up instead of
 * asking forever — that case is [tryRequestFocus]'s and this stays honest about not being it.
 */
suspend fun FocusRequester.insistOnFocus(attempts: Int = FOCUS_ATTEMPTS) {
    repeat(attempts) {
        if (runCatching { requestFocus() }.getOrDefault(false)) return
        withFrameNanos { }
    }
}

/**
 * How many frames the bar will wait for itself to exist.
 *
 * Enough to cover a slow first layout on a television — the panels this is written for take
 * several frames to place a full screen from cold — and few enough that a requester attached to
 * nothing at all stops asking within a fifth of a second rather than for the life of the screen.
 */
private const val FOCUS_ATTEMPTS = 10
