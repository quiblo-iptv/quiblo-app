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
 * Where along the bar the remote is resting.
 *
 * The icons past the tabs are positions here rather than focusables of their own. The bar is a
 * single focus target that moves by key press — a hard-won shape, because a focusable per tab
 * meant any content change that destroyed the focused element threw the viewer onto another tab
 * — and an icon that opts out of that model cannot be reached: it sits inside the bar's own
 * focusable, so a focus search walks past it into the content below. That is exactly what
 * happened, and why the settings icon was unreachable by remote as well as wired to nothing.
 *
 * **This was a boolean until the profile icon arrived**, and a second boolean beside it would
 * have made "on the gear and on the profile at once" a state the type allowed and the bar had to
 * be trusted never to produce. An enum cannot express it, so nothing downstream has to check.
 */
internal enum class TvBarSpot {
    TABS,
    GEAR,

    /** Rightmost, past the gear, and the end of the bar. */
    PROFILE,
}

internal data class TvBarState(val selectedTab: Int, val spot: TvBarSpot = TvBarSpot.TABS)

/** What a key press on the bar should do. */
internal sealed interface TvBarAction {
    /** Move along the bar. */
    data class Move(val state: TvBarState) : TvBarAction

    /** Leave the bar for the content below it. */
    data object EnterContent : TvBarAction

    data object OpenSettings : TvBarAction

    /**
     * Back to "who is watching".
     *
     * The profile icon does the one thing a household reaches for: hand the remote to somebody
     * else. It is not a menu — there is nothing else it could offer that Settings does not
     * already hold, and a television app with no dialogs is not the place to invent one.
     */
    data object SwitchProfile : TvBarAction

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
    // Each direction is its own function because the bar has three stops now rather than two:
    // written inline, the whole vocabulary reads as one nested `when` deep enough that the
    // ends of the bar — the cases that are actually easy to get wrong — are the hardest lines
    // in it to find.
    Key.DirectionLeft -> leftAlongBar(state)

    Key.DirectionRight -> rightAlongBar(state, lastIndex)

    Key.DirectionCenter, Key.Enter -> when (state.spot) {
        TvBarSpot.GEAR -> TvBarAction.OpenSettings
        TvBarSpot.PROFILE -> TvBarAction.SwitchProfile
        TvBarSpot.TABS -> TvBarAction.EnterContent
    }

    // Down always leaves the bar, wherever along it the remote is: what is below is the
    // content, and a viewer pressing down means "out of this bar" rather than anything about
    // where they are in it.
    Key.DirectionDown -> TvBarAction.EnterContent

    else -> TvBarAction.Unhandled
}

/** Back along the bar: profile, gear, then the tabs, and off the left end nothing. */
private fun leftAlongBar(state: TvBarState): TvBarAction = when (state.spot) {
    TvBarSpot.PROFILE -> TvBarAction.Move(state.copy(spot = TvBarSpot.GEAR))
    TvBarSpot.GEAR -> TvBarAction.Move(state.copy(spot = TvBarSpot.TABS))
    TvBarSpot.TABS -> if (state.selectedTab > 0) {
        TvBarAction.Move(state.copy(selectedTab = state.selectedTab - 1))
    } else {
        TvBarAction.Unhandled
    }
}

/** On along the bar: the tabs in turn, then the gear, then the profile. */
private fun rightAlongBar(state: TvBarState, lastIndex: Int): TvBarAction = when (state.spot) {
    // Nothing lies past the profile, and the press is consumed rather than falling through —
    // there is nothing further to reach, and letting focus escape sideways off the end of the
    // bar drops it into the content by a route no viewer intended.
    TvBarSpot.PROFILE -> TvBarAction.Move(state)
    TvBarSpot.GEAR -> TvBarAction.Move(state.copy(spot = TvBarSpot.PROFILE))
    TvBarSpot.TABS -> if (state.selectedTab < lastIndex) {
        TvBarAction.Move(state.copy(selectedTab = state.selectedTab + 1))
    } else {
        TvBarAction.Move(state.copy(spot = TvBarSpot.GEAR))
    }
}
