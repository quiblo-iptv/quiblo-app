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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One focus frame for a whole screen of posters, which slides from the tile the remote left to
 * the tile it arrived on.
 *
 * Each poster used to draw its own border, so moving along a row was one frame vanishing and
 * another appearing. Here the frame belongs to the screen instead: the focused poster says
 * *where* it is, and [TvFocusSlideHost] draws a single outline that travels there. The poster
 * still grows when it is focused — only the frame moves.
 *
 * **It draws and it measures nothing**, and that is the invariant to keep. The rows under it are
 * the ones `TvBrowseScrollStabilityTest` measures (#008): a focused tile whose bounds change is the
 * wobble all over again. Nothing here takes up layout or sits between a focusable and its list —
 * a poster only hands over its artwork's coordinates, and the frame is painted over the top of
 * everything in the host's own draw pass.
 */
@Stable
class TvFocusSlideState internal constructor() {

    /** The artwork the frame should be around, or null when nothing in the host is focused. */
    internal var target: LayoutCoordinates? by mutableStateOf(null)
        private set

    /**
     * Bumped whenever the target may have moved — a scroll, a scale step — so the host redraws.
     * Read only in the draw pass, so a bump costs a redraw and never a recomposition.
     */
    internal var moves by mutableIntStateOf(0)
        private set

    internal fun claim(coordinates: LayoutCoordinates) {
        target = coordinates
        moves++
    }

    /** Only lets go of [coordinates] if they are still the target: focus moving A to B may report B first. */
    internal fun release(coordinates: LayoutCoordinates) {
        if (target === coordinates) target = null
    }

    internal fun moved(coordinates: LayoutCoordinates) {
        if (target === coordinates) moves++
    }

    internal fun tick() {
        moves++
    }
}

/** The host for the screen being drawn, or null where posters still draw their own border. */
val LocalTvFocusSlide = staticCompositionLocalOf<TvFocusSlideState?> { null }

/**
 * Draws the sliding focus frame over [content].
 *
 * Put it around a list or grid of posters. A poster inside reports itself through
 * [Modifier.focusSlideTarget]; anything else focusable inside simply hides the frame.
 */
@Composable
fun TvFocusSlideHost(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = FRAME_CORNER,
    content: @Composable () -> Unit,
) {
    val state = remember { TvFocusSlideState() }
    val frame = remember { FrameMemory() }
    val travel = remember { Animatable(1f) }
    val visibility = remember { Animatable(0f) }

    LaunchedEffect(state) {
        var wasShowing = false
        var settling: Job? = null
        var hiding: Job? = null
        snapshotFlow { state.target }.collect { target ->
            if (target == null) {
                // Not at once. Moving from one poster to the next, the old one can let go a
                // moment before the new one claims, and treating that gap as "focus left" would
                // turn every slide into a fade.
                hiding = launch {
                    delay(HIDE_GRACE_MILLIS)
                    wasShowing = false
                    visibility.animateTo(0f, tween(FADE_MILLIS))
                }
                return@collect
            }
            hiding?.cancel()

            // From wherever the frame was last drawn — mid-slide included, so a held remote
            // bends the path rather than restarting it. Coming in from outside the host (the tab
            // bar, the hero) there is nothing to slide from, so it appears in
            // place instead of flying in from where it was left minutes ago.
            frame.from = frame.last.takeIf { wasShowing }
            wasShowing = true

            launch { visibility.animateTo(1f, tween(FADE_MILLIS)) }
            launch {
                // `snapTo` cancels a slide still running on the same value, which is what lets
                // the next press take over from the frame wherever it is.
                travel.snapTo(0f)
                travel.animateTo(1f, tween(SLIDE_MILLIS, easing = FastOutSlowInEasing))
            }
            // The poster grows for a moment after it is focused, and its row scrolls it into
            // view. Neither is reported to the host reliably, so it redraws every frame for as
            // long as they take and then goes back to redrawing only on moves.
            settling?.cancel()
            settling = launch {
                val start = withFrameNanos { it }
                while (withFrameNanos { it } - start < SETTLE_NANOS) state.tick()
            }
        }
    }

    Box(
        modifier = modifier
            .onPlaced { frame.host = it }
            .drawWithContent {
                drawContent()

                // Read first, so this pass is invalidated by any of them whatever happens below.
                frame.seenMoves = state.moves
                val progress = travel.value
                val alpha = visibility.value

                val host = frame.host ?: return@drawWithContent
                val target = state.target?.takeIf { it.isAttached && host.isAttached }
                val here = if (target != null) {
                    // Not clipped: a poster scrolling in at a row's edge is still the shape it is,
                    // and a frame cut to its visible sliver would look like a different tile.
                    host.localBoundingBoxOf(target, clipBounds = false).also { frame.landing = it }
                } else {
                    frame.landing
                } ?: return@drawWithContent

                val drawn = frame.from?.let { lerp(it, here, progress) } ?: here
                frame.last = drawn
                if (alpha <= 0f) return@drawWithContent

                val stroke = FRAME_WIDTH.toPx()
                val inset = stroke / 2f
                clipRect {
                    drawRoundRect(
                        color = Color.White.copy(alpha = alpha),
                        topLeft = Offset(drawn.left + inset, drawn.top + inset),
                        size = Size(drawn.width - stroke, drawn.height - stroke),
                        cornerRadius = CornerRadius(cornerRadius.toPx()),
                        style = Stroke(width = stroke),
                    )
                }
            },
    ) {
        CompositionLocalProvider(LocalTvFocusSlide provides state) {
            content()
        }
    }
}

/**
 * Marks this node as what the sliding frame goes around while [isFocused] is true.
 *
 * Put it **inside** the tile's scale layer, on the artwork, so the frame follows the tile as it
 * grows. It adds no layout and no focus target of its own.
 */
fun Modifier.focusSlideTarget(state: TvFocusSlideState, isFocused: Boolean): Modifier = composed {
    val node = remember { NodeMemory() }

    LaunchedEffect(isFocused) {
        val coordinates = node.coordinates ?: return@LaunchedEffect
        if (isFocused) state.claim(coordinates) else state.release(coordinates)
    }
    DisposableEffect(state) {
        onDispose { node.coordinates?.let(state::release) }
    }

    onGloballyPositioned { coordinates ->
        val first = node.coordinates == null
        node.coordinates = coordinates
        // Focus can arrive before the first placement — a returning viewer's tile is asked for
        // focus as it is built — so a tile that is already focused claims on its first pass.
        if (isFocused && first) state.claim(coordinates) else state.moved(coordinates)
    }
}

/** What the host remembers between frames. Plain fields: written in the draw pass, never observed. */
private class FrameMemory {
    var host: LayoutCoordinates? = null
    var from: Rect? = null
    var last: Rect? = null

    /** Where the frame last landed, so it can fade out there rather than vanish. */
    var landing: Rect? = null

    /** Only there to be written: reading [TvFocusSlideState.moves] is what subscribes the draw. */
    var seenMoves: Int = 0
}

private class NodeMemory {
    var coordinates: LayoutCoordinates? = null
}

/** Matches the border every poster drew for itself before the frame was shared. */
private val FRAME_WIDTH = 3.dp

/** The poster's 8dp corner at its focused 1.1 scale. */
private val FRAME_CORNER = 9.dp

/** Quick enough to keep up with a held remote, slow enough to be seen moving. */
private const val SLIDE_MILLIS = 220
private const val FADE_MILLIS = 150

/** Longer than the gap between one tile letting go and the next claiming; shorter than a glance. */
private const val HIDE_GRACE_MILLIS = 80L

/** Long enough to cover the poster's scale spring and the row's bring-into-view scroll. */
private const val SETTLE_NANOS = 600_000_000L
