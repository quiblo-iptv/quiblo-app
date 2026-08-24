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

// Named for what the file is about rather than for the one object in it: the object is three key
// factories and the useful half of this file is the six extensions under it.
@file:Suppress("MatchingDeclarationName")

package dev.quiblo.core.datastore

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * One preference, filed under the profile that chose it.
 *
 * **Settings used to be one answer for the whole television and that was the bug.** Two people
 * sharing a set-top box share a playlist and an account; they do not share a taste in theme, a
 * seek interval, or which shelves they want to see. Every preference in [PlayerSettingsStore] is
 * now written under a key of its own per profile, so switching person redraws the app rather than
 * handing the next viewer the last one's choices.
 *
 * **A profile that has never written a value reads the unscoped one.** That is the migration, and
 * it is deliberately not a one-off copy: an install that has been configured once keeps every
 * setting it had, for every profile, until somebody changes one — at which point only that
 * profile moves. Nothing is lost, nobody is asked to set the app up again, and there is no
 * moment where a half-run migration has written some keys and not others.
 *
 * The separator is `@` because a preference name is a plain string and a profile id is a number:
 * neither can contain it, so `theme_mode@3` cannot collide with any name this app will ever use.
 */
internal object Scoped {

    private const val SEPARATOR = '@'

    private fun name(base: String, profileId: Long) = "$base$SEPARATOR$profileId"

    fun bool(base: String, profileId: Long): Preferences.Key<Boolean> =
        booleanPreferencesKey(name(base, profileId))

    fun text(base: String, profileId: Long): Preferences.Key<String> =
        stringPreferencesKey(name(base, profileId))

    fun textSet(base: String, profileId: Long): Preferences.Key<Set<String>> =
        stringSetPreferencesKey(name(base, profileId))
}

/** This profile's answer, or the one the app had before profiles owned settings. See [Scoped]. */
internal fun Preferences.scopedBoolean(base: String, profileId: Long): Boolean? =
    this[Scoped.bool(base, profileId)] ?: this[booleanPreferencesKey(base)]

/** This profile's answer, or the one the app had before profiles owned settings. See [Scoped]. */
internal fun Preferences.scopedString(base: String, profileId: Long): String? =
    this[Scoped.text(base, profileId)] ?: this[stringPreferencesKey(base)]

/** This profile's answer, or the one the app had before profiles owned settings. See [Scoped]. */
internal fun Preferences.scopedStringSet(base: String, profileId: Long): Set<String>? =
    this[Scoped.textSet(base, profileId)] ?: this[stringSetPreferencesKey(base)]

internal fun MutablePreferences.putScoped(base: String, profileId: Long, value: Boolean) {
    this[Scoped.bool(base, profileId)] = value
}

internal fun MutablePreferences.putScoped(base: String, profileId: Long, value: String) {
    this[Scoped.text(base, profileId)] = value
}

internal fun MutablePreferences.putScoped(base: String, profileId: Long, value: Set<String>) {
    this[Scoped.textSet(base, profileId)] = value
}
