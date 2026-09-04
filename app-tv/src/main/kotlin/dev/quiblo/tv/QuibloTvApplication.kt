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

package dev.quiblo.tv

import android.app.Application
import androidx.work.Configuration
import dev.quiblo.core.data.CatalogueIdentityBackfill
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.ProfileRepository
import dev.quiblo.feature.sync.SyncScheduler
import dev.quiblo.tv.di.tvModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.mp.KoinPlatform.getKoin

/**
 * Starts Koin and nothing else.
 *
 * The same posture as the phone application: no analytics, no crash reporter, no update
 * check. Anything added here that opens a socket is a design regression
 * (docs/FREEZE.md §4.5, AC-NFR-03).
 */
class QuibloTvApplication : Application(), Configuration.Provider {

    /**
     * WorkManager's configuration, resolved from the graph.
     *
     * Read on demand rather than by `androidx.startup`, which runs from a ContentProvider before
     * `onCreate` — before Koin exists. The manifest removes that initializer for exactly this
     * reason, so the first `WorkManager.getInstance` is what initialises it, from inside
     * [beginSession].
     */
    override val workManagerConfiguration: Configuration
        get() = getKoin().get()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@QuibloTvApplication)
            modules(tvModules)
        }

        beginSession()
    }

    /**
     * Clears last time's session before anything is drawn: no leftover guest, and nobody
     * chosen.
     *
     * A guest session is promised not to outlive itself, and the only moment that promise can
     * be kept for certain is startup: a process the system killed never got to tidy up, and a
     * television is switched off at the wall rather than exited. Deleting the row takes its
     * favourites and resume points with it by foreign key.
     *
     * Clearing the chosen id is the other half, and it is #016: a television is the device a
     * household shares, so it has to ask who is watching every time it opens rather than once
     * when it was installed.
     */
    private fun beginSession() {
        val profiles: ProfileRepository = getKoin().get()
        val backfill: CatalogueIdentityBackfill = getKoin().get()
        val settings: PlayerSettingsRepository = getKoin().get()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Registered from the chosen interval, and re-registered whenever the viewer changes it.
        // The first registration uses `ExistingPeriodicWorkPolicy.KEEP`, which is what makes doing
        // this on every launch safe: `UPDATE` there would restart the interval each time the app
        // opened, so a household that opens Quiblo every evening would never reach it and the sync
        // would silently never run — for exactly the viewers who use the app most. This one never
        // returns, so it gets its own launch rather than sharing the one below.
        scope.launch { getKoin().get<SyncScheduler>().watch(settings.catalogueSyncInterval) }

        scope.launch {
            profiles.beginSession()
            // Catalogues written before schema 19 carry no cleaned title and no script mask.
            // After the first pass this costs one indexed count, and a fresh install never has
            // work here at all. Nothing waits for it — see [CatalogueIdentityBackfill].
            backfill.run()
        }
    }
}
