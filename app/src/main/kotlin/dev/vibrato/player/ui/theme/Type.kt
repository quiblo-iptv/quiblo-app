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

import androidx.compose.material3.Typography

/**
 * Typography for Vibrato.
 *
 * The Material 3 defaults are used deliberately: they ship with the library, add nothing
 * to the APK, and keep the project inside the AC-NFR-02 size budget. A bundled typeface
 * would be the first thing to justify against that budget.
 */
internal val VibratoTypography = Typography()
