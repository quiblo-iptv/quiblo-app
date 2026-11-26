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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Outfit: the titles and the tab bar on the television. The name itself is not set in it — see
 * [QuibloWordmark], which is drawn.
 *
 * Bundled rather than downloaded, because a television is often offline when it is switched on.
 * Two weights and nothing else: SemiBold for titles and the selected tab, Medium for headings
 * under them and the other tabs. Outfit covers Latin only; a title in
 * another script falls back to the system font glyph by glyph, which is what Android does with
 * any family that lacks a character.
 */
val QuibloDisplayFamily = FontFamily(
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)

/**
 * The name as the wordmark writes it, for whoever cannot see the picture. Lowercase is the brand,
 * not a typo: q, b and o are three screens held apart by straight strokes, and capitals would
 * break the rhythm.
 *
 * The app's *label* — on the launcher, in settings — stays "Quiblo", because a label is a name in
 * a sentence and the wordmark is a picture of one.
 */
const val QUIBLO_WORDMARK = "quiblo"

/** The brand's colours. Everything is ink and paper; the only colour is the light in the screen. */
object QuibloColours {
    /** The room. Every background. */
    val Ink = Color(0xFF0B0C10)

    /** Raised surfaces on ink. */
    val Ink2 = Color(0xFF14161D)

    /** The mark, the wordmark, text. Warm white rather than pure, so it does not glare. */
    val Paper = Color(0xFFF3F1EC)

    /** Tungsten: the light inside the screen, and the accent. */
    val Glow = Color(0xFFFFB648)

    /** The hot centre of the light. */
    val GlowCore = Color(0xFFFFD58A)

    /** The glow's edge, and only that. */
    val Ember = Color(0xFFFF6A3D)
}
