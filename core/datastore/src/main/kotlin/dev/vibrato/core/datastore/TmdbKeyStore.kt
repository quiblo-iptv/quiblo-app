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

package dev.vibrato.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The user's own TMDB key.
 *
 * Encrypted, in the same store as playlist credentials rather than in plain DataStore
 * beside the playback settings. It is a credential: it identifies its owner to a third
 * party, it is rate-limited against them, and it can be revoked. Treating it as a
 * preference because it happens to be configured on a settings screen would be a
 * categorisation mistake with a security consequence (AC-XT-04 applies the same reasoning
 * to playlist passwords).
 *
 * Exposed as a [StateFlow] so enabling the feature updates every screen at once, and read
 * eagerly on construction because the value is a single short string.
 */
class TmdbKeyStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val appContext = context.applicationContext

    private val preferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _apiKey = MutableStateFlow<String?>(null)

    /** Null when unset, which is also what "the feature is off" means. */
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    suspend fun load() = withContext(ioDispatcher) {
        _apiKey.value = preferences.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
    }

    suspend fun set(apiKey: String?) = withContext(ioDispatcher) {
        val cleaned = apiKey?.trim()?.takeIf { it.isNotBlank() }
        preferences.edit().apply {
            if (cleaned == null) remove(KEY_API) else putString(KEY_API, cleaned)
        }.apply()
        _apiKey.value = cleaned
    }

    private companion object {
        const val FILE_NAME = "vibrato_tmdb"
        const val KEY_API = "tmdb_api_key"
    }
}
