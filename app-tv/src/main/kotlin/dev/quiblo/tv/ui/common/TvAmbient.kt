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

// The file is named for what it is rather than for the one class in it: the class is a pair of
// colours and the useful half of this file is the two functions that make and paint them.
@file:Suppress("MatchingDeclarationName")

package dev.quiblo.tv.ui.common

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.get
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The colours of whatever is on screen, spilled onto the black behind it.
 *
 * **A television app that is black everywhere reads as bleak, and that is the whole of the
 * problem this solves.** A phone is held at arm's length in a lit room; a television fills a
 * dark one, and an unlit rectangle three metres wide is not neutral, it is a void with a grid
 * of posters floating in it. Every television interface worth using — Google TV's, a set-top
 * box's, YouTube's ambient mode — answers this the same way: take the colours of the thing
 * being looked at and let them light the room behind it.
 *
 * Two rules keep it from becoming the thing you look at:
 *
 * - **It is never brighter than the content.** The alphas here are low enough that the
 *   backdrop reads as a tint on black rather than as a picture, because the moment it competes
 *   with a poster it has taken the viewer's attention for nothing.
 * - **It crossfades slowly.** Focus moves along a row faster than the eye wants a background
 *   to move; a backdrop that snapped with every press would be a strobe. The transition is
 *   longer than a key repeat on purpose, so holding the D-pad along a row produces one slow
 *   drift rather than twelve flashes.
 */
@Immutable
data class AmbientColours(val start: Color, val end: Color) {

    companion object {
        /** Black on black. What the app looked like before, and the state it falls back to. */
        val None = AmbientColours(Color.Transparent, Color.Transparent)
    }
}

/**
 * Paints [colours] as two soft pools of light, and nothing else.
 *
 * Corners rather than a centred wash: a wash behind a grid of posters dims the middle of the
 * screen, which is where everything a viewer is reading happens to be. Light from the edges
 * leaves the middle alone.
 */
fun Modifier.ambientBackdrop(
    colours: AmbientColours,
    /**
     * How long the light takes to become the new light.
     *
     * A parameter rather than one constant, because the two callers are answering different
     * things. A browse grid is answering a D-pad, and [GRID_CROSSFADE_MILLIS] is longer than a
     * key repeat on purpose: holding right along a row should be one slow drift rather than
     * twelve flashes. The player is answering the picture itself, where the same number reads as
     * the light lagging behind the scene — see [PLAYER_CROSSFADE_MILLIS].
     */
    crossfadeMillis: Int = GRID_CROSSFADE_MILLIS,
): Modifier = composed {
    val start by animateColorAsState(
        targetValue = colours.start,
        animationSpec = tween(crossfadeMillis),
        label = "ambientStart",
    )
    val end by animateColorAsState(
        targetValue = colours.end,
        animationSpec = tween(crossfadeMillis),
        label = "ambientEnd",
    )

    drawBehind {
        if (start.alpha == 0f && end.alpha == 0f) return@drawBehind

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(start, Color.Transparent),
                center = Offset(size.width * NEAR_X, size.height * NEAR_Y),
                radius = size.width * NEAR_RADIUS,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(end, Color.Transparent),
                center = Offset(size.width * FAR_X, size.height * FAR_Y),
                radius = size.width * FAR_RADIUS,
            ),
        )
    }
}

/**
 * Two colours worth lighting a room with, from any picture.
 *
 * **Not the average.** Averaging a poster gives mud — every photograph averages to a brownish
 * grey, and a wall of brownish grey is what this feature exists to avoid. It samples the
 * quadrants, throws away anything too dark or too washed out to carry, and then forces what
 * survives to a fixed lightness and a floor of saturation. The result is a colour *related* to
 * the artwork rather than taken from it, which is what reads as light rather than as a smear.
 *
 * Returns [AmbientColours.None] when there is nothing usable — a black-and-white poster, a
 * letterboxed frame that is mostly bars — because a wrong colour is worse than no colour.
 */
fun ambientFrom(bitmap: Bitmap?, alpha: Float = BACKDROP_ALPHA): AmbientColours {
    if (bitmap == null || bitmap.width < 2 || bitmap.height < 2) return AmbientColours.None

    val candidates = buildList {
        val stepX = bitmap.width / SAMPLE_GRID
        val stepY = bitmap.height / SAMPLE_GRID
        for (x in 0 until SAMPLE_GRID) {
            for (y in 0 until SAMPLE_GRID) {
                val sampleX = (x * stepX + stepX / HALF).coerceIn(0, bitmap.width - 1)
                val sampleY = (y * stepY + stepY / HALF).coerceIn(0, bitmap.height - 1)
                val px = bitmap[sampleX, sampleY]
                val hsl = FloatArray(HSL_COMPONENTS)
                ColorUtils.colorToHSL(px, hsl)
                // Too dark to be light, or too grey to be a colour.
                if (hsl[LIGHTNESS] < MIN_LIGHTNESS || hsl[SATURATION] < MIN_SATURATION) continue
                add(hsl.copyOf())
            }
        }
    }

    if (candidates.isEmpty()) return AmbientColours.None

    // The most and least saturated survivor, so the two pools differ from each other. Taking
    // the two brightest would light both corners with the same colour and waste the second.
    val sorted = candidates.sortedBy { it[SATURATION] }
    return AmbientColours(
        start = sorted.last().toGlow(alpha),
        end = sorted.first().toGlow(alpha * SECOND_POOL_SCALE),
    )
}

