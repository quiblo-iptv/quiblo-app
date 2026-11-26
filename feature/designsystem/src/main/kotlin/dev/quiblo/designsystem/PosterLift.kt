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

package dev.quiblo.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A poster that comes towards whoever is reaching for it: the television's focus, for a finger.
 *
 * **The same gesture as the television's poster rows, re-timed for touch.** On the television a
 * poster grows by a tenth and takes a thin white frame when the remote lands on it, and that one
 * movement is most of what makes the catalogue feel like a place rather than a spreadsheet. A
 * phone has no resting focus, so the lift is spent where a finger does have a state: the press.
 * Touch it and it rises with its frame; let go and it settles back on a spring. A keyboard, a
 * D-pad or a mouse pointer on a Chromebook does have a resting state, and gets the television's
 * behaviour exactly — lifted for as long as it is focused or hovered.
 *
 * Smaller than the television's tenth, because a phone is held a foot away rather than viewed
 * from three metres: the same scale at arm's length reads as a jump.
 *
 * The frame is the theme's text colour rather than white, so it survives the light theme, and is
 * drawn inside the scale layer so it grows with the poster rather than staying behind.
 */
fun Modifier.posterLift(interactionSource: InteractionSource, shape: Shape): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isLifted = isPressed || isFocused || isHovered

    val scale by animateFloatAsState(
        targetValue = if (isLifted) LIFTED_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "posterLift",
    )
    val frame by animateFloatAsState(
        targetValue = if (isLifted) 1f else 0f,
        animationSpec = tween(FRAME_FADE_MILLIS),
        label = "posterFrame",
    )
    val colour = MaterialTheme.colorScheme.onBackground

    this
        // Over its neighbours while it is up, or the next poster along draws across its edge.
        .zIndex(if (isLifted) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(FRAME_WIDTH, colour.copy(alpha = frame), shape)
}

/** A twentieth: the television's tenth, brought to arm's length. */
private const val LIFTED_SCALE = 1.05f

/** The television's frame weight: thin enough to sit on the artwork rather than box it in. */
private val FRAME_WIDTH = 2.dp

private const val FRAME_FADE_MILLIS = 150
