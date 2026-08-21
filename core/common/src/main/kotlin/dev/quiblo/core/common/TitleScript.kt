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
enum class TitleScript(val bit: Int) {
    Latin(1 shl 0),
    Arabic(1 shl 1),
    Hebrew(1 shl 2),
    Cyrillic(1 shl 3),
    Greek(1 shl 4),
    Han(1 shl 5),
    Kana(1 shl 6),
    Hangul(1 shl 7),
    Devanagari(1 shl 8),
    Thai(1 shl 9),
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
 * These scripts as one integer, for a column and a `WHERE` clause.
 *
 * **The bit is written down rather than taken from `ordinal`**, because this number is persisted:
 * a mask stored against sixty thousand catalogue rows would silently come to mean something else
 * the day somebody inserts an entry above another, and the failure would be titles disappearing
 * from a viewer's catalogue with nothing in the diff that looks like it did that.
 */
fun Set<TitleScript>.toMask(): Int = fold(0) { mask, script -> mask or script.bit }

/**
 * Every script this title has a letter in, as a mask — the stored form of [isInHiddenScript].
 *
 * Computed once when a catalogue row is written and read back as an integer, so hiding a writing
 * system is a bitwise test in SQL rather than a regex strip, a full codepoint walk and a `Set`
 * allocation for every row of every page. Same rule, same exception for a trailing bracketed tag,
 * same cost — paid at import instead of on every read.
 */
fun String.scriptMask(): Int = withoutTrailingTags().strongScripts().toMask()

/**
 * What [scriptMask] means when nothing has computed it yet.
 *
 * Its own value rather than `0`, because "this title is in no script we name" and "nobody has
 * looked" are different facts and only one of them is safe to filter on. A row still carrying
 * this is filtered in Kotlin, exactly as it was before the column existed.
 */
const val SCRIPT_MASK_UNKNOWN: Int = -1

/**
 * The script of this title, read from its first letter.
 *
 * The first letter and not a count of the whole string, for the same reason
 * [firstStrongDirection] uses the first strong character — an isolate's contents are skipped for
 * that reason too.
 *
 * **This is the layout question, not the hiding question.** Which way a line of text runs is
 * decided by its first strong character and by nothing else; [isInHiddenScript] used to be
 * decided the same way and no longer is. The two moved apart deliberately, so do not collapse
 * them back together.
 *
 * Returns `null` when the title has no letters at all — a channel called "4K 01" says nothing
 * about what its viewer reads, and nothing is what it should be treated as saying.
 */
fun String.firstStrongScript(): TitleScript? =
    codePointsOutsideIsolates()
        .filter { Character.isLetter(it) }
        .firstNotNullOfOrNull { Character.UnicodeScript.of(it).toTitleScript() }

/**
 * Every script this title has a letter in.
 *
 * A set rather than a single answer, because a provider's title routinely carries two: an English
 * film filed with an Arabic word for "subtitled", an Arabic series with "HD" on the end.
 */
fun String.strongScripts(): Set<TitleScript> =
    codePointsOutsideIsolates()
        .filter { Character.isLetter(it) }
        .mapNotNull { Character.UnicodeScript.of(it).toTitleScript() }
        .toSet()

/**
 * True when any part of this title is written in one of [hidden].
 *
 * **Any letter, not the first one.** The first-letter rule was this filter's original design and
 * the reported problem with it: a catalogue is full of titles that begin in Latin and are
 * otherwise Arabic — a provider's prefix, a quality marker, a stray "The" — and every one of them
 * came back for a viewer who had asked not to be shown Arabic. Reading one letter of a title and
 * deciding from it only works when titles are clean, and a panel's titles are not.
 *
 * **A trailing bracketed tag does not count**, because that is where a provider puts what it has
 * done to a title rather than what the title is: `Oppenheimer [عربي]` is an English film with an
 * Arabic dub, and hiding Arabic should not lose it. [withoutTrailingTags] is what draws that line.
 *
 * What the rule cannot save is the same tag written without brackets — `Dune 2024 مترجم` hides.
 * That is the cost of the rule and it was accepted knowingly: the alternative is counting letters
 * and calling a title Arabic when it is mostly Arabic, which is a threshold nobody can predict
 * from the screen.
 *
 * False for an empty set, and false for a title with no letters. Both are the same rule as
 * before: the filter hides only what it can positively identify (INC-F14).
 */
fun String.isInHiddenScript(hidden: Set<TitleScript>): Boolean =
    hidden.isNotEmpty() && withoutTrailingTags().strongScripts().any { it in hidden }

/**
 * This title with any trailing fillers removed for display.
 *
 * Similar to TMDB cleanup but safe for UI: removes "(مترجم)", "(YEAR)", and common
 * quality markers like "4K" or "UHD" that often clutter provider titles.
 */
fun String.cleanedForDisplay(): String =
    replace(ANY_BRACKETED, " ")
        .replace(YEAR, " ")
        .replace(DISPLAY_CLEANUP, " ")
        .replace(WHITESPACE, " ")
        .trim()

/**
 * This title with any trailing bracketed groups removed — `[…]`, `(…)` and `{…}`, repeatedly.
 *
 * **Only brackets, and only at the end.** A trailing `| AR` segment is the other shape a tag
 * comes in and it is deliberately left alone, because the same providers write it at the *front*
 * far more often: stripping the last pipe-separated segment of `AR | مسلسل الاختيار` removes the
 * title and keeps the tag, which is the exact inversion of what this is for.
 */
fun String.withoutTrailingTags(): String {
    var trimmed = trim()
    while (true) {
        val stripped = TRAILING_TAG.replace(trimmed, "").trim()
        if (stripped == trimmed) return trimmed
        trimmed = stripped
    }
}

/** One bracketed group at the very end, of any of the three kinds a provider uses. */
private val TRAILING_TAG = Regex("""\s*[\[({][^\[\](){}]*[])}]\s*$""")

/** Any bracketed group anywhere in the string. */
private val ANY_BRACKETED = Regex("""[\[({][^\[\](){}]*[])}]""")

/** Common fillers in provider titles that are noise for a UI slider. */
private val DISPLAY_CLEANUP = Regex(
    "(?i)(\\b(4k|uhd|fhd|hd|sd|hq|hevc|h26[45]|x26[45]|av1|10bit|1080p|720p|" +
        "2160p|bluray|multi|dual|dubbed|subbed|sub|ar|en|fr|vo|vf|web|webdl|cam|ts)\\b|مترجم|مدبلج)",
)

private val YEAR = Regex("""\b(19\d{2}|20\d{2})\b""")
private val WHITESPACE = Regex("""\s+""")

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
