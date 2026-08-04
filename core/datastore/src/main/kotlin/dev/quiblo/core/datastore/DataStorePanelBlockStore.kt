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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.quiblo.source.api.PanelBlockStore
import kotlinx.coroutines.flow.first

private val Context.panelBlockDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "panel_block",
)

/**
 * The provider backoff, on disk.
 *
 * Its own tiny store rather than a key in the settings file, because it is not a setting:
 * nothing the user chose is in it, and it is written from a background code path that has
 * no business touching the file the settings screen owns.
 *
 * A deadline is stored rather than a "blocked" flag, so a stale value expires on its own.
 * A flag written by an app that was killed before it could clear it would leave the user
 * permanently unable to refresh, with nothing on screen explaining why.
 */
class DataStorePanelBlockStore(context: Context) : PanelBlockStore {

    private val dataStore = context.applicationContext.panelBlockDataStore

    override suspend fun blockedUntil(): Long = dataStore.data.first()[BLOCKED_UNTIL] ?: 0L

    override suspend fun setBlockedUntil(epochMillis: Long) {
        dataStore.edit { it[BLOCKED_UNTIL] = epochMillis }
    }

    private companion object {
        val BLOCKED_UNTIL = longPreferencesKey("blocked_until_epoch_millis")
    }
}
