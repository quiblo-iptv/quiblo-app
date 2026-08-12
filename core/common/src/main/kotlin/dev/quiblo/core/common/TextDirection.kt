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

/** The writing direction of a piece of text, where the text says which one it is. */
enum class TextDirection {
    LeftToRight,
    RightToLeft,
}

/**
 * The direction of this text by the first-strong rule — rule P2 of the Unicode bidirectional
 * algorithm. The first character that is strongly left-to-right or strongly right-to-left decides
 * the whole string; characters inside an isolate are skipped, because an isolate exists precisely
 * to keep its contents from deciding anything outside it.
 *
 * Returns `null` when nothing in the string is strong — "2026", "S01 E04", punctuation, an emoji.
 * A caller that gets `null` keeps whatever direction it already had, so a numeric title never
 * flips a right-to-left screen.
 *
 * A contains-check would answer differently and would be wrong: "Dune 2 مترجم" is a
 * left-to-right title that carries an Arabic word, and the first strong character says so.
 */
fun String.firstStrongDirection(): TextDirection? =
    codePointsOutsideIsolates().firstNotNullOfOrNull { it.strongDirection() }

/** True when [firstStrongDirection] says right-to-left. Absent a strong character, false. */
fun String.isRightToLeft(): Boolean = firstStrongDirection() == TextDirection.RightToLeft

/**
 * The code points of this string that are not inside an isolate. An isolate left unterminated
 * swallows the rest of the string, which is what the algorithm says and is also the safer answer:
 * nothing decides, and the caller keeps the direction it had.
 */
private fun String.codePointsOutsideIsolates(): Sequence<Int> = sequence {
    var index = 0
    var isolateDepth = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        index += Character.charCount(codePoint)
        when {
            codePoint in ISOLATE_INITIATORS -> isolateDepth++
            codePoint == POP_DIRECTIONAL_ISOLATE -> if (isolateDepth > 0) isolateDepth--
            isolateDepth == 0 -> yield(codePoint)
        }
    }
}

private fun Int.strongDirection(): TextDirection? = when (Character.getDirectionality(this)) {
    Character.DIRECTIONALITY_LEFT_TO_RIGHT -> TextDirection.LeftToRight
    Character.DIRECTIONALITY_RIGHT_TO_LEFT,
    Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
    -> TextDirection.RightToLeft

    else -> null
}

/** Left-to-right, right-to-left and first-strong isolate initiators. */
private val ISOLATE_INITIATORS = 0x2066..0x2068
private const val POP_DIRECTIONAL_ISOLATE = 0x2069
