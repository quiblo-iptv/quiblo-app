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

package dev.quiblo.tv.ui.splash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiblo.designsystem.QuibloSplashScreen

/**
 * The launch screen, at the size a television is watched from.
 *
 * **A set of numbers, not a second implementation.** It was a copy of the phone's file with four
 * values changed and a hand-drawn brand mark of its own that had the wrong geometry; the mark and
 * the animation are `dev.quiblo.designsystem.QuibloSplashScreen` now and this says only what a
 * television wants of them — a bigger mark, a bigger name, the whole five seconds of the sting,
 * and no system-bar insets, because a television has none to avoid.
 */
@Composable
fun TvSplashScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    durationMillis: Long = TV_SPLASH_DURATION_MILLIS,
    playSound: Boolean = true,
    onSplashComplete: () -> Unit = {},
) {
    QuibloSplashScreen(
        versionName = versionName,
        modifier = modifier,
        logoSize = TV_LOGO_SIZE,
        titleSize = TV_TITLE_FONT_SIZE,
        logoTitleSpacing = TV_LOGO_TITLE_SPACING,
        durationMillis = durationMillis,
        insetForSystemBars = false,
        playSound = playSound,
        onSplashComplete = onSplashComplete,
    )
}

/** The whole sting, reverb and all: a television is not waiting to be unlocked and used. */
private const val TV_SPLASH_DURATION_MILLIS = 5000L

// Read from across a room rather than at arm's length.
private val TV_LOGO_SIZE = 240.dp
private val TV_LOGO_TITLE_SPACING = 8.dp
private val TV_TITLE_FONT_SIZE = 64.sp
