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

package dev.quiblo.feature.player

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * How the playback info overlay writes its numbers (`FEAT-040`), as plain functions.
 *
 * In `Locale.ROOT` on purpose: the overlay is read off a screenshot by whoever fixes the bug, and
 * a decimal comma or another script's digits would make "23.976" something to decode first.
 */

/** `18.4 Mb/s`, `640 kb/s`. */
internal fun formatBitrate(bitsPerSecond: Long): String = when {
    bitsPerSecond >= MEGA -> String.format(Locale.ROOT, "%.1f Mb/s", bitsPerSecond / MEGA.toDouble())
    else -> "${(bitsPerSecond / KILO.toDouble()).roundToLong()} kb/s"
}

/** `512.3 MB`, `840 KB`. */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= GIBI -> String.format(Locale.ROOT, "%.2f GB", bytes / GIBI.toDouble())
    bytes >= MEBI -> String.format(Locale.ROOT, "%.1f MB", bytes / MEBI.toDouble())
    else -> "${bytes / KIBI} KB"
}

/** `24.3 s`. */
internal fun formatSeconds(millis: Long): String = String.format(Locale.ROOT, "%.1f s", millis / MILLIS_PER_SECOND)

/** `1:02:03`, or `2:03` under an hour. */
internal fun formatClock(millis: Long): String {
    val totalSeconds = millis / MILLIS_PER_SECOND.toLong()
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** `23.976`, `25`, `59.94`: as many decimals as the rate has, up to three. */
internal fun formatFrameRate(framesPerSecond: Float): String =
    String.format(Locale.ROOT, "%.3f", framesPerSecond).trimEnd('0').trimEnd('.')

/**
 * Whether a film at [contentFps] judders on a screen refreshing at [displayHz].
 *
 * Every frame has to be held for a whole number of refreshes to move evenly. 24 fps on 60 Hz holds
 * frames for two refreshes and then three, and a viewer sees that as a stutter on every pan however
 * smoothly the decoder runs — which is why the overlay flags it beside the dropped-frame counts.
 */
internal fun isJudderProne(contentFps: Float, displayHz: Float): Boolean {
    if (contentFps <= 0f || displayHz <= 0f) return false
    val ratio = displayHz / contentFps
    return abs(ratio - Math.round(ratio)) > CADENCE_TOLERANCE
}

private const val KILO = 1_000L
private const val MEGA = 1_000_000L
private const val KIBI = 1_024L
private const val MEBI = 1_048_576L
private const val GIBI = 1_073_741_824L
private const val MILLIS_PER_SECOND = 1_000.0
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** 24 fps against 23.976 Hz is a 0.1 % drift, one repeated frame every 40 s: not judder. */
private const val CADENCE_TOLERANCE = 0.01f
