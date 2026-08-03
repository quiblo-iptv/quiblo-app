/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.feature.player

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vibrato.core.media.PlaybackState
import dev.vibrato.core.media.PlaybackStatus
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
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(channelId, streamUrl, title) { viewModel.load(channelId, customUrl = streamUrl, customTitle = title) }

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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        VideoSurface(viewModel = viewModel, modifier = Modifier.fillMaxSize())

        when {
            state.status == PlaybackStatus.ERROR -> PlaybackErrorMessage(
                state = state,
                onRetry = viewModel::retry,
                onBack = onBack,
            )

            state.status == PlaybackStatus.BUFFERING -> BufferingIndicator(state)
        }

        if (controlsVisible && state.status != PlaybackStatus.ERROR) {
            PlayerControls(
                state = state,
                onBack = onBack,
                onPlayPause = {
                    viewModel.togglePlayPause()
                    controlsVisible = true
                },
                onSeek = viewModel::seekTo,
                onCycleSubtitles = {
                    val next = state.textTracks.firstOrNull { !it.isSelected }
                    viewModel.selectTextTrack(next?.id)
                    controlsVisible = true
                },
            )
        }
    }
}

/**
 * The video output.
 *
 * A [SurfaceView] rather than a `TextureView`: it composites in the display pipeline
 * rather than through the GPU, which matters for battery on long viewing sessions.
 */
@Composable
private fun VideoSurface(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val controller = remember { viewModel.controllerHandle() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).also(controller::attachSurface)
        },
    )
    DisposableEffect(Unit) {
        onDispose { controller.detachSurface() }
    }
}

@Composable
private fun BufferingIndicator(state: PlaybackState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSubtitles: () -> Unit,
    onPictureInPictureClick: () -> Unit = {},
) {
    var isLocked by remember { mutableStateOf(false) }
    var isLockTopLeft by remember { mutableStateOf(true) }
    var showVolumeSlider by remember { mutableStateOf(false) }
    var showBrightnessSlider by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(1.0f) }
    var brightness by remember { mutableStateOf(1.0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Floating Lock Button positioned Top-Left or Top-Right
        IconButton(
            onClick = { isLocked = !isLocked },
            modifier = Modifier
                .padding(16.dp)
                .align(if (isLockTopLeft) Alignment.TopStart else Alignment.TopEnd),
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (isLocked) "Unlock screen" else "Lock screen",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }

        if (!isLocked) {
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

                    // Start Over (Seek to 0L)
                    IconButton(onClick = { onSeek(0L) }) {
                        Icon(
                            imageVector = Icons.Filled.Replay,
                            contentDescription = "Start over",
                            tint = Color.White,
                        )
                    }

                    // Lock Position Swap (Top-Left <-> Top-Right)
                    IconButton(onClick = { isLockTopLeft = !isLockTopLeft }) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "Swap lock icon position",
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
                    IconButton(onClick = onPictureInPictureClick) {
                        Icon(
                            imageVector = Icons.Filled.PictureInPictureAlt,
                            contentDescription = "Picture in Picture",
                            tint = Color.White,
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        IconButton(onClick = { onSeek((state.positionMillis - 10_000L).coerceAtLeast(0L)) }) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "Skip Backward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
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

                        IconButton(onClick = { onSeek(state.positionMillis + 10_000L) }) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "Skip Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
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
                    IconButton(onClick = { showVolumeSlider = !showVolumeSlider }) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White,
                        )
                    }

                    IconButton(onClick = { showBrightnessSlider = !showBrightnessSlider }) {
                        Icon(
                            imageVector = Icons.Filled.Brightness6,
                            contentDescription = "Brightness",
                            tint = Color.White,
                        )
                    }

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
