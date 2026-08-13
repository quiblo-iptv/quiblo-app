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
 *
 * ---
 *
 * The generator below is a port of the "bauhaus" variant of boring-avatars.
 *
 *   Copyright (c) 2021 boringdesigners
 *   https://github.com/boringdesigners/boring-avatars — MIT License
 *
 * MIT permits this, and MIT into GPLv3 is compatible in this direction: their notice stays
 * above, and the combined work ships under the GPL. It is a port rather than a dependency
 * because boring-avatars is a React component library — there is no artefact to declare in
 * `libs.versions.toml`, so the obligation lands here, on the source, instead.
 */

// The file is named for the avatar it draws rather than for the one class in it: the class is a
// description of a single shape, and what anybody comes here for is `BoringAvatar`.
@file:Suppress("MatchingDeclarationName")

package dev.quiblo.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * A generated avatar: four coloured shapes, arranged by a hash of a seed.
 *
 * **Nothing here is random and nothing is stored but the seed.** The same string yields the same
 * picture on a phone, on the television, and after a backup is restored onto a new device — which
 * is the whole reason a generator is usable as an avatar at all. A household recognises a profile
 * by its colours long before it reads the name, so a face that changed between devices would be
 * worse than no face.
 *
 * The arithmetic is deliberately a faithful port rather than something in the same spirit. Every
 * odd-looking constant below — `23 - i`, the digit at index 1 deciding a sign — is theirs, and
 * changing any of them changes every avatar every viewer has already chosen. That is why this
 * reads like transcribed maths instead of like our code: it is transcribed maths.
 */
data class BauhausShape(
    val colour: Color,
    val translateX: Float,
    val translateY: Float,
    val rotate: Float,
    val isSquare: Boolean,
)

/**
 * boring-avatars' own palette.
 *
 * Kept rather than replaced with the app's colours. These five are what the library looks like,
 * they are already balanced against each other, and white reads on all of them — which matters
 * because the avatar sits under a white name on a black television.
 */
val BoringPalette: List<Color> = listOf(
    Color(0xFF92A1C6),
    Color(0xFF146A7C),
    Color(0xFFF0AB3D),
    Color(0xFFC271B4),
    Color(0xFFC20D90),
)

/**
 * What a generated avatar looks like in the database.
 *
 * `Profile.avatar` is one nullable string and stays one: a generated face is stored as its seed
 * behind this prefix, an illustrated one as its key from [AvatarFaces], and neither needs a
 * column, a migration, or a second thing for a backup to carry. The prefix is what tells them
 * apart, and it contains a character no face key uses.
 *
 * **The seed is frozen at the moment of choosing, not derived at draw time.** It is built from
 * the name the viewer typed, so two people in one household get different faces — but once it is
 * stored it is a literal, and renaming a profile later cannot silently redraw somebody's avatar.
 */
private const val GENERATED_PREFIX = "boring:"

/** The key to store for a profile wearing the avatar that [seed] generates. */
fun generatedAvatarKey(seed: String): String = GENERATED_PREFIX + seed

/** The seed inside a stored key, or null when the key is not a generated avatar at all. */
fun generatedAvatarSeed(avatar: String?): String? =
    avatar?.takeIf { it.startsWith(GENERATED_PREFIX) }?.removePrefix(GENERATED_PREFIX)

/**
 * Their `hashCode`, which is Java's, which is why this port is exact without effort.
 *
 * JavaScript coerces to a 32-bit integer at the shift and again at the trailing `& hash`;
 * Kotlin's `Int` wraps at every step. Both are arithmetic modulo 2^32, so they agree on every
 * input rather than merely on the ones anyone tried.
 *
 * **The result widens to `Long` before `abs`, and that is load-bearing.** `Math.abs` in
 * JavaScript returns a double, so a hash of `Int.MIN_VALUE` becomes 2147483648; `kotlin.math.abs`
 * on an `Int` returns `Int.MIN_VALUE` unchanged — still negative. One name in four billion would
 * have indexed the palette backwards and drawn its shapes off the canvas. This project has met
 * that exact trap once already, in `colourForName`.
 */
internal fun boringHash(seed: String): Long {
    var hash = 0
    for (character in seed) {
        hash = (hash shl HASH_SHIFT) - hash + character.code
    }
    return abs(hash.toLong())
}

/** Their `getDigit`: the digit at position [place], counting from the units. */
private fun digitAt(number: Long, place: Int): Long {
    var divisor = 1L
    repeat(place) { divisor *= BASE }
    return (number / divisor) % BASE
}

/** Their `getBoolean`: true when the digit at [place] is even. */
private fun booleanAt(number: Long, place: Int): Boolean = digitAt(number, place) % 2 == 0L

