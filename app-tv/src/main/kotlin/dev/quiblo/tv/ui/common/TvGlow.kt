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

import android.graphics.Matrix
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
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
     * they are still filling in.
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

        val centre = Offset(size.width / 2f, size.height / 2f)

        /*
         * Mostly nothing, with one bright arc in it, drawn three times.
         *
         * The stops matter more than the colours: the lit part is a sixth of the circuit, so
         * what travels reads as a single light rather than as a rotating rainbow. The first and
         * last stop are the same value because a sweep gradient wraps, and a seam is visible
         * from three metres when nothing else on the screen is moving.
         *
         * Three passes, widest and faintest first.
         *
         * **A single hairline is invisible on a fifty-inch panel, which is what the first
         * version of this was.** There is no blur to reach for — `Modifier.blur` wants API 31
         * and this app supports 30 — so the bloom is built by stroking the same outline three
         * times, each wider and fainter than the last. The eye reads the stack as one light
         * with a halo, which is what a glow is, and it costs three draw calls.
         */
        BLOOM.forEach { (multiplier, alpha) ->
            val shader = SweepGradientShader(
                center = centre,
                colors = listOf(
                    colour.copy(alpha = 0f),
                    colour.copy(alpha = 0f),
                    colour.copy(alpha = alpha * intensity),
                    colour.copy(alpha = 0f),
                    colour.copy(alpha = 0f),
                ),
                colorStops = listOf(0f, ARC_START, ARC_PEAK, ARC_END, 1f),
            )
            shader.setLocalMatrix(Matrix().apply { setRotate(angle, centre.x, centre.y) })

            /*
             * Drawn on the node's own edge, not inside it.
             *
             * The first version inset the rectangle by half the stroke, which put the light a
             * couple of pixels in from the field's outline — close enough to look like a
             * mistake rather than a highlight. Straddling the border is what makes it read as
             * the field lighting up instead of as something drawn near it.
             */
            drawRoundRect(
                brush = ShaderBrush(shader),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerRadius.toPx()),
                style = Stroke(width = width.toPx() * multiplier),
            )
        }
    }
}

/**
 * The bloom, as stroke width against brightness.
 *
 * Widest first so the sharp core lands on top of its own halo. The numbers are tuned for a
 * television across a room: the outer pass is nearly invisible up close and is the whole reason
 * the light reads at all from a sofa.
 */
private val BLOOM = listOf(
    4.5f to 0.10f,
    2.4f to 0.26f,
    1.0f to 1.0f,
)

/*
 * Where the lit arc begins, peaks and ends, round a circuit measured from 0 to 1.
 *
 * A sixth of the ring, centred. Wider and it stops reading as one travelling light; narrower
 * and it is invisible from a sofa.
 */
private const val ARC_START = 0.42f
private const val ARC_PEAK = 0.5f
private const val ARC_END = 0.58f

/** Slow enough to read as breathing. A fast circuit is a control asking to be pressed. */
internal const val CIRCUIT_MILLIS = 6_000
