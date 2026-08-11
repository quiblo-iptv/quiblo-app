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

package dev.quiblo.core.datastore.di

import android.content.Context
import dev.quiblo.core.datastore.ChannelLogoStore
import dev.quiblo.core.datastore.ConsentStore
import dev.quiblo.core.datastore.DataStorePanelBlockStore
import dev.quiblo.core.datastore.EncryptedCredentialStore
import dev.quiblo.core.datastore.PlayerSettingsStore
import dev.quiblo.core.datastore.ProfileStore
import dev.quiblo.core.datastore.TmdbKeyStore
import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.PanelBlockStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Wiring owned by `:core:datastore`. */
val datastoreModule: Module = module {
    single<CredentialStore> { EncryptedCredentialStore(get<Context>()) }
    single<PanelBlockStore> { DataStorePanelBlockStore(get<Context>()) }
    single { PlayerSettingsStore(get<Context>()) }
    single { TmdbKeyStore(get<Context>()) }
    single { ChannelLogoStore(get<Context>()) }
    single { ProfileStore(get<Context>()) }
    single { ConsentStore(get<Context>()) }
}
