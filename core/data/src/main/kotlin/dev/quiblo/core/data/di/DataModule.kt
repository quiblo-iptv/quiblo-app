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

package dev.quiblo.core.data.di

import android.content.Context
import dev.quiblo.core.data.CategoryRepository
import dev.quiblo.core.data.ChannelLogoRepository
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.GuideRepository
import dev.quiblo.core.data.LocalFileContentFetcher
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.data.backup.BackupRepository
import dev.quiblo.core.model.SourceKind
import dev.quiblo.core.network.HttpContentFetcher
import dev.quiblo.source.api.ContentFetcher
import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.PanelBlockStore
import dev.quiblo.source.iptvorg.IptvOrgClient
import dev.quiblo.source.m3u.M3uSource
import dev.quiblo.source.tmdb.TmdbClient
import dev.quiblo.source.xtream.createXtreamSource
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wiring for the data layer.
 *
 * The `MediaSource` map is the extension point from docs/FREEZE.md §4.2: adding Xtream in
 * M4 means adding one entry here and one module, with no change to any feature.
 */
val dataModule: Module = module {

    single<List<ContentFetcher>> {
        listOf(
            get<HttpContentFetcher>(),
            LocalFileContentFetcher(get<Context>()),
        )
    }

    single<Map<SourceKind, MediaSource>> {
        mapOf(
            SourceKind.M3U to M3uSource(get()),
            // Adding a protocol is one entry here plus one module (docs/FREEZE.md §4.2).
            SourceKind.XTREAM to createXtreamSource(get<HttpClient>(), get<CredentialStore>(), get<PanelBlockStore>()),
        )
    }

    single { SourceRepository(get(), get(), get(), get()) }
    single { ChannelRepository(get(), get(), get(), get()) }
    single { WatchHistoryRepository(get()) }
    single { CategoryRepository(get(), get()) }
    single { GuideRepository(get(), get(), get()) }
    single { BackupRepository(get(), get()) }
    single { PlayerSettingsRepository(get()) }
    single { TmdbClient(get<HttpClient>()) }
    single { TitleMetadataRepository(get(), get(), get()) }
    single { IptvOrgClient(get<HttpClient>()) }
    single { ChannelLogoRepository(get(), get(), get()) }
}
