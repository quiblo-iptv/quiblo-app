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

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AmbientLightTest {

    @Test
    fun `ambientFrom null bitmap returns None`() {
        assertEquals(AmbientColours.None, ambientFrom(null))
    }

    @Test
    fun `ambientFrom small bitmap returns None`() {
        val bitmap = mockk<Bitmap> {
            every { width } returns 1
            every { height } returns 1
        }
        assertEquals(AmbientColours.None, ambientFrom(bitmap))
    }

    @Test
    fun `AmbientColours None has transparent start and end`() {
        assertEquals(Color.Transparent, AmbientColours.None.start)
        assertEquals(Color.Transparent, AmbientColours.None.end)
    }

    @Test
    fun `AmbientColours holds custom colors`() {
        val custom = AmbientColours(start = Color.Red, end = Color.Blue)
        assertEquals(Color.Red, custom.start)
        assertEquals(Color.Blue, custom.end)
    }
}
