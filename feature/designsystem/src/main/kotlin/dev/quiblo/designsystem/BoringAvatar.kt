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
 * The generator below is a port of the "beam" variant of boring-avatars.
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
// description of a single face, and what anybody comes here for is `BoringAvatar`.
@file:Suppress("MatchingDeclarationName")

package dev.quiblo.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * A generated avatar: a face on a coloured tile, arranged by a hash of a seed.
 *
 * **Nothing here is random and nothing is stored but the seed.** The same string yields the same
 * picture on a phone, on the television, and after a backup is restored onto a new device — which
 * is the whole reason a generator is usable as an avatar at all. A household recognises a profile
 * by its face long before it reads the name, so a face that changed between devices would be
 * worse than no face.
 *
 * The arithmetic is deliberately a faithful port rather than something in the same spirit. Every
 * odd-looking constant below — the `13` added before the background colour is picked, the digit
 * at index 1 deciding a sign, the `36 / 9` nudge applied only to a translation under 5 — is
 * theirs. That is why this reads like transcribed maths instead of like our code: it is
 * transcribed maths.
 */
@Suppress("LongParameterList")
data class BeamFace(
    /** The tile behind everything, drawn edge to edge. */
    val background: Color,
    /** The shape the face sits on: a circle or a rounded square, moved, turned and grown. */
    val wrapper: Color,
    /** Black or white, whichever reads on [wrapper]. Their `getContrast`. */
    val face: Color,
    val wrapperTranslateX: Float,
    val wrapperTranslateY: Float,
    val wrapperRotate: Float,
    val wrapperScale: Float,
    val isCircle: Boolean,
    val isMouthOpen: Boolean,
    val eyeSpread: Float,
    val mouthSpread: Float,
    val faceRotate: Float,
    val faceTranslateX: Float,
    val faceTranslateY: Float,
)

/**
 * boring-avatars' own palette.
 *
 * Kept rather than replaced with the app's colours. These five are what the library looks like,
 * they are already balanced against each other, and the face drawn on top is black or white by
 * their own contrast rule — so every combination is legible without anybody choosing pairs.
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
 * **The prefix did not change when the variant did.** `022` replaced the bauhaus shapes with
 * these faces, and every profile already wearing a generated avatar became a face rather than
 * keeping a picture nobody could still produce. A second prefix would have kept the old shapes
 * alive forever in the one place they were least wanted — on the profiles that already existed.
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
 * have indexed the palette backwards and put its face off the canvas. This project has met that
 * exact trap once already, in `colourForName`.
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
 * [place] null means "always positive" — a rotation, a spread, a scale step, none of which has
 * anywhere negative to go.
 */
private fun unitOf(number: Long, range: Long, place: Int? = null): Float {
    val value = number % range
    return if (place != null && digitAt(number, place) % 2 == 0L) -value.toFloat() else value.toFloat()
}

/**
 * Their `getContrast`: black or white, whichever is readable on this colour.
 *
 * The YIQ luma of the eight-bit channels, against a threshold of 128 — theirs, and the reason the
 * palette above needs no hand-picked pairs. Read back through `toArgb` rather than off `Color`'s
 * floats, because their arithmetic is on the integers a hex string parses to and a rounding
 * disagreement here is a face that is white where theirs is black.
 */
private fun Color.readableFace(): Color {
    val argb = toArgb()
    val red = (argb shr RED_SHIFT) and CHANNEL_MASK
    val green = (argb shr GREEN_SHIFT) and CHANNEL_MASK
    val blue = argb and CHANNEL_MASK
    val luma = (red * LUMA_RED + green * LUMA_GREEN + blue * LUMA_BLUE) / LUMA_SCALE
    return if (luma >= LUMA_THRESHOLD) Color.Black else Color.White
}

/**
 * The face [seed] describes.
 *
 * Public so the chooser can compare a set of candidates without composing them to find out
 * whether they differ, and so this is testable as arithmetic rather than only as pixels.
 */
