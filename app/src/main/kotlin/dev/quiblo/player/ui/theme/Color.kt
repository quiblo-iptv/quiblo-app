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

package dev.quiblo.player.ui.theme

import androidx.compose.ui.graphics.Color
import dev.quiblo.designsystem.QuibloColours

/**
 * Static Material 3 palette.
 *
 * Used on devices without dynamic colour, and as the deliberate brand fallback. Ink and paper,
 * with tungsten — the warm light inside the mark's screen — as the one colour: a deep amber in
 * the light theme, where the bright one would not hold text, and the brand's own in the dark.
 */

// Light scheme
internal val QuibloPrimaryLight = Color(0xFF7C5800)
internal val QuibloOnPrimaryLight = Color(0xFFFFFFFF)
internal val QuibloPrimaryContainerLight = Color(0xFFFFDEA8)
internal val QuibloOnPrimaryContainerLight = Color(0xFF271900)
internal val QuibloSecondaryLight = Color(0xFF6C5C3F)
internal val QuibloOnSecondaryLight = Color(0xFFFFFFFF)
internal val QuibloSecondaryContainerLight = Color(0xFFF6DFBB)
internal val QuibloOnSecondaryContainerLight = Color(0xFF251A04)
internal val QuibloBackgroundLight = Color(0xFFF7F6F3)
internal val QuibloOnBackgroundLight = Color(0xFF1B1C1F)
internal val QuibloSurfaceLight = Color(0xFFFDFCF9)
internal val QuibloOnSurfaceLight = Color(0xFF1B1C1F)
internal val QuibloSurfaceVariantLight = Color(0xFFEEEDE8)
internal val QuibloOnSurfaceVariantLight = Color(0xFF4A4840)
internal val QuibloErrorLight = Color(0xFFBA1A1A)
internal val QuibloOnErrorLight = Color(0xFFFFFFFF)

// The roles Material derives bars, sheets and cards from, pinned to paper. Left to Material they
// come out of its baseline lavender, and the floating tab bar was a purple island on warm paper.
internal val QuibloSurfaceContainerLowestLight = Color(0xFFFFFFFF)
internal val QuibloSurfaceContainerLowLight = Color(0xFFF9F8F5)
internal val QuibloSurfaceContainerLight = Color(0xFFF2F1ED)
internal val QuibloSurfaceContainerHighLight = Color(0xFFECEBE6)
internal val QuibloSurfaceContainerHighestLight = Color(0xFFE6E4DE)

// Dark scheme: the television's own room, colour for colour (`QuibloColours`). The phone used
// its own near-miss of these — a surface two shades off, a variant a little blue — and side by
// side the two apps read as cousins rather than as one thing. The roles Material derives the
// bars, sheets and cards from are pinned too, so nothing falls back to a tonal guess.
internal val QuibloPrimaryDark = QuibloColours.Glow
internal val QuibloOnPrimaryDark = Color(0xFF432C00)
internal val QuibloPrimaryContainerDark = Color(0xFF3A2A10)
internal val QuibloOnPrimaryContainerDark = QuibloColours.GlowCore
internal val QuibloSecondaryDark = Color(0xFFD9C4A0)
internal val QuibloOnSecondaryDark = Color(0xFF3C2E15)
internal val QuibloSecondaryContainerDark = Color(0x29F3F1EC)
internal val QuibloOnSecondaryContainerDark = QuibloColours.Paper
internal val QuibloBackgroundDark = QuibloColours.Ink
internal val QuibloOnBackgroundDark = QuibloColours.Paper
internal val QuibloSurfaceDark = QuibloColours.Ink
internal val QuibloOnSurfaceDark = QuibloColours.Paper
internal val QuibloSurfaceVariantDark = QuibloColours.Ink2
internal val QuibloOnSurfaceVariantDark = Color(0xFFB9B6AE)
internal val QuibloOutlineDark = Color(0x3DF3F1EC)
internal val QuibloOutlineVariantDark = Color(0x1FF3F1EC)
internal val QuibloSurfaceContainerLowestDark = Color(0xFF07080B)
internal val QuibloSurfaceContainerLowDark = Color(0xFF101217)
internal val QuibloSurfaceContainerDark = QuibloColours.Ink2
internal val QuibloSurfaceContainerHighDark = Color(0xFF1B1E26)
internal val QuibloSurfaceContainerHighestDark = Color(0xFF23262F)
internal val QuibloErrorDark = Color(0xFFFFB4AB)
internal val QuibloOnErrorDark = Color(0xFF690005)
