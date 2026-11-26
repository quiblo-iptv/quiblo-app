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

@file:Suppress("MatchingDeclarationName")

package dev.quiblo.tv.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
    // How far the frame still is from the tile it is going to, edge by edge. The frame is drawn at
    // the tile plus this gap, and the gap springs to nothing: the tile can scroll or grow under it
    // and the frame follows exactly, while only the distance left to travel is animated.
    val gap = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
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

            /*
             * From wherever the frame was last drawn, mid-slide included, and *at the speed it
             * was already going*. A fixed-length slide restarted from rest on every press, so a
             * held remote moved the frame in a series of starts and stops; a spring handed its
             * own velocity carries on through the next press as one continuous movement.
             *
             * Coming in from outside the host (the tab bar, the hero) there is nothing to slide
             * from, so it appears in place instead of flying in from where it was left minutes ago.
             */
            val last = frame.last.takeIf { wasShowing }
            val landing = frame.boundsOf(target)
            wasShowing = true
            launch { visibility.animateTo(1f, tween(FADE_MILLIS)) }
            if (last != null && landing != null) {
                val velocity = gap.velocity
                gap.snapTo(last.minus(landing))
                launch {
                    gap.animateTo(
                        targetValue = Rect.Zero,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = SLIDE_STIFFNESS),
                        initialVelocity = velocity,
                    )
                }
            } else {
                gap.snapTo(Rect.Zero)
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
                val left = gap.value
                val alpha = visibility.value

                val here = state.target?.let(frame::boundsOf)?.also { frame.landing = it }
                    ?: frame.landing
                    ?: return@drawWithContent

                val drawn = here.plus(left)
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

/**
 * The focus border for a tile's artwork, wherever it is drawn.
 *
 * Inside a [TvFocusSlideHost] the host's sliding frame is the border, and this only reports where
 * the artwork is. Anywhere else the tile draws its own, as every poster did before the frame was
 * shared. Either way it measures nothing, so a tile's bounds never depend on its focus (#008).
 */
fun Modifier.focusFrame(isFocused: Boolean, shape: Shape): Modifier = composed {
    val slide = LocalTvFocusSlide.current
    val drawsOwnBorder = isFocused && slide == null
    (slide?.let { Modifier.focusSlideTarget(it, isFocused) } ?: Modifier)
        .clip(shape)
        .border(
            width = if (drawsOwnBorder) FRAME_WIDTH else 0.dp,
            color = if (drawsOwnBorder) Color.White else Color.Transparent,
            shape = shape,
        )
}

/** Edge by edge, so a gap between two rectangles can be added back to either of them. */
private fun Rect.plus(other: Rect) = Rect(
    left = left + other.left,
    top = top + other.top,
    right = right + other.right,
    bottom = bottom + other.bottom,
)

private fun Rect.minus(other: Rect) = Rect(
    left = left - other.left,
    top = top - other.top,
    right = right - other.right,
    bottom = bottom - other.bottom,
)

/** What the host remembers between frames. Plain fields: written in the draw pass, never observed. */
private class FrameMemory {
    var host: LayoutCoordinates? = null
    var last: Rect? = null

    /** Where the frame last landed, so it can fade out there rather than vanish. */
    var landing: Rect? = null

    /** Only there to be written: reading [TvFocusSlideState.moves] is what subscribes the draw. */
    var seenMoves: Int = 0

    /**
     * Where [target] is in the host, or null while either is not placed.
     *
     * Not clipped: a poster scrolling in at a row's edge is still the shape it is, and a frame cut
     * to its visible sliver would look like a different tile.
     */
    fun boundsOf(target: LayoutCoordinates): Rect? {
        val host = host?.takeIf { it.isAttached } ?: return null
        if (!target.isAttached) return null
        return host.localBoundingBoxOf(target, clipBounds = false)
    }
}

private class NodeMemory {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Thin enough to sit on the artwork rather than box it in. Three was what every poster drew for
 * itself, and as one frame moving across the screen it read as heavy.
 */
private val FRAME_WIDTH = 2.dp

/** The poster's 8dp corner at its focused 1.1 scale. */
private val FRAME_CORNER = 9.dp

/**
 * How firmly the frame is pulled to the tile: about a third of a second to arrive, with no
 * overshoot. Firmer, and a single press reads as a jump; softer, and a held remote leaves the
 * frame trailing behind the poster it is meant to be marking.
 */
private const val SLIDE_STIFFNESS = Spring.StiffnessMediumLow
private const val FADE_MILLIS = 150

/** Longer than the gap between one tile letting go and the next claiming; shorter than a glance. */
private const val HIDE_GRACE_MILLIS = 80L

/** Long enough to cover the poster's scale spring and the row's bring-into-view scroll. */
private const val SETTLE_NANOS = 700_000_000L
