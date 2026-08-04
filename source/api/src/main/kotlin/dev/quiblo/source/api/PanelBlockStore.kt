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
 * When a provider last refused this client, so the backoff outlives the process.
 *
 * Kept on disk rather than in memory because of what a user does when an app tells them
 * their provider is refusing it: they force-stop it and open it again. An in-memory
 * backoff is cleared by exactly that, and the reopened app goes straight back to asking —
 * turning the one reaction a blocked user is most likely to have into the thing that
 * extends the block.
 *
 * Implemented by `:core:datastore`.
 */
interface PanelBlockStore {

    /** Epoch millis before which no request should be made, or 0 when not blocked. */
    suspend fun blockedUntil(): Long

    suspend fun setBlockedUntil(epochMillis: Long)
}
