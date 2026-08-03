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
 * Used on devices without dynamic colour, and as the deliberate brand fallback. A deep
 * indigo primary reads well against video content, which is the dominant surface in this
 * app once playback starts.
 */

// Light scheme (Expressive 2026)
internal val QuibloPrimaryLight = Color(0xFF6C5CE7)
internal val QuibloOnPrimaryLight = Color(0xFFFFFFFF)
internal val QuibloPrimaryContainerLight = Color(0xFFECEBFF)
internal val QuibloOnPrimaryContainerLight = Color(0xFF231680)
internal val QuibloSecondaryLight = Color(0xFF5A5875)
internal val QuibloOnSecondaryLight = Color(0xFFFFFFFF)
internal val QuibloSecondaryContainerLight = Color(0xFFE2E0FF)
internal val QuibloOnSecondaryContainerLight = Color(0xFF171536)
internal val QuibloBackgroundLight = Color(0xFFF8F9FE)
internal val QuibloOnBackgroundLight = Color(0xFF191B23)
internal val QuibloSurfaceLight = Color(0xFFFFFFFF)
internal val QuibloOnSurfaceLight = Color(0xFF191B23)
internal val QuibloSurfaceVariantLight = Color(0xFFEFF0F8)
internal val QuibloOnSurfaceVariantLight = Color(0xFF454655)
internal val QuibloErrorLight = Color(0xFFBA1A1A)
internal val QuibloOnErrorLight = Color(0xFFFFFFFF)

// Dark scheme (Material 3 Expressive 2026 Dark & Glassmorphism)
internal val QuibloPrimaryDark = Color(0xFF9D8CFF)
internal val QuibloOnPrimaryDark = Color(0xFF1F0D78)
internal val QuibloPrimaryContainerDark = Color(0xFF3B2BA3)
internal val QuibloOnPrimaryContainerDark = Color(0xFFECEBFF)
internal val QuibloSecondaryDark = Color(0xFFC6C4E9)
internal val QuibloOnSecondaryDark = Color(0xFF2C2A48)
internal val QuibloSecondaryContainerDark = Color(0xFF42405F)
internal val QuibloOnSecondaryContainerDark = Color(0xFFE2E0FF)
internal val QuibloBackgroundDark = Color(0xFF0C0E14)
internal val QuibloOnBackgroundDark = Color(0xFFE4E5F1)
internal val QuibloSurfaceDark = Color(0xFF12141C)
internal val QuibloOnSurfaceDark = Color(0xFFE4E5F1)
internal val QuibloSurfaceVariantDark = Color(0xFF1B1E29)
internal val QuibloOnSurfaceVariantDark = Color(0xFFC7C7D7)
internal val QuibloErrorDark = Color(0xFFFFB4AB)
internal val QuibloOnErrorDark = Color(0xFF690005)
