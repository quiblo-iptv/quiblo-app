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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.quiblo.core.common.TextDirection
import dev.quiblo.core.common.firstStrongDirection

/**
 * Lays [content] out in the direction [text] is written in, inside a screen that keeps whatever
 * direction the system gave it.
 *
 * A plot in Arabic reads right-to-left and aligns right even when the app is English; an English
 * title inside an Arabic app reads left-to-right. Text that says nothing about its direction —
 * a year, an episode number — leaves the surrounding direction alone.
 *
 * Wrap the section, not the screen. Wrapping a whole screen moves its navigation and its padding
 * as well, which is not what a right-to-left plot is asking for.
 */
@Composable
fun AutoDirection(text: String, content: @Composable () -> Unit) {
    val direction = remember(text) { text.firstStrongDirection() }
    if (direction == null) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides when (direction) {
            TextDirection.LeftToRight -> LayoutDirection.Ltr
            TextDirection.RightToLeft -> LayoutDirection.Rtl
        },
        content = content,
    )
}
