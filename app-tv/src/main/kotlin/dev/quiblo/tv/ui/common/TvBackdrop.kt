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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Where a catalogue says which picture should stand behind the top of the screen.
 *
 * Best first — a backdrop, then a poster — and empty for "nothing". A composition local for
 * the reason [LocalAmbientSink] is one: the picture belongs to the *screen*, and only the shell
 * knows where the screen's edges are.
 */
internal val LocalBackdropSink = staticCompositionLocalOf<(List<String>) -> Unit> { {} }

/**
 * The focused title's picture, standing in the top right-hand corner of the screen.
 *
 * **Anchored to the screen, not to the banner, and that is the whole of the fix.** The picture
 * used to be drawn inside the banner and faded out on all four sides to hide that it was a
 * rectangle floating mid-screen. A fade cannot hide a rectangle: the eye finds the four soft
 * edges and the corners where they meet, and on a panel with any banding they are steps. Here
 * two of its sides *are* the edges of the screen, so there is nothing there to hide, and the
 * other two — towards the text on the left and the rows below — are long eased fades. The tab
 * bar's own dark gradient lies over its top. That is how every set-top box does it.
 *
 * **A picture appears once it has loaded, and not before.** The new one is decoded underneath
 * the old, and only then faded in over it, so a change of title is one dissolve from picture to
 * picture: never a frame of empty screen, never a hard rectangle while the fade catches up.
 */
@Composable
internal fun TvBackdrop(
    /** A lambda, so a new title recomposes this and not the shell it is drawn in. */
    candidates: () -> List<String>,
    modifier: Modifier = Modifier,
) {
    val chain = rememberArtworkChain(candidates())
    val wanted = chain.url

    /*
     * The pictures on screen, bottom to top. The top one is the newest; it fades in once it has
     * loaded, and the ones beneath it are dropped when it is fully in. Keyed by URL, so a picture
     * keeps its node — and its decoded bitmap — from the moment it arrives until it is dropped.
     */
    var layers by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(wanted) {
        if (wanted != null && layers.lastOrNull() != wanted) {
            layers = (layers - wanted + wanted).takeLast(MAX_LAYERS)
        }
    }

    // Nothing new is coming: what is there fades away rather than vanishing.
    val presence = remember { Animatable(1f) }
    LaunchedEffect(wanted == null) {
        if (wanted == null) {
            presence.animateTo(0f, tween(BACKDROP_FADE_MILLIS, easing = FastOutSlowInEasing))
            layers = emptyList()
        } else if (layers.isEmpty()) {
            presence.snapTo(1f)
        } else {
            presence.animateTo(1f, tween(BACKDROP_FADE_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(maxWidth * BACKDROP_WIDTH_SHARE)
                .height(maxHeight * BACKDROP_HEIGHT_SHARE)
                .graphicsLayer { alpha = presence.value }
                .fadedIntoTheRoom(),
        ) {
            layers.forEach { url ->
                key(url) {
                    BackdropLayer(
                        url = url,
                        onShown = { layers = layers.dropWhile { it != url } },
                        onFailed = {
                            chain.failed(url)
                            layers = layers - url
                        },
                    )
                }
            }
        }
    }
}

/** One picture: invisible until it has loaded, then faded in over whatever is beneath it. */
@Composable
private fun BackdropLayer(url: String, onShown: () -> Unit, onFailed: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    var loaded by remember { mutableStateOf(false) }
    val shown by rememberUpdatedState(onShown)

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Towards the top: a film's still has its faces in the upper half far more often than
        // in the lower, and the lower half is the part that fades away.
        alignment = BiasAlignment(horizontalBias = 0f, verticalBias = ABOVE_BIAS),
        onSuccess = { loaded = true },
        onError = { onFailed() },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value },
    )

    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        alpha.animateTo(1f, tween(BACKDROP_FADE_MILLIS, easing = FastOutSlowInEasing))
        shown()
    }
}

/**
 * Fades the picture out towards the left and the bottom — the two of its sides that are not the
 * screen's edge.
 *
 * A mask rather than a dark wash laid over it: the room behind is the ambient light, not black,
 * and a black wash would draw a dark rectangle on it. Applied once, to both pictures together,
 * so a crossfade between two titles is never two masks drifting against each other.
 */
private fun Modifier.fadedIntoTheRoom(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush = FADE_FROM_LEFT, blendMode = BlendMode.DstIn)
        drawRect(brush = FADE_TO_BOTTOM, blendMode = BlendMode.DstIn)
    }

/**
 * A curve rather than a ramp: a straight line from clear to solid shows on a television as a
 * visible band where it starts, and a smooth start is what makes a fade read as light.
 * Smootherstep — flat at both ends, so neither end of the fade has an edge.
 */
private fun eased(t: Float): Float = t * t * t * (t * (t * SMOOTHER_A - SMOOTHER_B) + SMOOTHER_C)

/** Perlin's smootherstep, 6t⁵ − 15t⁴ + 10t³. */
private const val SMOOTHER_A = 6f
private const val SMOOTHER_B = 15f
private const val SMOOTHER_C = 10f

/**
 * A fade as evenly spaced samples of [eased], clear to solid over [from]..[to] of the picture —
 * or solid to clear, when not [rising]. Many small steps rather than a few: a gradient between
 * two distant stops is a straight line again.
 */
private fun fade(from: Float, to: Float, rising: Boolean): List<Color> = List(FADE_SAMPLES) { i ->
    val x = i / (FADE_SAMPLES - 1f)
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    Color.Black.copy(alpha = eased(if (rising) t else 1f - t))
}

/** Clear at its left edge, solid a little past the middle — the title and plot are written there. */
private val FADE_FROM_LEFT = Brush.horizontalGradient(fade(from = 0f, to = 0.62f, rising = true))

/** Solid under the banner, gone by the first row of posters. */
private val FADE_TO_BOTTOM = Brush.verticalGradient(fade(from = 0.4f, to = 1f, rising = false))

/** Two thirds of the screen across, from the right-hand edge. */
private const val BACKDROP_WIDTH_SHARE = 0.68f

/** From the top of the screen to just past the banner, where the first row's heading is. */
private const val BACKDROP_HEIGHT_SHARE = 0.58f

private const val BACKDROP_FADE_MILLIS = 450
private const val FADE_SAMPLES = 33

/** The one on screen, the one arriving, and one more overtaken on its way in. */
private const val MAX_LAYERS = 3

/** Halfway from the middle to the top: faces, not knees. */
private const val ABOVE_BIAS = -0.5f