/**
 * Their `getUnit`: a value within [range], signed by a digit of the number itself.
 *
 * [place] null means "always positive" — the rotation, which has nowhere negative to go.
 */
private fun unitOf(number: Long, range: Long, place: Int? = null): Float {
    val value = number % range
    return if (place != null && digitAt(number, place) % 2 == 0L) -value.toFloat() else value.toFloat()
}

/**
 * The four shapes [seed] describes.
 *
 * Public so the chooser can draw a set of candidates without composing four avatars to find out
 * whether they differ, and so this is testable as arithmetic rather than only as pixels.
 */
fun bauhausShapes(seed: String, palette: List<Color> = BoringPalette): List<BauhausShape> {
    val hash = boringHash(seed)

    return List(ELEMENTS) { index ->
        val step = hash * (index + 1)
        BauhausShape(
            colour = palette[((hash + index) % palette.size).toInt()],
            // Theirs is `SIZE / 2 - (i + 17)`, left expanded so the shape of the expression
            // matches the source it came from.
            translateX = unitOf(step, (CANVAS / 2).toLong() - (index + TRAVEL_INSET), place = 1),
            translateY = unitOf(step, (CANVAS / 2).toLong() - (index + TRAVEL_INSET), place = 2),
            rotate = unitOf(step, ROTATION_RANGE),
            // Read off the unmultiplied hash, so all four elements agree — one avatar is either
            // a bar across a circle or a block behind it, never both.
            isSquare = booleanAt(hash, 2),
        )
    }
}

/**
 * The avatar [seed] names, drawn at [size].
 *
 * Drawn rather than rasterised: four shapes on a canvas costs less than a bitmap cache, scales
 * from a 32dp control beside the settings gear to a 120dp tile on a television with no second
 * asset, and never has to be invalidated because nothing about it is ever stale.
 */
@Composable
fun BoringAvatar(
    seed: String,
    size: Dp,
    modifier: Modifier = Modifier,
    palette: List<Color> = BoringPalette,
) {
    val shapes = remember(seed, palette) { bauhausShapes(seed, palette) }

    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    ) {
        // Everything below is written in their 80-unit square and scaled once, so the constants
        // stay the constants in the source rather than becoming ratios nobody can check.
        val scale = this.size.minDimension / CANVAS

        withTransform({ scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) }) {
            drawRect(
                color = shapes[0].colour,
                topLeft = Offset.Zero,
                size = Size(CANVAS, CANVAS),
            )

            // The bar, or the block. `translate` then `rotate` in that order, because SVG's
            // `transform="translate(…) rotate(…)"` rotates first and then moves the result.
            withTransform({
                translate(shapes[1].translateX, shapes[1].translateY)
                rotate(shapes[1].rotate, pivot = Offset(CANVAS / 2, CANVAS / 2))
            }) {
                drawRect(
                    color = shapes[1].colour,
                    topLeft = Offset(BAR_LEFT, BAR_TOP),
                    size = Size(CANVAS, if (shapes[1].isSquare) CANVAS else CANVAS / 8),
                )
            }

            withTransform({ translate(shapes[2].translateX, shapes[2].translateY) }) {
                drawCircle(
                    color = shapes[2].colour,
                    radius = CANVAS / 5,
                    center = Offset(CANVAS / 2, CANVAS / 2),
                )
            }

            withTransform({
                translate(shapes[3].translateX, shapes[3].translateY)
                rotate(shapes[3].rotate, pivot = Offset(CANVAS / 2, CANVAS / 2))
            }) {
                drawLine(
                    color = shapes[3].colour,
                    start = Offset(0f, CANVAS / 2),
                    end = Offset(CANVAS, CANVAS / 2),
                    strokeWidth = STROKE_WIDTH,
                )
            }
        }
    }
}

/** Their viewBox. Every coordinate in this file is in these units. */
private const val CANVAS = 80f

/** Their `ELEMENTS`: a background, a bar, a circle and a line. */
private const val ELEMENTS = 4

private const val ROTATION_RANGE = 360L
private const val STROKE_WIDTH = 2f

/** Their `hash * 31`, written as they write it — a shift and a subtraction. */
private const val HASH_SHIFT = 5

/** Their digits are decimal digits. */
private const val BASE = 10L

/** Theirs, the `17` in `SIZE / 2 - (i + 17)`: how far in from the edge a shape may travel. */
private const val TRAVEL_INSET = 17

/** Theirs, as `(SIZE - 60) / 2` and `(SIZE - 20) / 2`. */
private const val BAR_LEFT = 10f
private const val BAR_TOP = 30f
