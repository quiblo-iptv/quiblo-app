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

package dev.vibrato.core.model

/**
 * Scale factors that turn the stretched-to-fill surface into the requested framing.
 *
 * Returns 1:1 until the first frame has been decoded, because the correct correction is
 * unknowable without the frame size — and guessing produces a visible jump when the real
 * size arrives.
 */
fun videoScale(
    videoAspectRatio: Float?,
    containerAspectRatio: Float,
    mode: AspectRatioMode,
): Pair<Float, Float> {
    if (mode == AspectRatioMode.STRETCH) return 1f to 1f
    val video = videoAspectRatio ?: return 1f to 1f
    if (video <= 0f || containerAspectRatio <= 0f) return 1f to 1f

    val ratio = video / containerAspectRatio
    val isWiderThanContainer = ratio > 1f

    val base = when (mode) {
        // Contain: shrink whichever axis is overfilled, leaving bars on the other.
        AspectRatioMode.FIT ->
            if (isWiderThanContainer) 1f to (1f / ratio) else ratio to 1f

        // Cover: grow whichever axis is underfilled, cropping the overflow.
        AspectRatioMode.FILL, AspectRatioMode.ZOOM ->
            if (isWiderThanContainer) ratio to 1f else 1f to (1f / ratio)

        AspectRatioMode.STRETCH -> 1f to 1f
    }

    val extra = mode.extraScale
    return (base.first * extra) to (base.second * extra)
}
