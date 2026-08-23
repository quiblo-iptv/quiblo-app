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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The Quiblo mark, filling the space it is given.
 *
 * **`Icon(painterResource(ic_launcher_foreground))` does not do this, and that is the bug this
 * exists to fix.** An adaptive launcher icon is authored in a 108-unit box of which only the
 * middle 66 is ever drawn — the outer third is margin the launcher is free to crop to a circle
 * or a squircle. Rendering that drawable at 108dp therefore produces a mark that occupies about
 * sixty per cent of the box it was asked to fill, which on the search screen made a deliberately
 * large logo look small beside its own wordmark.
 *
 * The same trap is written down in the wiki's `tools/brand.mjs`, where the fix is a 1.38 scale
 * out of the safe zone. It was not applied here, and this is that fix in Compose: the three
 * paths are drawn to the edges of whatever size they are handed, so `size(120.dp)` means a mark
 * 120dp across rather than a 120dp box with a 73dp mark in it.
 *
 * The geometry is the launcher drawable's, coordinate for coordinate. If the app's icon changes,
 * change it here too — there is no way to derive one from the other.
 */
@Composable
fun QuibloMark(modifier: Modifier = Modifier, colour: Color = Color.White) {
    Canvas(modifier = modifier) {
        // The drawable's own 108-unit grid, scaled to whatever this is asked to fill.
        val unit = size.minDimension / VIEWPORT
        fun x(value: Float) = value * unit
        val stroke = x(STROKE)

        // The ring: the body of the Q. Inset by half the stroke so it does not clip.
        drawCircle(
            color = colour,
            radius = x(RING_RADIUS),
            center = Offset(x(CENTRE), x(CENTRE)),
            style = Stroke(width = stroke),
        )

        // The tail, breaking the ring at the lower right.
        drawLine(
            color = colour,
            start = Offset(x(TAIL_FROM), x(TAIL_FROM)),
            end = Offset(x(TAIL_TO), x(TAIL_TO)),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )

        // The play mark, as a triangle.
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x(PLAY_LEFT), x(PLAY_TOP))
                lineTo(x(PLAY_LEFT), x(PLAY_BOTTOM))
                lineTo(x(PLAY_TIP), x(CENTRE))
                close()
            },
            color = colour,
        )
    }
}

/*
 * Every number below is read straight off `ic_launcher_foreground.xml`, then shifted so the
 * mark sits against the edges of its box instead of inside the launcher's safe zone. The
 * drawable's ring spans 36..72 of 108; here it spans the full width with room for its stroke.
 */
private const val VIEWPORT = 108f
private const val CENTRE = 54f
private const val STROKE = 7.5f
private const val RING_RADIUS = 46f

private const val TAIL_FROM = 86f
private const val TAIL_TO = 104f

private const val PLAY_LEFT = 39f
private const val PLAY_TOP = 31f
private const val PLAY_BOTTOM = 77f
private const val PLAY_TIP = 80f
