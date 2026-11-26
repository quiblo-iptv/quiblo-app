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

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * The launch screen: the mark draws itself, lands on one hit, and the camera dives through its
 * screen into the app.
 *
 * **One composable for both frontends.** The phone and the television want different sizes and
 * a different window of time, which is parameters; they were once a file each and had drifted.
 *
 * The choreography, in seconds from launch:
 *
 * - **0.10–0.56 the ring draws** from the tail, clockwise, while the sting builds.
 * - **0.62 the hit.** The tail snaps in, the mark gives a small spring, and the light inside the
 *   screen flashes and settles to a glow that breathes.
 * - **0.70 the name** wipes in under it.
 * - **The hold**, for as long as the app behind is not ready — never shorter than
 *   [minDurationMillis] in all, never longer than [durationMillis].
 * - **The dive**, the last [DIVE_MILLIS]: the camera pushes into the screen, the light floods,
 *   and the app shows through — the app itself, composed underneath by [QuibloSplashHost], not a
 *   picture of it.
 *
 * [isReady] and [minDurationMillis] make the seconds the loading rather than a delay in front of
 * it (`030` #5): a warm start leaves at the floor, a cold one holds on the glowing mark until
 * the catalogue is warm, and a database that will not answer is not a locked television.
 *
 * Every frame is drawn from one clock, read only in draw and layer blocks, so the animation
 * recomposes nothing.
 */
@Composable
@Suppress("LongParameterList")
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
     * Defaults to true: the splash runs for [durationMillis] and nothing waits on anything.
     */
    isReady: Boolean = true,
    /** The floor of the window. Defaults to [durationMillis], which is a fixed-length splash. */
    minDurationMillis: Long = durationMillis,
    insetForSystemBars: Boolean = true,
    playSound: Boolean = true,
    /** The mark has landed: compose what the dive will fly into, while there is time. */
    onPrepareReveal: () -> Unit = {},
    /** The dive has begun. */
    onRevealStart: () -> Unit = {},
    onSplashComplete: () -> Unit = {},
) {
    val sting = rememberSting(playSound)
    val clock = rememberSplashClock()

    /** When, on [clock], the dive began. Null until it has. */
    var diveAt by remember { mutableStateOf<Float?>(null) }

    // Held in state rather than read from the parameters: the effect below is not restarted when
    // they change, and a captured `isReady` would be the value from the frame the splash started.
    val ready by rememberUpdatedState(isReady)
    val prepare by rememberUpdatedState(onPrepareReveal)
    val revealStart by rememberUpdatedState(onRevealStart)
    val complete by rememberUpdatedState(onSplashComplete)

    LaunchedEffect(durationMillis, minDurationMillis) {
        // The dive is the last stretch, always, so both bounds are measured on the time before it.
        val longestRest = (durationMillis - DIVE_MILLIS).coerceAtLeast(0L)
        val shortestRest = (minDurationMillis - DIVE_MILLIS).coerceIn(0L, longestRest)
        val prepareAt = PREPARE_AT_MILLIS.coerceAtMost(shortestRest)

        delay(prepareAt)
        prepare()
        delay(shortestRest - prepareAt)
        // Waits for the app behind to be worth showing, and gives up at the cap.
        withTimeoutOrNull(longestRest - shortestRest) {
            snapshotFlow { ready }.first { it }
        }

        diveAt = clock.value
        revealStart()
        sting.dive()
        delay(DIVE_MILLIS)
        complete()
    }

    val geometry = remember { MarkGeometry() }
    val cover = remember { coverAround(geometry.counter) }
    val unitPx = with(LocalDensity.current) { logoSize.toPx() } / MarkGeometry.GRID
    var origin by remember { mutableStateOf(Offset.Zero) }
    var markCentre by remember { mutableStateOf(Offset.Unspecified) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        // The room, with the screen cut out of it, and the light in the screen. Ink everywhere
        // until the mark has a place, so the first frame is never the app showing through.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = SplashFrame.at(clock.value, diveAt)
            if (!markCentre.isSpecified) {
                drawRect(QuibloColours.Ink)
                return@Canvas
            }
            onTheMark(markCentre, unitPx * frame.scale) {
                drawPath(cover, QuibloColours.Ink)
                drawPath(geometry.counter, QuibloColours.Ink, alpha = frame.counterInk)
                with(geometry) { drawLight(frame.light) }
            }
        }

        // The mark, in its own layer: the cut beside the tail clears what is beneath it.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            if (!markCentre.isSpecified) return@Canvas
            val frame = SplashFrame.at(clock.value, diveAt)
            onTheMark(markCentre, unitPx * frame.scale) {
                with(geometry) { drawStrokes(QuibloColours.Paper, ring = frame.ring, tail = frame.tail) }
            }
        }

        val insets = if (insetForSystemBars) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .then(insets),
        ) {
            // Where the mark goes. The canvases above draw it here, so the lockup centres as one.
            Spacer(
                modifier = Modifier
                    .size(logoSize)
                    .onGloballyPositioned { markCentre = it.boundsInRoot().center - origin },
            )
            Spacer(modifier = Modifier.height(logoTitleSpacing))
            QuibloWordmark(
                height = with(LocalDensity.current) { titleSize.toDp() },
                modifier = Modifier.wipedIn(clock) { diveAt },
            )
        }

        Text(
            text = if (versionName.startsWith("v")) versionName else "v$versionName",
            color = QuibloColours.Paper.copy(alpha = VERSION_ALPHA),
            fontSize = VERSION_TEXT_SIZE,
            fontWeight = FontWeight.Medium,
            letterSpacing = VERSION_TRACKING,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(insets)
                .padding(end = VERSION_END_PADDING, bottom = VERSION_BOTTOM_PADDING)
                .graphicsLayer { alpha = SplashFrame.at(clock.value, diveAt).chrome }
                .testTag(SPLASH_VERSION_TAG),
        )
    }
}

