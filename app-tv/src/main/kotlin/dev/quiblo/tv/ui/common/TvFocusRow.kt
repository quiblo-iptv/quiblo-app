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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A focusable, pressable row.
 *
 * Everything a remote can act on looks the same on purpose: a viewer should be able to tell
 * what is actionable without learning a vocabulary of shapes.
 *
 * It lives in `common` rather than beside one screen because Sources, the add-source form and
 * the first-launch flow all press the same button, and a copy each is how a focus fix lands in
 * one and is forgotten in the others — the same argument [TvChip] and [TvTextField] carry.
 */
@Composable
internal fun TvFocusRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Lights the row's outline while it holds focus, the way Search and Play are lit.
     *
     * Off by default, and it has to be. The light says "this is the thing to press", and a
     * screen where every row says that has told the viewer nothing. One row per screen at
     * most — Save on the add-source form, and nothing else here.
     */
    hasGlow: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(ROW_CORNER),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                shape = RoundedCornerShape(ROW_CORNER),
            )
            // After the border, so the travelling arc is drawn over the resting outline rather
            // than under it. The row's own bounds are unchanged either way: `travellingGlow`
            // draws behind and measures nothing.
            .travellingGlow(
                isActive = hasGlow && isFocused,
                cornerRadius = ROW_CORNER,
                intensity = GLOW_INTENSITY,
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (isFocused) 1f else 0.75f),
            fontSize = 17.sp,
        )
    }
}

/** One shape for the row, so the travelling glow has an outline it can actually trace. */
private val ROW_CORNER = 10.dp

/**
 * Dimmer than the search field's.
 *
 * `travellingGlow` makes the argument itself: the search field is the only thing on its screen
 * and can carry a full-strength light, while Save sits beside a form somebody is still filling
 * in, and the same brightness there pulls the eye off the field they are typing into.
 */
private const val GLOW_INTENSITY = 0.6f
