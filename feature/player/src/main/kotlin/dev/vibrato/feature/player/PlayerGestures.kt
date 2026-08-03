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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/**
 * Which half of the screen a vertical drag started in.
 *
 * The split is the near-universal convention in video players — brightness on the left,
 * volume on the right — and matching it is worth more than any argument for a different
 * arrangement.
 */
enum class DragTarget { BRIGHTNESS, VOLUME }

/** What a drag is currently adjusting, for the on-screen indicator. */
data class GestureFeedback(val target: DragTarget, val fraction: Float)

/**
 * Vertical drag to adjust brightness on the left half and volume on the right.
 *
 * Drag distance is scaled against the height of the gesture area rather than a fixed pixel
 * count, so a full-height swipe covers the full range on any screen. Up increases, which
 * means the sign of the drag has to be flipped: Android's y axis grows downward.
 */
fun Modifier.playerVolumeBrightnessGestures(
    onFeedback: (GestureFeedback?) -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    currentBrightness: () -> Float,
    currentVolume: () -> Float,
): Modifier = pointerInput(Unit) {
    var target: DragTarget? = null

    detectVerticalDragGestures(
        onDragStart = { offset ->
            target = if (offset.x < size.width / 2) DragTarget.BRIGHTNESS else DragTarget.VOLUME
        },
        onDragEnd = {
            target = null
            onFeedback(null)
        },
        onDragCancel = {
            target = null
            onFeedback(null)
        },
        onVerticalDrag = { _, dragAmount ->
            val fractionDelta = -dragAmount / size.height
            when (target) {
                DragTarget.BRIGHTNESS -> {
                    onBrightnessDelta(fractionDelta)
                    onFeedback(GestureFeedback(DragTarget.BRIGHTNESS, currentBrightness()))
                }

                DragTarget.VOLUME -> {
                    onVolumeDelta(fractionDelta)
                    onFeedback(GestureFeedback(DragTarget.VOLUME, currentVolume()))
                }

                null -> Unit
            }
        },
    )
}

/**
 * Window brightness for this screen only.
 *
 * Set on the window rather than in system settings: it applies while the player is in the
 * foreground and reverts on its own when it is not, which is what a viewer expects and
 * what avoids needing WRITE_SETTINGS (AC-NFR-04).
 */
class ScreenBrightness(private val activity: Activity?) {

    /** Starts from the system brightness, so the first drag continues rather than jumps. */
    private var level: Float = INITIAL_UNSET

    fun current(): Float = level.takeIf { it >= 0f } ?: DEFAULT_LEVEL

    fun adjustBy(delta: Float) {
        val window = activity?.window ?: return
        val next = (current() + delta).coerceIn(MIN_LEVEL, MAX_LEVEL)
        level = next
        window.attributes = window.attributes.apply { screenBrightness = next }
    }

    /** Hands brightness back to the system. */
    fun reset() {
        val window = activity?.window ?: return
        level = INITIAL_UNSET
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private companion object {
        const val INITIAL_UNSET = -1f
        const val DEFAULT_LEVEL = 0.5f

        /** Never fully dark: a black screen with no way back is indistinguishable from a crash. */
        const val MIN_LEVEL = 0.02f
        const val MAX_LEVEL = 1f
    }
}

/**
 * Media-stream volume, expressed as a fraction so the UI never sees raw step counts.
 *
 * The fraction is accumulated here rather than re-read from the system on every drag
 * event, because the system only stores whole steps — typically fifteen. Reading it back
 * each time rounds the drag away: a delta smaller than one step lands between two values
 * and is lost, so the volume only moves when a single event happens to cross a boundary.
 *
 * That asymmetry is what made this feel broken in one direction only. Truncating toward
 * zero turns 6.7 into 6, so a small *decrease* always crossed a step, while the same small
 * *increase* turned 7.3 back into 7 and did nothing at all. Volume went down easily and
 * refused to come back up.
 */
class MediaVolume(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxSteps = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /** Continuous position between drags; -1 until seeded from the system volume. */
    private var fraction: Float = UNSEEDED

    fun current(): Float {
        if (fraction < 0f) fraction = systemFraction()
        return fraction
    }

    fun adjustBy(delta: Float) {
        if (maxSteps <= 0) return
        fraction = (current() + delta).coerceIn(0f, 1f)
        // Round, so half a step up is a step up. Truncation is what broke this.
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * maxSteps).roundToInt(), 0)
    }

    /** Re-seeds from the system, for when the hardware keys have moved it behind our back. */
    fun resync() {
        fraction = systemFraction()
    }

    private fun systemFraction(): Float = if (maxSteps <= 0) {
        0f
    } else {
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSteps
    }

    private companion object {
        const val UNSEEDED = -1f
    }
}

/** Unwraps the Activity from a Compose context, which may be a themed wrapper. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
