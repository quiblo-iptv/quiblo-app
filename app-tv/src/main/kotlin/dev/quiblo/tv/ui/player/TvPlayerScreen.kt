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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import dev.quiblo.core.media.PlaybackError
import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.PlaybackStatus
import dev.quiblo.core.model.AspectRatioMode
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.videoScale
import dev.quiblo.feature.player.PlayerViewModel
import dev.quiblo.feature.player.TrackMenu
import dev.quiblo.feature.player.TrackMenuKind
import dev.quiblo.feature.player.messageRes
import dev.quiblo.feature.player.trackMenu
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.detail.DetailButton
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import dev.quiblo.feature.player.R as PlayerR

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
    var tracksVisible by remember { mutableStateOf(false) }
    var zapNotice: String? by remember { mutableStateOf(null) }

    // What there is to choose between, if anything. AC-PLAY-04 has required this since the
    // player was written and no screen has ever offered it (#023).
    val offLabel = stringResource(R.string.tv_player_subtitles_off)
    val trackMenu = remember(state.audioTracks, state.textTracks, offLabel) {
        trackMenu(state, offLabel)
    }

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

    // The controls time out; the menu does not.
    //
    // A list a viewer is reading is not the same thing as a bar telling them what is playing,
    // and having it vanish mid-choice would be its own defect. It closes on back or on a
    // choice, and on nothing else.
    LaunchedEffect(controlsVisible, tracksVisible, state.status) {
        if (controlsVisible && !tracksVisible && state.status == PlaybackStatus.PLAYING) {
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

    // Playback has failed and stopped trying. The status only becomes ERROR once the
    // controller has exhausted its retries, so this is the point where there is nothing
    // left to wait for.
    val hasFailed = state.status == PlaybackStatus.ERROR

    // Immersive.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    KeepScreenAwake(enabled = !hasFailed)

    // Back closes the controls first and leaves playback second, so a viewer who opened the
    // controls by accident does not lose the stream getting rid of them (AC-TV-03).
    BackHandler {
        when {
            tracksVisible -> tracksVisible = false
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
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
                    context = KeyContext(
                        // Nothing is playing, so nothing the transport keys mean applies —
                        // pausing a dead stream, seeking it, cycling its aspect ratio. Left
                        // unhandled so the same presses reach the Try again and Back buttons
                        // instead, which is the only thing a viewer can usefully do here.
                        hasFailed = hasFailed,
                        areControlsVisible = controlsVisible,
                        isSeekable = state.isSeekable,
                        // Only a live channel has anything to zap to. On a film, up and
                        // channel-up used to jump to whatever film happened to sit next in
                        // the category — which is not what either key means to a viewer.
                        canZap = request is TvPlaybackRequest.Live,
                    ),
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
                        // Only when there is a choice. Opening an empty panel would be the
                        // hollow-feature shape this project has deleted nine of.
                        openTracks = { if (!trackMenu.isEmpty) tracksVisible = true },
                    ),
                )
            },
    ) {
        VideoSurface(
            viewModel = viewModel,
            videoAspectRatio = state.videoAspectRatio,
            mode = aspectRatioMode,
            hasRenderedFirstFrame = state.hasRenderedFirstFrame,
        )

        PlayerOverlays(
            state = state,
            settings = settings,
            aspectRatioMode = aspectRatioMode,
            isLive = request is TvPlaybackRequest.Live,
            hasFailed = hasFailed,
            controlsVisible = controlsVisible,
            tracksVisible = tracksVisible,
            zapNotice = zapNotice,
            trackMenu = trackMenu,
            onRetry = viewModel::retry,
            onBack = onBack,
            onSelectTrack = { kind, trackId ->
                viewModel.selectTrack(kind, trackId)
                tracksVisible = false
            },
            onDismissTracks = { tracksVisible = false },
        )
    }
}

/**
 * Everything drawn over the video.
 *
 * Separated from [TvPlayerScreen] because that function had become the state, the lifecycle,
 * the key map *and* the layering, and only the last of those is about what is on screen. The
 * ordering here is the part worth reading in one place: the error is drawn over the controls
 * and the controls are not drawn over the error, because the controls describe keys that do
 * nothing once playback has failed and would sit on top of the only two that still work.
 */
