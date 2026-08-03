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
 * An Xtream username and password.
 *
 * [toString] is overridden and the class is deliberately **not** a `data class`, because
 * a generated `toString` would print the password into any log line, crash trace or
 * error message that happened to interpolate it. AC-XT-04 requires credentials to appear
 * in no log output, no export file and no crash trace, and the cheapest way to guarantee
 * that is to make it impossible to print them by accident.
 */
class Credentials(
    val username: String,
    val password: String,
) {
    /** Always redacted. Never change this to include the real values. */
    override fun toString(): String = "Credentials(username=***, password=***)"

    override fun equals(other: Any?): Boolean =
        this === other || (other is Credentials && username == other.username && password == other.password)

    override fun hashCode(): Int = 31 * username.hashCode() + password.hashCode()
}

/**
 * Encrypted, on-device storage for source credentials.
 *
 * Implemented by `:core:datastore`. Credentials never leave the device and are never
 * written to the database, so a database dump or a settings export cannot leak them
 * (docs/FREEZE.md §4.6, AC-DATA-03).
 */
interface CredentialStore {

    suspend fun credentials(sourceId: Long): Credentials?

    suspend fun put(sourceId: Long, credentials: Credentials)

    suspend fun clear(sourceId: Long)
}