/**
 * The app, with the splash over it until the splash has flown into it.
 *
 * **The dive needs something to fly into**, and a crossfade from one screen to the other has
 * nothing behind the splash at all. This composes [content] underneath once the mark has landed
 * — late enough that its first frame does not land on the hit, early enough that it is laid out
 * before the dive — and lets it settle from slightly too close as the camera arrives. The remote
 * is held while the splash is up, so a press cannot move focus in an app nobody can see yet.
 *
 * @param splash the splash, handed the two calls it should make; see [SplashReveal].
 */
@Composable
fun QuibloSplashHost(
    showSplash: Boolean,
    modifier: Modifier = Modifier,
    splash: @Composable (SplashReveal) -> Unit,
    content: @Composable () -> Unit,
) {
    var composed by remember { mutableStateOf(!showSplash) }
    val landing = remember { Animatable(if (showSplash) LAND_FROM_SCALE else 1f) }
    val scope = rememberCoroutineScope()
    val reveal = remember {
        SplashReveal(
            onPrepare = { composed = true },
            onStart = {
                composed = true
                scope.launch { landing.animateTo(1f, tween(LAND_MILLIS, easing = LAND_EASING)) }
            },
        )
    }

    // A splash taken away without a dive — a test, a recreated activity — leaves the app square.
    LaunchedEffect(showSplash) {
        if (!showSplash && !landing.isRunning) landing.snapTo(1f)
    }

    Box(modifier = modifier.fillMaxSize().onPreviewKeyEvent { showSplash }) {
        if (composed || !showSplash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = landing.value
                        scaleY = landing.value
                    },
            ) { content() }
        }
        if (showSplash) splash(reveal)
    }
}

/** What a splash tells [QuibloSplashHost]: compose the app now, and the dive has begun. */
class SplashReveal(val onPrepare: () -> Unit, val onStart: () -> Unit)

/** Where the version tag is, for the tests that check it is on screen. */
const val SPLASH_VERSION_TAG = "splash_version_text"

/**
 * Everything one frame draws, as a function of time — so the whole choreography can be read in
 * one place, and checked without a screen.
 */
