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

import dev.quiblo.core.data.diagnostics.Diagnosis
import dev.quiblo.core.data.diagnostics.StreamEvidence
import dev.quiblo.core.data.diagnostics.StreamFault
import dev.quiblo.core.media.FailureDetails
import dev.quiblo.core.media.PlaybackError
import dev.quiblo.core.media.PlaybackState

/**
 * Where the diagnosis of the current failure has got to (`FEAT-035`).
 *
 * The error screen appears the moment playback fails, exactly as it always has — AC-PLAY-05 is
 * about when the viewer is told *something* — and says "checking why" until the evidence is in.
 */
sealed interface DiagnosisState {

    /** Nothing has failed. */
    data object None : DiagnosisState

    /** Something has failed and the account check is under way. */
    data object Checking : DiagnosisState

    data class Ready(val diagnosis: Diagnosis) : DiagnosisState
}

/**
 * The player's failure, in the terms the diagnosis reads.
 *
 * Null when nothing has failed. The two vocabularies are kept apart on purpose — `:core:data`
 * does not know the player exists — so this is the one place they meet.
 */
internal fun PlaybackState.streamEvidence(): StreamEvidence? {
    val error = error ?: return null
    val details = failure ?: FailureDetails()
    return StreamEvidence(
        fault = if (details.httpStatus != null) StreamFault.BAD_STATUS else error.fault(),
        httpStatus = details.httpStatus,
        engineCode = details.engineCode,
        bytesReceived = details.bytesReceived,
        hadPlayed = details.hadPlayed,
        retries = details.retries,
    )
}

private fun PlaybackError.fault(): StreamFault = when (this) {
    PlaybackError.UNREACHABLE -> StreamFault.UNREACHABLE
    PlaybackError.TIMEOUT -> StreamFault.TIMEOUT
    PlaybackError.NETWORK -> StreamFault.NETWORK
    PlaybackError.UNSUPPORTED_FORMAT -> StreamFault.FORMAT
    PlaybackError.DRM_UNSUPPORTED -> StreamFault.DRM
    // Each of these is a status in practice. Without one — a local file that went missing — there
    // is nothing the diagnosis can say about whose it was.
    PlaybackError.SOURCE_GONE,
    PlaybackError.AUTH_REJECTED,
    PlaybackError.PROVIDER_REFUSED,
    PlaybackError.UNKNOWN,
    -> StreamFault.OTHER
}

/**
 * What **Copy details** puts on the clipboard.
 *
 * The verdict and the redacted evidence line, and nothing else — not the channel name and never a
 * URL. A viewer pastes this into a message to somebody, and it has to be safe to paste anywhere.
 */
fun Diagnosis.reportText(): String = "Quiblo playback: ${verdict.name} · $details"
