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
import dev.quiblo.core.data.AndroidPickedSubtitleFiles
import dev.quiblo.core.data.ApplicationScope
import dev.quiblo.core.data.CatalogueIdentityBackfill
import dev.quiblo.core.data.CategoryRepository
import dev.quiblo.core.data.ChannelLogoRepository
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.FeedRowCacheRepository
import dev.quiblo.core.data.GuideRepository
import dev.quiblo.core.data.LocalFileContentFetcher
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.PopularTitlesRepository
import dev.quiblo.core.data.ProfileRepository
import dev.quiblo.core.data.RecommendationRepository
import dev.quiblo.core.data.ScriptFilterRepository
import dev.quiblo.core.data.SearchRepository
import dev.quiblo.core.data.SeriesPreferenceRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.SubtitleRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.TitleMetadataScanner
import dev.quiblo.core.data.TitleOpinionRepository
import dev.quiblo.core.data.TitleVersionsRepository
import dev.quiblo.core.data.WatchEventRepository
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
    // Outlives every screen. See `ApplicationScope` — it exists because a resume point was being
    // cancelled mid-write by the back press that made it worth writing.
    single { ApplicationScope() }

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

    // Named rather than positional. Koin resolves by type and does not type-check the order, so a
    // fifth `get()` added to a positional list is a silent mis-wiring waiting for the day two
    // parameters share a type.
    single {
        SourceRepository(
            sourceDao = get(),
            channelDao = get(),
            feedRowDao = get(),
            mediaSources = get(),
            credentialStore = get(),
        )
    }
    single { ProfileRepository(profileDao = get(), profileStore = get()) }
    single { SeriesPreferenceRepository(dao = get(), profiles = get()) }
    // Named, and deliberately so. This class takes three collaborators followed by four
    // parameters that carry defaults — a clock and a dispatcher among them — and a positional
    // list cannot tell the two groups apart. Inserting `profiles` in the middle and appending
    // one more `get()` at the end is how the sixth argument came to land on `now: () -> Long`,
    // which Koin has no definition for and never will. It compiled, and it brought down every
    // catalogue screen in both apps the moment one was opened.
    single {
        ChannelRepository(
            channelDao = get(),
            favoriteDao = get(),
            profiles = get(),
            sourceDao = get(),
            mediaSources = get(),
            hiddenScripts = get<ScriptFilterRepository>().hiddenScripts,
            mergeDuplicates = get<PlayerSettingsRepository>().mergeDuplicateTitles,
        )
    }
    single { ScriptFilterRepository(store = get(), profiles = get()) }
    single {
        TitleVersionsRepository(
            dao = get(),
            mergeDuplicates = get<PlayerSettingsRepository>().mergeDuplicateTitles,
        )
    }
    // Named, like every definition here whose last parameter is a dispatcher with a default.
    single { CatalogueIdentityBackfill(channelDao = get()) }
    single { WatchHistoryRepository(get(), get()) }
    single {
        CategoryRepository(
            channelDao = get(),
            categoryOverrideDao = get(),
            profiles = get(),
            mergeDuplicates = get<PlayerSettingsRepository>().mergeDuplicateTitles,
        )
    }
    // Named, unlike the four positional `get()`s this replaced: the fifth argument is a
    // dispatcher with a default, and appending one more `get()` would have handed Koin's
    // answer for `Flow<Set<TitleScript>>` to `matchDispatcher`.
    single {
        SearchRepository(
            channelDao = get(),
            profiles = get(),
            titleMetadataDao = get(),
            metadataRepository = get(),
            hiddenScripts = get<ScriptFilterRepository>().hiddenScripts,
            mergeDuplicates = get<PlayerSettingsRepository>().mergeDuplicateTitles,
        )
    }
    // A singleton because its scope is the application's: a scan started in settings has to
    // outlive the screen that started it, and an hour of lookups outlives several.
    single { TitleMetadataScanner(channelDao = get(), metadataRepository = get(), checkpoint = get()) }
    single { GuideRepository(get(), get(), get()) }
    single { BackupRepository(get(), get(), get(), transactions = get()) }
    single { PlayerSettingsRepository(store = get(), profiles = get()) }
    single { SubtitleRepository(dao = get(), files = AndroidPickedSubtitleFiles(get<Context>())) }
    single { TmdbClient(get<HttpClient>()) }
    single { TitleMetadataRepository(get(), get(), get()) }
    single { IptvOrgClient(get<HttpClient>()) }
    single { ChannelLogoRepository(client = get(), store = get(), dao = get(), profiles = get()) }
    // Named for the same reason `SearchRepository` above is: both end in a dispatcher and a
    // clock that have defaults, and a positional `get()` too many hands Koin's answer for
    // something else to one of them. Koin resolves by type and does not type-check the order.
    single {
        PopularTitlesRepository(
            dao = get(),
            channelDao = get(),
            client = get(),
            metadata = get(),
        )
    }
    single { WatchEventRepository(dao = get(), profiles = get()) }
    single { TitleOpinionRepository(dao = get(), profiles = get()) }
    single {
        RecommendationRepository(
            history = get(),
            profiles = get(),
            watchEvents = get(),
            opinions = get(),
            titleMetadataDao = get(),
            channelDao = get(),
            favoriteDao = get(),
        )
    }
    single {
        FeedRowCacheRepository(
            dao = get(),
            profiles = get(),
        )
    }
}
