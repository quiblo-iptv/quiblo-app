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

package dev.quiblo.feature.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** How the playback info overlay writes its numbers, and the one judgement it makes (`FEAT-040`). */
class PlaybackInfoFormatTest {

    @Test
    fun `bitrates read in the unit a person would say`() {
        assertEquals("18.4 Mb/s", formatBitrate(18_400_000L))
        assertEquals("640 kb/s", formatBitrate(640_000L))
        assertEquals("0 kb/s", formatBitrate(0L))
    }

    @Test
    fun `sizes read in binary units`() {
        assertEquals("840 KB", formatBytes(860_160L))
        assertEquals("512.0 MB", formatBytes(512L * 1_048_576L))
        assertEquals("2.00 GB", formatBytes(2L * 1_073_741_824L))
    }

    @Test
    fun `times read as a clock, hours only when there are any`() {
        assertEquals("2:03", formatClock(123_000L))
        assertEquals("1:02:03", formatClock(3_723_000L))
        assertEquals("24.3 s", formatSeconds(24_300L))
    }

    @Test
    fun `frame rates keep the decimals they have and no more`() {
        assertEquals("23.976", formatFrameRate(23.976f))
        assertEquals("25", formatFrameRate(25f))
        assertEquals("59.94", formatFrameRate(59.94f))
    }

    @Test
    fun `a film at a rate the screen cannot hold evenly is flagged`() {
        assertTrue(isJudderProne(contentFps = 23.976f, displayHz = 60f))
        assertTrue(isJudderProne(contentFps = 25f, displayHz = 60f))
        assertTrue(isJudderProne(contentFps = 30f, displayHz = 50f))
    }

    @Test
    fun `a film at a rate the screen holds evenly is not`() {
        assertFalse(isJudderProne(contentFps = 25f, displayHz = 50f))
        assertFalse(isJudderProne(contentFps = 30f, displayHz = 60f))
        assertFalse(isJudderProne(contentFps = 23.976f, displayHz = 24f))
        assertFalse(isJudderProne(contentFps = 24f, displayHz = 120f))
    }

    @Test
    fun `an unknown rate is never flagged`() {
        assertFalse(isJudderProne(contentFps = 0f, displayHz = 60f))
        assertFalse(isJudderProne(contentFps = 24f, displayHz = 0f))
    }
}
