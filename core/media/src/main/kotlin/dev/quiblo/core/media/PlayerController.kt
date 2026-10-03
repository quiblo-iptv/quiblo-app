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
import android.view.View
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SubtitleFile
import dev.quiblo.core.model.SubtitleStyle
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
// One method per thing feature code may ask of an engine. That list is the seam (docs/FREEZE.md §4.4),
// and splitting it would only give the screens two seams to hold instead of one.
@Suppress("TooManyFunctions")
interface PlayerController {

    val state: StateFlow<PlaybackState>

    /** Loads [item] and begins buffering. Replaces anything currently loaded. */
    fun prepare(item: PlayableItem)

    fun play()

    fun pause()

    /**
     * Drops the network connection and the buffer, and keeps the item (`BUG-037`).
     *
     * [pause] holds both: the engine keeps reading to fill its buffer, so a paused live stream
     * goes on occupying one of the account's connections. On an account allowed one screen that
     * is the screen — and the next device, or this one after a channel change, is refused.
     * [retry] starts the item again, at the live edge for live.
     */
    fun stop()

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

    /**
     * Builds the view subtitles are drawn into, already wired to the engine (INC-F10, INC-F11).
     *
     * **The engine does not draw subtitles into the video surface.** Text tracks are decoded and
     * handed out as cues, and something has to put them on screen; a player built on a bare
     * `SurfaceView` shows none, however many subtitle tracks it has selected. That is what this
     * is for, and it is why `selectTextTrack` alone was never enough.
     *
     * A view rather than a stream of cues, because a cue is an engine type and the whole point of
     * this interface is that no feature module names one (docs/FREEZE.md §4.4). The caller puts
     * the returned view over the video and never looks inside it.
     *
     * Returns the same view on every call for the life of this controller.
     */
    fun subtitleOutput(context: Context): View

    /**
     * Applies [style] to whatever [subtitleOutput] returned.
     *
     * Takes effect immediately and on the current cue, so a viewer changing the size from inside
     * the player sees it change under their hand — which is the whole reason INC-F11 puts this
     * in the player rather than in Settings.
     */
    fun applySubtitleStyle(style: SubtitleStyle)

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
    /**
     * Subtitle files to load alongside the stream (INC-F10).
     *
     * Loaded, not selected. They join the container's own text tracks in the menu and none of
     * them starts showing on its own — a viewer who attached a file still has to turn it on,
     * for the same reason a container that declares subtitles starts with them off.
     */
    val subtitles: List<SubtitleFile> = emptyList(),
    /**
     * What the stream is, when whoever built this item knows (`BUG-044`) — `application/x-mpegURL`
     * for HLS. Null lets the engine work it out, which it does from the path's extension alone.
     */
    val mimeType: String? = null,
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

    /** The host could not be resolved, or refused the connection outright. */
    UNREACHABLE,
    TIMEOUT,
    UNSUPPORTED_FORMAT,
    DRM_UNSUPPORTED,

    /** The server answered 404 or 410: there is nothing at that address. */
    SOURCE_GONE,

    /** The server answered 401: the account's username or password was not accepted. */
    AUTH_REJECTED,

    /**
     * The server is there and refused this request — 403, 429, 458, 509, the panel firewall's
     * 46x family, or any 5xx. Usually a connection limit or an overloaded panel, and usually
     * temporary, which is why it is retried and [SOURCE_GONE] is not (`BUG-034`).
     */
    PROVIDER_REFUSED,
    UNKNOWN,
}

/**
 * What the engine knew about a failure, kept for the diagnosis and for a bug report (`FEAT-035`).
 *
 * [PlaybackError] is what the screen says. This is the evidence behind it, and it is what lets a
 * diagnosis tell "the provider refused" from "the provider answered and Quiblo could not play
 * what came back". It carries no URL, no host and nothing from a request: none of it can leak a
 * credential (AC-XT-04).
 *
 * @property httpStatus the status the server answered with, when it answered with a bad one.
 * @property engineCode the engine's own name for the failure, such as
 *   `ERROR_CODE_IO_BAD_HTTP_STATUS`, or null when the load was ended by the watchdog without
 *   the engine reporting anything at all.
 * @property hostUnreachable whether the host could not be resolved or refused the connection.
 * @property bytesReceived how much media arrived from the network for this item. Zero means the
 *   server never sent a byte, which is a different failure from one that sent data Quiblo could
 *   not play.
 * @property hadPlayed whether this item ever reached a playable state before failing.
 * @property retries how many automatic retries were made before giving up.
 */
data class FailureDetails(
    val httpStatus: Int? = null,
    val engineCode: String? = null,
    val hostUnreachable: Boolean = false,
    val bytesReceived: Long = 0L,
    val hadPlayed: Boolean = false,
    val retries: Int = 0,
)

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
    /** Set together with [error]: the evidence behind it. See [FailureDetails]. */
    val failure: FailureDetails? = null,
    val retryAttempt: Int = 0,
    /**
     * Stalls after playback first started, for the current item.
     *
     * Not shown to the user. It exists so "it stutters" can be checked against a number
     * during the acceptance sweep, and so a regression in buffering behaviour is visible
     * rather than a matter of opinion.
     */
    val rebufferCount: Int = 0,
    /**
     * Milliseconds from [PlayerController.prepare] to the first playable frame, or zero
     * until that happens.
     *
     * The zapping target is under half a second, and there was no way to tell whether it
     * was met. Same purpose as [rebufferCount]: a number the sweep can check, not an
     * impression.
     */
    val loadTimeMillis: Long = 0L,
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
    /**
     * Whether a frame of *this* item has been drawn yet.
     *
     * False from [PlayerController.prepare] until the engine reports its first rendered
     * frame. It exists because Media3 keeps the last decoded frame on the surface until the
     * next one arrives, so switching channels showed the **previous** stream's final picture
     * over the new one's loading (`agile/012` #013). The buffering spinner did not hide it:
     * a spinner draws *over* whatever is already there rather than replacing it.
     *
     * The UI uses it to hold an opaque shutter over the surface. Reported rather than handled
     * here because the surface belongs to the UI — `:core:media` has no view to cover.
     */
    val hasRenderedFirstFrame: Boolean = false,
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