@Composable
@Suppress("LongParameterList")
private fun PlayerOverlays(
    state: PlaybackState,
    settings: PlayerSettings,
    aspectRatioMode: AspectRatioMode,
    isLive: Boolean,
    hasFailed: Boolean,
    controlsVisible: Boolean,
    tracksVisible: Boolean,
    zapNotice: String?,
    trackMenu: TrackMenu,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSelectTrack: (TrackMenuKind, String?) -> Unit,
    onDismissTracks: () -> Unit,
) {
    if (state.status == PlaybackStatus.BUFFERING) {
        Buffering(retryAttempt = state.retryAttempt)
    }

    if (hasFailed) {
        PlaybackFailure(error = state.error, onRetry = onRetry, onBack = onBack)
    }

    zapNotice?.let { ZapNotice(name = it) }

    if (controlsVisible && !hasFailed) {
        Controls(
            hasTrackChoice = !trackMenu.isEmpty,
            title = state.item?.title.orEmpty(),
            isPlaying = state.isPlaying,
            isSeekable = state.isSeekable,
            // From the request, not from whether a duration has arrived yet. A film reports
            // no duration for its first moments, and the old test announced it as live for
            // exactly that long.
            isLive = isLive,
            positionMillis = state.positionMillis,
            durationMillis = state.durationMillis,
            skipSeconds = settings.seekInterval.seconds,
            aspectRatioMode = aspectRatioMode,
        )
    }

    if (tracksVisible && !hasFailed) {
        // Closed on a choice, and playback is never touched: AC-TV-06 says these controls
        // must not pause, and switching a track does not restart a stream.
        TvTrackMenu(menu = trackMenu, onSelect = onSelectTrack, onDismiss = onDismissTracks)
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
    /**
     * Opens the audio and subtitle list.
     *
     * The screen decides whether there is anything to open — a stream with one audio track
     * and no subtitles has no menu, and a control that opens an empty panel is worse than no
     * control. The key map only says which press means "show me the choices".
     */
    val openTracks: () -> Unit = {},
)

/**
 * What the player is doing, as far as the key map is concerned.
 *
 * Four facts rather than four parameters: the map has grown one fact per feature — seeking,
 * zapping, the error screen, and now the track menu — and a list of booleans at a call site
 * stops saying which is which long before it stops compiling.
 */
internal data class KeyContext(
    val isSeekable: Boolean,
    val canZap: Boolean,
    val hasFailed: Boolean = false,
    /** Down means two different things depending on this, so the map has to know it. */
    val areControlsVisible: Boolean = false,
)

/**
 * The whole remote-control vocabulary.
 *
 * Split in two below by what a key *is*: one group opens and closes things on top of the
 * video, the other drives playback. They were one `when` until the track menu made it long
 * enough that the two halves stopped being visible as halves.
 */
internal fun handleKey(key: Key, context: KeyContext, actions: KeyActions): Boolean {
    // A failed stream has no transport. Every key belongs to the error screen's buttons.
    if (context.hasFailed) return false

    return handleOverlayKey(key, context, actions) || handleTransportKey(key, context, actions)
}

/** Keys that show or hide something over the video, and change nothing about playback. */
private fun handleOverlayKey(key: Key, context: KeyContext, actions: KeyActions): Boolean =
    when (key) {
        // Down once brings the controls up (AC-TV-06); down again asks for the choices
        // behind them. Layering it on the same key rather than claiming a second one is what
        // keeps this reachable on the Haier's remote, which has very few keys to spare — the
        // same constraint that made Up double as the aspect key below.
        Key.DirectionDown -> {
            if (context.areControlsVisible) actions.openTracks() else actions.showControls()
            true
        }

        // Remotes that do have a subtitle key should use it. Nothing depends on this existing.
        Key.Captions -> {
            actions.showControls()
            actions.openTracks()
            true
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

        else -> false
    }

/** Keys that drive playback itself. */
private fun handleTransportKey(key: Key, context: KeyContext, actions: KeyActions): Boolean =
    when (key) {
        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
            actions.playPause()
            true
        }

        // Seeking is meaningless on a live stream, so the keys are left unhandled there rather
        // than silently doing nothing — the same rule the phone app applies to its skip buttons.
        Key.DirectionLeft, Key.MediaRewind -> if (context.isSeekable) {
            actions.skip(-1)
            actions.showControls()
            true
        } else {
            false
        }

        Key.DirectionRight, Key.MediaFastForward -> if (context.isSeekable) {
            actions.skip(1)
            actions.showControls()
            true
        } else {
            false
        }

        // Up and down the channel list, which is what a television remote's channel keys and
        // its D-pad up both mean to a viewer watching live.
        Key.DirectionUp, Key.ChannelUp -> if (context.canZap) {
            actions.zap(-1)
            true
        } else {
            actions.cycleAspect()
            true
        }

        Key.ChannelDown -> if (context.canZap) {
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
    /** False until a frame of *this* stream has been drawn. See [PlaybackState]. */
    hasRenderedFirstFrame: Boolean,
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

        /*
         * The shutter (#013).
         *
         * Media3 leaves the last decoded frame on the surface until the next one arrives, so
         * zapping showed the previous channel's final picture while the new one loaded — under
         * the new one's audio, which is what made it read as a fault rather than as a wait.
         * The buffering spinner never hid it, because a spinner draws over what is there
         * instead of replacing it.
         *
         * Opaque, and over the surface rather than over the whole screen: everything else the
         * player draws while waiting — the spinner, the channel name — belongs on top of this.
         */
        if (!hasRenderedFirstFrame) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.detachSurface() }
    }
}