/** Fixed lightness and a floor under saturation: a pool of light, not a copy of a pixel. */
private fun FloatArray.toGlow(alpha: Float): Color {
    val corrected = floatArrayOf(this[HUE], this[SATURATION].coerceIn(GLOW_SATURATION, 1f), GLOW_LIGHTNESS)
    return Color(ColorUtils.HSLToColor(corrected)).copy(alpha = alpha)
}

/** Sampled on a grid rather than every pixel: this runs on artwork, not on a hot path. */
private const val SAMPLE_GRID = 6

/** The three components of an HSL triple, named so the arithmetic above reads. */
private const val HUE = 0
private const val SATURATION = 1
private const val LIGHTNESS = 2
private const val HSL_COMPONENTS = 3

/** Sampling the middle of a cell rather than its corner. */
private const val HALF = 2

/*
 * Where the two pools sit, as fractions of the screen.
 *
 * Both are pushed into corners and neither reaches the middle, because the middle is where
 * every poster and every title on this app happens to be.
 */
private const val NEAR_X = 0.16f
private const val NEAR_Y = 0.1f
private const val NEAR_RADIUS = 0.72f
private const val FAR_X = 0.88f
private const val FAR_Y = 0.86f
private const val FAR_RADIUS = 0.66f

private const val MIN_LIGHTNESS = 0.16f
private const val MIN_SATURATION = 0.12f

private const val GLOW_SATURATION = 0.45f
private const val GLOW_LIGHTNESS = 0.42f

/**
 * Low, and worth defending. Raised past about 0.30 the backdrop stops being light in a room and
 * starts being a coloured screen with content on it, and every poster on top of it loses.
 */
const val BACKDROP_ALPHA = 0.22f

/** The far corner is quieter, so the screen has a direction rather than two equal lamps. */
private const val SECOND_POOL_SCALE = 0.7f

/** Longer than a D-pad key repeat, so a held press drifts rather than strobes. */
const val GRID_CROSSFADE_MILLIS = 700

/**
 * The player's crossfade, and it is short for the opposite reason the grid's is long.
 *
 * A grid backdrop follows *focus*, which moves faster than the eye wants a background to; the
 * player's follows the *picture*, and there the same 700ms read as the light arriving after the
 * scene it belongs to. Short enough to feel attached to the frame, long enough that a hard cut is
 * still a fade rather than a flash.
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
val LocalAmbientSink = staticCompositionLocalOf<(String?) -> Unit> { {} }

/**
 * The colours of the artwork at [url].
 *
 * **The first version of this took several seconds per poster and it was not the arithmetic.**
 * Reading six pixels is free; what was not free was asking Coil for the picture again. A request
 * at a different size is a different memory-cache key, so every time focus landed on a tile this
 * went out and fetched a poster the tile beside it had *just* downloaded — a network round trip
 * per press, on a television's wifi, while the catalogue was still loading its own images.
 *
 * Three things fix it, and the first is the one that matters:
 *
 * - **Every answer is kept.** A poster's colours cannot change, so they are worked out once per
 *   URL for the life of the app and every later visit is a map lookup. Walking back along a row
 *   is now instant, which is most of what walking a catalogue is.
 * - **Nothing starts until focus settles.** Flying along a row used to queue a fetch per tile
 *   and then wait for all of them; a short pause means only the tile actually being looked at
 *   ever costs anything. This is the same lesson as the guide prefetch, in a different place —
 *   rows flown past are not rows anyone read.
 * - **The thumbnail is smaller.** Twenty-four pixels is still four times what the sampler reads,
 *   and it is a quarter of the bytes to decode.
 *
 * A poster nobody has seen before still has to arrive before its colours can be read. That wait
 * is the download, it is once per poster ever, and the screen simply stays as it was until then.
 */
@Composable
fun rememberAmbient(url: String?): AmbientColours {
    val context = LocalContext.current
    var colours by remember { mutableStateOf(AmbientColours.None) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            colours = AmbientColours.None
            return@LaunchedEffect
        }

        // Already known: no wait, no request, no frame where the old light is still up.
        remembered[url]?.let {
            colours = it
            return@LaunchedEffect
        }

        // Cancelled by the next focus change, so a row flown through costs one fetch at the end
        // rather than one per tile passed.
        delay(SETTLE_MILLIS)

        val request = ImageRequest.Builder(context)
            .data(url)
            .size(AMBIENT_THUMB, AMBIENT_THUMB)
            // A hardware bitmap cannot be read back, and asking one for a pixel throws.
            .allowHardware(false)
            .build()

        val found = withContext(Dispatchers.Default) {
            val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image
            ambientFrom(image?.toBitmap())
        }

        remembered[url] = found
        colours = found
    }

    return colours
}

/**
 * Every set of colours worked out this session, by artwork URL.
 *
 * Bounded, and evicted oldest-first, because a 67,000-title catalogue walked end to end would
 * otherwise hold an entry per poster. A thousand of them is a few kilobytes and more than
 * anybody scrolls past in one sitting.
 */
private val remembered = object : LinkedHashMap<String, AmbientColours>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, AmbientColours>) = size > REMEMBERED_MAX
}

private const val REMEMBERED_MAX = 1_000

/** Long enough that a held D-pad passes straight through a tile without costing anything. */
private const val SETTLE_MILLIS = 180L

/** Four times what the sampler reads, and a quarter of the bytes the first version decoded. */
private const val AMBIENT_THUMB = 24
