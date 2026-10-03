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

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A deleted profile's settings go with it, and nobody else's do (`BUG-040`). */
class ProfileScopedPreferencesTest {

    private fun household() = mutablePreferencesOf(
        // From before profiles owned settings: belongs to nobody, read by everybody until they choose.
        stringPreferencesKey("theme_mode") to "DARK",
        booleanPreferencesKey("check_updates_on_launch") to true,
        stringPreferencesKey("theme_mode@3") to "LIGHT",
        stringSetPreferencesKey("hidden_tabs@3") to setOf("LIVE"),
        stringPreferencesKey("theme_mode@13") to "SYSTEM",
        booleanPreferencesKey("channel_logos@31") to true,
    )

    @Test
    fun `the owner of a key is the number after its last at sign`() {
        assertEquals(3L, scopedOwner("theme_mode@3"))
        assertEquals(13L, scopedOwner("theme_mode@13"))
        assertNull(scopedOwner("theme_mode"))
        assertNull(scopedOwner("odd@name"))
    }

    @Test
    fun `clearing one profile removes every key it wrote, of every type`() {
        val preferences = household()

        preferences.removeScoped { it == 3L }

        assertEquals(
            setOf("theme_mode", "check_updates_on_launch", "theme_mode@13", "channel_logos@31"),
            preferences.asMap().keys.map { it.name }.toSet(),
        )
    }

    @Test
    fun `profile 3 is not profile 13 or 31`() {
        val preferences = household()

        preferences.removeScoped { it == 3L }

        assertEquals("SYSTEM", preferences[stringPreferencesKey("theme_mode@13")])
        assertEquals(true, preferences[booleanPreferencesKey("channel_logos@31")])
    }

    @Test
    fun `clearing everyone but the living removes what deleted profiles left`() {
        val preferences = household()

        preferences.removeScoped { it !in setOf(13L) }

        assertEquals(
            setOf("theme_mode", "check_updates_on_launch", "theme_mode@13"),
            preferences.asMap().keys.map { it.name }.toSet(),
        )
    }
}
