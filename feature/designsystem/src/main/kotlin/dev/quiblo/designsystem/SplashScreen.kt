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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The launch screen: the mark, the name, a sting, and the version it is about to run.
 *
 * **One composable for both frontends, and that is the point of it being here.** The phone and
 * the television want different sizes and a different length of sting, which is four parameters;
 * they were a file each, three hundred and seventy lines apiece, differing in those four values
 * and in nothing else. The copy on the television had already drifted — a second hand-drawn brand
 * mark with the wrong geometry — which is what a copy does while nobody is reading both.
 *
 * The zoom lands on the last [ZOOM_DURATION_MILLIS] of [durationMillis], so a caller sets one
 * number and the timing follows it.
 *
 * [isReady] and [minDurationMillis] turn that one number into a window (`030` #5). A splash whose
 * length is a constant is a constant delay in front of an app that then makes the viewer wait
 * again while it reads its catalogue; gated on readiness, the same seconds are the loading, and
 * the screen behind is drawn the moment there is something to draw. Both bounds are kept: never
 * shorter than [minDurationMillis], so a warm start does not flash the mark and vanish, and never
 * longer than [durationMillis], so a database that will not answer is not a locked television.
 */
@Composable
fun QuibloSplashScreen(
    versionName: String,
    modifier: Modifier = Modifier,
    logoSize: Dp = LOGO_SIZE,
    titleSize: TextUnit = TITLE_SIZE,
    logoTitleSpacing: Dp = LOGO_TITLE_SPACING,
    durationMillis: Long = DEFAULT_SPLASH_DURATION_MILLIS,
    /**
     * Whether what is behind the splash is worth showing yet.
     *
     * Defaults to true, which is the old behaviour exactly: the splash runs for [durationMillis]
     * and nothing waits on anything.
     */
    isReady: Boolean = true,
    /** The floor of the window. Defaults to [durationMillis], which is a fixed-length splash. */
    minDurationMillis: Long = durationMillis,
    insetForSystemBars: Boolean = true,
    playSound: Boolean = true,
    onSplashComplete: () -> Unit = {},
) {
    if (playSound) SplashSting()

    val introAlpha = remember { Animatable(0f) }
    val introScale = remember { Animatable(INTRO_SCALE_FROM) }
    val zoomScale = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(1f) }

    // Held in state rather than read from the parameter. The effect below is not restarted when
    // readiness changes, so a captured `isReady` would be the value from the frame the splash
    // started on — false, always, and the cap would be the only thing that ever ended the wait.
    val ready by rememberUpdatedState(isReady)

    LaunchedEffect(durationMillis, minDurationMillis) {
        launch {
            introAlpha.animateTo(1f, tween(INTRO_DURATION_MILLIS, easing = FastOutSlowInEasing))
        }
        launch {
            introScale.animateTo(1f, tween(INTRO_DURATION_MILLIS, easing = FastOutSlowInEasing))
        }

        // Everything before the zoom is rest. The zoom is what the sting's crescendo is under,
        // so it is timed from the end rather than from the start — which is why both bounds are
        // measured against the rest and not against the whole.
        val longestRest = (durationMillis - ZOOM_DURATION_MILLIS).coerceAtLeast(0L)
        val shortestRest = (minDurationMillis - ZOOM_DURATION_MILLIS).coerceIn(0L, longestRest)

        delay(shortestRest)
        // Waits for the app behind to be worth showing, and gives up at the cap.
        withTimeoutOrNull(longestRest - shortestRest) {
            snapshotFlow { ready }.first { it }
        }

        launch {
            zoomScale.animateTo(
                targetValue = ZOOM_MAX_SCALE,
                animationSpec = tween(
                    ZOOM_DURATION_MILLIS.toInt(),
                    easing = CubicBezierEasing(ZOOM_EASE_A, 0f, ZOOM_EASE_B, ZOOM_EASE_C),
                ),
            )
        }
        launch {
            delay(ZOOM_FADEOUT_DELAY_MILLIS)
            exitAlpha.animateTo(0f, tween(ZOOM_FADEOUT_DURATION_MILLIS, easing = LinearEasing))
        }

        delay(ZOOM_DURATION_MILLIS)
        onSplashComplete()
    }

    val contentAlpha = introAlpha.value * exitAlpha.value
    val contentScale = introScale.value * zoomScale.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BACKDROP_NEAR, BACKDROP_MID, Color.Black),
                    center = Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY,
                ),
            )
            .then(
                // A television has no status bar to avoid and insets of its own that mean
                // something else; the phone would draw its version tag under the gesture bar.
                if (insetForSystemBars) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier,
            ),
    ) {
        SplashGlow(contentAlpha = contentAlpha, zoomScale = zoomScale.value)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    // Sit on the glow's centre rather than the box's, so the light is behind the
                    // mark instead of below it.
                    translationY = size.height * (LOGO_VERTICAL_BIAS - HALF)
                }
                .scale(contentScale)
                .alpha(contentAlpha),
        ) {
            QuibloMark(modifier = Modifier.size(logoSize), colour = Color.White)

            Spacer(modifier = Modifier.height(logoTitleSpacing))

            Text(
                text = QUIBLO,
                color = Color.White,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = TITLE_TRACKING,
            )
        }

        Text(
            text = if (versionName.startsWith("v")) versionName else "v$versionName",
            color = Color.White.copy(alpha = (VERSION_ALPHA * contentAlpha).coerceIn(0f, 1f)),
            fontSize = VERSION_TEXT_SIZE,
            fontWeight = FontWeight.Medium,
            letterSpacing = VERSION_TRACKING,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = VERSION_END_PADDING, bottom = VERSION_BOTTOM_PADDING)
                .alpha(contentAlpha)
                .testTag(SPLASH_VERSION_TAG),
        )
    }
}

