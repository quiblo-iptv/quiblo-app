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

package dev.quiblo.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The splash's choreography, beat by beat, without a screen.
 *
 * What matters is the order of things a viewer sees — nothing drawn, the ring on, the hit, the
 * screen still dark through the hold, the app visible through it at the end — so that is what is
 * checked, rather than any particular curve.
 */
class SplashFrameTest {

    @Test
    fun `nothing is drawn on the first frame`() {
        val frame = SplashFrame.at(seconds = 0f, diveAt = null)
        assertEquals(0f, frame.ring)
        assertEquals(0f, frame.tail)
        assertEquals(0f, frame.light)
        assertEquals(1f, frame.counterInk, "the app must not show through before the dive")
    }

    @Test
    fun `the mark is whole and lit from the hit on`() {
        val frame = SplashFrame.at(seconds = 0.7f, diveAt = null)
        assertEquals(1f, frame.ring)
        assertEquals(1f, frame.tail)
        assertTrue(frame.light > 0.5f, "the light flashes on the hit, was ${frame.light}")
    }

    @Test
    fun `a long hold keeps the screen dark and the mark still`() {
        val frame = SplashFrame.at(seconds = 4f, diveAt = null)
        assertEquals(1f, frame.counterInk)
        assertEquals(1f, frame.chrome)
        assertEquals(1f, frame.scale, 0.01f)
        assertTrue(frame.light in 0.25f..0.45f, "the light breathes at rest, was ${frame.light}")
    }

    @Test
    fun `the dive ends inside the screen with the app showing`() {
        val frame = SplashFrame.at(seconds = 2.3f, diveAt = 1.6f)
        assertEquals(0f, frame.counterInk)
        assertEquals(0f, frame.chrome)
        assertEquals(0f, frame.light)
        assertTrue(frame.scale > 50f, "the camera is through the screen, scale was ${frame.scale}")
    }

    @Test
    fun `the name is gone early in the dive`() {
        val frame = SplashFrame.at(seconds = 1.85f, diveAt = 1.6f)
        assertEquals(0f, frame.chrome)
        assertTrue(frame.counterInk < 1f, "the screen has begun to clear")
    }
}
