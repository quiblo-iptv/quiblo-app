/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Static Material 3 palette.
 *
 * Used on devices without dynamic colour, and as the deliberate brand fallback. A deep
 * indigo primary reads well against video content, which is the dominant surface in this
 * app once playback starts.
 */

// Light scheme (Expressive 2026)
internal val VibratoPrimaryLight = Color(0xFF6C5CE7)
internal val VibratoOnPrimaryLight = Color(0xFFFFFFFF)
internal val VibratoPrimaryContainerLight = Color(0xFFECEBFF)
internal val VibratoOnPrimaryContainerLight = Color(0xFF231680)
internal val VibratoSecondaryLight = Color(0xFF5A5875)
internal val VibratoOnSecondaryLight = Color(0xFFFFFFFF)
internal val VibratoSecondaryContainerLight = Color(0xFFE2E0FF)
internal val VibratoOnSecondaryContainerLight = Color(0xFF171536)
internal val VibratoBackgroundLight = Color(0xFFF8F9FE)
internal val VibratoOnBackgroundLight = Color(0xFF191B23)
internal val VibratoSurfaceLight = Color(0xFFFFFFFF)
internal val VibratoOnSurfaceLight = Color(0xFF191B23)
internal val VibratoSurfaceVariantLight = Color(0xFFEFF0F8)
internal val VibratoOnSurfaceVariantLight = Color(0xFF454655)
internal val VibratoErrorLight = Color(0xFFBA1A1A)
internal val VibratoOnErrorLight = Color(0xFFFFFFFF)

// Dark scheme (Material 3 Expressive 2026 Dark & Glassmorphism)
internal val VibratoPrimaryDark = Color(0xFF9D8CFF)
internal val VibratoOnPrimaryDark = Color(0xFF1F0D78)
internal val VibratoPrimaryContainerDark = Color(0xFF3B2BA3)
internal val VibratoOnPrimaryContainerDark = Color(0xFFECEBFF)
internal val VibratoSecondaryDark = Color(0xFFC6C4E9)
internal val VibratoOnSecondaryDark = Color(0xFF2C2A48)
internal val VibratoSecondaryContainerDark = Color(0xFF42405F)
internal val VibratoOnSecondaryContainerDark = Color(0xFFE2E0FF)
internal val VibratoBackgroundDark = Color(0xFF0C0E14)
internal val VibratoOnBackgroundDark = Color(0xFFE4E5F1)
internal val VibratoSurfaceDark = Color(0xFF12141C)
internal val VibratoOnSurfaceDark = Color(0xFFE4E5F1)
internal val VibratoSurfaceVariantDark = Color(0xFF1B1E29)
internal val VibratoOnSurfaceVariantDark = Color(0xFFC7C7D7)
internal val VibratoErrorDark = Color(0xFFFFB4AB)
internal val VibratoOnErrorDark = Color(0xFF690005)
