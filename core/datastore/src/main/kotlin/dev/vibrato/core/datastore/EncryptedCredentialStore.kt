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
import dev.vibrato.source.api.CredentialStore
import dev.vibrato.source.api.Credentials
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores Xtream credentials encrypted at rest, keyed by source id.
 *
 * Backed by `EncryptedSharedPreferences` with an AES-256 master key held in the Android
 * Keystore, so the ciphertext is unreadable without the device's hardware-backed key
 * (docs/PLAN.md §1, AC-XT-04).
 *
 * Credentials live here and nowhere else. They are never written to the Room database,
 * so a database dump cannot leak them, and the export format in M5 must continue to omit
 * or separately encrypt them (AC-DATA-03).
 */
class EncryptedCredentialStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CredentialStore {

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

    override suspend fun credentials(sourceId: Long): Credentials? = withContext(ioDispatcher) {
        val username = preferences.getString(usernameKey(sourceId), null)
        val password = preferences.getString(passwordKey(sourceId), null)
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            null
        } else {
            Credentials(username, password)
        }
    }

    override suspend fun put(sourceId: Long, credentials: Credentials) = withContext(ioDispatcher) {
        preferences.edit()
            .putString(usernameKey(sourceId), credentials.username)
            .putString(passwordKey(sourceId), credentials.password)
            .apply()
    }

    override suspend fun clear(sourceId: Long) = withContext(ioDispatcher) {
        preferences.edit()
            .remove(usernameKey(sourceId))
            .remove(passwordKey(sourceId))
            .apply()
    }

    private fun usernameKey(sourceId: Long) = "source.$sourceId.username"

    private fun passwordKey(sourceId: Long) = "source.$sourceId.password"

    private companion object {
        const val FILE_NAME = "vibrato_credentials"
    }
}
