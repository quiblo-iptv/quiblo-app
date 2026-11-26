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

import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The display mode a film at [contentFps] should play in, or null to leave the screen alone
 * (`FEAT-041`).
 *
 * Null when the screen already shows the film evenly — 25 fps on 50 Hz, 30 on 60 — or when no mode
 * at the current resolution would. A film is never moved to a lower resolution to get an even
 * cadence: a sharper picture that judders is the lesser fault than a softer one that does not.
 *
 * Among the modes that fit, the one that drifts least wins (23.976 Hz over 24 Hz for a 23.976 fps
 * film), then the highest refresh rate, so the menus drawn over the film stay as smooth as they can.
 */
fun matchingDisplayMode(
    contentFps: Float,
    current: DisplayModeOption,
    supported: List<DisplayModeOption>,
): DisplayModeOption? {
    if (contentFps <= 0f || !isJudderProne(contentFps, current.refreshRate)) return null
    return supported
        .filter { it.width == current.width && it.height == current.height }
        .filter { !isJudderProne(contentFps, it.refreshRate) }
        .minWithOrNull(
            compareBy<DisplayModeOption> { cadenceDrift(contentFps, it.refreshRate) }
                .thenByDescending { it.refreshRate },
        )
}

/** How far a refresh rate is from a whole multiple of the frame rate, in thousandths. */
private fun cadenceDrift(contentFps: Float, refreshRate: Float): Int {
    val ratio = refreshRate / contentFps
    return (abs(ratio - ratio.roundToInt()) * DRIFT_SCALE).roundToInt()
}

/**
 * Switches the screen to a refresh rate [contentFps] plays evenly at, and back when it leaves
 * (`FEAT-041`).
 *
 * A film at 24 frames a second on a 60 Hz panel holds its frames for two refreshes and then three,
 * and that uneven beat is a stutter on every pan however well the film decodes — what was left on
 * a MediaTek television once `BUG-064` had stopped it dropping frames. This is what every player
 * built for a television does about it, through the window's preferred display mode, the one way to
 * ask for a switch that is not seamless. The screen may go black for a moment while it changes.
 *
 * The last known rate is held between items, so the gap between two episodes does not switch the
 * screen back and forth; it is let go when the player is.
 */
@Composable
fun MatchContentFrameRate(contentFps: Float?, enabled: Boolean) {
    val view = LocalView.current
    val window = LocalContext.current.findActivity()?.window
    var held by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(contentFps) {
        if (contentFps != null) held = contentFps
    }

    DisposableEffect(window, held, enabled) {
        val display = view.display
        val fps = held
        val target = if (enabled && fps != null && display != null) {
            matchingDisplayMode(fps, display.mode.toOption(), display.supportedModes.map { it.toOption() })
        } else {
            null
        }
        if (window != null && target != null) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = target.id }
        }
        onDispose {
            if (window != null && target != null) {
                // Zero is "no preference": the system's own mode, whatever the viewer chose there.
                window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
            }
        }
    }
}

/** The refresh rates [display] offers at its current resolution, lowest first. */
fun Display.refreshRatesAtCurrentSize(): List<Float> {
    val current = mode
    return supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .map { it.refreshRate }
        .distinct()
        .sorted()
}

private fun Display.Mode.toOption() = DisplayModeOption(modeId, physicalWidth, physicalHeight, refreshRate)

/** One of the modes a display offers, as [matchingDisplayMode] reads it. */
data class DisplayModeOption(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

private const val DRIFT_SCALE = 1_000
