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
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SubtitleFile
import dev.quiblo.core.model.SubtitleStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

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
    okHttpClient: OkHttpClient,
) : PlayerController {

    private val appContext = context.applicationContext

    /**
     * OkHttp for network reads, wrapped so local files still resolve.
     *
     * Media3 defaults to `DefaultHttpDataSource`, which is `HttpURLConnection` and holds no
     * pool we control — every segment paid a fresh TCP and TLS handshake. The wrapper
     * matters as much as the swap: a playlist the user imported from storage is a `file://`
     * URI, and an HTTP-only factory would simply fail to open it.
     */
    private val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
        appContext,
        OkHttpDataSource.Factory(okHttpClient).setUserAgent(STREAM_USER_AGENT),
    )

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

    /**
     * When the current item was handed to the engine.
     *
     * Uptime rather than wall clock, so a clock correction mid-load cannot produce a
     * negative or absurd duration.
     */
    private var prepareStartedAtMillis = 0L

    private var settings = PlayerSettings()

    /**
     * What the live engine was built with.
     *
     * Tracked separately from [settings] and from the current item because both the
     * `LoadControl` and the load-error policy are fixed when the engine is constructed, so
     * the two legitimately disagree between a change and the next [prepare].
     *
     * Starts as live because that is what the app opens on; the first VOD item rebuilds.
     */
    private var builtWith = EngineProfile(settings.bufferMode, isLive = true)

    /** Kept so the surface can be rebound after the engine is rebuilt. */
    private var attachedSurface: SurfaceView? = null

    /** The view cues are drawn into, once something has asked for one. */
    private var subtitleView: SubtitleView? = null

    /**
     * The last cues the engine produced.
     *
     * Held so that a view built mid-line shows that line rather than waiting for the next one,
     * and so an engine rebuild does not blank the screen for however long the current cue had
     * left to run.
     */
    private var latestCues: List<Cue> = emptyList()

    private var subtitleStyle = SubtitleStyle()

    private var player: ExoPlayer = buildPlayer(builtWith)

    private fun buildPlayer(profile: EngineProfile): ExoPlayer = ExoPlayer.Builder(
        appContext,
        // A failing hardware decoder is the single most common way playback dies on cheap
        // TV boxes: a corrupt header, a stream the vendor's codec mis-handles, and the
        // frame goes green or audio plays over a black screen. Fallback lets the engine
        // try the next decoder that claims the format — in practice the platform software
        // one — instead of surfacing an error the user can do nothing about.
        DefaultRenderersFactory(appContext).setEnableDecoderFallback(true),
    )
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(appContext)
                .setDataSourceFactory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorPolicy(profile.isLive)),
        )
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    profile.bufferMode.minBufferMillis,
                    profile.bufferMode.maxBufferMillis,
                    profile.bufferMode.bufferForPlaybackMillis,
                    profile.bufferMode.bufferForReplayMillis,
                )
                // Honour the durations above even when the byte thresholds disagree. A
                // high-bitrate remux hits the size ceiling long before the time target,
                // and the user picked a time.
                .setPrioritizeTimeOverSizeThresholds(true)
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
     * How hard the engine tries before it hands us a load error.
     *
     * These are opposite problems and they were being given one answer.
     *
     * **Live** keeps the fail-fast count. AC-PLAY-05 caps a dead stream at 15 seconds, and
     * the engine's default ladder stacked under our own retry logic blew that budget.
     *
     * **VOD** gets the engine's default ladder back. A film or an episode is one long read
     * from one host, and a transient hiccup partway through is normal. Failing after a
     * single attempt handed the error to [scheduleRetry], which restarts the whole media
     * source — reopening the connection and rebuffering — for something the engine would
     * have absorbed by retrying the same byte range. Because `STATE_READY` resets the
     * attempt counter, that loop never escalated to an error and never stopped: it just
     * stalled for a second or two, every few seconds, forever. That is the stutter.
     */
    private fun loadErrorPolicy(isLive: Boolean): LoadErrorHandlingPolicy =
        if (isLive) DefaultLoadErrorHandlingPolicy(LIVE_ENGINE_LOAD_RETRIES) else DefaultLoadErrorHandlingPolicy()

    /**
     * Swaps in an engine built for [wanted].
     *
     * Only called from [prepare], where a rebuild is invisible: playback is about to
     * restart anyway. Doing it while something is playing would black the screen out.
     */
    private fun rebuildIfNeeded(wanted: EngineProfile) {
        if (wanted == builtWith) return

        progressJob?.cancel()
        player.release()
        player = buildPlayer(wanted)
        builtWith = wanted
        attachedSurface?.let(player::setVideoSurfaceView)
    }

    override fun prepare(item: PlayableItem) {
        retryJob?.cancel()
        hasEverBeenReady = false
        prepareStartedAtMillis = SystemClock.uptimeMillis()
        rebuildIfNeeded(EngineProfile(settings.bufferMode, item.isLive))
        _state.value = PlaybackState(status = PlaybackStatus.BUFFERING, item = item)
        startWatchdog()

        player.setMediaItem(item.toMediaItem())
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

    /**
     * The view cues are drawn into.
     *
     * Built once and kept, because it holds the current cue: rebuilding it on a recomposition
     * would blank the line a viewer is reading. It is re-registered on every engine rebuild —
     * [rebuildIfNeeded] releases the player the listener was attached to — which is why the
     * listener is added in [buildPlayer] and not here.
     */
    override fun subtitleOutput(context: Context): View =
        subtitleView ?: SubtitleView(context).also {
            it.setStyle(captionStyle)
            it.setFractionalTextSize(fractionalTextSize)
            it.setCues(latestCues)
            subtitleView = it
        }

    /**
     * Starts from the system's own caption style and overrides it only when asked.
     *
     * `setUserDefaultStyle` and `setUserDefaultTextSize` read what the person set in Android's
     * accessibility settings. Someone who enlarged captions there has already answered this, and
     * quietly ignoring that is the kind of decision an app should not make on their behalf.
     */
    override fun applySubtitleStyle(style: SubtitleStyle) {
        subtitleStyle = style
        subtitleView?.let { view ->
            if (style.matchSystem) {
                view.setUserDefaultStyle()
                view.setUserDefaultTextSize()
            } else {
                view.setStyle(captionStyle)
                view.setFractionalTextSize(fractionalTextSize)
            }
        }
    }

    private val captionStyle: CaptionStyleCompat
        get() = subtitleStyle.let { style ->
            CaptionStyleCompat(
                style.textColor.argb,
                // The alpha is the viewer's, the hue is the viewer's, and they are set
                // separately because "black box" and "how solid" are two questions people
                // answer differently.
                style.background.argb.withAlpha(style.backgroundOpacity.alpha),
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.BLACK,
                null,
            )
        }

    private val fractionalTextSize: Float get() = subtitleStyle.textSize.fractionOfHeight

    private fun Int.withAlpha(alpha: Int): Int = (this and RGB_MASK) or (alpha shl ALPHA_SHIFT)

    override fun release() {
        subtitleView = null
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

    /**
     * The stream, plus any sidecar subtitles (INC-F10).
     *
     * `DefaultMediaSourceFactory` merges each configuration in as its own text track, so
     * everything downstream — [selectTextTrack], the track list, the menu — is the code that
     * already existed. Nothing is flagged default or forced: a file the viewer attached is one
     * they still have to switch on, which is the same rule the container's own tracks follow.
     */
    private fun PlayableItem.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(url)
        .setSubtitleConfigurations(subtitles.map { it.toSubtitleConfiguration() })
        .build()

    private fun SubtitleFile.toSubtitleConfiguration(): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setMimeType(mimeType)
            .setLanguage(language)
            .setLabel(label)
            .build()

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

        /**
         * The cues for this instant, pushed straight at the view (INC-F10).
         *
         * Without this nothing draws subtitles at all. The engine decodes the text track and
         * reports it here, and a player built on a bare `SurfaceView` — which this one is,
         * deliberately, to keep Media3's views out of the feature modules — has nowhere for it
         * to land. Selecting a subtitle track and seeing nothing was the whole symptom.
         */
        override fun onCues(cueGroup: CueGroup) {
            latestCues = cueGroup.cues
            subtitleView?.setCues(latestCues)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val current = _state.value
            _state.value = when (playbackState) {
                // Buffering after the first frame is a stall the user sees. Counted so the
                // acceptance sweep can measure smoothness instead of arguing about it.
                Player.STATE_BUFFERING -> current.copy(
                    status = PlaybackStatus.BUFFERING,
                    rebufferCount = current.rebufferCount + if (hasEverBeenReady) 1 else 0,
                )

                Player.STATE_READY -> {
                    val firstReady = !hasEverBeenReady
                    hasEverBeenReady = true
                    watchdogJob?.cancel()
                    current.copy(
                        loadTimeMillis = if (firstReady) {
                            SystemClock.uptimeMillis() - prepareStartedAtMillis
                        } else {
                            current.loadTimeMillis
                        },
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

        override fun onRenderedFirstFrame() {
            // The moment the surface stops showing the previous stream. Until this fires the
            // UI holds a shutter over it (#013).
            _state.value = _state.value.copy(hasRenderedFirstFrame = true)
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
         * Live only: let the engine attempt a load once. Reconnection strategy is ours to
         * decide there, and stacking its ladder under ours blew the AC-PLAY-05 budget.
         * VOD deliberately keeps the engine default — see [loadErrorPolicy].
         */
        const val LIVE_ENGINE_LOAD_RETRIES = 1

        /** Where the alpha byte sits in an ARGB colour, and what is left when it is dropped. */
        const val ALPHA_SHIFT = 24
        const val RGB_MASK = 0x00FFFFFF

        /**
         * Sent on stream requests, matching what the API client already sends. Some panels
         * gate on a recognised player agent, and answering as two different clients on the
         * same account invites exactly the attention we do not want. Carries no device
         * identifier.
         */
        const val STREAM_USER_AGENT = "IPTVSmartersPlayer"

        /** AC-PLAY-08: the engine pauses and resumes us as focus moves. */
        const val HANDLE_AUDIO_FOCUS = true

        const val MAX_RETRIES = 3
        const val RETRY_BASE_DELAY_MILLIS = 1_500L
        const val PROGRESS_INTERVAL_MILLIS = 500L

        /** AC-PLAY-05: a dead stream must surface an error inside this budget. */
        const val INITIAL_LOAD_TIMEOUT_MILLIS = 12_000L
    }
}

/**
 * The engine settings that can only be chosen when the player is constructed.
 *
 * Grouped into one value so there is a single thing to compare against, rather than a
 * growing list of `builtWithX` fields that can drift apart.
 */
internal data class EngineProfile(
    val bufferMode: BufferMode,
    val isLive: Boolean,
)

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
