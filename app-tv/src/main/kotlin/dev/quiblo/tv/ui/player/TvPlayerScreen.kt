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

package dev.quiblo.tv.ui.player

import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.media.PlaybackStatus
import dev.quiblo.core.model.AspectRatioMode
import dev.quiblo.core.model.videoScale
import dev.quiblo.feature.player.PlayerViewModel
import dev.quiblo.tv.R
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * Playback, driven by a remote.
 *
 * The engine and every setting come from `:core:media` unchanged. Only the controls differ,
 * and they differ completely: there is no touchscreen, so there is nothing to tap, drag or
 * lock. What a phone does with gestures, a television does with five keys.
 */
@Composable
fun TvPlayerScreen(
    request: TvPlaybackRequest,
    onBack: () -> Unit,
    onZap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = koinViewModel(key = "tv-player"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(false) }
    var zapNotice: String? by remember { mutableStateOf(null) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(request) {
        viewModel.load(request)
        zapNotice = request.noticeTitle
    }

    // The surface has to hold focus or the D-pad reaches nothing at all. Requested once the
    // screen is composed rather than left to whatever the framework picks.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(controlsVisible, state.status) {
        if (controlsVisible && state.status == PlaybackStatus.PLAYING) {
            delay(CONTROLS_TIMEOUT_MILLIS)
            controlsVisible = false
        }
    }

    LaunchedEffect(zapNotice) {
        if (zapNotice != null) {
            delay(ZAP_NOTICE_MILLIS)
            zapNotice = null
        }
    }

    // Immersive, and awake. Nobody touches a television for two hours, so without this the
    // display timeout treats a viewer as an idle user and dims mid-film.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Back closes the controls first and leaves playback second, so a viewer who opened the
    // controls by accident does not lose the stream getting rid of them (AC-TV-03).
    BackHandler {
        if (controlsVisible) controlsVisible = false else onBack()
    }

    /*
     * Tell the player it is finished — on the way out, and on the way to the background.
     *
     * Neither happened before, and one omission caused three separate faults. This
     * ViewModel is activity-scoped, so leaving the player composition never cleared it:
     * `onCleared` did not run, the controller was never released, and audio kept playing
     * behind the catalogue. The same call is what persists the resume point, so nothing was
     * ever written either — which is why films offered no Resume and the continue-watching
     * row stayed empty however much had been watched.
     *
     * `onStopped` both pauses and saves, which is exactly the pair wanted here. The phone
     * has had this observer since AC-PLAY-09; the television simply never got one.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Leaving the screen is leaving playback. Without this, backing out to the
            // catalogue left the stream running and unreachable.
            viewModel.onStopped()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                handleKey(
                    key = event.key,
                    isSeekable = state.isSeekable,
                    // Only a live channel has anything to zap to. On a film, up and
                    // channel-up used to jump to whatever film happened to sit next in the
                    // category — which is not what either key means to a viewer.
                    canZap = request is TvPlaybackRequest.Live,
                    actions = KeyActions(
                        showControls = { controlsVisible = true },
                        playPause = {
                            viewModel.togglePlayPause()
                            controlsVisible = true
                        },
                        skip = { viewModel.skipBy(it) },
                        zap = onZap,
                        cycleAspect = {
                            viewModel.cycleAspectRatio()
                            controlsVisible = true
                        },
                    ),
                )
            },
    ) {
        VideoSurface(
            viewModel = viewModel,
            videoAspectRatio = state.videoAspectRatio,
            mode = aspectRatioMode,
        )

        if (state.status == PlaybackStatus.BUFFERING) {
            // TalkBack runs on Android TV too, and a spinner tells it nothing. Announced
            // politely so it does not talk over whatever the user just navigated to.
            val buffering = stringResource(R.string.tv_a11y_buffering)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = buffering
                    },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        zapNotice?.let { ZapNotice(name = it) }

        if (controlsVisible) {
            Controls(
                title = state.item?.title.orEmpty(),
                isPlaying = state.isPlaying,
                isSeekable = state.isSeekable,
                // From the request, not from whether a duration has arrived yet. A film
                // reports no duration for its first moments, and the old test announced it
                // as live for exactly that long.
                isLive = request is TvPlaybackRequest.Live,
                positionMillis = state.positionMillis,
                durationMillis = state.durationMillis,
                skipSeconds = settings.seekInterval.seconds,
                aspectRatioMode = aspectRatioMode,
            )
        }
    }
}

/**
 * Starts whatever this request describes.
 *
 * Each case loads differently, and that is the whole of bug #009. The engine already knew
 * how to play a film and an episode — [PlayerViewModel.load] has taken a custom URL, a
 * start position and an episode's numbering since the phone app needed them. The television
 * simply never passed any of it, so every request became "play this channel's URL": a film
 * lost its resume point, and a series was asked to play a URL that plays nothing.
 */
private fun PlayerViewModel.load(request: TvPlaybackRequest) = when (request) {
    is TvPlaybackRequest.Live -> load(request.channel.id)

    // A film resumes on its own when given no position — `load` reads the stored one for
    // anything that is not live. An explicit zero is what makes "start from the beginning"
    // a different action rather than the same one worded twice.
    is TvPlaybackRequest.Film -> load(
        channelId = request.channel.id,
        startPositionMillis = request.startPositionMillis,
    )

    is TvPlaybackRequest.Episode -> load(
        channelId = request.channel.id,
        customUrl = request.streamUrl,
        customTitle = request.episodeTitle,
        startPositionMillis = request.startPositionMillis,
        seasonNumber = request.seasonNumber,
        episodeNumber = request.episodeNumber,
    )
}

/**
 * The whole remote-control vocabulary, in one place.
 *
 * Written as a plain function rather than inline so the mapping is legible and testable:
 * on a television the key map *is* the interface, and burying it in a modifier makes the
 * one thing a reviewer needs to check the hardest thing to find.
 */
internal data class KeyActions(
    val showControls: () -> Unit,
    val playPause: () -> Unit,
    val skip: (Int) -> Unit,
    val zap: (Int) -> Unit,
    val cycleAspect: () -> Unit,
)

internal fun handleKey(
    key: Key,
    isSeekable: Boolean,
    canZap: Boolean,
    actions: KeyActions,
): Boolean = when (key) {
    Key.DirectionDown -> {
        actions.showControls()
        true
    }

    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
        actions.playPause()
        true
    }

    // Seeking is meaningless on a live stream, so the keys are left unhandled there rather
    // than silently doing nothing — the same rule the phone app applies to its skip buttons.
    Key.DirectionLeft, Key.MediaRewind -> if (isSeekable) {
        actions.skip(-1)
        actions.showControls()
        true
    } else {
        false
    }

    Key.DirectionRight, Key.MediaFastForward -> if (isSeekable) {
        actions.skip(1)
        actions.showControls()
        true
    } else {
        false
    }

    // Aspect ratio. The controls have always *announced* the current mode, and until now
    // nothing could change it — a readout for a control that did not exist.
    //
    // Menu is the natural key and many remotes have it; the Haier's does not, so up doubles
    // as the aspect key on a film, where it is otherwise dead because zapping is live-only.
    // Every remote therefore has a way to reach it on both kinds of content.
    Key.Menu, Key.Info -> {
        actions.cycleAspect()
        true
    }

    // Up and down the channel list, which is what a television remote's channel keys and
    // its D-pad up both mean to a viewer watching live.
    Key.DirectionUp, Key.ChannelUp -> if (canZap) {
        actions.zap(-1)
        true
    } else {
        actions.cycleAspect()
        true
    }

    Key.ChannelDown -> if (canZap) {
        actions.zap(1)
        true
    } else {
        false
    }

    else -> false
}

