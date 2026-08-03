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

package dev.vibrato.core.datastore.di

import android.content.Context
import dev.vibrato.core.datastore.EncryptedCredentialStore
import dev.vibrato.core.datastore.PlayerSettingsStore
import dev.vibrato.source.api.CredentialStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Wiring owned by `:core:datastore`. */
val datastoreModule: Module = module {
    single<CredentialStore> { EncryptedCredentialStore(get<Context>()) }
    single { PlayerSettingsStore(get<Context>()) }
}
