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
    /** See [QuibloSplashScreen]: whether the catalogue behind this is warm enough to draw. */
    isReady: Boolean = true,
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
        isReady = isReady,
        minDurationMillis = TV_SPLASH_MIN_DURATION_MILLIS,
        insetForSystemBars = false,
        playSound = playSound,
        onSplashComplete = onSplashComplete,
    )
}

/**
 * The longest the mark stays up: the whole sting, reverb and all.
 *
 * A ceiling since `030` rather than a fixed length. It is the time a cold catalogue is allowed to
 * take before the app is shown regardless — not a delay the viewer pays on every launch.
 */
private const val TV_SPLASH_DURATION_MILLIS = 5000L

/**
 * The shortest: long enough for the mark to be seen and read, and no longer (`030` #5).
 *
 * A warm start now leaves here rather than at five seconds. The sting is cut short when it does,
 * which is the trade the length was chosen against: a viewer opening the app wants their catalogue
 * more than they want the rest of a jingle they have heard every evening this year.
 */
private const val TV_SPLASH_MIN_DURATION_MILLIS = 1500L

// Read from across a room rather than at arm's length.
private val TV_LOGO_SIZE = 240.dp
private val TV_LOGO_TITLE_SPACING = 8.dp
private val TV_TITLE_FONT_SIZE = 64.sp