@Composable
private fun VideoSurface(
    viewModel: PlayerViewModel,
    videoAspectRatio: Float?,
    mode: AspectRatioMode,
) {
    val controller = remember { viewModel.controllerHandle() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
        val scale = remember(videoAspectRatio, mode, maxWidth, maxHeight) {
            videoScale(
                videoAspectRatio = videoAspectRatio,
                containerAspectRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else 1f,
                mode = mode,
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.first
                    scaleY = scale.second
                },
            factory = { context -> SurfaceView(context).also(controller::attachSurface) },
        )
    }

    DisposableEffect(Unit) {
        onDispose { controller.detachSurface() }
    }
}

/** What you just zapped to, shown briefly. Without it a channel change is unannounced. */
@Composable
private fun ZapNotice(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/**
 * A readout, not a control panel.
 *
 * Nothing here is focusable. The remote already drives playback directly, so focusable
 * buttons would add a layer of navigation between the viewer and an action they can
 * already perform with one press.
 */
@Composable
@Suppress("LongParameterList")
private fun Controls(
    title: String,
    isPlaying: Boolean,
    isSeekable: Boolean,
    isLive: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    skipSeconds: Int,
    aspectRatioMode: AspectRatioMode,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(text = title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)

        if (isSeekable && durationMillis > 0L) {
            val progress = (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(Color.White, RoundedCornerShape(2.dp)),
                )
            }
            Text(
                text = "${positionMillis.asClock()} / ${durationMillis.asClock()}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (isLive) {
            Text(
                text = stringResource(R.string.tv_player_live),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Hint(stringResource(if (isPlaying) R.string.tv_player_hint_pause else R.string.tv_player_hint_play))
            if (isSeekable) {
                Hint(stringResource(R.string.tv_player_hint_seek, skipSeconds))
            }
            // Only offered where the key does something. Advertising the channel keys on a
            // film was telling the viewer about a control that had just been taken away.
            if (isLive) {
                Hint(stringResource(R.string.tv_player_hint_zap))
            }
            Hint(stringResource(R.string.tv_player_hint_aspect, aspectRatioMode.name))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private const val CONTROLS_TIMEOUT_MILLIS = 4_000L
private const val ZAP_NOTICE_MILLIS = 2_500L
private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

private fun Long.asClock(): String {
    val total = this / MILLIS_PER_SECOND
    val hours = total / SECONDS_PER_HOUR
    val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
