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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import dev.quiblo.designsystem.QuibloDisplayFamily

/**
 * Typography for Quiblo: the television's voice, at a phone's sizes.
 *
 * **Outfit for everything that names something** — screen titles, a film's name, a shelf's
 * heading, the tab labels — and the platform's own face for everything that is read in a run:
 * plots, settings descriptions, a guide's programme lines. That is exactly the split the
 * television makes, and it is what makes the two apps read as one product rather than as a phone
 * app and a television app that happen to share a logo.
 *
 * The AC-NFR-02 note this file used to carry still holds, and it is why the weights stop at two:
 * Outfit is already in the APK — `:feature:designsystem` bundles it for the splash and the
 * wordmark's neighbours — so naming it here costs no bytes. A third weight would.
 */
private val Base = Typography()

private fun TextStyle.display(weight: FontWeight): TextStyle =
    copy(fontFamily = QuibloDisplayFamily, fontWeight = weight)

internal val QuibloTypography = Base.copy(
    displayLarge = Base.displayLarge.display(FontWeight.SemiBold),
    displayMedium = Base.displayMedium.display(FontWeight.SemiBold),
    displaySmall = Base.displaySmall.display(FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.display(FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.display(FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.display(FontWeight.SemiBold),
    titleLarge = Base.titleLarge.display(FontWeight.SemiBold),
    titleMedium = Base.titleMedium.display(FontWeight.Medium),
    titleSmall = Base.titleSmall.display(FontWeight.Medium),
    labelLarge = Base.labelLarge.display(FontWeight.Medium),
)
