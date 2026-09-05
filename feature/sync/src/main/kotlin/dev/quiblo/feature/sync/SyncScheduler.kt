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
import dev.quiblo.core.model.CatalogueSyncInterval
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Registers the two scheduled jobs, once per launch.
 *
 * **`KEEP`, not `UPDATE`, and that is the whole of why calling this on every launch is safe.**
 * `UPDATE` would restart the interval each time the app opened, so a household that opens Quiblo
 * every evening would never reach the interval and the sync would never run at all — the schedule
 * would silently become "never" for exactly the viewers who use the app most. `KEEP` leaves an
 * already-registered job alone, so the interval is measured from when it was first registered.
 *
 * **A viewer changing the interval is the one case that must not be kept**, and [watch] is where
 * that is handled: the first value seen registers with `KEEP`, and every later change re-registers
 * with `UPDATE`. Somebody choosing a new number in Settings is a deliberate act and has to take
 * effect now; an app being opened is not.
 *
 * **Both jobs want a network and nothing else.** No charging requirement and no idle requirement:
 * a television is plugged in and never idle by the system's definition, and a phone that only
 * synced while charging would sync at night or not at all. Battery-not-low is deliberately absent
 * for the same reason — these are a handful of HTTP requests, not a video encode.
 *
 * Nothing here is a foreground service. WorkManager survives a reboot, respects doze, and is what
 * the system will actually let run; a foreground service would be a permanent notification for
 * work nobody asked to watch, and on a television one nobody can see.
 */
class SyncScheduler(private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Follows the chosen interval, registering the jobs on the first value and re-registering on
     * every later one. Suspends until the caller's scope is cancelled.
     *
     * One subscription rather than a first-value read followed by a separate collect: split in
     * two, a change landing between them is a change nobody acts on.
     */
    suspend fun watch(interval: Flow<CatalogueSyncInterval>) {
        var seen = false
        interval.collect { value ->
            schedule(value, deliberate = seen)
            seen = true
        }
    }

    /**
     * @param deliberate the viewer asked for this interval just now, so replace what is running.
     *   False for the value read at launch, which must not restart an interval already counting.
     */
    fun schedule(interval: CatalogueSyncInterval, deliberate: Boolean = false) {
        // The four-day job, by its old name. Left registered it would keep running beside the new
        // one on its own schedule, and `KEEP` cannot reach it — see LEGACY_CATALOGUE_WORK_NAME.
        workManager.cancelUniqueWork(LEGACY_CATALOGUE_WORK_NAME)

        workManager.enqueueUniquePeriodicWork(
            CatalogueSyncWorker.WORK_NAME,
            if (deliberate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CatalogueSyncWorker>(interval.hours, TimeUnit.HOURS)
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

    companion object {
        /**
         * The name the four-day catalogue job was registered under, cancelled on sight.
         *
         * **Renaming the job is what carries an existing install onto the new interval.** `KEEP`
         * leaves an already-registered job exactly as it is, which is what makes calling
         * [schedule] on every launch safe — and it also means a job registered by an older build
         * would keep its four days forever, on every television this app is already installed on.
         * A new name is a job that has never been registered, so `KEEP` registers it.
         *
         * Cancelled rather than merely abandoned: an abandoned unique job keeps running.
         */
        const val LEGACY_CATALOGUE_WORK_NAME = "quiblo-catalogue-sync"

        /**
         * Forty hours between popular checks.
         *
         * A little under two days, so a household watching every evening sees the row change about
         * every other one. The repository's own interval is the same number and is what actually
         * guards the request — this only makes sure somebody asks.
         *
         * Not offered as a setting alongside the catalogue interval: this one reads a list this
         * project publishes, not the viewer's provider, so how often it runs is not a decision
         * about their account.
         */
        const val POPULAR_INTERVAL_HOURS = 40L
    }
}
