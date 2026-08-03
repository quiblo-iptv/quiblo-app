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

package dev.vibrato.core.media

import android.view.SurfaceView
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between feature code and the player engine (docs/FREEZE.md §4.4).
 *
 * Feature modules never reference ExoPlayer, Media3, or any of their types. They talk to
 * this interface, hand it a plain [SurfaceView] to draw on, and observe [state]. That is
 * what makes the engine replaceable and what leaves a clean place for DRM to slot in
 * later without touching a single screen.
 *
 * The surface is an Android [SurfaceView] rather than anything from Compose because
 * `:core:*` must not import Compose (AC-NFR-06). The player feature wraps it in an
 * `AndroidView` on its side of the boundary.
 */
interface PlayerController {

    val state: StateFlow<PlaybackState>

    /** Loads [item] and begins buffering. Replaces anything currently loaded. */
    fun prepare(item: PlayableItem)

    fun play()

    fun pause()

    /** Ignored when the current item is not seekable, such as a raw TS stream. */
    fun seekTo(positionMillis: Long)

    /** Retries the current item immediately, resetting the automatic backoff. */
    fun retry()

    fun selectAudioTrack(trackId: String?)

    fun selectTextTrack(trackId: String?)

    /** Binds the video output. Call again after a surface is recreated. */
    fun attachSurface(surfaceView: SurfaceView)

    fun detachSurface()

    /** Frees the engine. The controller is unusable afterwards. */
    fun release()
}

/** Something playable, described without reference to any engine type. */
data class PlayableItem(
    val id: String,
    val title: String,
    val url: String,
    /**
     * Live content is unseekable and has no meaningful duration, so the UI hides the seek
     * bar rather than showing a broken one (AC-PLAY-02).
     */
    val isLive: Boolean,
    /** Where to resume from, for VOD only (AC-PLAY-03). */
    val startPositionMillis: Long = 0L,
)

/** A selectable audio or subtitle track. */
data class TrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
)

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

/**
 * Why playback failed, as a type rather than a message.
 *
 * Same reasoning as `SourceError`: the UI owns the wording so it stays localisable, and
 * no raw engine exception can reach the screen (AC-PLAY-05).
 */
enum class PlaybackError {
    NETWORK,
    UNREACHABLE,
    TIMEOUT,
    UNSUPPORTED_FORMAT,
    DRM_UNSUPPORTED,
    SOURCE_GONE,
    UNKNOWN,
}

/**
 * Everything the player UI renders from.
 *
 * @property retryAttempt how many automatic retries have been made for the current item.
 *   Surfaced so the UI can say "reconnecting" instead of showing a dead frame
 *   (AC-PLAY-06).
 */
data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val item: PlayableItem? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val bufferedPositionMillis: Long = 0L,
    val isSeekable: Boolean = false,
    val error: PlaybackError? = null,
    val retryAttempt: Int = 0,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val hasTrackChoice: Boolean get() = audioTracks.size > 1 || textTracks.isNotEmpty()
}

enum class BufferMode(val minBufferMs: Int, val maxBufferMs: Int, val playbackBufferMs: Int) {
    LOW_LATENCY(minBufferMs = 5_000, maxBufferMs = 15_000, playbackBufferMs = 1_000),
    BALANCED(minBufferMs = 15_000, maxBufferMs = 30_000, playbackBufferMs = 1_500),
    HIGH_STABILITY(minBufferMs = 30_000, maxBufferMs = 60_000, playbackBufferMs = 3_000),
}

enum class MaxBitrateCap(val bitrateKbps: Int) {
    AUTO(bitrateKbps = Int.MAX_VALUE),
    HIGH_1080P(bitrateKbps = 8_000),
    MEDIUM_720P(bitrateKbps = 4_000),
    LOW_480P(bitrateKbps = 1_500),
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    CROP("Crop / Zoom"),
    STRETCH("Stretch"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
}

enum class SeekInterval(val seconds: Int) {
    SEEK_5(5),
    SEEK_10(10),
    SEEK_15(15),
    SEEK_30(30),
    SEEK_60(60),
}
