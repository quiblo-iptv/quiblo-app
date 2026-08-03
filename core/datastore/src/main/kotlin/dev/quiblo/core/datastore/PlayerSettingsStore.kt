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

package dev.quiblo.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SeekInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "player_settings",
)

/**
 * Persists the player tuning from the settings screen.
 *
 * Values are stored as enum *names*, not ordinals. An ordinal silently changes meaning the
 * moment someone reorders or inserts an entry, and the user's saved choice would quietly
 * become a different one. A name that no longer exists falls back to the default instead,
 * which is the only safe reading of a value this code no longer understands.
 *
 * Nothing here is sensitive, so this is plain `DataStore` rather than the encrypted store
 * credentials use (AC-XT-04).
 */
class PlayerSettingsStore(context: Context) {

    private val dataStore = context.applicationContext.playerSettingsDataStore

    val settings: Flow<PlayerSettings> = dataStore.data.map { preferences ->
        PlayerSettings(
            seekInterval = preferences.readEnum(SEEK_INTERVAL, SeekInterval.entries, PlayerSettings().seekInterval),
            bufferMode = preferences.readEnum(BUFFER_MODE, BufferMode.entries, PlayerSettings().bufferMode),
            maxBitrate = preferences.readEnum(MAX_BITRATE, MaxBitrateCap.entries, PlayerSettings().maxBitrate),
        )
    }

    suspend fun setSeekInterval(value: SeekInterval) = put(SEEK_INTERVAL, value.name)

    suspend fun setBufferMode(value: BufferMode) = put(BUFFER_MODE, value.name)

    suspend fun setMaxBitrate(value: MaxBitrateCap) = put(MAX_BITRATE, value.name)

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    private fun <T : Enum<T>> Preferences.readEnum(
        key: Preferences.Key<String>,
        values: List<T>,
        default: T,
    ): T {
        val stored = this[key] ?: return default
        return values.firstOrNull { it.name == stored } ?: default
    }

    private companion object {
        val SEEK_INTERVAL = stringPreferencesKey("seek_interval")
        val BUFFER_MODE = stringPreferencesKey("buffer_mode")
        val MAX_BITRATE = stringPreferencesKey("max_bitrate")
    }
}
