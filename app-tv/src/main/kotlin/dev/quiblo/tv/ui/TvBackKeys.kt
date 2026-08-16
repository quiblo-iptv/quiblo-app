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

/**
 * Where the shell is, as far as a back press is concerned.
 *
 * Only these two things decide what back means. The overlay stack does not appear here because
 * the shell is not composed at all while anything is on it — an overlay owns its own back — so
 * "the stack is empty" is not a condition to check, it is the reason this function is being
 * asked.
 */
data class TvBackState(val selectedTab: Int, val isExitArmed: Boolean)

/** What a back press does to the shell. */
sealed interface TvBackAction {

    /** Walk to the first tab. Where back comes to rest, and where it stops being navigation. */
    data object GoToSearch : TvBackAction

    /** Say that another press will close, and wait for it. */
    data object ArmExit : TvBackAction

    /** Close, having forgotten who was watching. */
    data object Exit : TvBackAction
}

/**
 * What a back press means, given where the shell is.
 *
 * **A function over state rather than branches inside the `BackHandler`**, for the reason
 * [tvBarAction] gives about the bar: a rule buried in a composable can only be checked by driving
 * a whole screen, and this project has shipped several rules that were right in the code and
 * wrong in the order they fired.
 *
 * Three cases, and only the first was ever written down:
 *
 * - **Anywhere but Search**, back is navigation: walk to the first tab, which is where back has
 *   always come to rest. The alternative — back leaving from wherever somebody happens to be — is
 *   how a television app loses a viewer three tabs deep with one stray press.
 * - **On Search, unarmed**, back asks. Two presses rather than one because back is also how a
 *   viewer walks out of everything else, and a stray press that closes the app is the one mistake
 *   a television app cannot let somebody make.
 * - **On Search, armed**, back closes.
 *
 * Walking off Search disarms, and that is the case a careless version drops: a viewer who has
 * gone somewhere else has stopped leaving, and without it, arming on Search and then walking to
 * Movies leaves the next back on Search closing the app with no notice on screen at all.
 */
fun tvBackAction(state: TvBackState): TvBackAction = when {
    state.selectedTab != TvTab.SEARCH.ordinal -> TvBackAction.GoToSearch
    state.isExitArmed -> TvBackAction.Exit
    else -> TvBackAction.ArmExit
}
