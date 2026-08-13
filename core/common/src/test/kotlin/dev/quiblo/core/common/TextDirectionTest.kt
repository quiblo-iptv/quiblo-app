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

package dev.quiblo.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextDirectionTest {

    @Test
    fun `latin title reads left to right`() {
        assertEquals(TextDirection.LeftToRight, "One Piece".firstStrongDirection())
    }

    @Test
    fun `arabic title reads right to left`() {
        assertEquals(TextDirection.RightToLeft, "قطعة واحدة".firstStrongDirection())
    }

    @Test
    fun `hebrew and persian and urdu read right to left`() {
        assertTrue("שם הסרט".isRightToLeft())
        assertTrue("نام فیلم".isRightToLeft())
        assertTrue("فلم کا نام".isRightToLeft())
    }

    @Test
    fun `an arabic word inside a latin title does not flip it`() {
        assertEquals(TextDirection.LeftToRight, "Dune 2 مترجم".firstStrongDirection())
        assertFalse("Dune 2 مترجم".isRightToLeft())
    }

    @Test
    fun `a latin word inside an arabic title does not flip it`() {
        assertEquals(TextDirection.RightToLeft, "الحلقة 4 HD".firstStrongDirection())
    }

    @Test
    fun `leading digits and punctuation are skipped, not answered`() {
        assertEquals(TextDirection.RightToLeft, "4 | مسلسلات".firstStrongDirection())
        assertEquals(TextDirection.LeftToRight, "4 | Series".firstStrongDirection())
    }

    @Test
    fun `text with no strong character answers nothing`() {
        assertNull("2026".firstStrongDirection())
        assertNull("01 · 04 — 1080".firstStrongDirection())
        assertNull("".firstStrongDirection())
        assertFalse("2026".isRightToLeft())
    }

    @Test
    fun `an isolate does not decide the direction of the text around it`() {
        val isolated = "⁧مترجم⁩ Dune"
        assertEquals(TextDirection.LeftToRight, isolated.firstStrongDirection())
    }

    @Test
    fun `an unterminated isolate swallows the rest rather than answering wrongly`() {
        assertNull("⁦مترجم".firstStrongDirection())
    }

    @Test
    fun `an emoji before the title does not decide it`() {
        assertEquals(TextDirection.RightToLeft, "🎬 مسلسل".firstStrongDirection())
    }
}
