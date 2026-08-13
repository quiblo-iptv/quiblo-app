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

class TitleScriptTest {

    @Test
    fun `each offered script is recognised from a title written in it`() {
        assertEquals(TitleScript.Latin, "One Piece".firstStrongScript())
        assertEquals(TitleScript.Arabic, "قطعة واحدة".firstStrongScript())
        assertEquals(TitleScript.Hebrew, "שם הסרט".firstStrongScript())
        assertEquals(TitleScript.Cyrillic, "Приключения".firstStrongScript())
        assertEquals(TitleScript.Greek, "Οδύσσεια".firstStrongScript())
        assertEquals(TitleScript.Han, "三国演义".firstStrongScript())
        assertEquals(TitleScript.Kana, "ワンピース".firstStrongScript())
        assertEquals(TitleScript.Hangul, "오징어 게임".firstStrongScript())
        assertEquals(TitleScript.Devanagari, "रामायण".firstStrongScript())
        assertEquals(TitleScript.Thai, "ตำนาน".firstStrongScript())
    }

    @Test
    fun `a persian title is arabic script, because the script is what is readable`() {
        assertEquals(TitleScript.Arabic, "نام فیلم".firstStrongScript())
    }

    @Test
    fun `digits and punctuation are skipped, letters are not`() {
        assertEquals(TitleScript.Arabic, "4 | مسلسلات".firstStrongScript())
        // And the limit of the rule, pinned rather than hidden: the K in "4K" is a Latin
        // letter, so a provider's "4K | مسلسلات" reads as Latin. A viewer hiding Latin loses
        // it. That is why this setting is a subtraction the viewer opts into, never a default.
        assertEquals(TitleScript.Latin, "4K | مسلسلات".firstStrongScript())
    }

    @Test
    fun `a title with no letters says nothing`() {
        assertNull("2026".firstStrongScript())
        assertNull("01 · 04 — 1080".firstStrongScript())
        assertNull("".firstStrongScript())
    }

    @Test
    fun `an unrecognised script is not hidden`() {
        // Armenian is not offered, so it has no entry and never matches a hidden set.
        assertNull("Հայկական".firstStrongScript())
        assertFalse("Հայկական".isInHiddenScript(TitleScript.offered.toSet()))
    }

    @Test
    fun `a trailing arabic word does not make a latin title arabic`() {
        assertEquals(TitleScript.Latin, "Dune 2 مترجم".firstStrongScript())
        assertFalse("Dune 2 مترجم".isInHiddenScript(setOf(TitleScript.Arabic)))
    }

    @Test
    fun `hiding a script hides titles written in it`() {
        assertTrue("قطعة واحدة".isInHiddenScript(setOf(TitleScript.Arabic)))
        assertFalse("One Piece".isInHiddenScript(setOf(TitleScript.Arabic)))
    }

    @Test
    fun `an empty hidden set hides nothing`() {
        assertFalse("قطعة واحدة".isInHiddenScript(emptySet()))
        assertFalse("One Piece".isInHiddenScript(emptySet()))
    }

    @Test
    fun `an emoji before the title does not decide it`() {
        assertEquals(TitleScript.Arabic, "🎬 مسلسل".firstStrongScript())
    }
}
