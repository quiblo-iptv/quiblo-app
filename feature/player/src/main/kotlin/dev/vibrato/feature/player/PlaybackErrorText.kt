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

import androidx.annotation.StringRes
import dev.vibrato.core.media.PlaybackError

/**
 * Maps a typed [PlaybackError] to a localised message.
 *
 * AC-PLAY-05 requires a clear error rather than a hang, and never a raw engine
 * exception. The wording lives here so it stays translatable (AC-NFR-08).
 */
@StringRes
internal fun PlaybackError?.messageRes(): Int = when (this) {
    PlaybackError.NETWORK -> R.string.player_error_network
    PlaybackError.UNREACHABLE -> R.string.player_error_unreachable
    PlaybackError.TIMEOUT -> R.string.player_error_timeout
    PlaybackError.UNSUPPORTED_FORMAT -> R.string.player_error_format
    PlaybackError.DRM_UNSUPPORTED -> R.string.player_error_drm
    PlaybackError.SOURCE_GONE -> R.string.player_error_gone
    PlaybackError.UNKNOWN, null -> R.string.player_error_unknown
}
