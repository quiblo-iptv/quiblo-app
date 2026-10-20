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

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiblo.core.media.AudioDiagnostics
import dev.quiblo.core.media.FrameDiagnostics
import dev.quiblo.core.media.PlaybackDiagnostics
import dev.quiblo.core.media.VideoDiagnostics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the engine knows about what is playing, drawn over the video (`FEAT-040`).
 *
 * For a viewer to screenshot when something plays badly, so that a stutter can be told apart from
 * a slow server, a slow decoder or a screen at the wrong refresh rate without guessing. Shared by
 * the phone and the television.
 *
 * Takes no input at all: no click, no focus. Taps and remote presses reach the player underneath
 * as if it were not there, and the button that opened it closes it.
 *
 * What would explain a stutter is drawn in amber — frames being dropped, a software decoder, a
 * frame rate the screen cannot show evenly — so the screenshot points at its own answer.
 */
@Composable
fun PlaybackInfoOverlay(diagnostics: PlaybackDiagnostics?, modifier: Modifier = Modifier) {
    val device = rememberDeviceFacts()

    Column(
        modifier = modifier
            .padding(12.dp)
            .widthIn(max = 760.dp)
            .background(Color.Black.copy(alpha = BACKDROP_ALPHA), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val taken = diagnostics?.takenAtEpochMillis ?: System.currentTimeMillis()
        Text(
            text = stringResource(R.string.player_info_title, clockFormat().format(Date(taken))),
            color = Color.White,
            fontSize = TITLE_SIZE.sp,
            fontWeight = FontWeight.Bold,
        )
        if (diagnostics == null) {
            InfoText(stringResource(R.string.player_info_collecting))
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                StreamSection(diagnostics)
                diagnostics.video?.let { VideoSection(it) }
                diagnostics.audio?.let { AudioSection(it) }
            }
            Column(modifier = Modifier.weight(1f)) {
                diagnostics.frames?.let { FramesSection(it) }
                NetworkSection(diagnostics)
                RecoverySection(diagnostics)
                DeviceSection(device, contentFps = diagnostics.video?.frameRate)
            }
        }
    }
}

@Composable
private fun StreamSection(d: PlaybackDiagnostics) {
    Heading(R.string.player_info_section_stream)
    val playing = stringResource(
        if (d.isPlaying) R.string.player_info_playing else R.string.player_info_paused,
    )
    // The engine's own state name: read by whoever fixes the bug, not display text for a viewer.
    InfoRow(R.string.player_info_state, "${d.engineState.name} · $playing")
    val kind = if (d.isLive) R.string.player_info_kind_live else R.string.player_info_kind_vod
    InfoRow(R.string.player_info_kind, stringResource(kind))
    InfoRow(R.string.player_info_container, d.containerMimeType ?: unknown())
    val duration = d.durationMillis?.let { " / " + formatClock(it) }.orEmpty()
    InfoRow(R.string.player_info_position, formatClock(d.positionMillis) + duration)
    InfoRow(
        R.string.player_info_buffered,
        formatSeconds(d.bufferedAheadMillis),
        warn = d.bufferedAheadMillis < LOW_BUFFER_MILLIS,
    )
    val cap = d.maxVideoBitrate?.let { formatBitrate(it.toLong()) }
        ?: stringResource(R.string.player_info_no_cap)
    InfoRow(R.string.player_info_buffer_setting, "${d.bufferMode} · $cap")
    // Amber past the first: an engine carried over from what played before (`BUG-064`).
    InfoRow(R.string.player_info_engine_items, d.engineItems.toString(), warn = d.engineItems > 1)
}

@Composable
private fun VideoSection(v: VideoDiagnostics) {
    Heading(R.string.player_info_section_video)
    val codec = listOfNotNull(v.mimeType, v.codecs?.let { "($it)" }).joinToString(" ")
    InfoRow(R.string.player_info_codec, codec.ifEmpty { unknown() })
    val rate = v.frameRate?.let { " @ ${formatFrameRate(it)} fps" }.orEmpty()
    InfoRow(R.string.player_info_size, "${v.width}×${v.height}$rate")
    InfoRow(R.string.player_info_bitrate, v.bitrate?.let { formatBitrate(it.toLong()) } ?: unknown())
    InfoRow(R.string.player_info_range, v.dynamicRange ?: unknown())
    val decoderKind = when (v.decoderIsSoftware) {
        true -> " (" + stringResource(R.string.player_info_software) + ")"
        false -> " (" + stringResource(R.string.player_info_hardware) + ")"
        null -> ""
    }
    InfoRow(
        R.string.player_info_decoder,
        (v.decoderName ?: unknown()) + decoderKind,
        warn = v.decoderIsSoftware == true,
    )
    InfoRow(R.string.player_info_software_allowed, yesNo(v.softwareAllowed))
    if (v.decoderHistory.isNotEmpty()) {
        InfoRow(R.string.player_info_history, "")
        val format = clockFormat()
        v.decoderHistory.forEach { event ->
            InfoText("  ${format.format(Date(event.atEpochMillis))} ${event.name} (${event.initMillis} ms)")
        }
    }
}

