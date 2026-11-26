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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** When frames were put on the screen, summarised for the overlay (`FEAT-040`). */
class FrameTimingTest {

    private fun ms(vararg values: Double) = values.map { (it * 1_000_000).toLong() }

    @Test
    fun `a film at its own refresh rate is perfectly even`() {
        val timing = summariseFrameIntervals(ms(*DoubleArray(24) { 41.7 }))
        assertNotNull(timing)
        assertEquals(41.7f, timing!!.meanMillis, 0.01f)
        assertEquals(0f, timing.jitterMillis, 0.01f)
        assertEquals(0f, timing.unevenShare)
    }

    @Test
    fun `24 fps on 60 Hz alternates two and three refreshes, every gap uneven`() {
        val gaps = ms(*DoubleArray(24) { if (it % 2 == 0) 33.3 else 50.0 })
        val timing = summariseFrameIntervals(gaps)!!
        assertEquals(41.65f, timing.meanMillis, 0.01f)
        assertEquals(33.3f, timing.minMillis, 0.01f)
        assertEquals(50.0f, timing.maxMillis, 0.01f)
        assertEquals(8.35f, timing.jitterMillis, 0.01f)
        assertEquals(1f, timing.unevenShare)
    }

    @Test
    fun `a single late frame is one uneven gap among many`() {
        val gaps = ms(*DoubleArray(19) { 41.7 }, 83.4)
        assertEquals(0.05f, summariseFrameIntervals(gaps)!!.unevenShare, 0.001f)
    }

    @Test
    fun `a pause or a seek is not a frame interval`() {
        val gaps = ms(*DoubleArray(10) { 41.7 }, 2_000.0)
        val timing = summariseFrameIntervals(gaps)!!
        assertEquals(10, timing.intervals)
        assertEquals(41.7f, timing.maxMillis, 0.01f)
    }

    @Test
    fun `too few gaps say nothing`() {
        assertNull(summariseFrameIntervals(ms(41.7, 41.7, 41.7)))
        assertNull(summariseFrameIntervals(emptyList()))
    }
}
