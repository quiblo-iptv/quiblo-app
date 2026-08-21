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

package dev.quiblo.designsystem

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

/**
 * The colours of whatever is on screen, spilled onto the black behind it.
 */
@Immutable
data class AmbientColours(val start: Color, val end: Color) {

    companion object {
        /** Black on black. What the app looked like before, and the state it falls back to. */
        val None = AmbientColours(Color.Transparent, Color.Transparent)
    }
}

/**
 * Paints [colours] as two soft pools of light.
 */
fun Modifier.ambientBackdrop(
    colours: AmbientColours,
    crossfadeMillis: Int = AMBIENT_CROSSFADE_MILLIS,
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
 * Two colours worth lighting a room with, from any picture.
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
                if (hsl[LIGHTNESS] < MIN_LIGHTNESS || hsl[SATURATION] < MIN_SATURATION) continue
                add(hsl.copyOf())
            }
        }
    }

    if (candidates.isEmpty()) return AmbientColours.None

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
 * The colours of the artwork at [url].
 */
@Composable
fun rememberAmbient(url: String?, alpha: Float = BACKDROP_ALPHA): AmbientColours {
    val context = LocalContext.current
    var colours by remember { mutableStateOf(AmbientColours.None) }

    LaunchedEffect(url, alpha) {
        if (url.isNullOrBlank()) {
            colours = AmbientColours.None
            return@LaunchedEffect
        }

        val cacheKey = "$url@$alpha"
        ambientCache[cacheKey]?.let {
            colours = it
            return@LaunchedEffect
        }

        delay(SETTLE_MILLIS)

        val request = ImageRequest.Builder(context)
            .data(url)
            .size(AMBIENT_THUMB, AMBIENT_THUMB)
            .allowHardware(false)
            .build()

        val found = withContext(Dispatchers.Default) {
            val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image
            ambientFrom(image?.toBitmap(), alpha)
        }

        ambientCache[cacheKey] = found
        colours = found
    }

    return colours
}

private val ambientCache = object : LinkedHashMap<String, AmbientColours>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, AmbientColours>) = size > REMEMBERED_MAX
}

private const val REMEMBERED_MAX = 1_000
private const val SETTLE_MILLIS = 180L
private const val AMBIENT_THUMB = 24
const val AMBIENT_CROSSFADE_MILLIS = 300

private const val SAMPLE_GRID = 6
private const val HUE = 0
private const val SATURATION = 1
private const val LIGHTNESS = 2
private const val HSL_COMPONENTS = 3
private const val HALF = 2

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
const val BACKDROP_ALPHA = 0.22f
private const val SECOND_POOL_SCALE = 0.7f