fun beamFace(seed: String, palette: List<Color> = BoringPalette): BeamFace {
    val hash = boringHash(seed)
    val wrapper = palette[(hash % palette.size).toInt()]

    // Theirs: a translation that would leave the tile nearly centred is nudged outwards instead,
    // so the shape is off-centre in a way the eye reads as deliberate rather than as a mistake.
    val preTranslateX = unitOf(hash, TRANSLATE_RANGE, place = 1)
    val translateX = if (preTranslateX < NUDGE_BELOW) preTranslateX + CANVAS / NUDGE_DIVISOR else preTranslateX
    val preTranslateY = unitOf(hash, TRANSLATE_RANGE, place = 2)
    val translateY = if (preTranslateY < NUDGE_BELOW) preTranslateY + CANVAS / NUDGE_DIVISOR else preTranslateY

    return BeamFace(
        // Their `numFromName + 13`. The offset is what stops the tile and the shape on it
        // landing on the same palette entry for most seeds, which would draw a face on nothing.
        background = palette[((hash + BACKGROUND_OFFSET) % palette.size).toInt()],
        wrapper = wrapper,
        face = wrapper.readableFace(),
        wrapperTranslateX = translateX,
        wrapperTranslateY = translateY,
        wrapperRotate = unitOf(hash, ROTATION_RANGE),
        wrapperScale = 1f + unitOf(hash, (CANVAS / SCALE_DIVISOR).toLong()) / SCALE_STEP,
        isCircle = booleanAt(hash, 1),
        isMouthOpen = booleanAt(hash, 2),
        eyeSpread = unitOf(hash, EYE_SPREAD_RANGE),
        mouthSpread = unitOf(hash, MOUTH_SPREAD_RANGE),
        faceRotate = unitOf(hash, FACE_ROTATION_RANGE, place = 3),
        // Theirs again, and the asymmetry is theirs too: a wrapper pushed well off centre pulls
        // the face half as far with it, and one that is not gets a translation of its own.
        faceTranslateX = if (translateX > CANVAS / FACE_FOLLOW_DIVISOR) {
            translateX / 2
        } else {
            unitOf(hash, FACE_TRANSLATE_X_RANGE, place = 1)
        },
        faceTranslateY = if (translateY > CANVAS / FACE_FOLLOW_DIVISOR) {
            translateY / 2
        } else {
            unitOf(hash, FACE_TRANSLATE_Y_RANGE, place = 2)
        },
    )
}

/**
 * The avatar [seed] names, drawn at [size].
 *
 * Drawn rather than rasterised: a tile, a shape and three marks cost less than a bitmap cache,
 * scale from a 32dp control beside the settings gear to a 120dp tile on a television with no
 * second asset, and never have to be invalidated because nothing about them is ever stale.
 */
@Composable
fun BoringAvatar(
    seed: String,
    size: Dp,
    modifier: Modifier = Modifier,
    palette: List<Color> = BoringPalette,
) {
    val face = remember(seed, palette) { beamFace(seed, palette) }

    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    ) {
        // Everything below is written in their 36-unit square and scaled once, so the constants
        // stay the constants in the source rather than becoming ratios nobody can check.
        val scale = this.size.minDimension / CANVAS

        withTransform({ scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) }) {
            drawRect(color = face.background, topLeft = Offset.Zero, size = Size(CANVAS, CANVAS))
            drawWrapper(face)
            drawFace(face)
        }
    }
}

/**
 * The shape the face sits on.
 *
 * `translate`, then `rotate`, then `scale`, in that order — which is SVG's
 * `transform="translate(…) rotate(…) scale(…)"` read left to right. The scale pivots on the
 * origin rather than on the centre, because theirs is a bare `scale(n)` and SVG's scales from
 * `0,0`; pivoting on the middle instead would grow the tile symmetrically and lose the
 * off-centre look the nudge above exists to produce.
 */
private fun DrawScope.drawWrapper(face: BeamFace) {
    withTransform({
        translate(face.wrapperTranslateX, face.wrapperTranslateY)
        rotate(face.wrapperRotate, pivot = Offset(CANVAS / 2, CANVAS / 2))
        scale(face.wrapperScale, face.wrapperScale, pivot = Offset.Zero)
    }) {
        drawRoundRect(
            color = face.wrapper,
            topLeft = Offset.Zero,
            size = Size(CANVAS, CANVAS),
            // Their `rx`: the full width, which on a square is a circle, or a sixth of it.
            cornerRadius = CornerRadius(if (face.isCircle) CANVAS else CANVAS / CORNER_DIVISOR),
        )
    }
}

