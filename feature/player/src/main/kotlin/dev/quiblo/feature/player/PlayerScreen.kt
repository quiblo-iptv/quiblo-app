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

package dev.quiblo.feature.player

import android.view.SurfaceView
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.PlaybackStatus
import dev.quiblo.core.model.AspectRatioMode
import dev.quiblo.core.model.SeekInterval
import dev.quiblo.core.model.videoScale
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private const val CONTROLS_TIMEOUT_MILLIS = 3_000L

/**
 * Full-screen playback.
 *
 * The video draws into a plain [SurfaceView] handed to the controller, which is what
 * keeps every Media3 type on the other side of the `:core:media` boundary
 * (docs/FREEZE.md §4.4).
 */
@Composable
fun PlayerScreen(
    channelId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    streamUrl: String? = null,
    title: String? = null,
    startPositionMillis: Long? = null,
    /** Which episode this is, for the history entry. Null for anything that is not one. */
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }

    // Hoisted out of PlayerControls, which leaves composition every time the controls
    // auto-hide — taking a `remember` inside it with them. The lock forgot itself after
    // three seconds, and locking never suppressed anything while it lasted.
    var isLocked by remember { mutableStateOf(false) }
    var isLockTopLeft by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val brightness = remember(context) { ScreenBrightness(context.findActivity()) }
    val volume = remember(context) { MediaVolume(context) }
    var gestureFeedback by remember { mutableStateOf<GestureFeedback?>(null) }

    // Brightness is a window override, so it must be handed back when this screen goes
    // away or the whole app inherits whatever the last drag left behind.
    DisposableEffect(brightness) {
        onDispose { brightness.reset() }
    }

    // Immersive playback: the status and navigation bars go away for the duration and come
    // back on the way out. Restoring them in onDispose rather than on back specifically is
    // what keeps the rest of the app usable if playback ends any other way.
    //
    // The same effect holds the screen awake. Nothing else does: watching a film involves
    // no touch input, so the display timeout treats a viewer as an idle user and dims the
    // screen mid-scene. FLAG_KEEP_SCREEN_ON is the declaration that this window is being
    // watched rather than ignored, and it is scoped to the player window so it lapses on
    // its own the moment playback is left — a wake lock this app never has to remember to
    // release.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context.findActivity())?.window
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

    LaunchedEffect(channelId, streamUrl, title, startPositionMillis) {
        viewModel.load(
            channelId,
            customUrl = streamUrl,
            customTitle = title,
            startPositionMillis = startPositionMillis,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
    }

    // AC-PLAY-10: controls auto-hide after three seconds of inactivity. Restarted on
    // every reveal, and suppressed while paused so the user is never left with a
    // paused frame and no way back.
    LaunchedEffect(controlsVisible, state.status) {
        if (controlsVisible && state.status == PlaybackStatus.PLAYING) {
            delay(CONTROLS_TIMEOUT_MILLIS)
            controlsVisible = false
        }
    }

    // AC-PLAY-09: leaving the foreground stops playback so no audio leaks. There is no
    // background playback in v1 (docs/FREEZE.md §2).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // A locked screen ignores taps and drags entirely. That is the whole point of
            // the lock: a sleeve or a stray hand must not pause, seek or change volume.
            .then(
                if (isLocked) {
                    Modifier
                } else {
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { controlsVisible = !controlsVisible }
                        .playerVolumeBrightnessGestures(
                            onFeedback = { gestureFeedback = it },
                            onBrightnessDelta = brightness::adjustBy,
                            onVolumeDelta = volume::adjustBy,
                            currentBrightness = brightness::current,
                            currentVolume = volume::current,
                        )
                },
            ),
    ) {
        VideoSurface(
            viewModel = viewModel,
            videoAspectRatio = state.videoAspectRatio,
            mode = aspectRatioMode,
            modifier = Modifier.fillMaxSize(),
        )

        // While locked the lock button is the only thing on screen, and the only thing that
        // responds. It stays visible rather than auto-hiding, because a lock with no
        // visible way out is indistinguishable from a frozen app.
        if (isLocked) {
            LockButton(
                isLocked = true,
                isTopLeft = isLockTopLeft,
                onClick = { isLocked = false },
            )
            return@Box
        }

        when {
            state.status == PlaybackStatus.ERROR -> PlaybackErrorMessage(
                state = state,
                onRetry = viewModel::retry,
                onBack = onBack,
            )

            state.status == PlaybackStatus.BUFFERING -> BufferingIndicator(state)
        }

        // Shown whenever playback is paused, independently of the control bars, so a
        // paused stream always offers an obvious way to resume even after the controls
        // have auto-hidden.
        if (state.status == PlaybackStatus.PAUSED) {
            TapToPlay(onPlay = {
                viewModel.togglePlayPause()
                controlsVisible = true
            })
        }

        if (controlsVisible && state.status != PlaybackStatus.ERROR) {
            PlayerControls(
                state = state,
                seekInterval = settings.seekInterval,
                aspectRatioMode = aspectRatioMode,
                isLockTopLeft = isLockTopLeft,
                onLock = { isLocked = true },
                onSwapLockSide = { isLockTopLeft = !isLockTopLeft },
                onBack = onBack,
                onPlayPause = {
                    viewModel.togglePlayPause()
                    controlsVisible = true
                },
                onSeek = viewModel::seekTo,
                onSkip = {
                    viewModel.skipBy(it)
                    controlsVisible = true
                },
                onCycleAspectRatio = {
                    viewModel.cycleAspectRatio()
                    controlsVisible = true
                },
                onCycleSubtitles = {
                    val next = state.textTracks.firstOrNull { !it.isSelected }
                    viewModel.selectTextTrack(next?.id)
                    controlsVisible = true
                },
            )
        }

        gestureFeedback?.let { GestureIndicator(it) }
    }
}

