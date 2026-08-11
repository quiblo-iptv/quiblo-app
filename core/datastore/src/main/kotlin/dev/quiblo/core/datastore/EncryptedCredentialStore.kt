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

// The suppression is file-level because the deprecation is reported on the imports as
// well as the uses. See the note above the class for why this library is still here.
@file:Suppress("DEPRECATION")

package dev.quiblo.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.Credentials
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
/*
 * **`androidx.security-crypto` is deprecated, and this is a decision to keep it for now.**
 *
 * Jetpack Security is no longer maintained: `EncryptedSharedPreferences` and `MasterKey` are
 * deprecated with no drop-in replacement, and Google's guidance is to use the Android Keystore
 * directly. `agile/006` gate 6 found this the moment a compiler warning stopped being something
 * one could scroll past, and it is the most consequential thing that gate has turned up.
 *
 * It is suppressed rather than migrated **here**, because this is the file holding a viewer's
 * panel password. Changing how it is encrypted means re-encrypting what is already on people's
 * devices, and a migration that goes wrong does not fail loudly — it silently loses the
 * credential and looks like a provider refusing the account. That is its own piece of work with
 * its own test, and it is written down in gate 6 rather than done in passing.
 *
 * What is *not* wrong today: the library still works, the ciphertext is still hardware-backed,
 * and nothing about the current storage is known to be insecure. Deprecated is not broken; it
 * means no fixes are coming, which is why this has a deadline in the plan rather than a shrug.
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
        const val FILE_NAME = "quiblo_credentials"
    }
}
