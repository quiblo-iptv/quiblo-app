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
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

/**
 * Reads the engine for [PlaybackDiagnostics] (`FEAT-040`).
 *
 * Its own class so the controller does not grow by the size of a debugging aid, and so that
 * everything the overlay knows about Media3 sits in one file. It listens for decoders starting —
 * the one fact the engine does not keep for later — and reads the rest from the player when
 * asked, once a second while the overlay is open.
 *
 * Main thread only: the engine calls its listeners on the application thread, and the
 * controller samples there too.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DiagnosticsSampler(private val appContext: Context) : AnalyticsListener {

    private val videoDecoders = ArrayDeque<DecoderEvent>()
    private var audioDecoder: String? = null
    private var previous: Sample? = null

    /** A new item: its decoders are its own. */
    fun reset() {
        videoDecoders.clear()
        audioDecoder = null
        previous = null
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        videoDecoders.addLast(DecoderEvent(System.currentTimeMillis(), decoderName, initializationDurationMs))
        while (videoDecoders.size > DECODER_HISTORY) videoDecoders.removeFirst()
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        audioDecoder = decoderName
    }

    fun sample(player: ExoPlayer, facts: ControllerFacts): PlaybackDiagnostics {
        val now = SystemClock.uptimeMillis()
        val counters = player.videoDecoderCounters?.also { it.ensureUpdated() }
        val sample = Sample(
            atMillis = now,
            counters = counters,
            rendered = counters?.renderedOutputBufferCount ?: 0,
            dropped = counters?.droppedBufferCount ?: 0,
            bytes = facts.bytesReceived,
        )
        val last = previous
        previous = sample

        return PlaybackDiagnostics(
            takenAtEpochMillis = System.currentTimeMillis(),
            engineState = player.playbackState.toEngineState(),
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            isLive = facts.isLive,
            positionMillis = player.currentPosition.coerceAtLeast(0L),
            durationMillis = player.duration.takeIf { it != C.TIME_UNSET },
            bufferedAheadMillis = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L),
            containerMimeType = (player.videoFormat ?: player.audioFormat)?.containerMimeType,
            video = player.videoFormat?.let { video(it, facts.softwareAllowed) },
            audio = player.audioFormat?.let(::audio),
            frames = counters?.let { frames(it, sample, last) },
            network = NetworkDiagnostics(
                bytesReceived = facts.bytesReceived,
                lastSecondBitsPerSecond = last?.let { bitsPerSecond(it, sample) } ?: 0L,
                estimateBitsPerSecond = DefaultBandwidthMeter.getSingletonInstance(appContext).bitrateEstimate
                    .takeIf { it > 0L },
                millisSinceLastByte = facts.lastByteAtMillis.takeIf { it > 0L }?.let { now - it },
            ),
            recovery = facts.recovery,
            bufferMode = facts.bufferMode,
            maxVideoBitrate = facts.maxVideoBitrate,
            lastEngineError = facts.lastEngineError,
        )
    }

    private fun video(format: Format, softwareAllowed: Boolean): VideoDiagnostics {
        val decoder = videoDecoders.lastOrNull()?.name
        return VideoDiagnostics(
            mimeType = format.sampleMimeType,
            codecs = format.codecs,
            width = format.width,
            height = format.height,
            frameRate = format.frameRate.takeIf { it > 0f },
            bitrate = format.bitrate.takeIf { it != Format.NO_VALUE },
            dynamicRange = format.colorInfo?.colorTransfer?.let(::dynamicRange),
            decoderName = decoder,
            decoderIsSoftware = decoder?.let { isSoftware(format.sampleMimeType, it) },
            decoderHistory = videoDecoders.toList(),
            softwareAllowed = softwareAllowed,
        )
    }

    private fun audio(format: Format) = AudioDiagnostics(
        mimeType = format.sampleMimeType,
        codecs = format.codecs,
        channels = format.channelCount.takeIf { it != Format.NO_VALUE },
        sampleRate = format.sampleRate.takeIf { it != Format.NO_VALUE },
        bitrate = format.bitrate.takeIf { it != Format.NO_VALUE },
        decoderName = audioDecoder,
    )

    private fun frames(counters: DecoderCounters, now: Sample, last: Sample?): FrameDiagnostics {
        // Rates only against a sample from the same decoder: a new one starts its counts at zero.
        val comparable = last?.takeIf { it.counters === counters }
        val seconds = comparable?.let { (now.atMillis - it.atMillis) / MILLIS_PER_SECOND } ?: 0f
        fun perSecond(count: Int, before: Int): Float = if (seconds > 0f) (count - before) / seconds else 0f
        return FrameDiagnostics(
            rendered = counters.renderedOutputBufferCount.toLong(),
            dropped = counters.droppedBufferCount.toLong(),
            skipped = counters.skippedOutputBufferCount.toLong(),
            maxConsecutiveDropped = counters.maxConsecutiveDroppedBufferCount,
            droppedToKeyframe = counters.droppedToKeyframeCount,
            renderedPerSecond = comparable?.let { perSecond(now.rendered, it.rendered) } ?: 0f,
            droppedPerSecond = comparable?.let { perSecond(now.dropped, it.dropped) } ?: 0f,
        )
    }

    private fun bitsPerSecond(last: Sample, now: Sample): Long {
        val millis = now.atMillis - last.atMillis
        if (millis <= 0L) return 0L
        return (now.bytes - last.bytes).coerceAtLeast(0L) * BITS_PER_BYTE * MILLIS_PER_SECOND.toLong() / millis
    }

    /** What Media3 says about the decoder in use; null when it cannot be asked. */
    private fun isSoftware(mimeType: String?, decoderName: String): Boolean? {
        if (mimeType == null) return null
        return try {
            MediaCodecUtil.getDecoderInfos(mimeType, false, false)
                .firstOrNull { it.name == decoderName }
                ?.softwareOnly
        } catch (ignored: MediaCodecUtil.DecoderQueryException) {
            null
        }
    }

    private class Sample(
        val atMillis: Long,
        val counters: DecoderCounters?,
        val rendered: Int,
        val dropped: Int,
        val bytes: Long,
    )

    private companion object {
        const val DECODER_HISTORY = 6
        const val MILLIS_PER_SECOND = 1_000f
        const val BITS_PER_BYTE = 8L
    }
}

/** What only the controller knows, handed to [DiagnosticsSampler.sample]. */
internal data class ControllerFacts(
    val isLive: Boolean,
    val bytesReceived: Long,
    val lastByteAtMillis: Long,
    val softwareAllowed: Boolean,
    val recovery: RecoveryDiagnostics,
    val bufferMode: String,
    val maxVideoBitrate: Int?,
    val lastEngineError: String?,
)

private fun Int.toEngineState(): EngineState = when (this) {
    Player.STATE_BUFFERING -> EngineState.BUFFERING
    Player.STATE_READY -> EngineState.READY
    Player.STATE_ENDED -> EngineState.ENDED
    else -> EngineState.IDLE
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun dynamicRange(colorTransfer: Int): String? = when (colorTransfer) {
    C.COLOR_TRANSFER_ST2084 -> "HDR10 (PQ)"
    C.COLOR_TRANSFER_HLG -> "HLG"
    C.COLOR_TRANSFER_SDR, C.COLOR_TRANSFER_GAMMA_2_2 -> "SDR"
    else -> null
}
