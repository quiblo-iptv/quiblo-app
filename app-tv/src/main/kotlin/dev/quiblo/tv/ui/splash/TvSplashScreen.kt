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
 * **A set of numbers, not a second implementation.** The mark and the choreography are
 * `dev.quiblo.designsystem.QuibloSplashScreen`; this says only what a television wants of them —
 * a mark sized for across a room, a window that can hold on the mark while a cold catalogue warms,
 * and no system-bar insets, because a television has none to avoid.
 */
@Composable
@Suppress("LongParameterList")
fun TvSplashScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    durationMillis: Long = TV_SPLASH_DURATION_MILLIS,
    /** See [QuibloSplashScreen]: whether the catalogue behind this is warm enough to draw. */
    isReady: Boolean = true,
    playSound: Boolean = true,
    /** See [QuibloSplashScreen]. Wired to `QuibloSplashHost` by `TvApp`. */
    onPrepareReveal: () -> Unit = {},
    onRevealStart: () -> Unit = {},
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
        minDurationMillis = TV_SPLASH_MIN_DURATION_MILLIS.coerceAtMost(durationMillis),
        insetForSystemBars = false,
        playSound = playSound,
        onPrepareReveal = onPrepareReveal,
        onRevealStart = onRevealStart,
        onSplashComplete = onSplashComplete,
    )
}

/**
 * The longest the mark stays up before it dives regardless.
 *
 * A ceiling rather than a fixed length (`030`): the time a cold catalogue is allowed to take
 * before the app is shown anyway — not a delay the viewer pays on every launch.
 */
private const val TV_SPLASH_DURATION_MILLIS = 5000L

/**
 * The shortest: the mark drawn, the hit, the name, a breath, and the dive (`030` #5).
 *
 * A warm start leaves here. Nothing is cut short to do it: the sting's dive half plays when the
 * picture dives, whenever that is, so the sound always finishes with the screen.
 */
private const val TV_SPLASH_MIN_DURATION_MILLIS = 2300L

// Read from across a room: the mark a fifth of the screen across, the name under it.
private val TV_LOGO_SIZE = 180.dp
private val TV_LOGO_TITLE_SPACING = 4.dp
private val TV_TITLE_FONT_SIZE = 68.sp
