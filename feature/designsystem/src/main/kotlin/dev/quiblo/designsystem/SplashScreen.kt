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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Creative launch screen: animated Quiblo glowing mark, title, tagline and version number.
 *
 * Sits at the start of app launch, providing a cinematic welcome before transitioning
 * into consent, profiles, or the catalogue.
 */
@Composable
fun QuibloSplashScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    durationMillis: Long = SPLASH_DURATION_MILLIS,
    onSplashComplete: () -> Unit = {},
) {
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(durationMillis)
        onSplashComplete()
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(ANIMATION_DURATION_MILLIS, easing = FastOutSlowInEasing),
        label = "splashContentAlpha",
    )

    val contentScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.88f,
        animationSpec = tween(ANIMATION_DURATION_MILLIS, easing = FastOutSlowInEasing),
        label = "splashContentScale",
    )

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
        // Subtle animated ambient light pool behind the center logo
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7986CB).copy(alpha = glowPulse * contentAlpha),
                        Color(0xFF3F51B5).copy(alpha = (glowPulse * GLOW_OUTER_SCALE) * contentAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height * LOGO_VERTICAL_BIAS),
                    radius = size.minDimension * GLOW_RADIUS_FACTOR,
                ),
                center = Offset(size.width / 2f, size.height * LOGO_VERTICAL_BIAS),
                radius = size.minDimension * GLOW_RADIUS_FACTOR,
            )
        }

        // Central branding: Logo + Name + Tagline
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .scale(contentScale)
                .alpha(contentAlpha),
        ) {
            QuibloLogoMark(modifier = Modifier.size(108.dp))

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Quiblo",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Free & Open Source IPTV",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
            )
        }

        // Version tag: Cleanly placed at the bottom right
        Text(
            text = if (versionName.startsWith("v")) versionName else "v$versionName",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 20.dp)
                .alpha(contentAlpha)
                .testTag("splash_version_text"),
        )
    }
}

/**
 * The Quiblo Brand Mark: The outer letter Q ring, triangular play symbol, and angled tail.
 */
@Composable
fun QuibloLogoMark(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF9FA4FF),
    secondaryColor: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val scale = w / CANVAS_BASE_SIZE

        val strokeWidth = 5f * scale
        val ringCenter = Offset(54f * scale, 54f * scale)
        val ringRadius = 18f * scale

        // The outer ring of the Q
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(accentColor, secondaryColor, accentColor),
                center = ringCenter,
            ),
            center = ringCenter,
            radius = ringRadius,
            style = Stroke(width = strokeWidth),
        )

        // The tail of the Q (breaking through bottom-right)
        drawLine(
            brush = Brush.linearGradient(
                listOf(accentColor, secondaryColor),
                start = Offset(64f * scale, 64f * scale),
                end = Offset(74f * scale, 74f * scale),
            ),
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
            brush = Brush.linearGradient(
                listOf(secondaryColor, accentColor),
                start = Offset(49f * scale, 45f * scale),
                end = Offset(65f * scale, 54f * scale),
            ),
        )
    }
}

private const val SPLASH_DURATION_MILLIS = 1200L
private const val ANIMATION_DURATION_MILLIS = 600
private const val GLOW_PULSE_DURATION_MILLIS = 1800
private const val GLOW_PULSE_MIN = 0.22f
private const val GLOW_PULSE_MAX = 0.42f
private const val GLOW_OUTER_SCALE = 0.5f
private const val GLOW_RADIUS_FACTOR = 0.55f
private const val LOGO_VERTICAL_BIAS = 0.46f
private const val CANVAS_BASE_SIZE = 108f
