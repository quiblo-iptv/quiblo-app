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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "profiles",
)

/**
 * Which profile is watching, remembered between launches.
 *
 * Only the id. Everything about a profile lives in the database, and holding a name here as
 * well would be two copies of one fact that can disagree.
 *
 * A stored id whose profile no longer exists is not an error — it is how guest ends. Guest
 * profiles are deleted at startup, so the id left behind points at nothing and the chooser
 * opens, which is exactly the desired behaviour and needs no separate "was guest" flag.
 */
class ProfileStore(context: Context) {

    private val dataStore = context.applicationContext.profileDataStore

    /** Null when nobody has chosen yet, or when whoever was chosen no longer exists. */
    val activeProfileId: Flow<Long?> = dataStore.data.map { it[ACTIVE_PROFILE_ID] }

    suspend fun setActiveProfileId(id: Long?) {
        dataStore.edit { preferences ->
            if (id == null) preferences.remove(ACTIVE_PROFILE_ID) else preferences[ACTIVE_PROFILE_ID] = id
        }
    }

    private companion object {
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
    }
}
