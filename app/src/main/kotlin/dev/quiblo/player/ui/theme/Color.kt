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

// Dark scheme: the brand's ink, paper and tungsten
internal val QuibloPrimaryDark = Color(0xFFFFB648)
internal val QuibloOnPrimaryDark = Color(0xFF432C00)
internal val QuibloPrimaryContainerDark = Color(0xFF604100)
internal val QuibloOnPrimaryContainerDark = Color(0xFFFFDEA8)
internal val QuibloSecondaryDark = Color(0xFFD9C4A0)
internal val QuibloOnSecondaryDark = Color(0xFF3C2E15)
internal val QuibloSecondaryContainerDark = Color(0xFF54452A)
internal val QuibloOnSecondaryContainerDark = Color(0xFFF6DFBB)
internal val QuibloBackgroundDark = Color(0xFF0B0C10)
internal val QuibloOnBackgroundDark = Color(0xFFF3F1EC)
internal val QuibloSurfaceDark = Color(0xFF121318)
internal val QuibloOnSurfaceDark = Color(0xFFF3F1EC)
internal val QuibloSurfaceVariantDark = Color(0xFF1C1E25)
internal val QuibloOnSurfaceVariantDark = Color(0xFFC9C6BD)
internal val QuibloErrorDark = Color(0xFFFFB4AB)
internal val QuibloOnErrorDark = Color(0xFF690005)
