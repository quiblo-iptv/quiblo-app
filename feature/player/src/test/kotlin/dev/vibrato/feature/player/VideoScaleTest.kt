/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.feature.player

import dev.vibrato.core.model.AspectRatioMode
import dev.vibrato.core.model.videoScale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The aspect-ratio maths.
 *
 * Worth testing on its own because the bug it guards against is silent: a wrong factor
 * does not crash, it just renders every stream slightly squashed, and nobody notices until
 * a user says the picture "looks off". The assertions are about the resulting *shape* of
 * the picture rather than the raw numbers, so they still mean something if the
 * implementation changes how it gets there.
 */
class VideoScaleTest {

    private val widescreen = 16f / 9f
    private val fourThree = 4f / 3f
    private val tolerance = 0.001f

    /** The aspect the viewer actually sees, given a container and the applied scale. */
    private fun renderedAspect(containerAspect: Float, scale: Pair<Float, Float>): Float =
        containerAspect * scale.first / scale.second

    @Test
    @DisplayName("FIT preserves the source aspect and never overflows")
    fun `fit letterboxes a wide video in a narrow container`() {
        val scale = videoScale(widescreen, fourThree, AspectRatioMode.FIT)

        assertEquals(widescreen, renderedAspect(fourThree, scale), tolerance)
        // Nothing may be cropped, so neither axis may exceed the container.
        assertTrue(scale.first <= 1f + tolerance, "width overflowed: ${scale.first}")
        assertTrue(scale.second <= 1f + tolerance, "height overflowed: ${scale.second}")
    }

    @Test
    fun `fit pillarboxes a narrow video in a wide container`() {
        val scale = videoScale(fourThree, widescreen, AspectRatioMode.FIT)

        assertEquals(fourThree, renderedAspect(widescreen, scale), tolerance)
        assertTrue(scale.first <= 1f + tolerance)
        assertTrue(scale.second <= 1f + tolerance)
    }

    @Test
    @DisplayName("FILL preserves the source aspect and leaves no bars")
    fun `fill crops rather than letterboxing`() {
        val scale = videoScale(widescreen, fourThree, AspectRatioMode.FILL)

        assertEquals(widescreen, renderedAspect(fourThree, scale), tolerance)
        // Covering means at least one axis overflows and neither falls short.
        assertTrue(scale.first >= 1f - tolerance, "width left a gap: ${scale.first}")
        assertTrue(scale.second >= 1f - tolerance, "height left a gap: ${scale.second}")
    }

    @Test
    fun `zoom magnifies beyond fill without distorting`() {
        val fill = videoScale(widescreen, fourThree, AspectRatioMode.FILL)
        val zoom = videoScale(widescreen, fourThree, AspectRatioMode.ZOOM)

        assertTrue(zoom.first > fill.first, "zoom did not magnify")
        assertTrue(zoom.second > fill.second, "zoom did not magnify")
        // Same shape, just larger.
        assertEquals(
            renderedAspect(fourThree, fill),
            renderedAspect(fourThree, zoom),
            tolerance,
        )
    }

    @Test
    fun `stretch fills the container exactly and does distort`() {
        val scale = videoScale(widescreen, fourThree, AspectRatioMode.STRETCH)

        assertEquals(1f, scale.first, tolerance)
        assertEquals(1f, scale.second, tolerance)
        assertEquals(fourThree, renderedAspect(fourThree, scale), tolerance)
    }

    @Test
    fun `a video matching its container is left alone in every mode`() {
        AspectRatioMode.entries
            .filter { it != AspectRatioMode.ZOOM }
            .forEach { mode ->
                val scale = videoScale(widescreen, widescreen, mode)
                assertEquals(1f, scale.first, tolerance, "$mode scaled width")
                assertEquals(1f, scale.second, tolerance, "$mode scaled height")
            }
    }

    @Test
    @DisplayName("no correction is applied before the first frame is decoded")
    fun `an unknown video size scales by one`() {
        // Guessing here would produce a visible jump the moment the real size arrives.
        val scale = videoScale(videoAspectRatio = null, containerAspectRatio = widescreen, mode = AspectRatioMode.FIT)

        assertEquals(1f, scale.first, tolerance)
        assertEquals(1f, scale.second, tolerance)
    }

    @Test
    fun `degenerate sizes are ignored rather than producing infinities`() {
        listOf(0f, -1f).forEach { bad ->
            assertEquals(1f to 1f, videoScale(bad, widescreen, AspectRatioMode.FIT))
            assertEquals(1f to 1f, videoScale(widescreen, bad, AspectRatioMode.FIT))
        }
    }
}
