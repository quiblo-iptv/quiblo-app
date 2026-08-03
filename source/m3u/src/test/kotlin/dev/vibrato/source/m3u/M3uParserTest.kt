/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.source.m3u

import dev.vibrato.core.model.Category
import dev.vibrato.core.model.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.StringReader

/**
 * Parser tests driven by the fixture corpus in `src/test/resources/fixtures`.
 *
 * The corpus was written before the parser, deliberately, because the failure mode this
 * module has to survive is dirty real-world input rather than the spec (docs/PLAN.md §3).
 * Every fixture uses synthetic hostnames under `.invalid` — never a real provider
 * (AC-LEGAL-04).
 */
class M3uParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "Missing fixture: $name"
        }.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun parse(name: String, sourceId: Long = 7L): M3uParseResult =
        M3uParser.parseToList(StringReader(fixture(name)), sourceId)

    @Nested
    @DisplayName("Well-formed playlists")
    inner class WellFormed {

        @Test
        fun `parses every entry with its attributes`() {
            val result = parse("valid-basic.m3u")

            assertEquals(3, result.report.parsedEntries)
            assertEquals(0, result.report.skippedEntries)
            assertTrue(result.report.hadHeader)

            val first = result.channels.first()
            assertEquals("Alpha One", first.name)
            assertEquals("alpha.1", first.tvgId)
            assertEquals("http://logos.example.invalid/alpha.png", first.logoUrl)
            assertEquals("News", first.groupTitle)
            assertEquals("http://stream.example.invalid/live/alpha1.m3u8", first.streamUrl)
        }

        @Test
        fun `stamps the source id onto every entry`() {
            val result = parse("valid-basic.m3u", sourceId = 42L)
            assertTrue(result.channels.all { it.sourceId == 42L })
        }

        @Test
        fun `groups entries by group-title`() {
            val byGroup = parse("valid-basic.m3u").channels.groupBy(Channel::groupTitle)
            assertEquals(setOf("News", "Sports"), byGroup.keys)
            assertEquals(2, byGroup.getValue("News").size)
        }
    }

    @Nested
    @DisplayName("AC-PL-04 — malformed input is survived, not rejected")
    inner class Malformed {

        @Test
        fun `strips a UTF-8 byte order mark before the header`() {
            val result = parse("bom-prefixed.m3u")
            assertTrue(result.report.hadHeader, "BOM prevented the #EXTM3U header from being recognised")
            assertEquals(1, result.report.parsedEntries)
            assertEquals("BOM Channel", result.channels.single().name)
        }

        @Test
        fun `handles CRLF line endings`() {
            val result = parse("crlf-line-endings.m3u")
            assertEquals(2, result.report.parsedEntries)
            assertTrue(
                result.channels.none { it.streamUrl.endsWith("\r") || it.name.endsWith("\r") },
                "A carriage return survived into the parsed values",
            )
        }

        @Test
        fun `tolerates a missing EXTM3U header`() {
            val result = parse("missing-header.m3u")
            assertFalse(result.report.hadHeader)
            assertEquals(2, result.report.parsedEntries, "A missing header must not discard entries")
        }

        @Test
        fun `skips and counts a truncated final entry`() {
            val result = parse("truncated-final-line.m3u")
            assertEquals(1, result.report.parsedEntries)
            assertEquals(1, result.report.skippedEntries)
            assertTrue(result.report.hasSkipped)
        }

        @Test
        fun `keeps unescaped commas inside the display name`() {
            val result = parse("unescaped-commas.m3u")
            assertEquals(2, result.report.parsedEntries)
            assertEquals("History, Science, and More", result.channels[0].name)
            assertEquals("Wait, What?, The Sequel", result.channels[1].name)
        }

        @Test
        fun `recovers around junk lines and entries missing a URL`() {
            val result = parse("mixed-garbage.m3u")

            assertEquals(3, result.report.parsedEntries)
            assertEquals(2, result.report.skippedEntries)
            assertEquals(
                listOf("Good One", "Good Two", "Bad Duration Still Usable"),
                result.channels.map(Channel::name),
            )
        }

        @Test
        fun `never throws on any fixture in the corpus`() {
            val corpus = listOf(
                "valid-basic.m3u", "bom-prefixed.m3u", "crlf-line-endings.m3u",
                "missing-header.m3u", "truncated-final-line.m3u", "unescaped-commas.m3u",
                "no-group-title.m3u", "mixed-garbage.m3u", "extgrp-directive.m3u",
                "attribute-edge-cases.m3u", "duplicate-entries.m3u", "header-only.m3u",
                "empty.m3u", "html-login-page.html",
            )
            corpus.forEach { name ->
                assertDoesNotThrow("Parser threw on $name") { parse(name) }
            }
        }

        @Test
        fun `produces nothing from an HTML page served instead of a playlist`() {
            val result = parse("html-login-page.html")
            assertEquals(0, result.report.parsedEntries)
        }

        @Test
        fun `handles structurally empty input`() {
            assertEquals(0, parse("empty.m3u").report.parsedEntries)
            val headerOnly = parse("header-only.m3u")
            assertEquals(0, headerOnly.report.parsedEntries)
            assertEquals(0, headerOnly.report.skippedEntries)
            assertTrue(headerOnly.report.hadHeader)
        }
    }

    @Nested
    @DisplayName("Grouping")
    inner class Grouping {

        @Test
        @DisplayName("AC-PL-06 — entries with no group land in a single Ungrouped bucket")
        fun `entries with no group-title are ungrouped`() {
            val result = parse("no-group-title.m3u")
            assertEquals(2, result.report.parsedEntries)
            assertTrue(result.channels.all { it.groupTitle == Category.UNGROUPED_TITLE })
        }

        @Test
        fun `EXTGRP supplies a group when the attribute is absent`() {
            val result = parse("extgrp-directive.m3u")
            assertEquals("Kids", result.channels[0].groupTitle)
        }

        @Test
        fun `group-title attribute beats a competing EXTGRP directive`() {
            val result = parse("extgrp-directive.m3u")
            assertEquals("Attribute Wins", result.channels[1].groupTitle)
        }
    }

    @Nested
    @DisplayName("Attribute parsing")
    inner class Attributes {

        @Test
        fun `tolerates padding, single quotes and empty values`() {
            val result = parse("attribute-edge-cases.m3u")
            assertEquals(3, result.report.parsedEntries)

            assertEquals("pad.1", result.channels[0].tvgId)
            assertEquals("Spaced Out", result.channels[0].groupTitle)
            assertNull(result.channels[0].logoUrl, "An empty tvg-logo must become null, not an empty string")

            assertEquals("single.1", result.channels[1].tvgId)
            assertEquals("Single Quotes", result.channels[1].groupTitle)

            assertEquals("dash-and.dot.2", result.channels[2].tvgId)
            assertEquals("Odd/Chars & Symbols", result.channels[2].groupTitle)
        }

        @Test
        fun `keeps duplicate entries — de-duplication is not the parser's job`() {
            val result = parse("duplicate-entries.m3u")
            assertEquals(2, result.report.parsedEntries)
        }
    }

    @Nested
    @DisplayName("Identity")
    inner class Identity {

        @Test
        @DisplayName("AC-FAV-03 — tvg-id is the stable key when present")
        fun `stable key prefers tvg-id`() {
            val channel = parse("valid-basic.m3u").channels.first()
            assertEquals("alpha.1", channel.stableKey)
        }

        @Test
        fun `stable key falls back to the stream URL when tvg-id is absent`() {
            val channel = parse("no-group-title.m3u").channels[1]
            assertNull(channel.tvgId)
            assertEquals("http://stream.example.invalid/live/ung2.m3u8", channel.stableKey)
        }
    }

    @Nested
    @DisplayName("AC-PL-05 — scale")
    inner class Scale {

        @Test
        fun `streams a 20000 entry playlist without collecting it twice`() {
            val playlist = buildString {
                appendLine("#EXTM3U")
                repeat(20_000) { index ->
                    appendLine(
                        "#EXTINF:-1 tvg-id=\"ch.$index\" group-title=\"Group ${index % 50}\",Channel $index",
                    )
                    appendLine("http://stream.example.invalid/live/$index.m3u8")
                }
            }

            var seen = 0
            var lastName: String? = null
            val report = M3uParser.parse(StringReader(playlist), sourceId = 1L) { channel ->
                seen++
                lastName = channel.name
            }

            assertEquals(20_000, report.parsedEntries)
            assertEquals(0, report.skippedEntries)
            assertEquals(20_000, seen)
            assertEquals("Channel 19999", lastName)
        }

        @Test
        fun `callback form emits entries incrementally rather than at the end`() {
            val playlist = """
                #EXTM3U
                #EXTINF:-1,First
                http://stream.example.invalid/1.m3u8
                #EXTINF:-1,Second
                http://stream.example.invalid/2.m3u8
            """.trimIndent()

            val emissionOrder = mutableListOf<String>()
            M3uParser.parse(StringReader(playlist)) { emissionOrder += it.name }

            assertEquals(listOf("First", "Second"), emissionOrder)
            assertNotNull(emissionOrder.firstOrNull())
        }
    }
}
