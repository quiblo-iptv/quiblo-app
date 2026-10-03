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

package dev.quiblo.source.api

/**
 * What the provider says about the account right now (`FEAT-035`).
 *
 * Asked when a stream fails, so the error can say whose side the failure is on. Every case is
 * something the provider *answered* — or, for [Unreachable], something it failed to answer while
 * the device was online. "We could not find out" is not a case here: it is a null from
 * [MediaSource.accountHealth], and the verdict built on it is "undetermined" rather than a guess.
 */
sealed interface AccountHealth {

    /**
     * The account is usable.
     *
     * Each figure is null when the panel did not send it, and a null is never read as a number.
     *
     * @property expiresAtEpochMillis null for an account that does not expire.
     */
    data class Ok(
        val expiresAtEpochMillis: Long?,
        val activeConnections: Int?,
        val maxConnections: Int?,
    ) : AccountHealth {

        /** True only when both figures are known and every allowed screen is in use. */
        val isAtConnectionLimit: Boolean
            get() = activeConnections != null && maxConnections != null &&
                maxConnections > 0 && activeConnections >= maxConnections
    }

    /** The subscription has ended, by its status or by its expiry date. */
    data class Expired(val atEpochMillis: Long?) : AccountHealth

    /** The provider reports the account as banned or disabled. */
    data object Disabled : AccountHealth

    /** The provider no longer accepts the stored username or password. */
    data object CredentialsRejected : AccountHealth

    /** The provider's server did not answer: unknown host, refused connection or timeout. */
    data object Unreachable : AccountHealth

    /** The provider's server answered with an error status of its own. */
    data class ServerError(val code: Int) : AccountHealth

    /** The provider's firewall is refusing this client for a while ([SourceError.ProviderBlocked]). */
    data object Blocked : AccountHealth
}
