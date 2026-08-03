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

package dev.quiblo.player.di

import dev.quiblo.core.data.di.dataModule
import dev.quiblo.core.database.di.databaseModule
import dev.quiblo.core.datastore.di.datastoreModule
import dev.quiblo.core.media.di.mediaModule
import dev.quiblo.core.network.di.networkModule
import dev.quiblo.feature.browse.di.browseModule
import dev.quiblo.feature.player.di.playerModule
import dev.quiblo.feature.series.di.seriesModule
import dev.quiblo.feature.settings.di.settingsModule
import dev.quiblo.feature.sources.di.sourcesModule
import dev.quiblo.feature.vod.di.vodModule
import org.koin.core.module.Module

/**
 * Every module's wiring, aggregated.
 *
 * Each layer declares its own Koin module and `:app` is the single assembly point
 * (docs/PLAN.md §2). Adding Xtream in M4 means adding one entry to this list.
 */
val appModules: List<Module> = listOf(
    databaseModule,
    datastoreModule,
    networkModule,
    mediaModule,
    dataModule,
    sourcesModule,
    browseModule,
    seriesModule,
    playerModule,
    settingsModule,
    vodModule,
)
