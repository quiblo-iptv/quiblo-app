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

package dev.quiblo.tv.ui.player

/**
 * What is on top of the player, as far as a back press is concerned.
 *
 * Only these three overlays decide what back means. Playback itself is the last step, not a
 * fourth condition — if nothing here is up, back leaves.
 */
data class TvPlayerBackState(
    val trackMenuOpen: Boolean,
    val controlsVisible: Boolean,
    val isOfferingNextEpisode: Boolean,
)

/** What a back press does inside the television player. */
sealed interface TvPlayerBackAction {

    /** Close the audio/subtitle panel; leave the controls as they are. */
    data object CloseTrackMenu : TvPlayerBackAction

    /** Hide the transport controls; stay in playback (AC-TV-06). */
    data object HideControls : TvPlayerBackAction

    /** Decline the next-episode offer without leaving the player. */
    data object DismissNextEpisode : TvPlayerBackAction

    /** Leave playback for the screen underneath. */
    data object Exit : TvPlayerBackAction
}

/**
 * What a back press means, given what the player has drawn on top of the video.
 *
 * **A function over state rather than branches inside a `BackHandler`**, for the same reason
 * the shell's [dev.quiblo.tv.ui.tvBackAction] is: a rule buried in a composable can only be
 * checked by driving a whole screen, and this hierarchy has already been rewritten twice —
 * once to exit immediately (wrong product), once to "one place" that dropped the key path
 * some remotes actually use (`BUG-032`).
 *
 * Order is the stack the viewer sees:
 *
 * - track menu on top of controls → close the menu
 * - controls on screen → hide them (first back; second back exits)
 * - next-episode offer → dismiss it
 * - nothing on top → leave playback
 */
fun tvPlayerBackAction(state: TvPlayerBackState): TvPlayerBackAction = when {
    state.trackMenuOpen -> TvPlayerBackAction.CloseTrackMenu
    state.controlsVisible -> TvPlayerBackAction.HideControls
    state.isOfferingNextEpisode -> TvPlayerBackAction.DismissNextEpisode
    else -> TvPlayerBackAction.Exit
}

/**
 * Whether a key-path back press should apply [action] and consume the event.
 *
 * Some remotes deliver Back only as a key to the focused control, never as an
 * `OnBackPressedDispatcher` callback. The preview handler therefore applies every step that
 * closes something on screen. Exit is left to the dispatcher so one physical press cannot
 * hide and leave in the same breath when both paths fire.
 */
fun tvPlayerBackConsumesKey(action: TvPlayerBackAction): Boolean =
    action != TvPlayerBackAction.Exit
