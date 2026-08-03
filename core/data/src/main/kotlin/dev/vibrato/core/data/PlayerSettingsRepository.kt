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

package dev.vibrato.core.data

import dev.vibrato.core.datastore.PlayerSettingsStore
import dev.vibrato.core.model.BufferMode
import dev.vibrato.core.model.MaxBitrateCap
import dev.vibrato.core.model.PlayerSettings
import dev.vibrato.core.model.SeekInterval
import kotlinx.coroutines.flow.Flow

/**
 * Player tuning, for the settings screen to edit and the player to read.
 *
 * A thin pass-through over [PlayerSettingsStore] on purpose. Features talk to `:core:data`
 * and nothing else (docs/PLAN.md §2), and honouring that for a small case is what stops
 * the rule eroding: the first feature to reach past it makes the second one reasonable.
 */
class PlayerSettingsRepository(private val store: PlayerSettingsStore) {

    /** Emits on every change, so a player already on screen picks up an edit immediately. */
    val settings: Flow<PlayerSettings> = store.settings

    suspend fun setSeekInterval(value: SeekInterval) = store.setSeekInterval(value)

    suspend fun setBufferMode(value: BufferMode) = store.setBufferMode(value)

    suspend fun setMaxBitrate(value: MaxBitrateCap) = store.setMaxBitrate(value)
}
