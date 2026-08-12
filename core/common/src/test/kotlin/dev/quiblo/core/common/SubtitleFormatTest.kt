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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubtitleFormatTest {

    @Test
    fun `every supported extension is recognised, in any case`() {
        assertEquals(SubtitleFormat.SubRip, subtitleFormatOfName("Dune.srt"))
        assertEquals(SubtitleFormat.WebVtt, subtitleFormatOfName("Dune.VTT"))
        assertEquals(SubtitleFormat.SubStationAlpha, subtitleFormatOfName("Dune.ssa"))
        assertEquals(SubtitleFormat.SubStationAlpha, subtitleFormatOfName("Dune.ass"))
        assertEquals(SubtitleFormat.Ttml, subtitleFormatOfName("Dune.dfxp"))
    }

    @Test
    fun `a query string is not part of the extension`() {
        assertEquals(
            SubtitleFormat.SubRip,
            subtitleFormatOfName("http://panel.invalid/subs/12.srt?token=abc&x=1"),
        )
    }

    @Test
    fun `a name that says nothing gets no format`() {
        assertNull(subtitleFormatOfName("Dune"))
        assertNull(subtitleFormatOfName("Dune.mkv"))
        assertNull(subtitleFormatOfName(""))
    }

    @Test
    fun `each format declares itself in its own head`() {
        assertEquals(SubtitleFormat.WebVtt, sniffSubtitleFormat("WEBVTT\n\n1\n00:00:01.000 --> x"))
        assertEquals(SubtitleFormat.SubStationAlpha, sniffSubtitleFormat("[Script Info]\nTitle: x"))
        assertEquals(SubtitleFormat.Ttml, sniffSubtitleFormat("""<?xml version="1.0"?><tt xmlns="">"""))
    }

    @Test
    fun `a comma in the cue time is what makes it subrip and a dot is what makes it webvtt`() {
        assertEquals(
            SubtitleFormat.SubRip,
            sniffSubtitleFormat("1\n00:00:01,000 --> 00:00:04,000\nHello"),
        )
        // The same file with no WEBVTT header. Only the separator tells them apart, which is
        // why the header is checked first and this rule is the fallback rather than the test.
        assertEquals(
            SubtitleFormat.WebVtt,
            sniffSubtitleFormat("1\n00:00:01.000 --> 00:00:04.000\nHello"),
        )
    }

    @Test
    fun `a byte order mark does not hide the header`() {
        assertEquals(SubtitleFormat.WebVtt, sniffSubtitleFormat("﻿WEBVTT\n\n"))
    }

    @Test
    fun `text that is not subtitles is not a format`() {
        assertNull(sniffSubtitleFormat("This is a readme.\n"))
        assertNull(sniffSubtitleFormat(""))
    }

    @Test
    fun `a language code in the name is read, in either separator`() {
        assertEquals("ar", subtitleLanguageOfName("Dune.2021.ar.srt"))
        assertEquals("ar", subtitleLanguageOfName("Dune_ar.srt"))
        assertEquals("ar", subtitleLanguageOfName("Dune-ar.srt"))
        assertEquals("ara", subtitleLanguageOfName("Dune.ara.srt"))
        assertEquals("en", subtitleLanguageOfName("Dune.EN.srt"))
    }

    @Test
    fun `a segment that is not a language is not treated as one`() {
        // This is the whole reason the ISO check is there. Without it every one of these
        // becomes a label in the subtitle menu, and none of them is a language.
        assertNull(subtitleLanguageOfName("Dune.HD.srt"))
        assertNull(subtitleLanguageOfName("Dune.1080p.srt"))
        assertNull(subtitleLanguageOfName("Dune.forced.srt"))
        assertNull(subtitleLanguageOfName("Dune.srt"))
        assertNull(subtitleLanguageOfName("Dune"))
    }
}
