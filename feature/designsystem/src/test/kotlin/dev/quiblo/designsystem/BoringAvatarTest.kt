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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Does our port draw what boring-avatars draws?
 *
 * **Every number below was produced by running their JavaScript**, not by reading it. That is the
 * only kind of assertion worth making about a port: the failure this catches is not a crash but a
 * subtly different picture, and a test that re-derives the expected value from the same reasoning
 * as the implementation would agree with the implementation about being wrong.
 *
 * The two languages disagree in places that look like nothing and are not. JavaScript coerces to
 * a 32-bit integer at the shift and again at the trailing `&`, then does the rest in doubles;
 * `hash * (i + 1)` exceeds `Int` for most names and must not wrap; and `Math.abs` widens where
 * `kotlin.math.abs` on an `Int` does not. Names covering all three are here.
 *
 * **This is also the guard on stability.** An avatar is stored as its seed, so the picture is
 * recomputed from scratch on every device and after every restore. Any change to this arithmetic
 * silently redraws the face of every profile already created — which is why the arithmetic is
 * pinned rather than merely exercised.
 */
class BoringAvatarTest {

    @Test
    fun `the hash matches theirs`() {
        assertEquals(851611154L, boringHash("Mahmoud#0"))
        assertEquals(1825675702L, boringHash("Sara#0"))
        assertEquals(1067288339L, boringHash("quiblo#0"))
        assertEquals(0L, boringHash(""))
        assertEquals(97L, boringHash("a"))
    }

    @Test
    fun `a name outside the Latin alphabet hashes the same as theirs`() {
        // JavaScript hashes UTF-16 code units and so does Kotlin's `Char`, so this agrees for
        // free — but "for free" is exactly the sort of claim that stops being true unnoticed.
        assertEquals(1936009260L, boringHash("محمود#3"))
    }

    @Test
    fun `the four shapes match theirs`() {
        val shapes = bauhausShapes("Mahmoud#0")

        assertEquals(BoringPalette[4], shapes[0].colour)
        assertEquals(21f, shapes[0].translateX)
        assertEquals(21f, shapes[0].translateY)
        assertEquals(194f, shapes[0].rotate)

        assertEquals(BoringPalette[0], shapes[1].colour)
        assertEquals(-18f, shapes[1].translateX)
        assertEquals(18f, shapes[1].translateY)
        assertEquals(28f, shapes[1].rotate)

        assertEquals(BoringPalette[1], shapes[2].colour)
        assertEquals(-6f, shapes[2].translateX)
        assertEquals(-6f, shapes[2].translateY)
        assertEquals(222f, shapes[2].rotate)

        assertEquals(BoringPalette[2], shapes[3].colour)
        assertEquals(16f, shapes[3].translateX)
        assertEquals(-16f, shapes[3].translateY)
        assertEquals(56f, shapes[3].rotate)
    }

    @Test
    fun `a large hash is not wrapped on the way through`() {
        // "Sara#0" hashes above 2^30, so `hash * 4` for the fourth element overflows a 32-bit
        // integer. JavaScript computes it in a double and does not wrap. A port that kept
        // everything in `Int` passes every test written against a short name and draws the last
        // shape of most real names in the wrong place.
        val shapes = bauhausShapes("Sara#0")

        assertEquals(-8f, shapes[3].translateX)
        assertEquals(-8f, shapes[3].translateY)
        assertEquals(208f, shapes[3].rotate)
    }

    @Test
    fun `the bar is a block or a stripe for the whole avatar at once`() {
        // Read off the unmultiplied hash in their source, so all four agree. One avatar is
        // never half one thing and half the other.
        assertTrue(bauhausShapes("a").all { it.isSquare })
        assertTrue(bauhausShapes("Mahmoud#0").none { it.isSquare })
    }

    @Test
    fun `the same seed always gives the same avatar`() {
        // The whole promise: a profile's face survives a reinstall, a restore, and the other app.
        assertEquals(bauhausShapes("Mahmoud#0"), bauhausShapes("Mahmoud#0"))
    }

    @Test
    fun `neighbouring seeds give different avatars`() {
        // The picker offers a dozen seeds that differ by one character. If the generator smeared
        // them together, the chooser would be twelve tiles of the same picture and nobody would
        // report it as a bug — they would just never pick a face.
        val offered = (0 until 12).map { bauhausShapes("Mahmoud#$it") }

        assertEquals(offered.size, offered.distinct().size)
    }

    @Test
    fun `an empty seed still draws something`() {
        // Not reachable from the television chooser, which substitutes a fallback — but this is
        // a public function and an empty name must not divide by zero or index past the palette.
        val shapes = bauhausShapes("")

        assertEquals(4, shapes.size)
        assertTrue(shapes.all { it.colour in BoringPalette })
    }

    @Test
    fun `a stored key round-trips through its seed`() {
        val key = generatedAvatarKey("Mahmoud#3")

        assertEquals("Mahmoud#3", generatedAvatarSeed(key))
    }

    @Test
    fun `an illustrated face is not mistaken for a generated one`() {
        // The two kinds share one nullable column, so this is what keeps a profile created
        // before this feature from being redrawn as somebody else entirely.
        assertNull(generatedAvatarSeed("star"))
        assertNull(generatedAvatarSeed(null))
        assertEquals(AvatarFaces.first { it.key == "star" }, avatarFaceFor("star"))
    }
}
