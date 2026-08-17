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

package dev.quiblo.feature.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Registers the two scheduled jobs, once per launch.
 *
 * **`KEEP`, not `UPDATE`, and that is the whole of why calling this on every launch is safe.**
 * `UPDATE` would restart the interval each time the app opened, so a household that opens Quiblo
 * every evening would never reach four days and the sync would never run at all — the schedule
 * would silently become "never" for exactly the viewers who use the app most. `KEEP` leaves an
 * already-registered job alone, so the interval is measured from when it was first registered.
 *
 * **Both jobs want a network and nothing else.** No charging requirement and no idle requirement:
 * a television is plugged in and never idle by the system's definition, and a phone that only
 * synced while charging would sync at night or not at all. Battery-not-low is deliberately absent
 * for the same reason — these are two HTTP requests, not a video encode.
 *
 * Nothing here is a foreground service. WorkManager survives a reboot, respects doze, and is what
 * the system will actually let run; a foreground service would be a permanent notification for
 * work nobody asked to watch, and on a television one nobody can see.
 */
class SyncScheduler(private val context: Context) {

    fun schedule() {
        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniquePeriodicWork(
            CatalogueSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CatalogueSyncWorker>(CATALOGUE_INTERVAL_DAYS, TimeUnit.DAYS)
                .setConstraints(networkRequired)
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            PopularTitlesWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PopularTitlesWorker>(POPULAR_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(networkRequired)
                .build(),
        )
    }

    private val networkRequired
        get() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private companion object {
        /**
         * Four days between catalogue syncs.
         *
         * A provider adds titles in batches rather than continuously, and the row this feeds shows
         * thirty days of them. Four days is frequent enough that "recently added" is true and rare
         * enough that a large account is not re-parsed for the sake of it: a sixty-thousand-title
         * playlist is a real download and a real pass over the database.
         */
        const val CATALOGUE_INTERVAL_DAYS = 4L

        /**
         * Forty hours between popular checks.
         *
         * A little under two days, so a household watching every evening sees the row change about
         * every other one. The repository's own interval is the same number and is what actually
         * guards the request — this only makes sure somebody asks.
         */
        const val POPULAR_INTERVAL_HOURS = 40L
    }
}
