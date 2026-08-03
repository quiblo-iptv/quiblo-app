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

package dev.quiblo.core.model

/**
 * Player tuning the user controls.
 *
 * Each preset carries its own engine parameters rather than leaving a `when` somewhere
 * else to map them, so adding one is a single entry and not a hunt through three files.
 *
 * These live here rather than in `:core:media` because three modules need to name them —
 * the player, the store that persists them, and the settings screen — and none of those
 * should have to depend on the media layer to do it. The values are plain numbers;
 * `Media3PlayerController` is the only thing that knows what to do with them
 * (docs/FREEZE.md §4.4).
 */
data class PlayerSettings(
    val seekInterval: SeekInterval = SeekInterval.TEN,
    val bufferMode: BufferMode = BufferMode.BALANCED,
    val maxBitrate: MaxBitrateCap = MaxBitrateCap.UNLIMITED,
)

/** How far the skip buttons jump (AC-PLAY-04). */
enum class SeekInterval(val seconds: Int) {
    FIVE(5),
    TEN(10),
    FIFTEEN(15),
    THIRTY(30),
    ;

    val millis: Long get() = seconds * MILLIS_PER_SECOND

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * How much the engine buffers ahead.
 *
 * The trade is latency against resilience. [LOW] starts fastest and is the right choice on
 * a solid connection; [HIGH] rides out a flaky one at the cost of a slower start and more
 * data held in memory.
 *
 * The ceiling is deliberately kept well under the AC-PLAY-05 budget even at [HIGH]: a
 * generous buffer must never turn a dead stream into a screen that hangs instead of
 * reporting an error.
 */
enum class BufferMode(
    val minBufferMillis: Int,
    val maxBufferMillis: Int,
    val bufferForPlaybackMillis: Int,
    val bufferForReplayMillis: Int,
) {
    LOW(
        minBufferMillis = 5_000,
        maxBufferMillis = 15_000,
        bufferForPlaybackMillis = 750,
        bufferForReplayMillis = 1_500,
    ),

    /** The previous hardcoded values, kept as the default so behaviour does not change. */
    BALANCED(
        minBufferMillis = 15_000,
        maxBufferMillis = 30_000,
        bufferForPlaybackMillis = 1_500,
        bufferForReplayMillis = 3_000,
    ),

    HIGH(
        minBufferMillis = 30_000,
        maxBufferMillis = 60_000,
        bufferForPlaybackMillis = 2_500,
        bufferForReplayMillis = 5_000,
    ),
}

/**
 * An upper bound on video bitrate, for metered or slow connections.
 *
 * Only meaningful for adaptive streams (HLS with several renditions). A single-rendition
 * stream has nothing to choose between, so the cap silently does nothing — which is the
 * correct behaviour, but worth knowing before concluding it is broken.
 */
enum class MaxBitrateCap(val bitsPerSecond: Int?) {
    UNLIMITED(null),
    EIGHT_MBPS(8_000_000),
    FOUR_MBPS(4_000_000),
    TWO_MBPS(2_000_000),
}

/**
 * How the video fills the screen.
 *
 * Applied by scaling the output surface in the player UI, not by the engine: the decoder
 * always produces frames at their native size, and how those are fitted to the display is
 * a presentation decision.
 */
enum class AspectRatioMode {
    /** Letterbox. The whole frame is visible and nothing is distorted. The default. */
    FIT,

    /** Crop to fill. Aspect preserved, the overflowing edges are cut off. */
    FILL,

    /** As [FILL], with extra magnification for content that is letterboxed in the source. */
    ZOOM,

    /** Distort to fill. Aspect is not preserved. */
    STRETCH,
    ;

    /** Extra magnification applied on top of the base fit. */
    val extraScale: Float get() = if (this == ZOOM) ZOOM_SCALE else 1f

    private companion object {
        const val ZOOM_SCALE = 1.15f
    }
}
