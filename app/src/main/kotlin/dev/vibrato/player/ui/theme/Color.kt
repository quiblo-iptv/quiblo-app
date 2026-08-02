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

// Light scheme
internal val VibratoPrimaryLight = Color(0xFF4A4FBF)
internal val VibratoOnPrimaryLight = Color(0xFFFFFFFF)
internal val VibratoPrimaryContainerLight = Color(0xFFE1E0FF)
internal val VibratoOnPrimaryContainerLight = Color(0xFF04006E)
internal val VibratoSecondaryLight = Color(0xFF5C5D72)
internal val VibratoOnSecondaryLight = Color(0xFFFFFFFF)
internal val VibratoSecondaryContainerLight = Color(0xFFE1E0F9)
internal val VibratoOnSecondaryContainerLight = Color(0xFF191A2C)
internal val VibratoBackgroundLight = Color(0xFFFCF8FF)
internal val VibratoOnBackgroundLight = Color(0xFF1B1B21)
internal val VibratoSurfaceLight = Color(0xFFFCF8FF)
internal val VibratoOnSurfaceLight = Color(0xFF1B1B21)
internal val VibratoSurfaceVariantLight = Color(0xFFE3E1EC)
internal val VibratoOnSurfaceVariantLight = Color(0xFF46464F)
internal val VibratoErrorLight = Color(0xFFBA1A1A)
internal val VibratoOnErrorLight = Color(0xFFFFFFFF)

// Dark scheme
internal val VibratoPrimaryDark = Color(0xFFC0C1FF)
internal val VibratoOnPrimaryDark = Color(0xFF1A1A8E)
internal val VibratoPrimaryContainerDark = Color(0xFF3235A6)
internal val VibratoOnPrimaryContainerDark = Color(0xFFE1E0FF)
internal val VibratoSecondaryDark = Color(0xFFC5C4DD)
internal val VibratoOnSecondaryDark = Color(0xFF2E2F42)
internal val VibratoSecondaryContainerDark = Color(0xFF444559)
internal val VibratoOnSecondaryContainerDark = Color(0xFFE1E0F9)
internal val VibratoBackgroundDark = Color(0xFF131318)
internal val VibratoOnBackgroundDark = Color(0xFFE4E1E9)
internal val VibratoSurfaceDark = Color(0xFF131318)
internal val VibratoOnSurfaceDark = Color(0xFFE4E1E9)
internal val VibratoSurfaceVariantDark = Color(0xFF46464F)
internal val VibratoOnSurfaceVariantDark = Color(0xFFC7C5D0)
internal val VibratoErrorDark = Color(0xFFFFB4AB)
internal val VibratoOnErrorDark = Color(0xFF690005)
