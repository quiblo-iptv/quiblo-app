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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Where a detail screen sits, and where the remote is, at the moment it opens.
 *
 * One function for both detail screens because they had two copies of this and the copies
 * disagreed — the film screen reset a `ScrollState` and the series screen a `LazyListState`, and
 * #015 had to be found twice.
 *
 * There are two openings and they want opposite things:
 *
 * - **Opening a title.** Focus goes to the first action and the screen stays at its top. A focus
 *   target inside a scrolling container asks to be brought into view, and the container obliges
 *   by scrolling before the viewer has pressed anything. On this panel that cost the top of the
 *   poster.
 * - **Returning from an episode.** Focus goes to that episode's row and the list is *supposed* to
 *   scroll to it. That is the cursor #020 asks for, and nothing here may take it away.
 */
suspend fun openDetailScreen(
    listState: LazyListState,
    firstAction: FocusRequester,
    episodeCursor: FocusRequester?,
    episodeIndex: Int?,
) {
    if (episodeCursor != null && episodeIndex != null) {
        // Scroll first: an episode a hundred rows down does not exist as a composable until the
        // list has been moved to it, and a FocusRequester attached to nothing focuses nothing.
        // One frame between the two is what lets it be composed.
        listState.scrollToItem(episodeIndex)
        withFrameNanos { }
        runCatching { episodeCursor.requestFocus() }
        return
    }

    runCatching { firstAction.requestFocus() }
    holdAtTop(listState)
}

/**
 * The same opening, for the screen that has no episode list and so needs no lazy container.
 *
 * Kept beside the lazy one rather than left inline in the film screen, because the two being in
 * different files is how they drifted: #015 was fixed here first and the series screen kept its
 * own copy of the old reasoning. A single `scrollTo` is genuinely enough on this side — the
 * measurement on the panel says so, and [holdAtTop] explains why the lazy side is different —
 * but that is a fact about `ScrollState`, not a difference anybody chose, so it is written down
 * where both are visible at once.
 */
suspend fun openDetailScreen(scrollState: ScrollState, firstAction: FocusRequester) {
    runCatching { firstAction.requestFocus() }
    scrollState.scrollTo(0)
}

/**
 * Keeps the list at its top until whatever the focus request started has finished moving it.
 *
 * **Why a single `scrollToItem(0)` is not enough, measured rather than reasoned.** The film
 * screen and the series screen run the same open logic and only the series one is wrong: on the
 * Haier the film's artwork sits at `[96,96][416,576]` — a whole 240dp poster flush with the top
 * of the viewport — while the series artwork sits at `[96,74][416,554]`, twenty-two pixels above
 * a viewport that starts at 96 and clips it. The difference between the two screens is not the
 * header, which is shared; it is the container. A `ScrollState` can answer a bring-into-view
 * within the frame the focus request is made, so a reset placed after it is the last word. A
 * lazy list cannot: it has to compose and measure items before it knows where the focused one
 * is, so its bring-into-view lands a frame or more later and overwrites the reset.
 *
 * So the top is re-asserted until it stops being taken away, rather than once. It gives up as
 * soon as the list has been still for a few frames, which in the ordinary case is immediately
 * after the bring-into-view resolves.
 *
 * **The cost, stated rather than hidden:** a viewer who presses down within the first frames of
 * the screen appearing is pulled back to the top once. That window is a few frames, and the
 * alternative — a poster that is cut on every open — is the one that was actually reported.
 */
private suspend fun holdAtTop(listState: LazyListState) {
    var stableFrames = 0
    var frames = 0
    while (stableFrames < FRAMES_STILL_BEFORE_RELEASING && frames < FRAMES_HELD_AT_MOST) {
        withFrameNanos { }
        frames++
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            stableFrames++
        } else {
            stableFrames = 0
            listState.scrollToItem(0)
        }
    }
}

/** Long enough that a bring-into-view resolving late cannot be mistaken for stillness. */
private const val FRAMES_STILL_BEFORE_RELEASING = 4

/**
 * The ceiling, so this can never become a screen that refuses to scroll.
 *
 * Half a second at sixty frames. If the list is still being pulled away from the top after that,
 * something other than the opening bring-into-view is doing it and holding on would be the wrong
 * answer — that would be a new fault, and it should look like one.
 */
private const val FRAMES_HELD_AT_MOST = 30
