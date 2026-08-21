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

package dev.quiblo.designsystem

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Creative launch screen: animated solid-white Quiblo brand mark, title, audio sting, and version tag.
 *
 * Synchronized with the audio sting peak at ~1.9s for a dramatic Netflix-style zoom-through landing.
 */
@Composable
fun QuibloSplashScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    logoSize: Dp = 150.dp,
    durationMillis: Long = DEFAULT_SPLASH_DURATION_MILLIS,
    playSound: Boolean = true,
    onSplashComplete: () -> Unit = {},
) {
    val context = LocalContext.current

    if (playSound) {
        DisposableEffect(Unit) {
            var player: MediaPlayer? = null
            try {
                player = MediaPlayer.create(context, R.raw.splash_sound)?.apply {
                    setOnCompletionListener { mp ->
                        try {
                            mp.release()
                        } catch (_: Throwable) {
                            // Ignore release errors
                        }
                    }
                    start()
                }
            } catch (_: Throwable) {
                // Ignore audio failure if audio device is unavailable
            }
            onDispose {
                // Let the audio tail finish decaying naturally on transition
            }
        }
    }

    val introAlpha = remember { Animatable(0f) }
    val introScale = remember { Animatable(0.85f) }
    val zoomScale = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // 1. Entrance animation (0ms to 400ms)
        launch {
            introAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(INTRO_DURATION_MILLIS, easing = FastOutSlowInEasing),
            )
        }
        launch {
            introScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(INTRO_DURATION_MILLIS, easing = FastOutSlowInEasing),
            )
        }

        // 2. Resting duration: aligns the zoom explosion directly with the audio peak at ~1.9s
        val effectiveZoomStart = if (durationMillis != DEFAULT_SPLASH_DURATION_MILLIS) {
            (durationMillis - ZOOM_DURATION_MILLIS).coerceAtLeast(0L)
        } else {
            ZOOM_START_DELAY_MILLIS
        }
        delay(effectiveZoomStart)

        // 3. Netflix-style camera zoom-through hitting exactly at the audio peak
        launch {
            zoomScale.animateTo(
                targetValue = NETFLIX_ZOOM_MAX_SCALE,
                animationSpec = tween(
                    ZOOM_DURATION_MILLIS.toInt(),
                    easing = CubicBezierEasing(0.65f, 0f, 0.85f, 0.2f),
                ),
            )
        }
        launch {
            delay(ZOOM_FADEOUT_DELAY_MILLIS)
            exitAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(ZOOM_FADEOUT_DURATION_MILLIS, easing = LinearEasing),
            )
        }

        delay(ZOOM_DURATION_MILLIS)
        onSplashComplete()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splashGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = GLOW_PULSE_MIN,
        targetValue = GLOW_PULSE_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(GLOW_PULSE_DURATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashGlowPulse",
    )

    val currentContentAlpha = introAlpha.value * exitAlpha.value
    val currentContentScale = introScale.value * zoomScale.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF14172B),
                        Color(0xFF090A12),
                        Color(0xFF000000),
                    ),
                    center = Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY,
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Ambient glow pool behind the central logo
        Canvas(modifier = Modifier.fillMaxSize()) {
            val flareScale = (zoomScale.value - 1f) * GLOW_ZOOM_FLARE_FACTOR + 1f
            val innerAlpha = (glowPulse * currentContentAlpha).coerceIn(0f, 1f)
            val outerAlpha = ((glowPulse * GLOW_OUTER_SCALE) * currentContentAlpha).coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7986CB).copy(alpha = innerAlpha),
                        Color(0xFF3F51B5).copy(alpha = outerAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height * LOGO_VERTICAL_BIAS),
                    radius = size.minDimension * GLOW_RADIUS_FACTOR * flareScale,
                ),
                center = Offset(size.width / 2f, size.height * LOGO_VERTICAL_BIAS),
                radius = size.minDimension * GLOW_RADIUS_FACTOR * flareScale,
            )
        }

        // Central branding: Large Pure White Logo + Name
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .scale(currentContentScale)
                .alpha(currentContentAlpha),
        ) {
            QuibloLogoMark(
                modifier = Modifier.size(logoSize),
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Quiblo",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }

        // Version tag: Cleanly placed at the bottom right
        Text(
            text = if (versionName.startsWith("v")) versionName else "v$versionName",
            color = Color.White.copy(alpha = (0.75f * currentContentAlpha).coerceIn(0f, 1f)),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 32.dp, bottom = 28.dp)
                .alpha(currentContentAlpha)
                .testTag("splash_version_text"),
        )
    }
}

/**
 * The Quiblo Brand Mark: Pure white outer letter Q ring, triangular play symbol, and angled tail.
 */
@Composable
fun QuibloLogoMark(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val scale = w / CANVAS_BASE_SIZE

        val strokeWidth = 5.5f * scale
        val ringCenter = Offset(54f * scale, 54f * scale)
        val ringRadius = 18f * scale

        // The outer ring of the Q
        drawCircle(
            color = color,
            center = ringCenter,
            radius = ringRadius,
            style = Stroke(width = strokeWidth),
        )

        // The tail of the Q (breaking through bottom-right)
        drawLine(
            color = color,
            start = Offset(64f * scale, 64f * scale),
            end = Offset(74f * scale, 74f * scale),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )

        // The play mark (triangle in center)
        val playPath = Path().apply {
            moveTo(49f * scale, 45f * scale)
            lineTo(49f * scale, 63f * scale)
            lineTo(65f * scale, 54f * scale)
            close()
        }

        drawPath(
            path = playPath,
            color = color,
        )
    }
}

private const val DEFAULT_SPLASH_DURATION_MILLIS = 2450L
private const val INTRO_DURATION_MILLIS = 400
private const val ZOOM_START_DELAY_MILLIS = 1750L
private const val ZOOM_DURATION_MILLIS = 700L
private const val ZOOM_FADEOUT_DELAY_MILLIS = 350L
private const val ZOOM_FADEOUT_DURATION_MILLIS = 350
private const val NETFLIX_ZOOM_MAX_SCALE = 20f
private const val GLOW_ZOOM_FLARE_FACTOR = 0.25f
private const val GLOW_PULSE_DURATION_MILLIS = 1800
private const val GLOW_PULSE_MIN = 0.25f
private const val GLOW_PULSE_MAX = 0.45f
private const val GLOW_OUTER_SCALE = 0.5f
private const val GLOW_RADIUS_FACTOR = 0.55f
private const val LOGO_VERTICAL_BIAS = 0.48f
private const val CANVAS_BASE_SIZE = 108f
