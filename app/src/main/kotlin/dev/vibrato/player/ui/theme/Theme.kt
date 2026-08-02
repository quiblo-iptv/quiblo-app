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

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = VibratoPrimaryLight,
    onPrimary = VibratoOnPrimaryLight,
    primaryContainer = VibratoPrimaryContainerLight,
    onPrimaryContainer = VibratoOnPrimaryContainerLight,
    secondary = VibratoSecondaryLight,
    onSecondary = VibratoOnSecondaryLight,
    secondaryContainer = VibratoSecondaryContainerLight,
    onSecondaryContainer = VibratoOnSecondaryContainerLight,
    background = VibratoBackgroundLight,
    onBackground = VibratoOnBackgroundLight,
    surface = VibratoSurfaceLight,
    onSurface = VibratoOnSurfaceLight,
    surfaceVariant = VibratoSurfaceVariantLight,
    onSurfaceVariant = VibratoOnSurfaceVariantLight,
    error = VibratoErrorLight,
    onError = VibratoOnErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = VibratoPrimaryDark,
    onPrimary = VibratoOnPrimaryDark,
    primaryContainer = VibratoPrimaryContainerDark,
    onPrimaryContainer = VibratoOnPrimaryContainerDark,
    secondary = VibratoSecondaryDark,
    onSecondary = VibratoOnSecondaryDark,
    secondaryContainer = VibratoSecondaryContainerDark,
    onSecondaryContainer = VibratoOnSecondaryContainerDark,
    background = VibratoBackgroundDark,
    onBackground = VibratoOnBackgroundDark,
    surface = VibratoSurfaceDark,
    onSurface = VibratoOnSurfaceDark,
    surfaceVariant = VibratoSurfaceVariantDark,
    onSurfaceVariant = VibratoOnSurfaceVariantDark,
    error = VibratoErrorDark,
    onError = VibratoOnErrorDark,
)

/**
 * The Material 3 theme for Vibrato.
 *
 * Honours the system dark/light setting by default (AC-NFR-09) and opts into dynamic
 * colour on Android 12+, falling back to the static palette in [Color.kt] elsewhere.
 *
 * @param darkTheme whether to use the dark scheme; defaults to the system setting.
 * @param dynamicColor whether to derive the scheme from the device wallpaper where supported.
 */
@Composable
fun VibratoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
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
        typography = VibratoTypography,
        content = content,
    )
}