/**
 * The large centred play button shown while paused.
 *
 * Deliberately outside [PlayerControls]: the control bars auto-hide after three seconds
 * (AC-PLAY-10), and a paused video with no visible way to resume reads as a frozen app.
 */
@Composable
private fun TapToPlay(onPlay: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onPlay,
            modifier = Modifier
                .size(88.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.player_play),
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/** The floating lock toggle, on whichever side the user has parked it. */
@Composable
private fun BoxScope.LockButton(
    isLocked: Boolean,
    isTopLeft: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(16.dp)
            .align(if (isTopLeft) Alignment.TopStart else Alignment.TopEnd)
            // A scrim, not decoration. While locked this is the only control on screen and
            // it sits directly over the video, so against a bright frame a white glyph on
            // nothing is invisible — and an invisible unlock is a player the user cannot
            // get out of.
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
    ) {
        Icon(
            imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = stringResource(
                if (isLocked) R.string.player_unlock else R.string.player_lock,
            ),
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Transient readout while a volume or brightness drag is in progress. */
@Composable
private fun GestureIndicator(feedback: GestureFeedback) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = when (feedback.target) {
                    DragTarget.BRIGHTNESS -> Icons.Filled.BrightnessMedium
                    DragTarget.VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = stringResource(
                    when (feedback.target) {
                        DragTarget.BRIGHTNESS -> R.string.player_brightness
                        DragTarget.VOLUME -> R.string.player_volume
                    },
                ),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = "${(feedback.fraction * PERCENT).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private const val PERCENT = 100

/**
 * The video output.
 *
 * A [SurfaceView] rather than a `TextureView`: it composites in the display pipeline
 * rather than through the GPU, which matters for battery on long viewing sessions.
 */
@Composable
private fun VideoSurface(
    viewModel: PlayerViewModel,
    videoAspectRatio: Float?,
    mode: AspectRatioMode,
    modifier: Modifier = Modifier,
) {
    val controller = remember { viewModel.controllerHandle() }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        // A bare SurfaceView stretches the frame to its bounds, so "fill the container"
        // is the starting point and every mode is expressed as a correction to it.
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
                SurfaceView(context).also(controller::attachSurface)
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { controller.detachSurface() }
    }
}

@Composable
private fun BufferingIndicator(state: PlaybackState) {
    // A spinner is silent to a screen reader: the stream stops, nothing is said, and the
    // user cannot tell a slow stream from a dead one. Polite, so it waits for whatever
    // TalkBack is already saying rather than cutting across it.
    val announcement = if (state.retryAttempt > 0) {
        stringResource(R.string.player_reconnecting, state.retryAttempt)
    } else {
        stringResource(R.string.player_a11y_buffering)
    }

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
            if (state.retryAttempt > 0) {
                Text(
                    text = stringResource(R.string.player_reconnecting, state.retryAttempt),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaybackErrorMessage(
    state: PlaybackState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(state.error.messageRes()),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            // Assertive rather than polite: playback has stopped and will not resume on
            // its own, so this is worth interrupting for.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Row(modifier = Modifier.padding(top = 20.dp)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = onBack) { Text(stringResource(R.string.player_back)) }
        }
    }
}

@Composable
private fun PlayerControls(
    state: PlaybackState,
    seekInterval: SeekInterval,
    aspectRatioMode: AspectRatioMode,
    isLockTopLeft: Boolean,
    onLock: () -> Unit,
    onSwapLockSide: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Int) -> Unit,
    onCycleAspectRatio: () -> Unit,
    onCycleSubtitles: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LockButton(isLocked = false, isTopLeft = isLockTopLeft, onClick = onLock)

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(start = 56.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.player_back),
                        tint = Color.White,
                    )
                }
                Text(
                    text = state.item?.title.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )

                // Only for content that can actually be seeked. A live stream cannot, so
                // the controller drops the request and the button does nothing — the same
                // reasoning that hides the seek bar in AC-PLAY-02.
                if (state.isSeekable) {
                    IconButton(onClick = { onSeek(0L) }) {
                        Icon(
                            imageVector = Icons.Filled.Replay,
                            contentDescription = stringResource(R.string.player_start_over),
                            tint = Color.White,
                        )
                    }
                }

                // Lock Position Swap (Top-Left <-> Top-Right)
                IconButton(onClick = onSwapLockSide) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = stringResource(R.string.player_swap_lock),
                        tint = Color.White,
                    )
                }

                // Cycles Fit → Fill → Zoom → Stretch. The label names the mode being
                // applied now, not the one a tap would move to.
                IconButton(onClick = onCycleAspectRatio) {
                    Icon(
                        imageVector = Icons.Filled.AspectRatio,
                        contentDescription = stringResource(
                            R.string.player_aspect_ratio,
                            stringResource(aspectRatioMode.labelRes()),
                        ),
                        tint = Color.White,
                    )
                }

                if (state.textTracks.isNotEmpty()) {
                    IconButton(onClick = onCycleSubtitles) {
                        Icon(
                            imageVector = Icons.Filled.ClosedCaption,
                            contentDescription = stringResource(R.string.player_subtitles),
                            tint = Color.White,
                        )
                    }
                }
            }

            // fillMaxWidth is load-bearing. `weight` in a Column only claims height, so
            // without it this Box wrapped its content and `Alignment.Center` centred the
            // transport controls inside a box the width of the controls — leaving them
            // pinned to the left edge of the screen.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Skip is meaningless on an unseekable stream: the controller ignores
                    // the seek, so the button would look active and do nothing.
                    if (state.isSeekable) {
                        IconButton(onClick = { onSkip(-1) }) {
                            Icon(
                                imageVector = seekInterval.replayIcon(),
                                contentDescription = stringResource(
                                    R.string.player_skip_back,
                                    seekInterval.seconds,
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }

                    IconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (state.isPlaying) R.string.player_pause else R.string.player_play,
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(56.dp),
                        )
                    }

                    if (state.isSeekable) {
                        IconButton(onClick = { onSkip(1) }) {
                            Icon(
                                imageVector = seekInterval.forwardIcon(),
                                contentDescription = stringResource(
                                    R.string.player_skip_forward,
                                    seekInterval.seconds,
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isSeekable && state.durationMillis > 0L) {
                    Text(text = state.positionMillis.asClock(), color = Color.White)
                    Slider(
                        value = state.positionMillis.toFloat(),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..state.durationMillis.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(text = state.durationMillis.asClock(), color = Color.White)
                } else {
                    // AC-PLAY-02: an unseekable stream shows no seek bar at all, rather than
                    // one that looks interactive and does nothing.
                    Text(
                        text = stringResource(R.string.player_live),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * Icons that carry the interval, falling back to a plain arrow where Material has no
 * numbered glyph. Better a generic icon than one that says 10 while skipping 15.
 */
private fun SeekInterval.replayIcon(): ImageVector = when (this) {
    SeekInterval.FIVE -> Icons.Filled.Replay5
    SeekInterval.TEN -> Icons.Filled.Replay10
    SeekInterval.THIRTY -> Icons.Filled.Replay30
    SeekInterval.FIFTEEN -> Icons.Filled.FastRewind
}

private fun SeekInterval.forwardIcon(): ImageVector = when (this) {
    SeekInterval.FIVE -> Icons.Filled.Forward5
    SeekInterval.TEN -> Icons.Filled.Forward10
    SeekInterval.THIRTY -> Icons.Filled.Forward30
    SeekInterval.FIFTEEN -> Icons.Filled.FastForward
}

@StringRes
private fun AspectRatioMode.labelRes(): Int = when (this) {
    AspectRatioMode.FIT -> R.string.player_aspect_fit
    AspectRatioMode.FILL -> R.string.player_aspect_fill
    AspectRatioMode.ZOOM -> R.string.player_aspect_zoom
    AspectRatioMode.STRETCH -> R.string.player_aspect_stretch
}

private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/** Formats a position as h:mm:ss, dropping the hour component when it is zero. */
private fun Long.asClock(): String {
    val totalSeconds = this / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
