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

package dev.quiblo.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.quiblo.designsystem.QuibloColours
import dev.quiblo.designsystem.QuibloShapes

/**
 * Always dark, and deliberately not the phone theme. Ink and paper, with tungsten — the light
 * inside the mark's screen — as the one colour.
 *
 * A television is watched at a distance in a dim room. The phone app's light theme would
 * be glaring there, and following the system setting is meaningless on a device that has
 * no daylight mode worth speaking of.
 */
@Composable
fun QuibloTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = QuibloColours.Glow,
            onPrimary = Color(0xFF432C00),
            background = QuibloColours.Ink,
            surface = QuibloColours.Ink2,
            onBackground = QuibloColours.Paper,
            onSurface = QuibloColours.Paper,
        ),
        shapes = QuibloShapes,
        content = content,
    )
}
