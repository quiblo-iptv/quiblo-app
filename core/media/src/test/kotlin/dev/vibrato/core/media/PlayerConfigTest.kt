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

package dev.vibrato.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerConfigTest {

    @Test
    fun `buffer mode values satisfy min less than max duration`() {
        BufferMode.entries.forEach { mode ->
            assertTrue(mode.minBufferMs < mode.maxBufferMs, "minBufferMs must be less than maxBufferMs for ${mode.name}")
            assertTrue(mode.playbackBufferMs > 0, "playbackBufferMs must be positive for ${mode.name}")
        }
    }

    @Test
    fun `max bitrate cap values are correctly ordered`() {
        assertEquals(Int.MAX_VALUE, MaxBitrateCap.AUTO.bitrateKbps)
        assertEquals(8_000, MaxBitrateCap.HIGH_1080P.bitrateKbps)
        assertEquals(4_000, MaxBitrateCap.MEDIUM_720P.bitrateKbps)
        assertEquals(1_500, MaxBitrateCap.LOW_480P.bitrateKbps)
    }
}