/**
 * The sting, and the one place it is released.
 *
 * **Released on dispose and nowhere else.** It was also released from an `onCompletion`
 * listener, so a splash that outlived its own sound released a player the listener had already
 * freed — an `IllegalStateException` into a `catch` that swallowed it. One owner, one release.
 */
@Composable
private fun SplashSting() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        // Null when the device will not give us an audio track at all, which a television box
        // with nothing plugged into it does.
        val player = runCatching { MediaPlayer.create(context, R.raw.splash_sound) }.getOrNull()
        runCatching { player?.start() }
        onDispose { runCatching { player?.release() } }
    }
}

/** The light behind the mark: two pools on a slow orbit, and a pulse under the logo itself. */
@Composable
private fun SplashGlow(contentAlpha: Float, zoomScale: Float) {
    val transition = rememberInfiniteTransition(label = "splashAnimations")
    val glowPulse by transition.animateFloat(
        initialValue = GLOW_PULSE_MIN,
        targetValue = GLOW_PULSE_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(GLOW_PULSE_DURATION_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashGlowPulse",
    )
    val ambientAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(
            animation = tween(AMBIENT_DRIFT_DURATION_MILLIS, easing = LinearEasing),
        ),
        label = "splashAmbientAngle",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // The pools widen with the zoom, so the light travels through the camera with the mark.
        val flare = (zoomScale - 1f) * GLOW_ZOOM_FLARE_FACTOR + 1f
        val radians = ambientAngle * (PI.toFloat() / HALF_TURN)
        val orbitX = cos(radians) * AMBIENT_ORBIT_REACH_X
        val orbitY = sin(radians) * AMBIENT_ORBIT_REACH_Y

        pool(
            colour = POOL_NEAR,
            alpha = POOL_NEAR_ALPHA * contentAlpha,
            centre = Offset(size.width * (POOL_NEAR_X + orbitX), size.height * (POOL_NEAR_Y + orbitY)),
            radius = size.minDimension * POOL_RADIUS_FACTOR * flare,
        )
        pool(
            colour = POOL_FAR,
            alpha = POOL_FAR_ALPHA * contentAlpha,
            centre = Offset(size.width * (POOL_FAR_X - orbitX), size.height * (POOL_FAR_Y - orbitY)),
            radius = size.minDimension * POOL_RADIUS_FACTOR * flare,
        )

        val centre = Offset(size.width / 2f, size.height * LOGO_VERTICAL_BIAS)
        val radius = size.minDimension * GLOW_RADIUS_FACTOR * flare
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GLOW_INNER.copy(alpha = (glowPulse * contentAlpha).coerceIn(0f, 1f)),
                    GLOW_OUTER.copy(alpha = (glowPulse * GLOW_OUTER_SCALE * contentAlpha).coerceIn(0f, 1f)),
                    Color.Transparent,
                ),
                center = centre,
                radius = radius,
            ),
            center = centre,
            radius = radius,
        )
    }
}

