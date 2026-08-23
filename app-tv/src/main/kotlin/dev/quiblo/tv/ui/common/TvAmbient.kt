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

// What is left here is the television's own plumbing for ambient light: how a screen says what
// it wants behind it. The light itself — the colours, the pools, the drift — is shared with the
// phone in `dev.quiblo.designsystem.Ambient`, because both frontends draw the same thing.
@file:Suppress("MatchingDeclarationName")

package dev.quiblo.tv.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import dev.quiblo.designsystem.AMBIENT_CROSSFADE_MILLIS

/**
 * The player's crossfade.
 *
 * The same number as [AMBIENT_CROSSFADE_MILLIS], and kept as its own name rather than folded
 * into it: the two follow different things — one follows focus, the other follows the picture —
 * and a single constant would make the next person to tune one of them tune both without
 * noticing.
 */
const val PLAYER_CROSSFADE_MILLIS = 300

/**
 * Where a focused tile says which picture it is showing.
 *
 * **A composition local rather than a callback threaded through the rows**, and that is a
 * deliberate exception to how everything else in this app passes state. The alternative is a
 * parameter on `TvCategoryList`, on every row, and on every poster — four signatures, all of
 * them on the composable measured by `TvBrowseScrollStabilityTest`, changed so that a
 * background can be tinted. The scroll behaviour of those rows is load-bearing and was expensive
 * to get right; the smallest possible edit to them is the right edit.
 *
 * Null when nothing focused has a picture, which is a live channel with no logo. The shell reads
 * that as "no light", not as "keep the last".
 */
val LocalAmbientSink = staticCompositionLocalOf<(AmbientRequest) -> Unit> { {} }

/**
 * What a screen wants behind it.
 *
 * Three answers rather than a nullable URL, because Search's answer was never expressible as one
 * and was therefore drawn somewhere else — on the screen's own `Column`, which sits inside the
 * shell's 48dp inset and below the tab bar. `drawBehind` clips to the node it is on and the pools
 * are sized as fractions of it, so the glow came out inset on three sides, cut off flat under the
 * bar, and smaller than the artwork light every other tab gets. It read as a lit rectangle on a
 * dark screen rather than as light in a room.
 *
 * Saying what is wanted and letting the shell draw it is what fixes that, and it is the same
 * shape the artwork light already had: one full-bleed layer at the root, fed from wherever.
 */
sealed interface AmbientRequest {

    /** No light. Live, which has only wordmarks to take a colour from. */
    data object None : AmbientRequest

    /** The screen's own light, from nothing. Search. See [Modifier.driftingGlow]. */
    data object Drift : AmbientRequest

    /** The colours of this picture. Every row of posters. Null is [None] by another name. */
    data class Artwork(val url: String?) : AmbientRequest
}
