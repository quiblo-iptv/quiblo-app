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

import android.view.SurfaceView
import dev.quiblo.core.model.PlayerSettings
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

    /**
     * Applies user tuning.
     *
     * The bitrate cap takes effect immediately. The buffer mode cannot: the engine fixes
     * its buffering policy when it is built, so a change there applies from the next
     * [prepare] onward. Callers should not pretend otherwise in the UI.
     */
    fun applySettings(settings: PlayerSettings)

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
    /**
     * Stalls after playback first started, for the current item.
     *
     * Not shown to the user. It exists so "it stutters" can be checked against a number
     * during the acceptance sweep, and so a regression in buffering behaviour is visible
     * rather than a matter of opinion.
     */
    val rebufferCount: Int = 0,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    /**
     * The decoded frame size, or zero before the first frame is decoded.
     *
     * Reported so the UI can fit the video to the screen itself. The surface handed to the
     * engine is a bare [SurfaceView] with no aspect handling of its own, which is the price
     * of keeping Media3's view classes out of the feature modules.
     */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val hasTrackChoice: Boolean get() = audioTracks.size > 1 || textTracks.isNotEmpty()

    /** Width over height, or null until the first frame has been decoded. */
    val videoAspectRatio: Float?
        get() = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight.toFloat()
        } else {
            null
        }
}
