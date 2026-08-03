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

package dev.quiblo.player.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeTest {

    /**
     * Guards the dark scheme against a partial edit.
     *
     * Comparing against the literal ARGB is the only assertion here worth making: a
     * `Color` constant can never be null, so a not-null check on one passes whatever the
     * value is.
     */
    @Test
    fun `dark theme colours keep their defined ARGB values`() {
        assertEquals(Color(0xFF0C0E14), QuibloBackgroundDark)
        assertEquals(Color(0xFF12141C), QuibloSurfaceDark)
        assertEquals(Color(0xFFE4E5F1), QuibloOnBackgroundDark)
    }
}
