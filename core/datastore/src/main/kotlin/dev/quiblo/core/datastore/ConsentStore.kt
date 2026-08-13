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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.consentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "consent",
)

/**
 * Whether this install has been shown the terms, and which version of them (`FREEZE.md`
 * Amendment 9).
 *
 * **A version rather than a boolean**, because those are the only two designs and one of them is
 * a trap: with a boolean, a materially changed policy leaves exactly two options — ask nobody,
 * which means people are held to terms they never saw, or ask everybody, which trains them to
 * dismiss the screen. A number lets a real change ask again and a corrected sentence stay quiet.
 *
 * **Per install, not per profile.** Profiles own favourites and resume positions; consent is not
 * a viewing preference, and the Guest profile must not be asked every time it is used.
 *
 * Nothing here is sensitive — it is one integer saying a screen was seen — so this is plain
 * `DataStore` rather than the encrypted store credentials use.
 */
class ConsentStore(context: Context) {

    private val dataStore = context.applicationContext.consentDataStore

    /** The terms version this install accepted, or `null` if it has never been asked. */
    val acceptedVersion: Flow<Int?> = dataStore.data.map { it[ACCEPTED_VERSION] }

    /** Whether the terms need showing now. See [needsConsent] for the rule and why. */
    val needsConsent: Flow<Boolean> = acceptedVersion.map { needsConsent(it, CURRENT_VERSION) }

    suspend fun accept() {
        dataStore.edit { it[ACCEPTED_VERSION] = CURRENT_VERSION }
    }

    companion object {
        /**
         * The version of the terms in the app and on the wiki.
         *
         * **Raise this only when what somebody agreed to has materially changed** — a new
         * category of data, a new outbound host, a change to what the project claims about
         * content. Fixing a typo or rewording a sentence does not qualify, and raising it for
         * one is how a consent screen becomes something people learn to press through.
         */
        const val CURRENT_VERSION = 1

        private val ACCEPTED_VERSION = intPreferencesKey("accepted_terms_version")
    }
}

/**
 * Whether an install that accepted [accepted] should be asked again for terms version [current].
 *
 * A free function so the rule can be tested without a `Context`, a `DataStore` or a device: the
 * decision is arithmetic and the storage is not, and only one of the two is worth a Robolectric
 * image to check.
 *
 * **Written as "accepted something older" rather than "did not accept the current one"**, so an
 * install carrying a *newer* number than this build knows about is left alone. That happens on a
 * downgrade, and asking somebody to re-accept terms they have already agreed to a later version
 * of would be a screen with nothing behind it.
 */
internal fun needsConsent(accepted: Int?, current: Int): Boolean =
    accepted == null || accepted < current
