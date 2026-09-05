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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.quiblo.core.data.RefreshOutcome
import dev.quiblo.core.data.SourceRepository

/**
 * Brings every configured source up to date, unattended.
 *
 * **A provider's catalogue changes and nothing was noticing.** Until this existed the only way a
 * new film reached the app was somebody opening Settings and pressing Refresh, which means the
 * "Recently added" row was answering "what has the provider added since you last thought to check"
 * — a question about the viewer's habits rather than about the service.
 *
 * **It merges rather than replaces, and that distinction is the whole of why this could not simply
 * call the existing refresh.** A refresh deletes every row of a source and reinserts it with new
 * ids, which destroys the arrival dates the recently-added row is built from — on an M3U playlist
 * the provider supplies no date at all, so a rebuild every four days would declare the entire
 * catalogue new, every four days, forever. See `ChannelDao.mergeForSource`.
 *
 * **Not a foreground service.** WorkManager is the platform's answer to "every four days": it
 * survives a reboot, it respects doze, and it is what the system will let run. A foreground
 * service would be a permanent notification for work the viewer never asked to watch, and on a
 * television it would be a notification nobody can even see.
 *
 * A failure is a retry rather than a loss. A provider that is down, a television on a network that
 * has not come up yet, an account being rate-limited: all of those are answered by trying again
 * later, and none of them is a reason to disturb a stored catalogue that still works.
 */
class CatalogueSyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val sources: SourceRepository,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val ids = sources.allSourceIds()
        if (ids.isEmpty()) return Result.success()

        // Every source is attempted even when one fails: two accounts where the first is down
        // should still leave the second up to date. The run is a retry if any of them failed,
        // which re-attempts all of them — a second load of a source that succeeded is one request
        // and this runs every four days.
        val outcomes = ids.map { sources.refresh(it, merge = true) }
        return if (outcomes.any { it is RefreshOutcome.Failure }) Result.retry() else Result.success()
    }

    companion object {
        /**
         * The name the scheduled copy is registered under. One per installation, not per source.
         *
         * **Versioned, and the version is not decoration.** `ExistingPeriodicWorkPolicy.KEEP`
         * leaves an already-registered job exactly as it was registered, so a build that changes
         * the interval cannot reach the job an older build enqueued — every existing install
         * would keep the four days this app shipped with. A new name is a job that has never been
         * registered, so the new interval takes. `SyncScheduler.LEGACY_CATALOGUE_WORK_NAME`
         * cancels the old one.
         */
        const val WORK_NAME = "quiblo-catalogue-sync-v2"
    }
}
