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

package dev.vibrato.source.xtream

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * AC-XT-03: with or without scheme, port and trailing slash, everything must normalise to
 * the same working endpoint. All hosts are synthetic (AC-LEGAL-04).
 */
class XtreamUrlTest {

    @Test
    @DisplayName("AC-XT-03 — every shape of the same address normalises identically")
    fun `equivalent inputs normalise to one endpoint`() {
        val expected = "http://panel.example.invalid:8080"
        val inputs = listOf(
            "panel.example.invalid:8080",
            "http://panel.example.invalid:8080",
            "http://panel.example.invalid:8080/",
            "  http://panel.example.invalid:8080///  ",
            "http://panel.example.invalid:8080/player_api.php",
            "http://PANEL.example.invalid:8080",
            "http://panel.example.invalid:8080/c/",
            "http://panel.example.invalid:8080/?username=x",
        )
        inputs.forEach { input ->
            assertEquals(expected, XtreamUrl.normalize(input), "Failed to normalise: $input")
        }
    }

    @Test
    fun `defaults to http when no scheme is given`() {
        assertEquals("http://panel.example.invalid", XtreamUrl.normalize("panel.example.invalid"))
    }

    @Test
    fun `preserves an explicit https scheme`() {
        assertEquals("https://panel.example.invalid", XtreamUrl.normalize("https://panel.example.invalid/"))
    }

    @Test
    fun `omits the port when none was given`() {
        assertEquals("http://panel.example.invalid", XtreamUrl.normalize("panel.example.invalid/"))
    }

    @Test
    fun `rejects input that is not a host`() {
        listOf("", "   ", "ftp://panel.example.invalid", "http://", "http://has space/", "panel.example.invalid:abc")
            .forEach { assertNull(XtreamUrl.normalize(it), "Should have rejected: $it") }
    }

    @Test
    fun `builds the player api endpoint`() {
        assertEquals(
            "http://panel.example.invalid:8080/player_api.php",
            XtreamUrl.playerApi("http://panel.example.invalid:8080"),
        )
    }

    @Test
    fun `builds stream urls for each kind`() {
        val base = "http://panel.example.invalid:8080"
        assertEquals("$base/live/user/pass/42.ts", XtreamUrl.liveStream(base, "user", "pass", "42"))
        assertEquals("$base/movie/user/pass/42.mkv", XtreamUrl.vodStream(base, "user", "pass", "42", "mkv"))
        assertEquals("$base/series/user/pass/42.mp4", XtreamUrl.seriesStream(base, "user", "pass", "42", ""))
        assertEquals(
            "$base/timeshift/user/pass/60/2026-08-03:06-00/42.ts",
            XtreamUrl.timeshiftStream(base, "user", "pass", "42", "2026-08-03:06-00", 60),
        )
    }
}