/** One pool of light, drawn where it is told. */
private fun DrawScope.pool(
    colour: Color,
    alpha: Float,
    centre: Offset,
    radius: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colour.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent),
            center = centre,
            radius = radius,
        ),
        center = centre,
        radius = radius,
    )
}

/** Where the version tag is, for the tests that check it is on screen. */
const val SPLASH_VERSION_TAG = "splash_version_text"

/** The name, which is a brand rather than copy and so is not translated. */
private const val QUIBLO = "Quiblo"

/** The phone's sizes. The television passes its own — see `TvSplashScreen`. */
private val LOGO_SIZE = 150.dp
private val TITLE_SIZE = 46.sp
private val LOGO_TITLE_SPACING = 6.dp

/**
 * How long the whole thing lasts on a phone.
 *
 * Short enough to be a launch screen rather than an interruption: the sound runs five seconds
 * and the picture does not wait for its reverb to decay.
 */
private const val DEFAULT_SPLASH_DURATION_MILLIS = 2450L

private const val INTRO_DURATION_MILLIS = 400
private const val INTRO_SCALE_FROM = 0.85f

/** The camera moving through the mark. The last stretch of the splash, always. */
private const val ZOOM_DURATION_MILLIS = 700L
private const val ZOOM_MAX_SCALE = 20f
private const val ZOOM_EASE_A = 0.65f
private const val ZOOM_EASE_B = 0.85f
private const val ZOOM_EASE_C = 0.2f
private const val ZOOM_FADEOUT_DELAY_MILLIS = 350L
private const val ZOOM_FADEOUT_DURATION_MILLIS = 350

private const val GLOW_ZOOM_FLARE_FACTOR = 0.25f
private const val GLOW_PULSE_DURATION_MILLIS = 1800
private const val GLOW_PULSE_MIN = 0.25f
private const val GLOW_PULSE_MAX = 0.45f
private const val GLOW_OUTER_SCALE = 0.5f
private const val GLOW_RADIUS_FACTOR = 0.55f
private val GLOW_INNER = Color(0xFF7986CB)
private val GLOW_OUTER = Color(0xFF3F51B5)

private const val AMBIENT_DRIFT_DURATION_MILLIS = 7000
private const val AMBIENT_ORBIT_REACH_X = 0.12f
private const val AMBIENT_ORBIT_REACH_Y = 0.08f
private val POOL_NEAR = Color(0xFF5C6BC0)
private val POOL_FAR = Color(0xFF7E57C2)
private const val POOL_NEAR_ALPHA = 0.28f
private const val POOL_FAR_ALPHA = 0.24f
private const val POOL_NEAR_X = 0.22f
private const val POOL_NEAR_Y = 0.20f
private const val POOL_FAR_X = 0.78f
private const val POOL_FAR_Y = 0.78f
private const val POOL_RADIUS_FACTOR = 0.75f

private val BACKDROP_NEAR = Color(0xFF14172B)
private val BACKDROP_MID = Color(0xFF090A12)

/** The mark sits a little above the true centre, and the glow is centred on the same line. */
private const val LOGO_VERTICAL_BIAS = 0.48f
private const val HALF = 0.5f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f

private const val VERSION_ALPHA = 0.75f
private val VERSION_TEXT_SIZE = 14.sp
private val VERSION_TRACKING = 0.8.sp
private val VERSION_END_PADDING = 32.dp
private val VERSION_BOTTOM_PADDING = 28.dp
private val TITLE_TRACKING = 2.sp
