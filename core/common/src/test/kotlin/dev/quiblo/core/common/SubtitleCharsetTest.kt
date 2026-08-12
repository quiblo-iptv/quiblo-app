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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class SubtitleCharsetTest {

    /** A cue, in the shape a real file has: ASCII timing around one line of prose. */
    private fun cue(line: String) = "1\n00:00:01,000 --> 00:00:04,000\n$line\n"

    private fun bytesIn(name: String, text: String): ByteArray? =
        runCatching { text.toByteArray(Charset.forName(name)) }.getOrNull()

    private val arabic = cue("الحلقة الأولى من هذا المسلسل تبدأ الليلة على الشاشة")
    private val russian = cue("Это первая серия нашего сериала которая идёт сегодня")
    private val hebrew = cue("זו הפרק הראשון של הסדרה שמתחיל הערב על המסך")
    private val french = cue("Où était-il passé pendant que la fête déjà commencée")

    @Test
    fun `a utf-8 byte order mark is believed, and does not survive into the text`() {
        val bytes = "﻿${cue("Hello")}".toByteArray(StandardCharsets.UTF_8)
        assertEquals(StandardCharsets.UTF_8, detectSubtitleCharset(bytes))
        assertEquals(cue("Hello"), decodeSubtitle(bytes))
    }

    @Test
    fun `a utf-16 byte order mark is believed`() {
        val bytes = "﻿${cue("Hello")}".toByteArray(StandardCharsets.UTF_16LE)
        assertEquals(StandardCharsets.UTF_16LE, detectSubtitleCharset(bytes))
        assertEquals(cue("Hello"), decodeSubtitle(bytes))
    }

    @Test
    fun `plain ascii is utf-8, because there is nothing to choose between`() {
        assertEquals(
            StandardCharsets.UTF_8,
            detectSubtitleCharset(cue("Hello").toByteArray(StandardCharsets.US_ASCII)),
        )
    }

    @Test
    fun `arabic written in utf-8 without a mark is still utf-8`() {
        val bytes = arabic.toByteArray(StandardCharsets.UTF_8)
        assertEquals(StandardCharsets.UTF_8, detectSubtitleCharset(bytes))
        assertEquals(arabic, decodeSubtitle(bytes))
    }

    @Test
    fun `arabic written in windows-1256 is read as arabic, not as mojibake`() {
        // The failure INC-F10 names. Assumed UTF-8, these bytes are not valid UTF-8 at all,
        // and the older habit of falling back to Latin-1 turns the line into symbols.
        val bytes = bytesIn("windows-1256", arabic)
        assumeTrue(bytes != null, "windows-1256 is not available on this platform")

        assertEquals(arabic, decodeSubtitle(bytes!!))
    }

    @Test
    fun `russian in windows-1251 is not mistaken for arabic in windows-1256`() {
        // The two encodings both turn most high bytes into letters, so this is the case the
        // frequency scoring exists for: which letters, and in what proportion.
        val bytes = bytesIn("windows-1251", russian)
        assumeTrue(bytes != null, "windows-1251 is not available on this platform")

        assertEquals(russian, decodeSubtitle(bytes!!))
    }

    @Test
    fun `hebrew in windows-1255 is read as hebrew`() {
        val bytes = bytesIn("windows-1255", hebrew)
        assumeTrue(bytes != null, "windows-1255 is not available on this platform")

        assertEquals(hebrew, decodeSubtitle(bytes!!))
    }

    @Test
    fun `accented french stays latin rather than being claimed by a non-latin encoding`() {
        val bytes = bytesIn("windows-1252", french)
        assumeTrue(bytes != null, "windows-1252 is not available on this platform")

        assertEquals(french, decodeSubtitle(bytes!!))
    }

    @Test
    fun `bytes that are nothing recognisable still decode to something rather than throwing`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0x81.toByte(), 0x8D.toByte(), 0x90.toByte())
        assertTrue(decodeSubtitle(bytes).isNotEmpty())
    }
}
