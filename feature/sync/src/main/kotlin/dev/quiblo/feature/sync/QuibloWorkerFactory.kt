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
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.quiblo.core.data.PopularTitlesRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository

/**
 * Builds this project's workers with their repositories in hand.
 *
 * WorkManager instantiates a worker from a class name it recovered from its own database, possibly
 * after a reboot and before anything in the app has run. There is no constructor call to inject
 * into, so this is the seam.
 *
 * **It returns null for anything it does not recognise, and that is required rather than tidy.**
 * A factory that throws, or that returns a wrong worker, breaks every other worker in the process
 * — including ones a library scheduled. Null means "not mine", and the delegating factory it is
 * registered with then falls through to the default construction.
 */
class QuibloWorkerFactory(
    private val sources: SourceRepository,
    private val popularTitles: PopularTitlesRepository,
    private val metadata: TitleMetadataRepository,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        CatalogueSyncWorker::class.java.name -> CatalogueSyncWorker(appContext, workerParameters, sources)
        PopularTitlesWorker::class.java.name ->
            PopularTitlesWorker(appContext, workerParameters, popularTitles, metadata)
        else -> null
    }
}