/**
 * Holds the display on, for as long as there is something to watch.
 *
 * Nobody touches a television for two hours, so without the flag the display timeout
 * treats a viewer as an idle user and dims mid-film. It used to be held for the whole life
 * of the player screen, which meant a dead stream held the panel on at full brightness, on
 * a black frame, until somebody walked over to the remote. An error is not playback; the
 * television is allowed to sleep through one.
 */
@Composable
private fun KeepScreenAwake(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = (view.context as? android.app.Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/**
 * Waiting — and, when the controller is retrying, waiting for what.
 *
 * A bare spinner cannot distinguish a stream that is loading slowly from one that is
 * coming back from one that is already gone. The attempt count is the difference between
 * a viewer waiting and a viewer wondering.
 */
@Composable
private fun Buffering(retryAttempt: Int) {
    val isReconnecting = retryAttempt > 0
    val reconnecting = stringResource(PlayerR.string.player_reconnecting, retryAttempt)

    // TalkBack runs on Android TV too, and a spinner tells it nothing. Announced politely
    // so it does not talk over whatever the user just navigated to.
    val announcement = if (isReconnecting) reconnecting else stringResource(R.string.tv_a11y_buffering)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            if (isReconnecting) {
                Text(
                    text = reconnecting,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/**
 * The stream is gone, and this says so.
 *
 * The one place on this screen with focusable controls, and deliberately so: everywhere
 * else the remote drives playback directly, so a focus layer would only get in the way.
 * Here there is no playback to drive, and two choices worth offering — which is exactly
 * when buttons earn their place.
 *
 * Focus lands on Try again, so the common case is one press of OK. The wording is the
 * phone's, unchanged: there is one set of reasons a stream can fail and one set of words
 * for them ([messageRes]).
 */
@Composable
private fun PlaybackFailure(
    error: PlaybackError?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Over the last decoded frame, which is otherwise still sitting there behind
            // the message looking like something that might resume.
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(error.messageRes()),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            // Assertive rather than polite: playback has stopped and will not resume on
            // its own, so this is worth interrupting for.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Row(
            modifier = Modifier.padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailButton(
                label = stringResource(PlayerR.string.player_retry),
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryFocus),
                isPrimary = true,
            )
            DetailButton(
                label = stringResource(PlayerR.string.player_back),
                onClick = onBack,
            )
        }
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
    hasTrackChoice: Boolean,
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

            // Only where the press does something. The aspect hint above was once a readout
            // for a control that did not exist, and that is not repeated here.
            if (hasTrackChoice) {
                Hint(stringResource(R.string.tv_player_hint_tracks))
            }
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
