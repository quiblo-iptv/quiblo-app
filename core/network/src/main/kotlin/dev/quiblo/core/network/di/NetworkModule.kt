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

package dev.quiblo.core.network.di

import android.content.Context
import dev.quiblo.core.network.AndroidConnectivityChecker
import dev.quiblo.core.network.ConnectivityChecker
import dev.quiblo.core.network.HttpContentFetcher
import dev.quiblo.core.network.createHttpClient
import dev.quiblo.core.network.createOkHttpClient
import dev.quiblo.core.network.update.ReleaseChecker
import dev.quiblo.core.network.update.ReleaseDownloader
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wiring owned by `:core:network`.
 *
 * Ktor is constructed and kept here so it never reaches a consumer's compile classpath.
 * There is exactly one client, and it is only ever pointed at hosts the user configured
 * (AC-NFR-03).
 *
 * The `OkHttpClient` underneath it is the one exception, published on purpose: `:core:media`
 * hands it to the player so streaming and API traffic share a connection pool instead of
 * opening a socket per segment.
 */
val networkModule: Module = module {
    single { createOkHttpClient() }
    single { createHttpClient(get()) }
    single<ConnectivityChecker> { AndroidConnectivityChecker(get<Context>()) }
    single { HttpContentFetcher(get(), get()) }
    single { ReleaseChecker(get(), get()) }
    single { ReleaseDownloader(get()) }
}
