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

package dev.quiblo.tv.di

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
import dev.quiblo.feature.sync.di.syncModule
import dev.quiblo.feature.vod.di.vodModule
import org.koin.core.module.Module

/**
 * The TV application's wiring.
 *
 * Almost the same list as `:app`'s, because the TV app reuses the shared ViewModels rather
 * than declaring its own. It is duplicated rather than shared because the two applications
 * are the assembly points and neither may depend on the other — and where the lists genuinely
 * diverge, that divergence is visible here rather than hidden behind a flag in a common module.
 *
 * [tvUpdateModule] is the one divergence: checking for a newer APK is something a television
 * needs and a phone with a store does not.
 */
val tvModules: List<Module> = listOf(
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
    syncModule,
    tvUpdateModule,
)
