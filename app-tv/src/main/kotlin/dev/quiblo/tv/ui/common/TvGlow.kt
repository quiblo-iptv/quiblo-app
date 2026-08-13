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

package dev.quiblo.tv.ui.common

import android.graphics.PathMeasure
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A highlight that travels round the edge of something (`013` INC-E3).
 *
 * Asked for on the search field, and held in `docs/STOPPERS.md` S10 for a year of a reason:
 * **a television has exactly one moving thing on it that a viewer must never lose track of,
 * and that is the focus indicator** (`AC-TV-02`). A second moving highlight beside it is a
 * competitor for the eye, and no amount of reading the code answers whether it wins. So this is
 * written to be looked at on the panel and kept or deleted that evening, which is what S10 asked
 * for, and everything about it is tuned to lose that competition on purpose:
 *
 * - **It runs only while the field is *not* focused.** The moment the remote is on the field,
 *   the focus ring is the thing that matters and this gets out of its way. That single rule is
 *   what makes it safe: the two are never on screen together.
 * - **One arc, one colour, and it is slow.** Six seconds for a full circuit reads as breathing
 *   rather than as something asking to be pressed.
 * - The rest of the ring is not dark, it is the ordinary border — so with the animation
 *   stopped, this looks exactly like the field always did.
 *
 * **The rotation is applied to the shader, not to the drawing.** Rotating a `DrawScope` rotates
 * the rounded rectangle with it, which on a control four times wider than it is tall is a
 * spinning box rather than a travelling light. Rotating the sweep gradient's own local matrix
 * turns the colours underneath a stationary outline, which is the effect that was asked for.
 */
fun Modifier.travellingGlow(
    isActive: Boolean,
    cornerRadius: Dp,
    colour: Color = Color.White,
    width: Dp = 3.dp,
    /**
     * How bright the arc is, against the search field's own.
     *
     * Below one for anything that is not the screen's main event. The search field is the only
     * thing on its screen, so it can carry a full-strength light; a Save button sits beside a
     * form somebody is reading, and the same brightness there would pull the eye off the field
     * they are still filling in. It is the difference between a hint and a distraction.
     */
    intensity: Float = 1f,
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "glow")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CIRCUIT_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glowAngle",
    )

    drawBehind {
        if (!isActive) return@drawBehind

        /*
         * The light is a segment of the outline, moved along it — not a gradient rotated
         * behind it.
         *
         * **A sweep gradient was the first attempt and it is the wrong instrument for this
         * shape.** A sweep distributes colour evenly by *angle*, and the search field is ten
         * times wider than it is tall: most of the angular range points at the two short ends,
         * so the light crawls down the sides and leaps across the top. On the panel it read as
         * a blob appearing and vanishing rather than as anything travelling.
         *
         * Measuring the path and cutting a piece out of it moves at a constant speed in
         * pixels, which is what "travels along the edge" means to somebody watching it.
         */
        val outline = Path().apply {
            addRoundRect(
                RoundRect(
                    left = width.toPx() / 2f,
                    top = width.toPx() / 2f,
                    right = size.width - width.toPx() / 2f,
                    bottom = size.height - width.toPx() / 2f,
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                ),
            )
        }

        val measure = PathMeasure(outline.asAndroidPath(), false)
        val total = measure.length
        if (total <= 0f) return@drawBehind

        val lit = total * ARC_FRACTION
        val head = (angle / FULL_CIRCLE) * total

        /*
         * Cut once, or twice where the segment runs off the end and wraps to the start. Without
         * the second cut the light disappears for a fraction of every circuit at the same
         * corner — the seam a viewer notices precisely because nothing else is moving.
         */
        val pieces = if (head + lit <= total) {
            listOf(head to head + lit)
        } else {
            listOf(head to total, 0f to (head + lit - total))
        }

        BLOOM.forEach { (multiplier, alpha) ->
            pieces.forEach { (from, to) ->
                val piece = android.graphics.Path()
                measure.getSegment(from, to, piece, true)
                drawPath(
                    path = piece.asComposePath(),
                    color = colour.copy(alpha = alpha * intensity),
                    style = Stroke(width = width.toPx() * multiplier, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * The bloom, as stroke width against brightness.
 *
 * Widest first so the core lands on top of its own halo. **Softened after seeing the hard
 * version on screen**: a solid white segment at full alpha reads as a loading bar somebody
 * forgot to remove, not as light. Just over half brightness on the core, with two wide faint
 * passes under it, gives the same travel without the bar.
 */
private val BLOOM = listOf(
    5.5f to 0.06f,
    3.0f to 0.13f,
    1.0f to 0.55f,
)

/**
 * How much of the outline is lit at once.
 *
 * A fifth. Longer and it stops reading as one light and starts reading as a border that is
 * partly on; shorter and it is a dot crossing a very long box.
 */
private const val ARC_FRACTION = 0.28f

private const val FULL_CIRCLE = 360f

/** Slow enough to read as breathing. A fast circuit is a control asking to be pressed. */
private const val CIRCUIT_MILLIS = 6_000
