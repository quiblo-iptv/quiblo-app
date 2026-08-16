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

package dev.quiblo.feature.sync.di

import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import dev.quiblo.feature.sync.QuibloWorkerFactory
import dev.quiblo.feature.sync.SyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The scheduled work, and how a worker gets what it needs.
 *
 * WorkManager builds workers itself, from a class name recovered after a reboot, so a worker
 * cannot be given its repositories through a constructor the way everything else here is. A
 * factory is the seam the platform provides for exactly that, and [QuibloWorkerFactory] is the
 * one place a worker's dependencies are resolved.
 */
val syncModule: Module = module {
    single { SyncScheduler(androidContext()) }
    single { QuibloWorkerFactory(get(), get(), get()) }
    single {
        Configuration.Builder()
            // Delegating rather than a bare factory, so that anything WorkManager itself schedules
            // — and anything a library adds later — still gets the default construction. A factory
            // that returns null for a class it does not know is what makes that possible; ours
            // does, which is why this cannot swallow somebody else's worker.
            .setWorkerFactory(DelegatingWorkerFactory().apply { addFactory(get<QuibloWorkerFactory>()) })
            .build()
    }
}
