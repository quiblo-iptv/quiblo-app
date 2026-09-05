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
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.quiblo.core.model.CatalogueSyncInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * What is registered, and what happens when it is registered twice.
 *
 * **The second half is the one worth a test.** [SyncScheduler.schedule] runs on every launch, and
 * with `ExistingPeriodicWorkPolicy.UPDATE` that would restart the interval each time — so a
 * household that opens Quiblo every evening would never reach the interval and the sync would
 * silently never run at all, for exactly the viewers who use the app most. `KEEP` is what makes
 * calling it on every launch correct rather than merely harmless, and nothing about the call site
 * says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncSchedulerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun `both jobs are registered, on their own intervals`() {
        SyncScheduler(context).schedule(CatalogueSyncInterval.FOUR_HOURS)

        val catalogue = single(CatalogueSyncWorker.WORK_NAME)
        val popular = single(PopularTitlesWorker.WORK_NAME)

        assertEquals(TimeUnit.HOURS.toMillis(4), catalogue.periodicityInfo?.repeatIntervalMillis)
        assertEquals(TimeUnit.HOURS.toMillis(40), popular.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `the catalogue job takes the interval it is given`() {
        SyncScheduler(context).schedule(CatalogueSyncInterval.TWELVE_HOURS)

        assertEquals(
            TimeUnit.HOURS.toMillis(12),
            single(CatalogueSyncWorker.WORK_NAME).periodicityInfo?.repeatIntervalMillis,
        )
    }

    /**
     * `FEAT-031`. `KEEP` leaves an already-registered job exactly as it was, so a build that
     * changes the interval cannot reach the job an older build enqueued — every existing install
     * would keep its four days forever. The new name is what carries them across, and cancelling
     * the old one is what stops the two running side by side.
     */
    @Test
    fun `the four-day job is cancelled, not left running beside the new one`() {
        val scheduler = SyncScheduler(context)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncScheduler.LEGACY_CATALOGUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CatalogueSyncWorker>(4, TimeUnit.DAYS)
                // The same constraint the real one carries, so the test harness does not simply
                // run it the moment the looper is driven and report it finished rather than
                // cancelled.
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build(),
        )

        scheduler.schedule(CatalogueSyncInterval.FOUR_HOURS)
        // `cancelUniqueWork` finishes on WorkManager's own executor, which Robolectric only runs
        // when the main looper is driven. Without this the assertion reads the state before the
        // cancellation has landed.
        shadowOf(Looper.getMainLooper()).idle()

        val legacy = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SyncScheduler.LEGACY_CATALOGUE_WORK_NAME)
            .get()
        assertTrue(
            "the four-day job is still enqueued",
            legacy.all { it.state == WorkInfo.State.CANCELLED },
        )
    }

    /**
     * A viewer choosing a new number is the one case that must not be kept. `deliberate` is what
     * separates it from the app simply being opened, which must not restart a counting interval.
     */
    @Test
    fun `a deliberate change replaces the running schedule`() {
        val scheduler = SyncScheduler(context)
        scheduler.schedule(CatalogueSyncInterval.FOUR_HOURS)

        scheduler.schedule(CatalogueSyncInterval.DAILY, deliberate = true)

        assertEquals(
            TimeUnit.HOURS.toMillis(24),
            single(CatalogueSyncWorker.WORK_NAME).periodicityInfo?.repeatIntervalMillis,
        )
    }

    @Test
    fun `both want a network and nothing else`() {
        SyncScheduler(context).schedule(CatalogueSyncInterval.FOUR_HOURS)

        listOf(CatalogueSyncWorker.WORK_NAME, PopularTitlesWorker.WORK_NAME).forEach { name ->
            val constraints = single(name).constraints
            assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
            // Deliberately not required: a television is plugged in and never idle by the
            // system's definition, and a phone that only synced while charging would sync at
            // night or not at all. These are two HTTP requests, not a video encode.
            assertTrue("charging must not be required", !constraints.requiresCharging())
            assertTrue("device idle must not be required", !constraints.requiresDeviceIdle())
        }
    }

    /**
     * Scheduling again leaves the running schedule alone.
     *
     * Asserted by the work being the same enqueued item rather than a replacement: `UPDATE` would
     * produce a fresh one, and the interval would start again from this moment.
     */
    @Test
    fun `scheduling twice does not restart the interval`() {
        val scheduler = SyncScheduler(context)
        scheduler.schedule(CatalogueSyncInterval.FOUR_HOURS)
        val first = single(CatalogueSyncWorker.WORK_NAME).id

        scheduler.schedule(CatalogueSyncInterval.FOUR_HOURS)

        assertEquals(first, single(CatalogueSyncWorker.WORK_NAME).id)
    }

    private fun single(name: String): WorkInfo =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get().single()
}
