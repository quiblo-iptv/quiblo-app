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

package dev.quiblo.core.media

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.PlayerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Media3 implementation of [PlayerController].
 *
 * This is the only class in the project that knows ExoPlayer exists.
 *
 * Media3 marks its buffering and load-error tuning APIs as unstable. Opting in is
 * deliberate: the defaults let a dead stream hang well past the AC-PLAY-05 budget, and
 * that is exactly the behaviour this class exists to control. The opt-in is confined to
 * this one file, which is the whole point of the [PlayerController] boundary.
 *
 * @param scope drives position polling and the retry backoff; cancelled by [release].
 */
@androidx.annotation.OptIn(UnstableApi::class)
class Media3PlayerController(
    context: Context,
    private val scope: CoroutineScope,
) : PlayerController {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var retryJob: Job? = null
    private var progressJob: Job? = null
    private var watchdogJob: Job? = null

    /**
     * Whether this item has ever reached a playable state.
     *
     * Separates the two requirements that would otherwise fight each other: AC-PLAY-05
     * caps the *initial* load at 15 seconds, while AC-PLAY-06 wants three backed-off
     * retries when an already-playing stream drops. A URL that never worked is not the
     * same situation as a stream that died.
     */
    private var hasEverBeenReady = false

    private var settings = PlayerSettings()

    /**
     * The buffer mode the live engine was built with.
     *
     * Tracked separately from [settings] because `LoadControl` is fixed at build time, so
     * the two legitimately disagree between a settings change and the next [prepare].
     */
    private var builtWithBufferMode = settings.bufferMode

    /** Kept so the surface can be rebound after the engine is rebuilt. */
    private var attachedSurface: SurfaceView? = null

    private var player: ExoPlayer = buildPlayer(builtWithBufferMode)

    private fun buildPlayer(bufferMode: BufferMode): ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(appContext).setLoadErrorHandlingPolicy(
                // ExoPlayer's default policy retries a failed load several times, with its
                // own backoff, before it ever calls onPlayerError. Stacked under our retry
                // logic that pushed a dead URL far past the 15 second budget in AC-PLAY-05.
                // One internal attempt; reconnection is our decision to make, not the
                // engine's.
                object : DefaultLoadErrorHandlingPolicy(ENGINE_LOAD_RETRIES) {},
            ),
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    bufferMode.minBufferMillis,
                    bufferMode.maxBufferMillis,
                    bufferMode.bufferForPlaybackMillis,
                    bufferMode.bufferForReplayMillis,
                )
                .build(),
        )
        .build()
        .apply {
            // handleAudioFocus = true is what satisfies AC-PLAY-08: an incoming call or
            // another app taking focus pauses us, and we resume when it is handed back.
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                HANDLE_AUDIO_FOCUS,
            )
            addListener(PlayerListener())
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .applyBitrateCap(settings.maxBitrate)
                .build()
        }

    override fun applySettings(settings: PlayerSettings) {
        this.settings = settings

        // Takes effect on the next adaptive selection, so mid-stream and without a rebuild.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .applyBitrateCap(settings.maxBitrate)
            .build()
    }

    /**
     * Swaps in an engine built with the current buffer mode.
     *
     * Only called from [prepare], where a rebuild is invisible: playback is about to
     * restart anyway. Doing it while something is playing would black the screen out.
     */
    private fun rebuildForBufferModeIfNeeded() {
        if (settings.bufferMode == builtWithBufferMode) return

        progressJob?.cancel()
        player.release()
        player = buildPlayer(settings.bufferMode)
        builtWithBufferMode = settings.bufferMode
        attachedSurface?.let(player::setVideoSurfaceView)
    }

    override fun prepare(item: PlayableItem) {
        retryJob?.cancel()
        hasEverBeenReady = false
        rebuildForBufferModeIfNeeded()
        _state.value = PlaybackState(status = PlaybackStatus.BUFFERING, item = item)
        startWatchdog()

        player.setMediaItem(MediaItem.fromUri(item.url))
        if (!item.isLive && item.startPositionMillis > 0L) {
            player.seekTo(item.startPositionMillis)
        }
        player.prepare()
        player.playWhenReady = true
        startProgressUpdates()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMillis: Long) {
        if (_state.value.isSeekable) {
            player.seekTo(positionMillis)
        }
    }

    override fun retry() {
        retryJob?.cancel()
        hasEverBeenReady = false
        startWatchdog()
        _state.value = _state.value.copy(
            status = PlaybackStatus.BUFFERING,
            error = null,
            retryAttempt = 0,
        )
        player.prepare()
        player.playWhenReady = true
    }

    override fun selectAudioTrack(trackId: String?) = selectTrack(C.TRACK_TYPE_AUDIO, trackId)

    override fun selectTextTrack(trackId: String?) = selectTrack(C.TRACK_TYPE_TEXT, trackId)

    override fun attachSurface(surfaceView: SurfaceView) {
        attachedSurface = surfaceView
        player.setVideoSurfaceView(surfaceView)
    }

    override fun detachSurface() {
        attachedSurface = null
        player.clearVideoSurface()
    }

    override fun release() {
        retryJob?.cancel()
        progressJob?.cancel()
        watchdogJob?.cancel()
        player.release()
        _state.value = PlaybackState()
    }

    private fun selectTrack(trackType: @C.TrackType Int, trackId: String?) {
        val parameters = player.trackSelectionParameters.buildUpon()
        if (trackId == null) {
            // Null means "off", which only makes sense for subtitles.
            parameters.clearOverridesOfType(trackType)
            parameters.setTrackTypeDisabled(trackType, trackType == C.TRACK_TYPE_TEXT)
        } else {
            val group = player.currentTracks.groups
                .filter { it.type == trackType }
                .firstOrNull { groupId(it) == trackId }

            if (group != null) {
                parameters.setTrackTypeDisabled(trackType, false)
                parameters.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            }
        }
        player.trackSelectionParameters = parameters.build()
    }

    private fun groupId(group: Tracks.Group): String =
        "${group.type}:${group.mediaTrackGroup.id}:${group.getTrackFormat(0).language ?: "und"}"

    /**
     * Fails the initial load if nothing becomes playable in time.
     *
     * AC-PLAY-05 requires a clear error within 15 seconds and forbids hanging
     * indefinitely. Relying on the engine to report that is not enough: a server that
     * accepts the connection and then stalls produces no error at all.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(INITIAL_LOAD_TIMEOUT_MILLIS)
            if (!hasEverBeenReady) {
                retryJob?.cancel()
                _state.value = _state.value.copy(
                    status = PlaybackStatus.ERROR,
                    error = _state.value.error ?: PlaybackError.TIMEOUT,
                )
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                if (player.isPlaying || player.playbackState == Player.STATE_READY) {
                    _state.value = _state.value.copy(
                        positionMillis = player.currentPosition.coerceAtLeast(0L),
                        bufferedPositionMillis = player.bufferedPosition.coerceAtLeast(0L),
                    )
                }
                delay(PROGRESS_INTERVAL_MILLIS)
            }
        }
    }

    /**
     * Reconnects after a stream drops.
     *
     * AC-PLAY-06 requires at least three automatic attempts with backoff before the user
     * is shown an error. Streams that die for a few seconds are the norm on live IPTV, and
     * surfacing an error immediately would make the app feel broken when it is not.
     */
    private fun scheduleRetry(error: PlaybackError) {
        // Retrying these is pointless and only delays telling the user something true.
        // A 404 will still be a 404, and v1 will still not support DRM.
        val isTerminal = error == PlaybackError.SOURCE_GONE ||
            error == PlaybackError.UNSUPPORTED_FORMAT ||
            error == PlaybackError.DRM_UNSUPPORTED

        // AC-PLAY-06's three retries are for a stream that dropped mid-playback. A URL
        // that never worked gets reported immediately instead.
        if (isTerminal || !hasEverBeenReady) {
            watchdogJob?.cancel()
            _state.value = _state.value.copy(status = PlaybackStatus.ERROR, error = error)
            return
        }

        val attempt = _state.value.retryAttempt + 1

        if (attempt > MAX_RETRIES) {
            _state.value = _state.value.copy(status = PlaybackStatus.ERROR, error = error)
            return
        }

        _state.value = _state.value.copy(
            status = PlaybackStatus.BUFFERING,
            retryAttempt = attempt,
            error = null,
        )

        retryJob?.cancel()
        retryJob = scope.launch {
            delay(RETRY_BASE_DELAY_MILLIS * attempt)
            player.prepare()
            player.playWhenReady = true
        }
    }

    inner class PlayerListener : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            val current = _state.value
            _state.value = when (playbackState) {
                Player.STATE_BUFFERING -> current.copy(status = PlaybackStatus.BUFFERING)

                Player.STATE_READY -> {
                    hasEverBeenReady = true
                    watchdogJob?.cancel()
                    current.copy(
                        status = if (player.playWhenReady) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED,
                        // A live stream reports no duration, which tells the UI to hide the
                        // seek bar rather than render an unusable one (AC-PLAY-02).
                        durationMillis = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
                        isSeekable = player.isCurrentMediaItemSeekable && current.item?.isLive != true,
                        error = null,
                        retryAttempt = 0,
                    )
                }

                Player.STATE_ENDED -> current.copy(status = PlaybackStatus.ENDED)

                // ExoPlayer drops to STATE_IDLE immediately after reporting an error.
                // Letting that through would overwrite the error we just recorded and
                // leave the user staring at a blank frame with no message at all.
                else -> if (current.status == PlaybackStatus.ERROR) {
                    current
                } else {
                    current.copy(status = PlaybackStatus.IDLE)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val current = _state.value
            if (current.status == PlaybackStatus.PLAYING || current.status == PlaybackStatus.PAUSED) {
                _state.value = current.copy(
                    status = if (isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED,
                )
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.value = _state.value.copy(
                audioTracks = tracks.toOptions(C.TRACK_TYPE_AUDIO),
                textTracks = tracks.toOptions(C.TRACK_TYPE_TEXT),
            )
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            // The bare SurfaceView the UI hands us has no aspect handling of its own, so
            // the frame size has to reach the UI for it to fit the video to the screen.
            _state.value = _state.value.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            scheduleRetry(error.toPlaybackError())
        }
    }

    /**
     * Constrains adaptive selection to renditions at or below the cap.
     *
     * [MaxBitrateCap.UNLIMITED] clears the constraint rather than setting a huge number, so
     * the engine's own logic is left alone rather than merely bounded very high.
     */
    private fun TrackSelectionParameters.Builder.applyBitrateCap(
        cap: MaxBitrateCap,
    ): TrackSelectionParameters.Builder =
        setMaxVideoBitrate(cap.bitsPerSecond ?: Int.MAX_VALUE)

    private fun Tracks.toOptions(trackType: @C.TrackType Int): List<TrackOption> =
        groups.filter { it.type == trackType && it.length > 0 }
            .map { group ->
                val format = group.getTrackFormat(0)
                TrackOption(
                    id = groupId(group),
                    label = format.label ?: format.language ?: "Track",
                    language = format.language,
                    isSelected = group.isSelected,
                )
            }

    private companion object {
        /**
         * Let the engine attempt a load once. Reconnection strategy is ours to decide,
         * and stacking its ladder under ours blew the AC-PLAY-05 budget.
         */
        const val ENGINE_LOAD_RETRIES = 1

        /** AC-PLAY-08: the engine pauses and resumes us as focus moves. */
        const val HANDLE_AUDIO_FOCUS = true

        const val MAX_RETRIES = 3
        const val RETRY_BASE_DELAY_MILLIS = 1_500L
        const val PROGRESS_INTERVAL_MILLIS = 500L

        /** AC-PLAY-05: a dead stream must surface an error inside this budget. */
        const val INITIAL_LOAD_TIMEOUT_MILLIS = 12_000L
    }
}

/** Maps an engine exception to a typed error. No engine detail reaches the UI. */
internal fun PlaybackException.toPlaybackError(): PlaybackError = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    -> PlaybackError.TIMEOUT

    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    -> PlaybackError.UNSUPPORTED_FORMAT

    // v1 ships no DRM at all (docs/FREEZE.md §3), so an encrypted stream is a clear,
    // expected failure rather than a bug to chase.
    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
    -> PlaybackError.DRM_UNSUPPORTED

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    -> PlaybackError.SOURCE_GONE

    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> PlaybackError.NETWORK

    else -> PlaybackError.UNKNOWN
}
