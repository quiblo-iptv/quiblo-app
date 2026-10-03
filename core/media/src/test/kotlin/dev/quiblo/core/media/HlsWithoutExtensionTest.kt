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

package dev.quiblo.core.media

import androidx.media3.common.PlaybackException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * An HLS playlist served from an address that does not end in `.m3u8` (`BUG-044`).
 *
 * The engine picks how to read a stream from its path's extension alone, so these went down the
 * progressive path and failed as "a format Quiblo cannot play". Every URL here is synthetic.
 */
class HlsWithoutExtensionTest {

    private val unsupported = PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED

    @Test
    fun `a playlist named in the query is hls`() {
        listOf(
            "http://cdn.example.invalid/play.php?file=index.m3u8",
            "http://cdn.example.invalid/stream?id=12&output=m3u8",
            "http://cdn.example.invalid/live/12?type=m3u8&token=x",
        ).forEach { assertEquals(HLS_MIME_TYPE, hlsMimeTypeFor(it), it) }
    }

    @Test
    fun `a playlist at the end of the path is hls too, whatever its case`() {
        assertEquals(HLS_MIME_TYPE, hlsMimeTypeFor("http://cdn.example.invalid/x/INDEX.M3U8?token=1"))
    }

    @Test
    fun `nothing else is called hls`() {
        listOf(
            "http://cdn.example.invalid/play.php?id=12",
            "http://cdn.example.invalid/film.mkv",
            "http://cdn.example.invalid/live/u/p/1.ts",
            "http://cdn.example.invalid/m3u8s/film.mp4",
            "http://cdn.example.invalid/x?name=notm3u8",
        ).forEach { assertNull(hlsMimeTypeFor(it), it) }
    }

    @Test
    fun `an extension-less stream with no container is tried once as hls`() {
        assertTrue(retriesAsHls(unsupported, null, "http://cdn.example.invalid/play.php?id=12", alreadyTried = false))
        assertTrue(retriesAsHls(unsupported, null, "http://cdn.example.invalid/hls/12", alreadyTried = false))
    }

    @Test
    fun `but only once`() {
        assertFalse(retriesAsHls(unsupported, null, "http://cdn.example.invalid/play.php?id=12", alreadyTried = true))
    }

    @Test
    fun `not when the stream said what it was`() {
        assertFalse(retriesAsHls(unsupported, "video/mp2t", "http://cdn.example.invalid/play.php?id=12", false))
        assertFalse(retriesAsHls(unsupported, HLS_MIME_TYPE, "http://cdn.example.invalid/play.php?id=12", false))
    }

    @Test
    fun `not when the path names a container the engine already knows`() {
        listOf("film.mkv", "clip.mp4", "1.ts", "index.m3u8").forEach { name ->
            assertFalse(retriesAsHls(unsupported, null, "http://cdn.example.invalid/$name", false), name)
        }
    }

    @Test
    fun `not for any other failure`() {
        listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ).forEach { code ->
            assertFalse(retriesAsHls(code, null, "http://cdn.example.invalid/play.php?id=12", false), "code $code")
        }
    }
}