@Composable
private fun AudioSection(a: AudioDiagnostics) {
    Heading(R.string.player_info_section_audio)
    val details = listOfNotNull(
        a.mimeType,
        a.codecs?.let { "($it)" },
        a.channels?.let { "$it ch" },
        a.sampleRate?.let { "$it Hz" },
        a.bitrate?.let { formatBitrate(it.toLong()) },
    )
    InfoRow(R.string.player_info_codec, details.joinToString(" · ").ifEmpty { unknown() })
    InfoRow(R.string.player_info_decoder, a.decoderName ?: unknown())
}

@Composable
private fun FramesSection(f: FrameDiagnostics) {
    Heading(R.string.player_info_section_frames)
    InfoRow(
        R.string.player_info_per_second,
        String.format(Locale.ROOT, "%.1f · %.1f", f.renderedPerSecond, f.droppedPerSecond),
        warn = f.droppedPerSecond > 0f,
    )
    InfoRow(
        R.string.player_info_totals,
        "${f.rendered} · ${f.dropped} · ${f.skipped}",
        warn = f.dropped > 0L,
    )
    InfoRow(
        R.string.player_info_worst,
        "${f.maxConsecutiveDropped} · ${f.droppedToKeyframe}",
        warn = f.droppedToKeyframe > 0,
    )
}

@Composable
private fun NetworkSection(d: PlaybackDiagnostics) {
    val n = d.network
    Heading(R.string.player_info_section_network)
    InfoRow(R.string.player_info_last_second, formatBitrate(n.lastSecondBitsPerSecond))
    InfoRow(R.string.player_info_estimate, n.estimateBitsPerSecond?.let(::formatBitrate) ?: unknown())
    InfoRow(R.string.player_info_received, formatBytes(n.bytesReceived))
    val lastByte = n.millisSinceLastByte?.let {
        stringResource(R.string.player_info_last_byte_ago, formatSeconds(it))
    }
    InfoRow(R.string.player_info_last_byte, lastByte ?: unknown())
}

@Composable
private fun RecoverySection(d: PlaybackDiagnostics) {
    val r = d.recovery
    Heading(R.string.player_info_section_recovery)
    InfoRow(R.string.player_info_retries, "${r.retries} · ${r.rebuffers} · ${formatSeconds(r.loadTimeMillis)}")
    InfoRow(
        R.string.player_info_reloads,
        "${r.stallReloads} · ${r.stutterReloads} · ${r.hardwareDecoderRetries} · ${r.liveEdgeRejoins}",
        warn = r.stutterReloads > 0 || r.hardwareDecoderRetries > 0,
    )
    d.lastEngineError?.let { InfoRow(R.string.player_info_last_error, it, warn = true) }
}

@Composable
private fun DeviceSection(device: DeviceFacts, contentFps: Float?) {
    Heading(R.string.player_info_section_device)
    InfoRow(R.string.player_info_model, device.model)
    val judders = contentFps != null && isJudderProne(contentFps, device.refreshRate)
    InfoRow(R.string.player_info_display, device.display, warn = judders)
    InfoRow(R.string.player_info_app, device.appVersion)
}

/** The device, as the overlay draws it. */
private class DeviceFacts(val model: String, val display: String, val refreshRate: Float, val appVersion: String)

/**
 * The model and version read once; the display read on every draw, which is once a second.
 *
 * A panel that switches its refresh rate for a film does so after the overlay is open, and a rate
 * read once at opening would have shown the one it switched away from (`BUG-064`).
 */
@Composable
private fun rememberDeviceFacts(): DeviceFacts {
    val context = LocalContext.current
    val view = LocalView.current
    val model = remember {
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})"
    }
    val appVersion = remember(context) { context.versionName() }
    val display = view.display
    val mode = display?.mode
    val refreshRate = display?.refreshRate ?: 0f
    return DeviceFacts(
        model = model,
        display = mode?.let {
            "${it.physicalWidth}×${it.physicalHeight} @ ${formatFrameRate(refreshRate)} Hz"
        }.orEmpty(),
        refreshRate = refreshRate,
        appVersion = appVersion,
    )
}

private fun Context.versionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

@Composable
private fun Heading(label: Int) {
    Text(
        text = stringResource(label).uppercase(Locale.ROOT),
        color = HEADING,
        fontSize = TEXT_SIZE.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun InfoRow(label: Int, value: String, warn: Boolean = false) {
    InfoText("${stringResource(label)}: $value", color = if (warn) WARNING else Color.White)
}

@Composable
private fun InfoText(text: String, color: Color = Color.White) {
    Text(
        text = text,
        color = color,
        fontSize = TEXT_SIZE.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = LINE_HEIGHT.sp,
    )
}

@Composable
private fun unknown(): String = stringResource(R.string.player_info_unknown)

@Composable
private fun yesNo(value: Boolean): String =
    stringResource(if (value) R.string.player_info_yes else R.string.player_info_no)

/** A wall-clock time with seconds, so screenshots taken a few seconds apart sort themselves. */
private fun clockFormat() = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

private const val BACKDROP_ALPHA = 0.62f
private const val TITLE_SIZE = 12
private const val TEXT_SIZE = 10
private const val LINE_HEIGHT = 13

/** Under two seconds of buffer, a film is one slow answer from a rebuffer. */
private const val LOW_BUFFER_MILLIS = 2_000L

private val HEADING = Color(0xFF9ECBFF)
private val WARNING = Color(0xFFFFC14D)