/** The two eyes and the mouth, as one group that moves and turns together. */
private fun DrawScope.drawFace(face: BeamFace) {
    withTransform({
        translate(face.faceTranslateX, face.faceTranslateY)
        rotate(face.faceRotate, pivot = Offset(CANVAS / 2, CANVAS / 2))
    }) {
        val mouthY = MOUTH_BASELINE + face.mouthSpread

        if (face.isMouthOpen) {
            // Theirs: `M15 y c2 1 4 1 6 0`, stroked at SVG's default width of 1.
            drawPath(
                path = Path().apply {
                    moveTo(MOUTH_OPEN_LEFT, mouthY)
                    cubicTo(
                        x1 = MOUTH_OPEN_CONTROL_LEFT,
                        y1 = mouthY + MOUTH_OPEN_DEPTH,
                        x2 = MOUTH_OPEN_CONTROL_RIGHT,
                        y2 = mouthY + MOUTH_OPEN_DEPTH,
                        x3 = MOUTH_OPEN_RIGHT,
                        y3 = mouthY,
                    )
                },
                color = face.face,
                style = Stroke(width = 1f, cap = StrokeCap.Round),
            )
        } else {
            // Theirs: `M13,y a1,0.75 0 0,0 10,0`. Radii too small to reach the end point are
            // scaled up until they exactly do — that is SVG's rule, not a choice here — so a
            // `1,0.75` arc across ten units becomes a half-ellipse of `5,3.75`.
            drawPath(
                path = Path().apply {
                    arcTo(
                        rect = Rect(
                            MOUTH_CLOSED_LEFT,
                            mouthY - MOUTH_CLOSED_RADIUS_Y,
                            MOUTH_CLOSED_RIGHT,
                            mouthY + MOUTH_CLOSED_RADIUS_Y,
                        ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = HALF_TURN,
                        forceMoveTo = false,
                    )
                    close()
                },
                color = face.face,
            )
        }

        drawEye(face, x = EYE_LEFT - face.eyeSpread)
        drawEye(face, x = EYE_RIGHT + face.eyeSpread)
    }
}

/**
 * One eye.
 *
 * Their `rx` is 1 on a 1.5-by-2 rectangle, and SVG clamps a corner radius to half the side it
 * runs along — so the corners are 0.75 across and 1 down, which is what makes an eye a capsule
 * rather than a rounded box.
 */
private fun DrawScope.drawEye(face: BeamFace, x: Float) {
    drawRoundRect(
        color = face.face,
        topLeft = Offset(x, EYE_TOP),
        size = Size(EYE_WIDTH, EYE_HEIGHT),
        cornerRadius = CornerRadius(EYE_WIDTH / 2, EYE_HEIGHT / 2),
    )
}

/** Their viewBox. Every coordinate in this file is in these units. */
private const val CANVAS = 36f

/** Their `hash * 31`, written as they write it — a shift and a subtraction. */
private const val HASH_SHIFT = 5

/** Their digits are decimal digits. */
private const val BASE = 10L

/** Their `numFromName + 13`, which picks the tile a different colour from the shape on it. */
private const val BACKGROUND_OFFSET = 13L

/* The translation, and the nudge that keeps a nearly-centred tile from looking accidental. */
private const val TRANSLATE_RANGE = 10L
private const val NUDGE_BELOW = 5f
private const val NUDGE_DIVISOR = 9f

private const val ROTATION_RANGE = 360L

/* Their `1 + getUnit(n, SIZE / 12) / 10`: a scale of 1.0, 1.1 or 1.2. */
private const val SCALE_DIVISOR = 12f
private const val SCALE_STEP = 10f

/** Their `rx` for a shape that is not a circle: a sixth of the side. */
private const val CORNER_DIVISOR = 6f

private const val EYE_SPREAD_RANGE = 5L
private const val MOUTH_SPREAD_RANGE = 3L
private const val FACE_ROTATION_RANGE = 10L

/** Their `SIZE / 6`: past this the face follows the tile instead of moving on its own. */
private const val FACE_FOLLOW_DIVISOR = 6f

private const val FACE_TRANSLATE_X_RANGE = 8L
private const val FACE_TRANSLATE_Y_RANGE = 7L

/* The mouth, in their coordinates. */
private const val MOUTH_BASELINE = 19f
private const val MOUTH_OPEN_LEFT = 15f
private const val MOUTH_OPEN_RIGHT = 21f

/* The two control points of their `c2 1 4 1 6 0`, resolved from relative to absolute. */
private const val MOUTH_OPEN_CONTROL_LEFT = 17f
private const val MOUTH_OPEN_CONTROL_RIGHT = 19f
private const val MOUTH_OPEN_DEPTH = 1f
private const val MOUTH_CLOSED_LEFT = 13f
private const val MOUTH_CLOSED_RIGHT = 23f
private const val MOUTH_CLOSED_RADIUS_Y = 3.75f
private const val HALF_TURN = 180f

/* The eyes, in their coordinates. */
private const val EYE_TOP = 14f
private const val EYE_LEFT = 14f
private const val EYE_RIGHT = 20f
private const val EYE_WIDTH = 1.5f
private const val EYE_HEIGHT = 2f

/* Their `getContrast`: the YIQ luma of the eight-bit channels, against a threshold of 128. */
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
private const val LUMA_RED = 299
private const val LUMA_GREEN = 587
private const val LUMA_BLUE = 114
private const val LUMA_SCALE = 1000.0
private const val LUMA_THRESHOLD = 128.0
