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

package dev.quiblo.feature.series

import androidx.annotation.StringRes
import dev.quiblo.source.api.SourceError

/**
 * Maps a typed [SourceError] to a localised message for the series detail screen.
 *
 * AC-PL-07 applies here as much as it does to loading a playlist: "Failed to load series
 * details" is exactly the bare message it forbids, and it hides the difference between a
 * dropped connection and a provider refusing the account — which need different actions
 * from the user.
 */
@StringRes
internal fun SourceError.seriesMessageRes(): Int = when (this) {
    SourceError.NoNetwork -> R.string.series_error_no_network
    SourceError.Timeout -> R.string.series_error_timeout
    SourceError.UnreachableHost -> R.string.series_error_unreachable
    SourceError.ProviderBlocked -> R.string.series_error_provider_blocked
    SourceError.Unauthorized -> R.string.series_error_unauthorized
    SourceError.SubscriptionExpired -> R.string.series_error_expired
    SourceError.AccountDisabled -> R.string.series_error_disabled
    SourceError.NotFound -> R.string.series_error_not_found
    SourceError.EmptyPlaylist -> R.string.series_error_not_found
    SourceError.NotAPlaylist -> R.string.series_error_unknown
    SourceError.FileUnreadable -> R.string.series_error_unknown
    is SourceError.HttpStatus -> R.string.series_error_http_status
    is SourceError.Unknown -> R.string.series_error_unknown
}

/** The status code to interpolate, for the one error that carries a number. */
internal fun SourceError.seriesMessageArg(): Int? = (this as? SourceError.HttpStatus)?.code
