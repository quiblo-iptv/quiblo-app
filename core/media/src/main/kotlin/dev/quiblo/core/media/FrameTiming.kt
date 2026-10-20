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

package dev.quiblo.core.media

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * [FrameTimingDiagnostics] for a run of gaps between scheduled frames, in nanoseconds.
 *
 * Gaps longer than [MAX_INTERVAL_NANOS] are a pause or a seek, not a frame interval, and are left
 * out; fewer than [MIN_INTERVALS] gaps say nothing either way, and give null.
 */
internal fun summariseFrameIntervals(intervalsNanos: List<Long>): FrameTimingDiagnostics? {
    val millis = intervalsNanos
        .filter { it in 1..MAX_INTERVAL_NANOS }
        .map { it / NANOS_PER_MILLI }
    if (millis.size < MIN_INTERVALS) return null
    val mean = millis.average()
    val jitter = sqrt(millis.sumOf { (it - mean) * (it - mean) } / millis.size)
    val uneven = millis.count { abs(it - mean) > mean * UNEVEN_FRACTION }
    return FrameTimingDiagnostics(
        intervals = millis.size,
        meanMillis = mean.toFloat(),
        minMillis = millis.min().toFloat(),
        maxMillis = millis.max().toFloat(),
        jitterMillis = jitter.toFloat(),
        unevenShare = uneven.toFloat() / millis.size,
    )
}

/**
 * When the frames of the last second were put on the screen, summarised (`FEAT-040`).
 *
 * The frame counts say whether every frame was shown; this says whether they were shown *evenly*,
 * which is the other half of a stutter. A 23.976 fps film on a 60 Hz panel shows every frame and
 * still alternates 33 ms and 50 ms between them; on a 24 Hz panel every gap is 41.7 ms. Gaps that
 * wander with no pattern are a timing fault, whatever the counts say.
 *
 * Read from the time the engine schedules each frame for, which is what it asks the screen for —
 * not a measurement of the panel itself, which nothing on Android exposes to an app.
 *
 * @property meanMillis the average gap between two frames.
 * @property jitterMillis how far the gaps stray from that average (their standard deviation).
 * @property unevenShare the share of gaps more than [UNEVEN_FRACTION] away from the average.
 */
data class FrameTimingDiagnostics(
    val intervals: Int,
    val meanMillis: Float,
    val minMillis: Float,
    val maxMillis: Float,
    val jitterMillis: Float,
    val unevenShare: Float,
)

/** A gap more than this share away from the average is uneven: 33 or 50 ms around 41.7 is. */
internal const val UNEVEN_FRACTION = 0.15

private const val MAX_INTERVAL_NANOS = 500_000_000L
private const val MIN_INTERVALS = 5
private const val NANOS_PER_MILLI = 1_000_000.0
