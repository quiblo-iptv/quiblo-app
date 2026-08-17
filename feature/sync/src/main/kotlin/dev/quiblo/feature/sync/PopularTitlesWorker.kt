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
import dev.quiblo.core.data.PopularTitlesRepository
import dev.quiblo.core.data.TitleMetadataRepository

/**
 * Asks what the world is watching, without waiting for somebody to open a tab.
 *
 * The check used to happen when For You was composed, which made "what is popular now" a question
 * about how often the viewer opened that tab: a household that watched nothing but Live saw a
 * fortnight-old list the day they finally looked.
 *
 * **No key means no request and no work at all** — not a failure, and not a retry. That is
 * `AC-META-01`, and it is why the key is read before anything else here: a worker that woke up
 * every forty hours to discover it had nothing to ask with would be the same wasted wake-up
 * repeated forever.
 *
 * The interval guard lives in the repository rather than here, so the worker asking and a viewer
 * arriving cannot between them cost two fetches. This worker's job is to make sure *somebody*
 * asks; it does not decide whether the answer is stale.
 *
 * A refusal is a retry. It is never written down — a cached refusal would stand for the whole
 * interval, which is the failure this project's blocked provider account taught it to avoid.
 */
class PopularTitlesWorker(
    context: Context,
    parameters: WorkerParameters,
    private val popularTitles: PopularTitlesRepository,
    private val metadata: TitleMetadataRepository,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        // The key is kept encrypted, so reading it is a keystore round trip rather than a field
        // access. Awaited rather than sampled: sampling it is the race that left the row empty for
        // the life of a screen once already (BUG-021).
        metadata.load()
        if (metadata.apiKey.value.isNullOrBlank()) return Result.success()

        return runCatching { popularTitles.refreshIfStale() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val WORK_NAME = "quiblo-popular-titles"
    }
}
