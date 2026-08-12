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

package dev.quiblo.core.model

/**
 * How subtitles are drawn (INC-F11).
 *
 * **The system's own caption style is the starting point, and [matchSystem] returns to it.**
 * Android lets a person set caption size, colour and background once for every app, and someone
 * who has enlarged captions there has already answered this question — overriding that silently
 * would be this app deciding it knows better than an accessibility setting. Ignoring a viewer who
 * asks for something different in the player would be the opposite mistake, so both are possible
 * and the system's answer is the default.
 *
 * Choosing anything explicit turns [matchSystem] off, because a menu that lets you pick a size
 * while announcing it is following the system is a menu that lies about what it is doing.
 *
 * Values are enums rather than numbers: these are read from a sofa and set with a remote, and a
 * slider is neither. They also persist by name, which is what makes a stored choice survive a
 * new option being inserted.
 */
data class SubtitleStyle(
    val matchSystem: Boolean = true,
    val textSize: SubtitleTextSize = SubtitleTextSize.MEDIUM,
    val textColor: SubtitleColor = SubtitleColor.WHITE,
    val background: SubtitleColor = SubtitleColor.BLACK,
    val backgroundOpacity: SubtitleOpacity = SubtitleOpacity.SOLID,
)

/**
 * Caption size, as a fraction of the video's height.
 *
 * Fractional rather than in points, because these are the same subtitles on a phone held at
 * arm's length and a television across a room. A fixed point size is legible on exactly one of
 * them. The numbers bracket Android's own default of 0.0533.
 */
enum class SubtitleTextSize(val fractionOfHeight: Float) {
    SMALL(0.040f),
    MEDIUM(0.0533f),
    LARGE(0.070f),
    EXTRA_LARGE(0.090f),
}

/**
 * The colours offered, as ARGB with the alpha already opaque.
 *
 * A short list rather than a picker. Every one of these is a colour subtitles are actually set
 * in, and a full colour wheel driven by a remote is a feature nobody finishes using.
 */
enum class SubtitleColor(val argb: Int) {
    WHITE(0xFFFFFFFF.toInt()),
    YELLOW(0xFFFFEB3B.toInt()),
    BLACK(0xFF000000.toInt()),
    TRANSPARENT(0x00000000),
}

/** How solid the box behind the text is. */
enum class SubtitleOpacity(val alpha: Int) {
    NONE(0),
    LIGHT(0x40),
    MEDIUM(0x80),
    SOLID(0xCC),
}
