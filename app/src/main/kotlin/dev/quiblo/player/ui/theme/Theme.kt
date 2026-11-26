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

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.quiblo.core.model.Appearance
import dev.quiblo.core.model.ThemeMode
import dev.quiblo.designsystem.QuibloShapes

private val LightColors = lightColorScheme(
    primary = QuibloPrimaryLight,
    onPrimary = QuibloOnPrimaryLight,
    primaryContainer = QuibloPrimaryContainerLight,
    onPrimaryContainer = QuibloOnPrimaryContainerLight,
    secondary = QuibloSecondaryLight,
    onSecondary = QuibloOnSecondaryLight,
    secondaryContainer = QuibloSecondaryContainerLight,
    onSecondaryContainer = QuibloOnSecondaryContainerLight,
    background = QuibloBackgroundLight,
    onBackground = QuibloOnBackgroundLight,
    surface = QuibloSurfaceLight,
    onSurface = QuibloOnSurfaceLight,
    surfaceVariant = QuibloSurfaceVariantLight,
    onSurfaceVariant = QuibloOnSurfaceVariantLight,
    surfaceContainerLowest = QuibloSurfaceContainerLowestLight,
    surfaceContainerLow = QuibloSurfaceContainerLowLight,
    surfaceContainer = QuibloSurfaceContainerLight,
    surfaceContainerHigh = QuibloSurfaceContainerHighLight,
    surfaceContainerHighest = QuibloSurfaceContainerHighestLight,
    error = QuibloErrorLight,
    onError = QuibloOnErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = QuibloPrimaryDark,
    onPrimary = QuibloOnPrimaryDark,
    primaryContainer = QuibloPrimaryContainerDark,
    onPrimaryContainer = QuibloOnPrimaryContainerDark,
    secondary = QuibloSecondaryDark,
    onSecondary = QuibloOnSecondaryDark,
    secondaryContainer = QuibloSecondaryContainerDark,
    onSecondaryContainer = QuibloOnSecondaryContainerDark,
    background = QuibloBackgroundDark,
    onBackground = QuibloOnBackgroundDark,
    surface = QuibloSurfaceDark,
    onSurface = QuibloOnSurfaceDark,
    surfaceVariant = QuibloSurfaceVariantDark,
    onSurfaceVariant = QuibloOnSurfaceVariantDark,
    outline = QuibloOutlineDark,
    outlineVariant = QuibloOutlineVariantDark,
    surfaceContainerLowest = QuibloSurfaceContainerLowestDark,
    surfaceContainerLow = QuibloSurfaceContainerLowDark,
    surfaceContainer = QuibloSurfaceContainerDark,
    surfaceContainerHigh = QuibloSurfaceContainerHighDark,
    surfaceContainerHighest = QuibloSurfaceContainerHighestDark,
    error = QuibloErrorDark,
    onError = QuibloOnErrorDark,
)

/**
 * The Material 3 theme for Quiblo.
 *
 * Honours the system dark/light setting by default (AC-NFR-09). The dark scheme is the
 * television's own room — see [Color.kt] — and dynamic colour on Android 12+ is there for
 * anybody who switches it on, no longer by default.
 *
 * @param appearance the user's stored preference. [ThemeMode.SYSTEM] follows the device,
 *   which is the default and what AC-NFR-09 requires to keep working.
 */
@Composable
fun QuibloTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamicColor = appearance.dynamicColor

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = QuibloTypography,
        shapes = QuibloShapes,
        content = content,
    )
}
