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

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import dev.quiblo.feature.player.SubtitleNotice
import dev.quiblo.feature.player.TrackMenu
import dev.quiblo.feature.player.TrackMenuActionKind
import dev.quiblo.feature.player.TrackMenuKind
import dev.quiblo.feature.player.messageRes
import dev.quiblo.feature.player.rememberSubtitleActions
import dev.quiblo.feature.player.rememberSubtitleAppearance
import dev.quiblo.feature.player.rememberSubtitleFilePicker
import dev.quiblo.feature.player.subtitleActionHandler
import dev.quiblo.feature.player.subtitleNoticeText
import dev.quiblo.feature.player.trackMenu
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.AmbientColours
import dev.quiblo.tv.ui.common.PLAYER_CROSSFADE_MILLIS
import dev.quiblo.tv.ui.common.ambientBackdrop
import dev.quiblo.tv.ui.common.ambientFrom
import dev.quiblo.tv.ui.detail.DetailButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.compose.koinViewModel
import kotlin.coroutines.resume
import dev.quiblo.feature.player.R as PlayerR

/**
 * Playback, driven by a remote.
 *
 * The engine and every setting come from `:core:media` unchanged. Only the controls differ,
 * and they differ completely: there is no touchscreen, so there is nothing to tap, drag or
 * lock.
 *
 * **Two ways in, not one.** The five keys a remote actually has still drive playback with
 * nothing on screen — that is the fastest way to pause something and it is not being taken
 * away. Everything past those five is a button now ([TvPlayerControls]), because the old
 * arrangement had run out of keys: subtitles were on a second press of Down and picture fit on
 * Up, on a film, where zapping happened not to be using it. The rule that keeps the two ways
 * agreeing is in [handleKey] — while the controls are on screen the D-pad moves focus and does
 * nothing else, and while they are not, it drives playback.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun TvPlayerScreen(
    request: TvPlaybackRequest,
    onBack: () -> Unit,
    onZap: (Int) -> Unit,
    /**
     * Move [Int] episodes along this series, which the caller does by replacing this screen.
     *
     * The player never navigates itself: it says which episode it wants and the shell decides
     * what that does to the back stack. Doing it here would have meant this screen owning a
     * stack it cannot see.
     */
    onStepEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = koinViewModel(key = "tv-player"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(false) }

    /**
     * Which section of the track panel is open, or null when it is shut.
     *
     * One value rather than a boolean beside a kind, because "open at nothing" and "shut at
     * Audio" are states the pair can express and this cannot.
     */
    var trackMenuAt: TrackMenuKind? by remember { mutableStateOf(null) }
    var zapNotice: String? by remember { mutableStateOf(null) }

    // Bumped on every press while the controls are up, purely to restart their timeout. See
    // the effect below for why it cannot be read off the key handler.
    var interactionTick by remember { mutableIntStateOf(0) }

    // Reset by `remember(request)` rather than by hand: a new episode is a new offer, and the
    // viewer saying no to one has nothing to say about the next.
    var nextEpisodeDismissed by remember(request) { mutableStateOf(false) }

    // What there is to choose between, if anything. AC-PLAY-04 has required this since the
    // player was written and no screen has ever offered it (#023).
    val offLabel = stringResource(R.string.tv_player_subtitles_off)
    val subtitleActions = rememberSubtitleActions(state)
    val subtitleStyle by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val appearance = rememberSubtitleAppearance(subtitleStyle)
    val trackMenu = remember(state.audioTracks, state.textTracks, offLabel, subtitleActions, appearance) {
        trackMenu(state, offLabel, subtitleActions, appearance)
    }

    // INC-F10. Many televisions ship without a document picker at all, so launching is
    // wrapped and the viewer is told rather than dropped into a crash.
    val subtitleNotice by viewModel.subtitleNotice.collectAsStateWithLifecycle()
    val pickSubtitleFile = rememberSubtitleFilePicker(
        onPicked = viewModel::attachSubtitleFile,
        onNoPicker = { viewModel.showSubtitleNotice(SubtitleNotice.NO_PICKER) },
    )

    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }

    LaunchedEffect(request) {
        viewModel.load(request)
        zapNotice = request.noticeTitle
    }

    val episode = request as? TvPlaybackRequest.Episode
    val isOfferingNextEpisode = shouldOfferNextEpisode(
        request = request,
        status = state.status,
        playingId = state.item?.id,
        isDismissed = nextEpisodeDismissed,
    )

    /*
     * Where the remote is, in every state this screen has.
     *
     * Focus has to live somewhere at all times or the panel goes dead, and this screen now has
     * four places it can be: the root, the controls, the track panel and the banner. The last
     * three request it for themselves; this puts it back on the root whenever none of them is
     * on screen, which is the case the old code only handled once, at first composition.
     */
    LaunchedEffect(controlsVisible, trackMenuAt, isOfferingNextEpisode) {
        when {
            isOfferingNextEpisode || trackMenuAt != null -> Unit
            controlsVisible -> runCatching { playPauseFocus.requestFocus() }
            else -> runCatching { rootFocus.requestFocus() }
        }
    }

    // The offer replaces the controls rather than sitting over them. Two focusable things
    // arriving at once is how a viewer ends up pressing OK on something they were not looking
    // at, and the episode has finished — there is nothing left for the transport to do.
    LaunchedEffect(isOfferingNextEpisode) {
        if (isOfferingNextEpisode) controlsVisible = false
    }

    /*
     * The controls time out; the track panel and the banner do not.
     *
     * A list a viewer is reading is not the same thing as a bar telling them what is playing,
     * and having it vanish mid-choice would be its own defect.
     *
     * [interactionTick] is what makes the timeout mean "since the last press" rather than
     * "since it opened". Now that the controls hold focus, most presses are consumed by a
     * button before this screen's key handler ever sees them, so the tick is counted in a
     * preview handler instead — see the modifier below.
     */
    LaunchedEffect(controlsVisible, trackMenuAt, interactionTick, state.status) {
        if (controlsVisible && trackMenuAt == null && state.status == PlaybackStatus.PLAYING) {
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

    // Back closes what is on top first and leaves playback last, so a viewer who opened the
    // controls by accident does not lose the stream getting rid of them (AC-TV-03).
    //
    // Leaving while the next episode is being offered cancels the offer on the way out. The
    // countdown is disposed with the screen either way; saying so here is what stops a future
    // reader hoisting that state somewhere it would survive.
    BackHandler {
        when {
            trackMenuAt != null -> trackMenuAt = null
            controlsVisible -> controlsVisible = false
            else -> {
                nextEpisodeDismissed = true
                onBack()
            }
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
            .focusRequester(rootFocus)
            .focusable()
            // Never consumes anything. It runs before the focused control gets the press,
            // which is the only place that sees every press: once the controls hold focus,
            // a button consumes OK and the arrows are eaten by focus traversal, so a handler
            // reading them on the way back up would restart the timeout on almost nothing.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && controlsVisible) interactionTick++
                false
            }
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
                        isOfferingNextEpisode = isOfferingNextEpisode,
                        isSeekable = state.isSeekable,
                        // Only a live channel has anything to zap to. On a film, up and
                        // channel-up used to jump to whatever film happened to sit next in
                        // the category — which is not what either key means to a viewer.
                        canZap = request is TvPlaybackRequest.Live,
                        canStepEpisode = episode != null,
                    ),
                    actions = KeyActions(
                        showControls = { controlsVisible = true },
                        playPause = {
                            viewModel.togglePlayPause()
                            controlsVisible = true
                        },
                        skip = { viewModel.skipBy(it) },
                        zap = onZap,
                        stepEpisode = onStepEpisode,
                        cycleAspect = {
                            viewModel.cycleAspectRatio()
                            controlsVisible = true
                        },
                        // Only when there is a choice. Opening an empty panel would be the
                        // hollow-feature shape this project has deleted nine of.
                        openTracks = { if (!trackMenu.isEmpty) trackMenuAt = TrackMenuKind.SUBTITLES },
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

        // Where cues land. Without it a selected subtitle track changes nothing on screen —
        // see the same composable's note on the phone.
        val subtitleController = remember { viewModel.controllerHandle() }
        AndroidView(
            factory = { context -> subtitleController.subtitleOutput(context) },
            modifier = Modifier.fillMaxSize(),
        )

        PlayerOverlays(
            state = state,
            settings = settings,
            request = request,
            aspectRatioMode = aspectRatioMode,
            hasFailed = hasFailed,
            controlsVisible = controlsVisible,
            trackMenuAt = trackMenuAt,
            zapNotice = zapNotice,
            trackMenu = trackMenu,
            isOfferingNextEpisode = isOfferingNextEpisode,
            playPauseFocus = playPauseFocus,
            controlActions = TvControlActions(
                playPause = {
                    viewModel.togglePlayPause()
                    controlsVisible = true
                },
                skip = viewModel::skipBy,
                nextEpisode = { onStepEpisode(1) },
                previousEpisode = { onStepEpisode(-1) },
                openAudio = { trackMenuAt = TrackMenuKind.AUDIO },
                openSubtitles = { trackMenuAt = TrackMenuKind.SUBTITLES },
                cycleAspect = viewModel::cycleAspectRatio,
            ),
            onRetry = viewModel::retry,
            onBack = onBack,
            onSelectTrack = { kind, trackId ->
                viewModel.selectTrack(kind, trackId)
                trackMenuAt = null
            },
            onDismissTracks = { trackMenuAt = null },
            onSubtitleAction = subtitleActionHandler(
                onPick = {
                    trackMenuAt = null
                    pickSubtitleFile()
                },
                onRemove = {
                    trackMenuAt = null
                    viewModel.detachSubtitleFile()
                },
            ),
            onPlayNextEpisode = { onStepEpisode(1) },
            onStopNextEpisode = { nextEpisodeDismissed = true },
            subtitleNotice = subtitleNotice,
        )
    }

    LaunchedEffect(subtitleNotice) {
        if (subtitleNotice != null) {
            delay(SUBTITLE_NOTICE_MILLIS)
            viewModel.showSubtitleNotice(null)
        }
    }
}

/**
 * Everything drawn over the video.
 *
 * Separated from [TvPlayerScreen] because that function had become the state, the lifecycle,
 * the key map *and* the layering, and only the last of those is about what is on screen. The
 * ordering here is the part worth reading in one place: the error is drawn over the controls
 * and the controls are not drawn over the error, because the controls describe actions that do
 * nothing once playback has failed and would sit on top of the only two that still work.
 */
@Composable
@Suppress("LongParameterList")
private fun PlayerOverlays(
    state: PlaybackState,
    settings: PlayerSettings,
    request: TvPlaybackRequest,
    aspectRatioMode: AspectRatioMode,
    hasFailed: Boolean,
    controlsVisible: Boolean,
    trackMenuAt: TrackMenuKind?,
    zapNotice: String?,
    trackMenu: TrackMenu,
    isOfferingNextEpisode: Boolean,
    playPauseFocus: FocusRequester,
    controlActions: TvControlActions,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSelectTrack: (TrackMenuKind, String?) -> Unit,
    onDismissTracks: () -> Unit,
    onSubtitleAction: (TrackMenuActionKind) -> Unit,
    onPlayNextEpisode: () -> Unit,
    onStopNextEpisode: () -> Unit,
    subtitleNotice: SubtitleNotice?,
) {
    if (state.status == PlaybackStatus.BUFFERING) {
        Buffering(retryAttempt = state.retryAttempt)
    }

    if (hasFailed) {
        PlaybackFailure(error = state.error, onRetry = onRetry, onBack = onBack)
    }

    zapNotice?.let { ZapNotice(name = it) }

    if (controlsVisible && !hasFailed) {
        TvPlayerControls(
            state = controlsState(state, settings, request, aspectRatioMode, trackMenu),
            actions = controlActions,
            playPauseFocus = playPauseFocus,
        )
    }

    if (trackMenuAt != null && !hasFailed) {
        // Closed on a choice, and playback is never touched: AC-TV-06 says these controls
        // must not pause, and switching a track does not restart a stream.
        TvTrackMenu(
            menu = trackMenu,
            openAt = trackMenuAt,
            onSelect = onSelectTrack,
            onAction = onSubtitleAction,
            onDismiss = onDismissTracks,
        )
    }

    TvNextEpisodeBanner(
        isVisible = isOfferingNextEpisode,
        delaySetting = settings.autoNextDelay,
        episodeLabel = nextEpisodeLabel(request),
        onPlayNext = onPlayNextEpisode,
        onStop = onStopNextEpisode,
    )

    subtitleNotice?.let { SubtitleNoticeBanner(it) }
}

/**
 * The controls' whole input, gathered in one place.
 *
 * Here rather than at the call site because it is the only thing that reads a playback state, a
 * settings value, a request and a menu together, and burying that in an argument list is what
 * made the previous version of this screen unreadable.
 */
@Composable
private fun controlsState(
    state: PlaybackState,
    settings: PlayerSettings,
    request: TvPlaybackRequest,
    aspectRatioMode: AspectRatioMode,
    trackMenu: TrackMenu,
): TvControlsState {
    val episode = request as? TvPlaybackRequest.Episode
    return TvControlsState(
        title = state.item?.title.orEmpty(),
        isPlaying = state.isPlaying,
        isSeekable = state.isSeekable,
        // From the request, not from whether a duration has arrived yet. A film reports no
        // duration for its first moments, and the old readout announced it as live for
        // exactly that long.
        isLive = request is TvPlaybackRequest.Live,
        positionMillis = state.positionMillis,
        durationMillis = state.durationMillis,
        seekInterval = settings.seekInterval,
        aspectRatioMode = aspectRatioMode,
        hasAudioChoice = trackMenu.sections.any { it.kind == TrackMenuKind.AUDIO },
        hasSubtitleChoice = trackMenu.sections.any { it.kind == TrackMenuKind.SUBTITLES },
        hasNextEpisode = episode?.hasNext == true,
        hasPreviousEpisode = episode?.hasPrevious == true,
    )
}

/** "S2 E4 — The Constant", or nothing when there is no next episode to name. */
@Composable
private fun nextEpisodeLabel(request: TvPlaybackRequest): String {
    val next = (request as? TvPlaybackRequest.Episode)?.steppedBy(1) ?: return ""
    val numbering = stringResource(
        R.string.tv_series_episode_number,
        next.seasonNumber,
        next.episodeNumber,
    )
    return "$numbering — ${next.episodeTitle}"
}

/**
 * What happened to the subtitle file the viewer picked (INC-F10).
 *
 * Bottom-centred and larger than the phone's, for the reason everything on this app is: it is
 * read from a sofa. Not a dialog — this app has none, and an acknowledgement that has to be
 * dismissed with a remote is worse than no acknowledgement at all.
 */
@Composable
private fun SubtitleNoticeBanner(notice: SubtitleNotice) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = subtitleNoticeText(notice),
            color = Color.White,
            fontSize = 20.sp,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
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
    /** Along the series, for the remotes that have the two keys for it. */
    val stepEpisode: (Int) -> Unit = {},
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
 * Facts rather than parameters: the map has grown one fact per feature — seeking, zapping, the
 * error screen, the track menu, and now the episode steps and the end-of-episode offer — and a
 * list of booleans at a call site stops saying which is which long before it stops compiling.
 */
internal data class KeyContext(
    val isSeekable: Boolean,
    val canZap: Boolean,
    val hasFailed: Boolean = false,
    /**
     * Whether the controls are on screen — which is the same question as "does the D-pad
     * belong to something else right now". See [handleKey].
     */
    val areControlsVisible: Boolean = false,
    val isOfferingNextEpisode: Boolean = false,
    /** Only a series has episodes to step between. A film and a channel have none. */
    val canStepEpisode: Boolean = false,
)

/**
 * The whole remote-control vocabulary.
 *
 * **Two states, and the D-pad means different things in each.** With nothing on screen the
 * arrows drive playback: left and right seek, up and down zap or reveal. With the controls up
 * they belong to the controls, and this returns false for every one of them so that Compose's
 * own focus traversal moves between the buttons — a key consumed here is a key focus never
 * sees, and the first version of these controls was unreachable for exactly that reason.
 *
 * The keys that are not arrows keep working in both states, because a remote's play, rewind
 * and channel keys mean one thing wherever the viewer is looking.
 */
internal fun handleKey(key: Key, context: KeyContext, actions: KeyActions): Boolean {
    // A failed stream has no transport. Every key belongs to the error screen's buttons.
    if (context.hasFailed) return false

    // Nor does a finished one. The banner's own two buttons are the only things worth
    // pressing, and they are focusable, so the whole remote is theirs.
    if (context.isOfferingNextEpisode) return false

    if (context.areControlsVisible && key.isDirection()) return false

    return handleOverlayKey(key, actions) || handleTransportKey(key, context, actions)
}

private fun Key.isDirection(): Boolean =
    this == Key.DirectionUp ||
        this == Key.DirectionDown ||
        this == Key.DirectionLeft ||
        this == Key.DirectionRight

/**
 * Keys that show or hide something over the video, and change nothing about playback.
 *
 * It used to need the context, to decide what a second press of Down meant. It does not any
 * more: the second press is focus traversal, and [handleKey] has already returned by the time
 * this is reached with the controls up.
 */
private fun handleOverlayKey(key: Key, actions: KeyActions): Boolean =
    when (key) {
        // Down brings the controls up (AC-TV-06), and once they are up this branch is not
        // reached at all — the guard in [handleKey] hands the arrows to focus, so a second
        // press steps from the transport row into the options row underneath it.
        Key.DirectionDown -> {
            actions.showControls()
            true
        }

        // Remotes that do have a subtitle key should use it. Nothing depends on this existing.
        Key.Captions -> {
            actions.showControls()
            actions.openTracks()
            true
        }

        // Aspect ratio. Menu is the natural key and many remotes have it; the Haier's does
        // not, so up doubles as the aspect key on a film, where it is otherwise dead because
        // zapping is live-only. There is a button for it now as well, which is what this
        // pairing was always a substitute for.
        Key.Menu, Key.Info -> {
            actions.cycleAspect()
            true
        }

        else -> false
    }

/** Keys that drive playback itself. */
@Suppress("CyclomaticComplexMethod")
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

        // The two keys that mean this on a remote that has them. Unlike the arrows they are
        // unambiguous, so they work whether or not the controls are on screen.
        Key.MediaNext -> if (context.canStepEpisode) {
            actions.stepEpisode(1)
            true
        } else {
            false
        }

        Key.MediaPrevious -> if (context.canStepEpisode) {
            actions.stepEpisode(-1)
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
    val ambientEnabled by viewModel.ambientPlayer.collectAsStateWithLifecycle()

    /*
     * Ambient light from the picture itself.
     *
     * A film is 2.35:1 and a panel is 16:9, so a film plays inside two black bars for its whole
     * length — and a channel that broadcasts in 4:3 plays inside two more. Those bars are the
     * screen, and they were dead black. This lights them with the colours of the frame between
     * them, which is what YouTube's ambient mode does and for the same reason: the bars stop
     * being the edge of a small picture and start being the room the picture is in.
     *
     * It is drawn *behind* the surface, and it works because the surface is scaled by a graphics
     * layer rather than letterboxed inside itself — the view genuinely occupies less of the box
     * than the box has, so what is painted underneath shows around it. Where the video fills the
     * screen exactly there are no bars, nothing shows, and none of this costs anything.
     *
     * **On by default and switchable off in Settings.** It is a taste, and one with a real cost
     * on the other side: switched off the surface is never sampled, rather than sampled and the
     * answer thrown away.
     */
    var ambient by remember { mutableStateOf(AmbientColours.None) }
    var surface by remember { mutableStateOf<SurfaceView?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .ambientBackdrop(ambient, crossfadeMillis = PLAYER_CROSSFADE_MILLIS),
    ) {
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
            factory = { context ->
                SurfaceView(context).also {
                    controller.attachSurface(it)
                    surface = it
                }
            },
        )

        /*
         * A frame every 400ms, at 32x18.
         *
         * Both numbers are the point. A frame that small costs nothing to copy and nothing to
         * read, and it is already the blur — sampling six colours out of thirty-two pixels cannot
         * pick up a detail, only a cast. And 400ms with a 300ms crossfade behind it is close
         * enough to the picture to read as the picture's own light, where the 1500ms and 700ms
         * this replaces could put the light a full two seconds behind the frame it came from.
         *
         * Stopped whenever there is no first frame, so nothing is sampled off a dead surface, and
         * never started when the setting is off — a feature switched off does not do its work and
         * discard the answer.
         */
        LaunchedEffect(hasRenderedFirstFrame, surface, ambientEnabled) {
            val view = surface ?: return@LaunchedEffect
            if (!hasRenderedFirstFrame || !ambientEnabled) {
                ambient = AmbientColours.None
                return@LaunchedEffect
            }

            while (isActive) {
                ambient = sampleAmbient(view) ?: ambient
                delay(AMBIENT_SAMPLE_MILLIS)
            }
        }

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
    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }

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
 * One frame off the video surface, as colours, or null if it could not be read.
 *
 * `PixelCopy` is the only way to read a `SurfaceView` — its content is a separate layer that
 * the view hierarchy never draws, so every ordinary capture route returns an empty rectangle.
 * It is also allowed to fail for perfectly normal reasons: the surface is gone, the stream is
 * between items, the decoder is secure. **Every one of those returns null and keeps the light
 * that is already on screen**, because the alternative is the room going dark at a cut.
 */
private suspend fun sampleAmbient(view: SurfaceView): AmbientColours? =
    suspendCancellableCoroutine { continuation ->
        if (!view.holder.surface.isValid) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val frame = Bitmap.createBitmap(AMBIENT_WIDTH, AMBIENT_HEIGHT, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(view, frame, { result ->
                val colours = if (result == PixelCopy.SUCCESS) ambientFrom(frame) else null
                frame.recycle()
                if (continuation.isActive) continuation.resume(colours)
            }, Handler(Looper.getMainLooper()))
        }.onFailure {
            frame.recycle()
            if (continuation.isActive) continuation.resume(null)
        }
    }

/** Small enough to be free, and small enough to be the blur. See the caller. */
private const val AMBIENT_WIDTH = 32
private const val AMBIENT_HEIGHT = 18

/**
 * How often the surface is read.
 *
 * **Four times more often than it was, and the previous number was the complaint.** At 1500ms,
 * plus a 700ms crossfade behind it, the light could be a full two seconds behind the frame it was
 * supposedly taken from — close enough to be a feature, far enough to read as one thing following
 * another rather than as one thing. A `PixelCopy` of 32×18 pixels is a trivial copy and the
 * sampler reads six of them, so the cost of asking this often is the call itself, which is
 * nothing next to decoding the frame it copies.
 *
 * Still slow enough that a scene drifts rather than flickers: the crossfade is the same length
 * again, so nothing snaps.
 */
private const val AMBIENT_SAMPLE_MILLIS = 400L

/**
 * Longer than it was, because the controls are now something a viewer navigates rather than
 * something they read. The count restarts on every press, so this is time spent deciding, not
 * time spent using them.
 */
private const val CONTROLS_TIMEOUT_MILLIS = 6_000L
private const val ZAP_NOTICE_MILLIS = 2_500L

/** Longer than the zap notice: this one is a sentence, and it is read from across a room. */
private const val SUBTITLE_NOTICE_MILLIS = 5_000L
