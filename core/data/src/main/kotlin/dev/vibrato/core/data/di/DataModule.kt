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

package dev.vibrato.core.data.di

import android.content.Context
import dev.vibrato.core.data.LocalFileContentFetcher
import dev.vibrato.core.data.SourceRepository
import dev.vibrato.core.model.SourceKind
import dev.vibrato.core.network.HttpContentFetcher
import dev.vibrato.source.api.ContentFetcher
import dev.vibrato.source.api.MediaSource
import dev.vibrato.source.m3u.M3uSource
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
        mapOf(SourceKind.M3U to M3uSource(get()))
    }

    single { SourceRepository(get(), get(), get()) }
}