internal data class SplashFrame(
    /** How much of the ring is drawn. */
    val ring: Float,
    /** How much of the tail. */
    val tail: Float,
    /** The mark's size: the approach, the spring on the hit, then the dive. */
    val scale: Float,
    /** The light inside the screen. */
    val light: Float,
    /** How much of the screen is still dark. It clears in the dive, showing the app behind. */
    val counterInk: Float,
    /** How far the name has wiped in. */
    val word: Float,
    /** The name and the version: there until the dive, gone in its first moments. */
    val chrome: Float,
) {
    companion object {
        fun at(seconds: Float, diveAt: Float?): SplashFrame {
            val t = seconds
            val ring = easeInOut(span(t, DRAW_START, DRAW_END))
            val tail = easeOut(span(t, TAIL_START, HIT))
            val sinceHit = t - HIT

            var scale = APPROACH_FROM + (1f - APPROACH_FROM) * easeOut(span(t, 0f, HIT))
            var light = 0f
            if (sinceHit >= 0f) {
                scale = 1f + SPRING_SIZE * exp(-sinceHit * SPRING_DAMPING) * cos(sinceHit * SPRING_RATE)
                light = LIGHT_REST + LIGHT_FLASH * exp(-sinceHit * LIGHT_DECAY) +
                    LIGHT_BREATH * sin(sinceHit * LIGHT_BREATH_RATE)
            }

            var counterInk = 1f
            var chrome = 1f
            if (diveAt != null) {
                val d = span(t, diveAt, diveAt + DIVE_SECONDS)
                scale *= exp(ln(DIVE_ZOOM) * easeIn(d))
                val flood = 1f - span(d, FLOOD_FROM, FLOOD_TO)
                light = max(light, flood) * (1f - span(d, LIGHT_OUT_FROM, LIGHT_OUT_TO))
                counterInk = 1f - easeInOut(span(d, CLEAR_FROM, CLEAR_TO))
                chrome = 1f - span(t, diveAt, diveAt + CHROME_OUT_SECONDS)
            }
            val word = easeOut(span(t, WORD_START, WORD_END))
            return SplashFrame(ring, tail, scale, light.coerceIn(0f, 1f), counterInk, word, chrome)
        }

        private fun span(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
        private fun easeIn(x: Float) = x * x * x
        private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
        private fun easeInOut(x: Float) =
            if (x < HALF) CUBIC_IN_OUT * x * x * x else 1f - CUBIC_IN_OUT * (1f - x) * (1f - x) * (1f - x)

        private const val DRAW_START = 0.10f
        private const val DRAW_END = 0.56f
        private const val TAIL_START = 0.46f
        private const val HIT = 0.62f
        private const val WORD_START = 0.70f
        private const val WORD_END = 1.08f
        private const val DIVE_SECONDS = DIVE_MILLIS / 1000f
        private const val CHROME_OUT_SECONDS = 0.18f

        private const val APPROACH_FROM = 0.94f
        private const val SPRING_SIZE = 0.045f
        private const val SPRING_DAMPING = 7f
        private const val SPRING_RATE = 16f

        private const val LIGHT_REST = 0.34f
        private const val LIGHT_FLASH = 0.62f
        private const val LIGHT_DECAY = 5f
        private const val LIGHT_BREATH = 0.05f
        private const val LIGHT_BREATH_RATE = 4.2f

        /** Ninety times: the screen's corners pass the edges of any panel well before the end. */
        private const val DIVE_ZOOM = 90f
        private const val FLOOD_FROM = 0.35f
        private const val FLOOD_TO = 0.85f
        private const val LIGHT_OUT_FROM = 0.6f
        private const val LIGHT_OUT_TO = 0.9f
        private const val CLEAR_FROM = 0.18f
        private const val CLEAR_TO = 0.62f

        private const val HALF = 0.5f
        private const val CUBIC_IN_OUT = 4f
    }
}

/** Seconds since the splash's first frame. Read it in draw and layer blocks only. */
@Composable
private fun rememberSplashClock(): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        // The infinite-animation frame, so a test's clock is not held open by a loop that never
        // ends. On a device it is an ordinary frame.
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { seconds.floatValue = (it - start) / MILLIS_PER_SECOND }
        }
    }
    return seconds
}

