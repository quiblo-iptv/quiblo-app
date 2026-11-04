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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Scrolls a row of posters to the focused one as a glide rather than a jump.
 *
 * A row used to be scrolled by Compose itself, which brings a newly focused tile into view with a
 * fixed, quick animation that starts from rest. On a television that reads as the row moving a
 * whole tile at a time in short snaps — and with the remote held, as a run of them. Compose no
 * longer lets that animation be changed (`BringIntoViewSpec.scrollAnimationSpec` is deprecated
 * and ignored), so a row hands it [NoRowBringIntoView] and scrolls itself here instead.
 *
 * **The scroll is a spring with memory.** Each press moves the spring's target one tile further
 * and lets it carry the speed it already had, so a held remote is one continuous glide that
 * settles softly on the last tile, and a single press is a short ease rather than a snap. Where the
 * row comes to rest is unchanged: just far enough that the focused tile is wholly on screen.
 *
 * **Only the row's own horizontal scroll is touched.** A focused tile still asks the catalogue to
 * bring it into view vertically exactly as before — the behaviour `TvBrowseScrollStabilityTest`
 * measures (#008) — because the row passes that request up untouched.
 */
@Stable
internal class TvRowGlide(private val state: LazyListState) {

    /** Everything the row has been asked to scroll, as one running total the spring chases. */
    private val travelled = Animatable(0f)

    /** How much of [travelled] has been handed to the row so far. */
    private var applied = 0f

    /** Hands the spring's movement to the row, frame by frame. Runs for as long as the row does. */
    suspend fun drive() {
        snapshotFlow { travelled.value }.collect { value ->
            state.dispatchRawDelta(value - applied)
            applied = value
        }
    }

    /**
     * Glides the row until the tile at [index] is wholly on screen, from wherever it is now.
     *
     * The tile may be off screen — the remote has just moved onto it — and then it is not in the
     * row's visible items. Its place is worked out from the first visible tile instead, which is
     * exact here and only here: **every tile in a row measures the same** (the invariant #008 is
     * built on), and the row has no spacing or padding of its own.
     */
    fun reveal(index: Int, scope: CoroutineScope) {
        val info = state.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull()
        val extent = first?.size ?: 0
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        if (first == null || extent <= 0 || viewport <= 0) return

        // Where the tile is now, then where it will be once the glide already under way lands.
        val now = info.visibleItemsInfo.firstOrNull { it.index == index }?.offset
            ?: (first.offset + (index - first.index) * extent)
        val start = now - (travelled.targetValue - travelled.value)
        val end = start + extent

        val distance = when {
            start < 0f -> start
            end > viewport -> end - viewport
            else -> 0f
        }
        if (distance == 0f) return
        val target = travelled.targetValue + distance
        scope.launch {
            // `animateTo` starts from the speed the spring already has: that is the glide.
            travelled.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = GLIDE_STIFFNESS))
        }
    }
}

/**
 * Tells a row of posters not to scroll itself to a focused tile; [TvRowGlide] does that.
 *
 * Zero for the row only. The request still travels up to the catalogue, which brings the tile
 * into view vertically exactly as it always has.
 */
@OptIn(ExperimentalFoundationApi::class)
internal val NoRowBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * About a third of a second to settle, with no overshoot.
 *
 * Softer and a held remote outruns the row, leaving the focused tile half off the edge; firmer
 * and a single press is the snap this replaced.
 */
private const val GLIDE_STIFFNESS = Spring.StiffnessMediumLow
