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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A switch: a label, and a track with a knob that slides.
 *
 * **`027` #6, and the argument is that a chip cannot say "on".** Everything on the search strip
 * used to be a [TvChip] — the genres, which are a choice of one out of many, and *Include hidden*,
 * which is a switch. A chip expresses being selected by filling in, and filled-in is exactly how
 * the chosen genre looks, so a viewer scanning the strip could not tell a filter that was on from
 * a genre that was picked. Two questions with one answer drawn for both.
 *
 * A switch is unmistakable at three metres and needs no legend, which is why every television
 * settings screen in the world uses one. The knob moving is the whole affordance: it is *where*
 * rather than *what colour*, and where survives a bad panel, a colour-blind viewer and a room with
 * the lights on.
 *
 * Focus draws a ring around the whole control rather than around the track alone. The remote
 * lands on "Include hidden", not on a rectangle beside those words.
 *
 * **Nothing about it changes size when focused or switched.** The knob slides inside a track of
 * fixed width, and the ring is a border drawn on padding that is always there. A control that grew
 * would move whatever sits beside it, and this one lives on a row with the search field.
 */
@Composable
internal fun TvToggle(
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Animated rather than snapped, because the movement is the message: a knob that teleports
    // reads as two different pictures, and one that travels reads as the same knob being moved.
    val knobOffset by animateDpAsState(
        targetValue = if (isOn) TRACK_WIDTH - KNOB_SIZE - TRACK_INSET else TRACK_INSET,
        label = "toggleKnob",
    )

    Row(
        modifier = modifier
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = { onToggle(!isOn) },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (isFocused || isOn) 1f else 0.6f),
            fontSize = 15.sp,
            fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )

        Box(
            modifier = Modifier
                .width(TRACK_WIDTH)
                .height(TRACK_HEIGHT)
                .background(
                    // Filled when on, outlined when off — so the two states differ in weight and
                    // not only in where the knob is. Either one alone would do; from a sofa, both
                    // is the difference between reading it and looking twice.
                    color = if (isOn) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.15f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .size(KNOB_SIZE)
                    .background(
                        color = if (isOn) Color.Black else Color.White.copy(alpha = 0.75f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/** Wide enough that the knob visibly travels, narrow enough to sit on a row with other things. */
private val TRACK_WIDTH = 40.dp
private val TRACK_HEIGHT = 22.dp
private val KNOB_SIZE = 16.dp

/** The gap between the knob and the end of its track, at both ends. */
private val TRACK_INSET = 3.dp
