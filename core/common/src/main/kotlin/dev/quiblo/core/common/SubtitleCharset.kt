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

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Reads a subtitle file as text, in whatever encoding it actually is (INC-F10).
 *
 * A `.srt` is a text file with no field saying what it is encoded in, and the format predates
 * the assumption that everything is UTF-8. An Arabic subtitle written in windows-1256 is common
 * enough that treating every file as UTF-8 is not a corner case: it is the ordinary way this
 * feature fails, and it fails as a screen of mojibake rather than as an error anybody can act on.
 *
 * The order is: a byte-order mark if there is one, then UTF-8 if the bytes are valid UTF-8, then
 * a guess among the single-byte encodings that subtitle files are actually written in.
 */
fun decodeSubtitle(bytes: ByteArray): String {
    val charset = detectSubtitleCharset(bytes)
    return String(bytes, charset).removePrefix(BOM_CHARACTER)
}

/**
 * The encoding [bytes] are most likely written in.
 *
 * **A byte-order mark is a statement and is believed.** After that, valid UTF-8 is taken at its
 * word: a byte sequence that decodes cleanly as UTF-8 and contains a multi-byte character is
 * essentially never anything else by accident.
 *
 * What is left is a genuine guess, and it has to be, because the same bytes are legal text in
 * every single-byte encoding — the file does not contain the answer. So each candidate is scored
 * on how much of what it produces is *frequent* in the language that encoding exists for.
 * Arabic bytes read as windows-1251 do produce Cyrillic letters; they produce the wrong ones, in
 * the wrong proportions, and that is the difference this measures. Ties go to Western European,
 * so a file this cannot read confidently is not silently declared Arabic.
 */
fun detectSubtitleCharset(bytes: ByteArray): Charset =
    byteOrderMark(bytes) ?: guessSubtitleCharset(bytes)

private fun guessSubtitleCharset(bytes: ByteArray): Charset {
    if (bytes.isValidUtf8()) return StandardCharsets.UTF_8

    val sample = bytes.copyOf(minOf(bytes.size, SCORING_SAMPLE_BYTES))
    return CANDIDATES
        .mapNotNull { candidate -> candidate.charset()?.let { it to candidate.score(sample, it) } }
        .maxByOrNull { it.second }
        ?.first
        ?: StandardCharsets.UTF_8
}

/**
 * The encoding a byte-order mark declares, or null when there is no mark.
 *
 * UTF-32 is not read. No subtitle file has ever been written in it, and its little-endian mark
 * begins with the UTF-16LE mark, so guessing at it would misread UTF-16 files to catch nothing.
 */
private fun byteOrderMark(bytes: ByteArray): Charset? = when {
    bytes.startsWith(UTF8_MARK) -> StandardCharsets.UTF_8
    bytes.startsWith(UTF16LE_MARK) -> StandardCharsets.UTF_16LE
    bytes.startsWith(UTF16BE_MARK) -> StandardCharsets.UTF_16BE
    else -> null
}

private fun ByteArray.startsWith(prefix: IntArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it].toInt() and BYTE_MASK == prefix[it] }

/**
 * True when every byte is part of a well-formed UTF-8 sequence.
 *
 * Pure ASCII passes, which is correct and costs nothing: a file with no byte above 0x7F reads
 * the same under every encoding here, so there is nothing to choose between them.
 */
private fun ByteArray.isValidUtf8(): Boolean {
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(this))
        true
    } catch (_: CharacterCodingException) {
        false
    }
}

/**
 * One encoding worth guessing at, and the letters that are common in the language it exists for.
 *
 * **A dozen letters, not an alphabet, and that is the point.** A wrong encoding still produces
 * letters — that is exactly why this is hard — so an alphabet-sized list scores full marks for
 * every candidate and decides nothing. Hebrew read as windows-1251 is Cyrillic letters end to
 * end, and the only thing separating the two answers is that they are the *wrong* Cyrillic
 * letters: a Hebrew ר becomes a ш, which real Russian barely uses. Keeping each list to roughly
 * the ten commonest letters is what leaves that difference visible.
 *
 * Lower case only, for the same reason. Prose is mostly lower case, and admitting capitals lets
 * a misread encoding collect points from the half of the range its own language rarely reaches.
 */
private class CharsetCandidate(private val name: String, private val frequent: Set<Char>) {

    /** Null when the platform does not ship this encoding, which is a reason to skip it. */
    fun charset(): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    /**
     * How well [charset] explains the high bytes of [sample].
     *
     * Only bytes above 0x7F are scored: the ASCII range is identical in all of these, so timecodes
     * and cue numbers would give every candidate the same score and drown the signal.
     *
     * A frequent letter earns; anything that is not a letter at all loses, because a symbol where
     * a language's text should be is the shape of a misread. A letter that is merely uncommon
     * scores nothing either way — it is the neutral evidence it looks like.
     */
    fun score(sample: ByteArray, charset: Charset): Int {
        val decoded = String(sample, charset)
        if (decoded.length != sample.size) return 0

        var score = 0
        for (index in sample.indices) {
            if (sample[index].toInt() and HIGH_BIT == 0) continue
            val character = decoded[index]
            when {
                character in frequent -> score += FREQUENT_LETTER_WEIGHT
                Character.isLetter(character) -> Unit
                else -> score -= FOREIGN_SYMBOL_PENALTY
            }
        }
        return score
    }
}

/**
 * In order, and the order is the tie-break.
 *
 * Western European first so that it wins a draw. A non-Latin encoding has to be positively better
 * than "this is ordinary accented Latin text" before it is chosen, which is the conservative
 * direction: reading French as Arabic destroys the file, reading it as Latin at worst leaves an
 * accent wrong.
 */
private val CANDIDATES = listOf(
    CharsetCandidate("windows-1252", "éèàêôçüöäñáíóúâëïûùãõå".toSet()),
    CharsetCandidate("windows-1256", "الموينهربتدع".toSet()),
    CharsetCandidate("windows-1251", "оеаинтсрвл".toSet()),
    CharsetCandidate("windows-1255", "יוהמלארנתשב".toSet()),
    CharsetCandidate("windows-1254", "ışğüöçâîû".toSet()),
)

private val UTF8_MARK = intArrayOf(0xEF, 0xBB, 0xBF)
private val UTF16LE_MARK = intArrayOf(0xFF, 0xFE)
private val UTF16BE_MARK = intArrayOf(0xFE, 0xFF)

private const val SCORING_SAMPLE_BYTES = 64 * 1024
private const val FREQUENT_LETTER_WEIGHT = 2
private const val FOREIGN_SYMBOL_PENALTY = 1
private const val HIGH_BIT = 0x80
private const val BYTE_MASK = 0xFF
private const val BOM_CHARACTER = "﻿"
