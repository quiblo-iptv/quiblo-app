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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
     * A parameter rather than one constant, because the two callers follow different things —
     * [GRID_CROSSFADE_MILLIS] follows focus and [PLAYER_CROSSFADE_MILLIS] follows the picture —
     * and one shared value would make the next person to tune either of them tune both without
     * noticing. They happen to agree today, and that is a fact about the tuning rather than
     * about the design.
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
        drawPools(start, end, nearAt = Offset(NEAR_X, NEAR_Y), farAt = Offset(FAR_X, FAR_Y))
    }
}

/**
 * The two pools, given their colours and where on the screen they sit.
 *
 * Shared by the artwork backdrop above and the drifting glow below, so the two cannot come to
 * disagree about how light on this app is shaped. The offsets are fractions of the screen rather
 * than pixels, which is what lets the glow move them without knowing the size of anything.
 */
private fun DrawScope.drawPools(start: Color, end: Color, nearAt: Offset, farAt: Offset) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(start, Color.Transparent),
            center = Offset(size.width * nearAt.x, size.height * nearAt.y),
            radius = size.width * NEAR_RADIUS,
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(end, Color.Transparent),
            center = Offset(size.width * farAt.x, size.height * farAt.y),
            radius = size.width * FAR_RADIUS,
        ),
    )
}

/**
 * Light for a screen that has no artwork to take it from.
 *
 * **Search opens on nothing and stays on nothing until somebody types.** The catalogue lights
 * itself from whatever poster the remote is resting on; a search screen has no poster, so before
 * `022` it inherited whatever the last one had left lit — the colours of a film looked at two tabs
 * ago, sitting behind an empty field. Clearing that and leaving black would have been honest and
 * bleak, which is the exact problem `014` added ambient light to solve in the first place.
 *
 * So this is light that comes from nowhere but itself: two pools that drift and turn slowly
 * through the spectrum, never arriving anywhere and never repeating an arrangement the eye can
 * catch. It costs no request, no bitmap and no focus tracking, and it cannot be the wrong colours
 * for what is on screen because there is nothing on screen to be wrong about.
 *
 * **It is exactly as bright as artwork is**, because it is built through the same fixed lightness,
 * the same saturation floor and the same [BACKDROP_ALPHA] ceiling. A glow that read brighter than
 * a poster's would make Search the loudest screen in the app, which is the opposite of what a
 * screen you arrive at to type is for.
 */
fun Modifier.driftingGlow(): Modifier = composed {
    val drift = rememberInfiniteTransition(label = "roomGlow")

    /*
     * The same circuit the field's own highlight travels on.
     *
     * Deliberately shared rather than a period of its own: the light going round the search box
     * and the light in the room behind it are one thing seen twice, and two nearly-equal periods
     * would drift apart into two things that never quite agree — which is the sort of wrongness
     * nobody can name and everybody sees.
     */
    val angle by drift.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(CIRCUIT_MILLIS, easing = LinearEasing)),
        label = "roomGlowAngle",
    )

    // Turning rather than sitting on two fixed colours. A hue that keeps going has no endpoints
    // for the eye to learn, and a full turn takes twenty circuits — long enough that nobody
    // watching a search screen ever sees the colour change, only that it has.
    val hue by drift.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(HUE_TURN_MILLIS, easing = LinearEasing)),
        label = "roomGlowHue",
    )

    drawBehind {
        val radians = angle * PI.toFloat() / HALF_TURN
        val orbitX = cos(radians) * DRIFT_REACH
        val orbitY = sin(radians) * DRIFT_REACH

        drawPools(
            start = glowOfHue(hue, BACKDROP_ALPHA),
            end = glowOfHue((hue + POOL_HUE_APART) % FULL_TURN, BACKDROP_ALPHA * SECOND_POOL_SCALE),
            // Opposite ends of the same orbit, so the room has a direction that turns rather
            // than two lamps wandering independently.
            nearAt = Offset(NEAR_X + orbitX, NEAR_Y + orbitY),
            farAt = Offset(FAR_X - orbitX, FAR_Y - orbitY),
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

/**
 * A pool of light of one hue, through the same treatment artwork gets.
 *
 * The saturation is the floor rather than a measurement, because there is no pixel here to
 * measure — but the lightness and the alpha are the artwork path's own, which is what keeps
 * [driftingGlow] from being brighter than the catalogue it sits beside.
 */
private fun glowOfHue(hue: Float, alpha: Float): Color =
    floatArrayOf(hue, GLOW_SATURATION, GLOW_LIGHTNESS).toGlow(alpha)

/* [driftingGlow]'s own numbers. Slow enough that nobody watching a search screen sees a cycle. */

/** A full turn of the colour wheel, in the degrees `HSLToColor` reads a hue in. */
private const val FULL_TURN = 360f

/** How far apart the two pools are kept on that wheel, so they light the room from two colours. */
private const val POOL_HUE_APART = 140f

/** Two minutes for a whole turn of hue: a colour never seen to change, only seen to have. */
private const val HUE_TURN_MILLIS = 120_000

/** Half a turn, which is also the degrees in pi radians. */
private const val HALF_TURN = 180f

/** How far a pool travels from where it sits, as a fraction of the screen. */
private const val DRIFT_REACH = 0.12f

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

/**
 * How long the light behind a grid takes to become the new light.
 *
 * **700 until `022`, and the reasoning for it was doing a job something else already does.** It
 * was set longer than a D-pad key repeat so that holding right along a row produced one slow
 * drift rather than twelve flashes — but nothing is fetched at all until focus has rested for
 * [SETTLE_MILLIS], so a row flown through never produces twelve colours to flash between. The
 * crossfade was paying for a problem the settle had already solved, and what it bought instead
 * was light that arrived after the tile it belonged to.
 *
 * The same 300 as the player, because the two are now answering the same complaint.
 */
const val GRID_CROSSFADE_MILLIS = 300

/**
 * The player's crossfade.
 *
 * The same number as the grid's, and kept as its own name rather than folded into one: the two
 * follow different things — one follows focus, the other follows the picture — and a single
 * constant would make the next person to tune one of them tune both without noticing.
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

/**
 * Long enough that a held D-pad passes straight through a tile without costing anything.
 *
 * **Deliberately untouched when the crossfade came down in `022`.** This is the restraint, not
 * the slowness: it is what makes walking a row cost one fetch at the end rather than one per tile
 * passed, and it is also what makes a short crossfade safe — a row flown through never produces a
 * queue of colours to flash between, because it never asks for them.
 */
private const val SETTLE_MILLIS = 180L

/** Four times what the sampler reads, and a quarter of the bytes the first version decoded. */
private const val AMBIENT_THUMB = 24
