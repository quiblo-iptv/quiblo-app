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

package dev.vibrato.feature.sources

import androidx.annotation.StringRes
import dev.vibrato.source.api.SourceError

/**
 * Maps a typed [SourceError] to a localised, human-readable string resource.
 *
 * AC-PL-07 requires a specific message for every failure and forbids a raw stack trace or
 * a bare "Error". Keeping the mapping here — in the UI layer, against `strings.xml` —
 * is what keeps the domain layer free of user-facing text (AC-NFR-08).
 */
@StringRes
internal fun SourceError.messageRes(): Int = when (this) {
    SourceError.NoNetwork -> R.string.source_error_no_network
    SourceError.Timeout -> R.string.source_error_timeout
    SourceError.UnreachableHost -> R.string.source_error_unreachable
    SourceError.NotFound -> R.string.source_error_not_found
    SourceError.NotAPlaylist -> R.string.source_error_not_a_playlist
    SourceError.EmptyPlaylist -> R.string.source_error_empty
    SourceError.FileUnreadable -> R.string.source_error_file_unreadable
    is SourceError.HttpStatus -> R.string.source_error_http_status
    is SourceError.Unknown -> R.string.source_error_unknown
}

/** The status code to interpolate, for the one error that carries a number. */
internal fun SourceError.messageArg(): Int? = (this as? SourceError.HttpStatus)?.code
