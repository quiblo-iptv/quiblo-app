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
        // The K in "4K" is a Latin letter, so a provider's "4K | مسلسلات" *runs* left to right.
        // What it is hidden by is a different question, and `any letter of a hidden script`
        // answers it: that title is both Latin and Arabic and either set hides it.
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

    /**
     * The rule this filter used to have, kept as a measurement of the half that did not change.
     *
     * Which way a line of text runs is still decided by its first strong letter, and
     * [firstStrongScript] is what answers that. What moved is the hiding decision, below.
     */
    @Test
    fun `a trailing arabic word does not change which way a latin title runs`() {
        assertEquals(TitleScript.Latin, "Dune 2 مترجم".firstStrongScript())
    }

    /**
     * **Any letter hides, not the first one**, and this is the case that was reported.
     *
     * A catalogue is full of titles that begin in Latin and are otherwise Arabic — a provider's
     * prefix, a quality marker, a stray English word — and under the first-letter rule every one
     * of them came back for a viewer who had asked not to be shown Arabic.
     */
    @Test
    fun `any letter of a hidden script hides the title`() {
        val arabic = setOf(TitleScript.Arabic)

        assertTrue("Dune 2 مترجم".isInHiddenScript(arabic))
        assertTrue("4K | مسلسلات".isInHiddenScript(arabic))
        assertTrue("THE مسلسل الاختيار".isInHiddenScript(arabic))
        assertFalse("Dune 2".isInHiddenScript(arabic))
    }

    /**
     * A trailing bracketed tag is what a provider has *done* to a title, not what the title is.
     *
     * `Oppenheimer [عربي]` is an English film with an Arabic dub, and a viewer hiding Arabic
     * wants to keep it. All three bracket shapes, and however many of them are stacked up.
     */
    @Test
    fun `a trailing bracketed tag does not decide the title`() {
        val arabic = setOf(TitleScript.Arabic)

        assertFalse("Oppenheimer [عربي]".isInHiddenScript(arabic))
        assertFalse("Dune 2024 (مترجم)".isInHiddenScript(arabic))
        assertFalse("Dune 2024 {مترجم}".isInHiddenScript(arabic))
        assertFalse("Dune 2024 (مترجم) [عربي]".isInHiddenScript(arabic))
    }

    /**
     * The cost of the rule, pinned rather than hidden.
     *
     * The same tag written without brackets is indistinguishable from a title, so an English film
     * a panel has labelled that way disappears. Accepted knowingly: the alternative is calling a
     * title Arabic when it is *mostly* Arabic, and nobody can predict a threshold from a screen.
     */
    @Test
    fun `an unbracketed tag is not distinguishable from a title, and hides`() {
        assertTrue("Dune 2024 مترجم".isInHiddenScript(setOf(TitleScript.Arabic)))
    }

    /** And it runs both ways: an Arabic title with an English word in it hides Latin. */
    @Test
    fun `the rule is symmetric`() {
        assertTrue("مسلسل الاختيار HD".isInHiddenScript(setOf(TitleScript.Latin)))
        assertTrue("مسلسل الاختيار HD".isInHiddenScript(setOf(TitleScript.Arabic)))
        assertFalse("مسلسل الاختيار".isInHiddenScript(setOf(TitleScript.Latin)))
    }

    @Test
    fun `every script in a title is reported`() {
        assertEquals(
            setOf(TitleScript.Latin, TitleScript.Arabic),
            "Dune 2 مترجم".strongScripts(),
        )
        assertEquals(setOf(TitleScript.Latin), "One Piece".strongScripts())
        assertEquals(emptySet<TitleScript>(), "2026".strongScripts())
    }

    /** A bracket a provider never closed is not a tag, and must not eat the title behind it. */
    @Test
    fun `an unclosed bracket is left alone`() {
        assertTrue("Dune 2024 [مترجم".isInHiddenScript(setOf(TitleScript.Arabic)))
    }

    @Test
    fun `hiding a script hides titles written in it`() {
        assertTrue("قطعة واحدة".isInHiddenScript(setOf(TitleScript.Arabic)))
        assertFalse("One Piece".isInHiddenScript(setOf(TitleScript.Arabic)))
    }

    /**
     * A tag at the *front* is why the trailing-pipe form is left alone.
     *
     * Providers write `AR | <title>` far more often than `<title> | AR`, so a rule that stripped
     * the last pipe-separated segment would remove the title and keep the tag — the exact
     * inversion of what the bracket rule is for. This title is Arabic and hides, which is right.
     */
    @Test
    fun `a leading provider tag does not save an arabic title`() {
        assertTrue("AR | مسلسل الاختيار".isInHiddenScript(setOf(TitleScript.Arabic)))
    }

    @Test
    fun `an empty hidden set hides nothing`() {
        assertFalse("قطعة واحدة".isInHiddenScript(emptySet()))
        assertFalse("One Piece".isInHiddenScript(emptySet()))
    }

    @Test
    fun `cleanedForDisplay removes bracketed tags and common fillers`() {
        assertEquals("Dune", "Dune 2024 (مترجم)".cleanedForDisplay())
        assertEquals("The Matrix", "The Matrix (1999) 4K UHD".cleanedForDisplay())
        assertEquals("Interstellar", "Interstellar [BluRay] مترجم".cleanedForDisplay())
        assertEquals("Oppenheimer", "Oppenheimer (2023) {4K} [sub]".cleanedForDisplay())
        assertEquals("Film Name", "Film Name 1080p HEVC".cleanedForDisplay())
        assertEquals("Arabic Title", "Arabic Title مدبلج".cleanedForDisplay())
        assertEquals("Arabic Title", "Arabic Title مترجم".cleanedForDisplay())
        assertEquals("Movie", "EN Movie [4K]".cleanedForDisplay())
    }

    @Test
    fun `an emoji before the title does not decide it`() {
        assertEquals(TitleScript.Arabic, "🎬 مسلسل".firstStrongScript())
    }
}
