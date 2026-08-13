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

/**
 * The writing system a title is set in — as much as its characters can say.
 *
 * This is a reading list, not a language list. A viewer who does not read Cyrillic cannot read
 * Russian or Ukrainian or Serbian, and grouping the three under one entry is what makes the
 * choice answerable. Japanese is [Kana] because a title in kana is unmistakably Japanese, while
 * one written only in kanji is [Han] and shares that entry with Chinese — which is the honest
 * answer, since those characters alone do not say which language it is.
 */
enum class TitleScript {
    Latin,
    Arabic,
    Hebrew,
    Cyrillic,
    Greek,
    Han,
    Kana,
    Hangul,
    Devanagari,
    Thai,
    ;

    companion object {
        /**
         * The scripts a viewer can choose to hide.
         *
         * Every entry, in the order they are offered. A title in a script that is not on this
         * list is never hidden — a filter that can silently remove something the viewer has no
         * control over is worse than one that lets a few titles through.
         */
        val offered: List<TitleScript> = entries
    }
}

/**
 * The script of this title, read from its first letter.
 *
 * The first letter and not a count of the whole string, for the same reason
 * [firstStrongDirection] uses the first strong character: "Dune 2 مترجم" is a Latin title with an
 * Arabic word appended, and a viewer hiding Arabic wants to keep it. Characters inside an isolate
 * are skipped for the same reason.
 *
 * Returns `null` when the title has no letters at all — a channel called "4K 01" says nothing
 * about what its viewer reads, and nothing is what it should be treated as saying.
 */
fun String.firstStrongScript(): TitleScript? =
    codePointsOutsideIsolates()
        .filter { Character.isLetter(it) }
        .firstNotNullOfOrNull { Character.UnicodeScript.of(it).toTitleScript() }

/**
 * True when this title is written in one of [hidden].
 *
 * False for an empty set, and false for a title with no letters. Both are the same rule: the
 * filter hides only what it can positively identify (INC-F14).
 */
fun String.isInHiddenScript(hidden: Set<TitleScript>): Boolean =
    hidden.isNotEmpty() && firstStrongScript() in hidden

private fun Character.UnicodeScript.toTitleScript(): TitleScript? = when (this) {
    Character.UnicodeScript.LATIN -> TitleScript.Latin
    Character.UnicodeScript.ARABIC -> TitleScript.Arabic
    Character.UnicodeScript.HEBREW -> TitleScript.Hebrew
    Character.UnicodeScript.CYRILLIC -> TitleScript.Cyrillic
    Character.UnicodeScript.GREEK -> TitleScript.Greek
    Character.UnicodeScript.HAN -> TitleScript.Han
    Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> TitleScript.Kana
    Character.UnicodeScript.HANGUL -> TitleScript.Hangul
    Character.UnicodeScript.DEVANAGARI -> TitleScript.Devanagari
    Character.UnicodeScript.THAI -> TitleScript.Thai
    // Deliberately not a catch-all bucket. An unrecognised script is a title this filter has no
    // opinion about, and a title it has no opinion about stays visible.
    else -> null
}
