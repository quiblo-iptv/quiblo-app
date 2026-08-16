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

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `the face matches theirs`() {
        // Every number here came out of their JavaScript, run on this seed. `Mahmoud#0` picks
        // the darkest entry in the palette, so it also pins `getContrast` choosing white.
        val face = beamFace("Mahmoud#0")

        assertEquals(BoringPalette[4], face.wrapper)
        assertEquals(BoringPalette[2], face.background)
        assertEquals(Color.White, face.face)
        assertEquals(8f, face.wrapperTranslateX)
        assertEquals(8f, face.wrapperTranslateY)
        assertEquals(194f, face.wrapperRotate)
        assertEquals(1.2f, face.wrapperScale)
        assertFalse(face.isCircle)
        assertFalse(face.isMouthOpen)
        assertEquals(4f, face.eyeSpread)
        assertEquals(2f, face.mouthSpread)
        assertEquals(4f, face.faceRotate)
        assertEquals(4f, face.faceTranslateX)
        assertEquals(4f, face.faceTranslateY)
    }

    /**
     * The nudge, which is the one branch in the whole generator that is easy to drop.
     *
     * A translation under 5 is pushed out by `36 / 9`; one at 5 or above is left alone. `Sara#0`
     * translates to 2 and 6, so it takes the branch on one axis and not on the other — a port
     * that applied it to both, or to neither, passes on a seed that happens to agree.
     */
    @Test
    fun `a translation below five is nudged outwards and one above it is not`() {
        val face = beamFace("Sara#0")

        assertEquals(2f, face.wrapperTranslateX)
        assertEquals(6f, face.wrapperTranslateY)
    }

    /**
     * The face moves on its own beside a tile that has not moved far.
     *
     * **The comparison is strictly greater, and `Sara#0` is the seed that says so.** Its tile
     * translates 2 and 6, and `36 / 6` is exactly 6 — so *neither* axis is past it and the face
     * takes a translation of its own on both. A port reading `>=` gives the `y` axis 3 instead
     * of 4, which is a face a unit off its tile on every seed that lands on the boundary.
     */
    @Test
    fun `a face beside a tile that has not moved far takes its own translation`() {
        val face = beamFace("Sara#0")

        assertEquals(-6f, face.faceTranslateX)
        assertEquals(4f, face.faceTranslateY)
    }

    /**
     * And it follows a tile that has.
     *
     * `quiblo#0` translates 9 on both axes, which is past `36 / 6`, so the face follows at half.
     * This is also the seed that catches the arithmetic being kept in `Int`: it hashes above
     * 2^30, JavaScript does the rest in doubles and does not wrap, and a port that does puts the
     * face somewhere else entirely on most real names.
     */
    @Test
    fun `a face on a tile that has moved far follows it at half`() {
        val face = beamFace("quiblo#0")

        assertEquals(299f, face.wrapperRotate)
        assertEquals(-9f, face.faceRotate)
        assertEquals(4.5f, face.faceTranslateX)
        assertEquals(4.5f, face.faceTranslateY)
    }

    @Test
    fun `the contrast rule picks black on a light tile`() {
        // "Sara#0" lands on the amber, whose luma is above their threshold of 128. A port that
        // read the colour off Compose's floats instead of its eight-bit channels rounds one of
        // these five the other way, and the face disappears into its own tile.
        assertEquals(BoringPalette[2], beamFace("Sara#0").wrapper)
        assertEquals(Color.Black, beamFace("Sara#0").face)
    }

    @Test
    fun `the mouth and the shape are one decision each for the whole avatar`() {
        // Read off the unmultiplied hash in their source, so a face is never half one thing.
        assertTrue(beamFace("").isCircle)
        assertTrue(beamFace("").isMouthOpen)
        assertFalse(beamFace("Mahmoud#0").isCircle)
        assertFalse(beamFace("Mahmoud#0").isMouthOpen)
    }

    @Test
    fun `the same seed always gives the same avatar`() {
        // The whole promise: a profile's face survives a reinstall, a restore, and the other app.
        assertEquals(beamFace("Mahmoud#0"), beamFace("Mahmoud#0"))
    }

    @Test
    fun `neighbouring seeds give different avatars`() {
        // The picker offers a dozen seeds that differ by one character. If the generator smeared
        // them together, the chooser would be twelve tiles of the same picture and nobody would
        // report it as a bug — they would just never pick a face.
        val offered = (0 until 12).map { beamFace("Mahmoud#$it") }

        assertEquals(offered.size, offered.distinct().size)
    }

    @Test
    fun `an empty seed still draws something`() {
        // Not reachable from the television chooser, which substitutes a fallback — but this is
        // a public function and an empty name must not divide by zero or index past the palette.
        val face = beamFace("")

        assertTrue(face.wrapper in BoringPalette)
        assertTrue(face.background in BoringPalette)
        // Zero on every axis, and the nudge still applied: their branch reads `< 5`, not `!= 0`.
        assertEquals(4f, face.wrapperTranslateX)
        assertEquals(0f, face.wrapperRotate)
        assertEquals(1f, face.wrapperScale)
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
