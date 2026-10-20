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

/**
 * What the engine is doing right now, for the playback info overlay (`FEAT-040`).
 *
 * Plain values, never an engine type, so the screens can draw it without naming Media3
 * (docs/FREEZE.md §4.4). Numbers stay numbers: formatting them is the screen's job, and a
 * screenshot of the overlay is the thing a bug report is built from, so nothing here may carry a
 * URL, a host or a credential (AC-XT-04).
 *
 * Everything that may be unknown is nullable — a stream with no audio, a decoder not yet chosen —
 * because "unknown" and zero are different answers to a question about a stutter.
 */
data class PlaybackDiagnostics(
    /** When this snapshot was taken, wall clock, so screenshots can be put in order. */
    val takenAtEpochMillis: Long,
    val engineState: EngineState,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val isLive: Boolean,
    val positionMillis: Long,
    val durationMillis: Long?,
    /** How far past the playhead the buffer reaches. */
    val bufferedAheadMillis: Long,
    /** The container as the engine read it, such as `video/x-matroska` or `video/mp2t`. */
    val containerMimeType: String?,
    val video: VideoDiagnostics?,
    val audio: AudioDiagnostics?,
    val frames: FrameDiagnostics?,
    /** When the last second's frames were scheduled for the screen; null while too few to say. */
    val timing: FrameTimingDiagnostics?,
    val network: NetworkDiagnostics,
    val recovery: RecoveryDiagnostics,
    /** The buffer setting the engine was built with, and the bitrate cap in force. */
    val bufferMode: String,
    val maxVideoBitrate: Int?,
    /** The engine's name for the last failure on this item, if there was one. */
    val lastEngineError: String?,
    /**
     * Items the engine has been handed since it was built, this one included (`BUG-064`).
     *
     * One is a fresh engine. More is an engine carried over from what played before — which is
     * what a film that plays well first time and badly the second time has to be checked against.
     */
    val engineItems: Int,
)

/** The engine's own playback state, as it reports it. */
enum class EngineState { IDLE, BUFFERING, READY, ENDED }

data class VideoDiagnostics(
    val mimeType: String?,
    /** The codec string, such as `hvc1.2.4.L153.B0`: the profile and level are in it. */
    val codecs: String?,
    val width: Int,
    val height: Int,
    /** As the stream declares it; null when it declares none. */
    val frameRate: Float?,
    val bitrate: Int?,
    /** `SDR`, `HDR10 (PQ)` or `HLG`; null when the stream does not say. */
    val dynamicRange: String?,
    /** The decoder in use, or null before one is chosen. */
    val decoderName: String?,
    /** Whether that decoder runs on the CPU. Null when it is not known. */
    val decoderIsSoftware: Boolean?,
    /** Decoders this engine has started, oldest first, with when — a fallback shows up here. */
    val decoderHistory: List<DecoderEvent>,
    /** Whether software decoders may be used for this codec right now (`BUG-063`). */
    val softwareAllowed: Boolean,
)

data class AudioDiagnostics(
    val mimeType: String?,
    val codecs: String?,
    val channels: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val decoderName: String?,
)

/** One decoder starting, and how long it took. */
data class DecoderEvent(
    val atEpochMillis: Long,
    val name: String,
    val initMillis: Long,
    /**
     * Why it started, when the engine said: `first` for an engine's first, a codec error, or the
     * format change the decoder before could not take. Null when nothing was said — a seek past a
     * resolution change, a surface handed over — which is itself worth knowing.
     */
    val reason: String?,
)

/**
 * The video decoder's counts since it started, and the last second of them.
 *
 * Rendered and dropped per second are the two numbers that say whether a stutter is the decoder:
 * a film at 24 fps that renders 8 and drops 16 is a decoder that cannot keep up.
 */
data class FrameDiagnostics(
    val rendered: Long,
    val dropped: Long,
    val skipped: Long,
    /** The longest run of frames dropped one after another. */
    val maxConsecutiveDropped: Int,
    /** Times the decoder fell so far behind it jumped to the next keyframe. */
    val droppedToKeyframe: Int,
    val renderedPerSecond: Float,
    val droppedPerSecond: Float,
)

data class NetworkDiagnostics(
    /** Media bytes received for this item. */
    val bytesReceived: Long,
    /** What arrived in the last second, in bits per second. */
    val lastSecondBitsPerSecond: Long,
    /** The engine's running estimate of the connection, in bits per second. */
    val estimateBitsPerSecond: Long?,
    val millisSinceLastByte: Long?,
)

/** What the controller has done on its own for this item. */
data class RecoveryDiagnostics(
    val retries: Int,
    val rebuffers: Int,
    val loadTimeMillis: Long,
    val stallReloads: Int,
    val stutterReloads: Int,
    val hardwareDecoderRetries: Int,
    val liveEdgeRejoins: Int,
)