/** Draws [block] in the mark's 100-unit grid, its centre on [centre], [scale] pixels to a unit. */
private inline fun DrawScope.onTheMark(centre: Offset, scale: Float, crossinline block: DrawScope.() -> Unit) {
    withTransform({
        translate(centre.x, centre.y)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-MarkGeometry.CENTRE, -MarkGeometry.CENTRE)
    }) { block() }
}

/** The room as a shape: everything but the screen, so the dive can see through it. */
private fun coverAround(counter: Path): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    addRect(Rect(-COVER_REACH, -COVER_REACH, MarkGeometry.GRID + COVER_REACH, MarkGeometry.GRID + COVER_REACH))
    addPath(counter)
}

/**
 * The name, wiped in from the left with a soft edge and a small rise, then faded as the dive
 * begins. A layer, so the wipe can mask it, and read from the clock in draw only.
 */
private fun Modifier.wipedIn(clock: State<Float>, diveAt: () -> Float?): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        val frame = SplashFrame.at(clock.value, diveAt())
        alpha = frame.chrome
        translationY = (1f - frame.word) * size.height * WORD_RISE
    }
    .drawWithContent {
        drawContent()
        val frame = SplashFrame.at(clock.value, diveAt())
        val edge = frame.word * (1f + WIPE_SOFTNESS) * size.width
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black,
                1f to Color.Transparent,
                startX = edge - WIPE_SOFTNESS * size.width,
                endX = edge,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * The sting, in two clips: the build and the hit at launch, the whoosh and the landing at the
 * dive. Two because the hold between them is as long as the catalogue takes, and one file would
 * play its dive on a fixed beat whether the picture dived or not.
 *
 * **Released on dispose and nowhere else.** One owner, one release.
 */
@Composable
private fun rememberSting(enabled: Boolean): Sting {
    val context = LocalContext.current
    val sting = remember { Sting() }
    DisposableEffect(enabled) {
        if (enabled) sting.open(context)
        onDispose { sting.release() }
    }
    return sting
}

private class Sting {
    private var intro: MediaPlayer? = null
    private var dive: MediaPlayer? = null

    fun open(context: Context) {
        // Null when the device will not give us an audio track at all, which a television box
        // with nothing plugged into it does.
        intro = runCatching { MediaPlayer.create(context, R.raw.splash_intro) }.getOrNull()
        dive = runCatching { MediaPlayer.create(context, R.raw.splash_dive) }.getOrNull()
        runCatching { intro?.start() }
    }

    fun dive() {
        runCatching { dive?.start() }
    }

    fun release() {
        runCatching { intro?.release() }
        runCatching { dive?.release() }
        intro = null
        dive = null
    }
}

/** The phone's sizes. The television passes its own — see `TvSplashScreen`. */
private val LOGO_SIZE = 120.dp
private val TITLE_SIZE = 44.sp
private val LOGO_TITLE_SPACING = 2.dp

/** How long the whole thing lasts on a phone: draw, hit, the name, a breath, and the dive. */
private const val DEFAULT_SPLASH_DURATION_MILLIS = 2300L

/** The dive: the last stretch of the splash, always. */
private const val DIVE_MILLIS = 700L

/** After the name is in, and well before any dive: the app composes under the mark here. */
private const val PREPARE_AT_MILLIS = 1100L

/** The app starts this much too close and settles as the camera arrives. */
private const val LAND_FROM_SCALE = 1.14f
private const val LAND_MILLIS = 1100
private val LAND_EASING = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Far enough out, in mark units, that the room covers any screen before the dive. */
private const val COVER_REACH = 2000f

private const val WORD_RISE = 0.28f
private const val WIPE_SOFTNESS = 0.35f
private const val MILLIS_PER_SECOND = 1000f

private const val VERSION_ALPHA = 0.6f
private val VERSION_TEXT_SIZE = 14.sp
private val VERSION_TRACKING = 0.8.sp
private val VERSION_END_PADDING = 32.dp
private val VERSION_BOTTOM_PADDING = 28.dp
