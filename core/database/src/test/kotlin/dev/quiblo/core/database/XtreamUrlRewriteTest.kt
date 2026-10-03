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

package dev.quiblo.core.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The rewrite MIGRATION_24_25 applies (`BUG-041`), as plain functions.
 *
 * The locators here are the strings `:source:xtream`'s `XtreamUrl.liveLocator`, `vodLocator` and
 * `seriesLocator` produce; `XtreamCredentialsAtPlayTimeTest` asserts the same strings from that
 * side, so the two cannot drift apart without one of them failing.
 */
class XtreamUrlRewriteTest {

    @Test
    fun `a live url loses its credentials and its extension`() {
        assertEquals("xtream:live/101", xtreamLocatorOf("http://panel.example.invalid:8080/live/someone/s3cret/101.ts"))
        assertEquals("xtream:live/101", xtreamLocatorOf("https://panel.example.invalid/live/someone/s3cret/101.m3u8"))
    }

    @Test
    fun `a film and an episode keep the container the panel named`() {
        assertEquals("xtream:movie/7.mkv", xtreamLocatorOf("http://panel.example.invalid/movie/someone/s3cret/7.mkv"))
        assertEquals(
            "xtream:series/501.mp4",
            xtreamLocatorOf("http://panel.example.invalid/series/someone/s3cret/501.mp4"),
        )
    }

    @Test
    fun `anything that is not an xtream stream url is left alone`() {
        listOf(
            "http://cdn.example.invalid/hls/news/index.m3u8",
            "http://panel.example.invalid/someone/s3cret/101",
            "http://panel.example.invalid/live/someone/101.ts",
            "http://panel.example.invalid/live/someone/s3cret/101.ts?token=x",
            "news.epg",
            "xtream:live/101",
            "",
        ).forEach { assertNull(xtreamLocatorOf(it), it) }
    }

    @Test
    fun `a source url is matched by host whatever shape it was typed in`() {
        assertEquals("panel.example.invalid", hostOfUrl("panel.example.invalid:8080"))
        assertEquals("panel.example.invalid", hostOfUrl("HTTP://Panel.Example.Invalid:8080/"))
        assertEquals("panel.example.invalid", hostOfUrl("http://someone:s3cret@panel.example.invalid/live/a/b/1.ts"))
        assertEquals("[::1]", hostOfUrl("http://[::1]:8080/"))
    }
}
