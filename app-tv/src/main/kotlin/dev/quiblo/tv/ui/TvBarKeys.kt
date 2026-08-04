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

package dev.quiblo.tv.ui

import androidx.compose.ui.input.key.Key

/**
 * Where the remote is resting along the top bar.
 *
 * The gear is a position here rather than a focusable of its own. The bar is a single focus
 * target that moves by key press — a hard-won shape, because a focusable per tab meant any
 * content change that destroyed the focused element threw the viewer onto another tab — and
 * an icon that opts out of that model cannot be reached: it sits inside the bar's own
 * focusable, so a focus search walks past it into the content below. That is exactly what
 * happened, and why the settings icon was unreachable by remote as well as wired to nothing.
 */
internal data class TvBarState(val selectedTab: Int, val onGear: Boolean = false)

/** What a key press on the bar should do. */
internal sealed interface TvBarAction {
    /** Move along the bar. */
    data class Move(val state: TvBarState) : TvBarAction

    /** Leave the bar for the content below it. */
    data object EnterContent : TvBarAction

    data object OpenSettings : TvBarAction

    /**
     * Not the bar's key.
     *
     * Left unconsumed rather than swallowed so focus can leave the way it came instead of
     * the remote going dead in the corner.
     */
    data object Unhandled : TvBarAction
}

/**
 * The bar's whole key vocabulary.
 *
 * A plain function so the one thing a reviewer needs to check is not buried in a modifier,
 * and so the awkward cases — the ends of the bar, and the gear beyond them — can be
 * asserted rather than tried by hand on a television.
 */
internal fun tvBarAction(key: Key, state: TvBarState, lastIndex: Int): TvBarAction = when (key) {
    Key.DirectionLeft -> when {
        state.onGear -> TvBarAction.Move(state.copy(onGear = false))
        state.selectedTab > 0 -> TvBarAction.Move(state.copy(selectedTab = state.selectedTab - 1))
        else -> TvBarAction.Unhandled
    }

    Key.DirectionRight -> when {
        // Nothing lies past the gear, and the press is consumed rather than falling through
        // — there is no sixth thing to reach, and letting focus escape sideways off the end
        // of the bar drops it into the content by a route no viewer intended.
        state.onGear -> TvBarAction.Move(state)
        state.selectedTab < lastIndex ->
            TvBarAction.Move(state.copy(selectedTab = state.selectedTab + 1))

        else -> TvBarAction.Move(state.copy(onGear = true))
    }

    Key.DirectionCenter, Key.Enter ->
        if (state.onGear) TvBarAction.OpenSettings else TvBarAction.EnterContent

    // Down always leaves the bar, gear or not: what is below is the content, and a viewer
    // pressing down means "out of this bar" rather than anything about where they are in it.
    Key.DirectionDown -> TvBarAction.EnterContent

    else -> TvBarAction.Unhandled
}
