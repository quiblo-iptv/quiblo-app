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

package dev.quiblo.player

import android.app.Application
import dev.quiblo.core.data.ProfileRepository
import dev.quiblo.player.di.appModules
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
 * Application entry point.
 *
 * Starts Koin and nothing else. Quiblo never phones home: there is no analytics SDK, no
 * crash reporter and no update check against a project-controlled server (docs/FREEZE.md
 * §4.5, AC-NFR-03). Any initialisation added here that opens a socket is a design
 * regression.
 */
class QuibloApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@QuibloApplication)
            modules(appModules)
        }

        endAnyGuestSession()
    }

    /**
     * Deletes a guest left over from last time, before anything is drawn.
     *
     * The one moment the promise can be kept for certain: a process the system killed never
     * got to tidy up after itself. Deleting the row takes its favourites and resume points
     * with it by foreign key, and leaves the stored profile id pointing at nothing — which is
     * what puts the chooser back.
     */
    private fun endAnyGuestSession() {
        val profiles: ProfileRepository = getKoin().get()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            profiles.endGuestSessions()
        }
    }
}
