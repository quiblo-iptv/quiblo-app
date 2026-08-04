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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.channelLogoDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "channel_logos",
)

/**
 * Whether to fill in missing channel logos from the iptv-org reference list, and when the
 * index was last downloaded.
 *
 * **Off by default, and that is not a UI preference — it is AC-NFR-03.** A clean install
 * must make no request to a host the user did not configure, verified by packet capture,
 * and iptv-org is a host nobody configured. The same reasoning keeps TMDB behind a key the
 * user has to paste in. The switch is what configures it.
 *
 * Nothing here is sensitive, so this is plain `DataStore` rather than the encrypted store
 * credentials use (AC-XT-04).
 */
class ChannelLogoStore(context: Context) {

    private val dataStore = context.applicationContext.channelLogoDataStore

    val isEnabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }

    /**
     * The same answer, read once.
     *
     * For the lookup path, which runs per channel and must not hold a subscription open for
     * the life of a browse screen just to read one boolean.
     */
    suspend fun isEnabledNow(): Boolean = isEnabled.first()

    /** Epoch millis of the last successful download, or 0 if it has never happened. */
    suspend fun lastRefreshedAt(): Long = dataStore.data.first()[LAST_REFRESHED] ?: 0L

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENABLED] = enabled
            // Turning it off forgets when the index was fetched, so turning it back on
            // later gets a fresh one rather than trusting a timestamp from a period the
            // feature was not running.
            if (!enabled) preferences.remove(LAST_REFRESHED)
        }
    }

    suspend fun markRefreshed(timestamp: Long) {
        dataStore.edit { it[LAST_REFRESHED] = timestamp }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("channel_logos_enabled")
        val LAST_REFRESHED = longPreferencesKey("channel_logos_refreshed_at")
    }
}
