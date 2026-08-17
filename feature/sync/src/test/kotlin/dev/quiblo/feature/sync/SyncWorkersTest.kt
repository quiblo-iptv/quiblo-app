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

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dev.quiblo.core.data.PopularTitlesRepository
import dev.quiblo.core.data.RefreshOutcome
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceReport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the two scheduled jobs do, and what they refuse to do.
 *
 * The interesting cases are all failures. Unattended work that treats a temporary refusal as a
 * success never tries again; unattended work that treats a permanent absence as a failure retries
 * forever. Both are invisible until somebody looks at a battery graph or an empty row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkersTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val sources: SourceRepository = mockk(relaxed = true)
    private val popularTitles: PopularTitlesRepository = mockk(relaxed = true)
    private val metadata: TitleMetadataRepository = mockk(relaxed = true)
    private val apiKey = MutableStateFlow<String?>("a-key")

    @Test
    fun `the catalogue sync merges rather than replaces`() = runTest {
        coEvery { sources.allSourceIds() } returns listOf(1L, 2L)
        coEvery { sources.refresh(any(), any()) } returns RefreshOutcome.Success(1L, REPORT)

        assertEquals(ListenableWorker.Result.success(), catalogueWorker().doWork())

        // The whole reason this worker exists rather than calling the refresh a person presses:
        // that one renumbers the catalogue and destroys the arrival dates behind Recently Added.
        coVerify { sources.refresh(1L, merge = true) }
        coVerify { sources.refresh(2L, merge = true) }
    }

    @Test
    fun `a source that failed is a retry, and the others are still done`() = runTest {
        coEvery { sources.allSourceIds() } returns listOf(1L, 2L)
        coEvery { sources.refresh(1L, any()) } returns RefreshOutcome.Failure(SourceError.NoNetwork)
        coEvery { sources.refresh(2L, any()) } returns RefreshOutcome.Success(2L, REPORT)

        assertEquals(ListenableWorker.Result.retry(), catalogueWorker().doWork())

        // Two accounts where the first is down should still leave the second up to date.
        coVerify { sources.refresh(2L, merge = true) }
    }

    @Test
    fun `no sources at all is nothing to do rather than a failure`() = runTest {
        coEvery { sources.allSourceIds() } returns emptyList()

        assertEquals(ListenableWorker.Result.success(), catalogueWorker().doWork())
    }

    /**
     * No key is not a failure, and this is the assertion that stops a wake-up loop.
     *
     * A retry here would have the system re-run this worker with backoff, forever, on every
     * installation that has never configured a metadata key — which is the default. `AC-META-01`
     * also says nothing here issues a request without one.
     */
    @Test
    fun `with no metadata key the popular check asks nothing and succeeds`() = runTest {
        apiKey.value = null

        assertEquals(ListenableWorker.Result.success(), popularWorker().doWork())

        coVerify(exactly = 0) { popularTitles.refreshIfStale() }
    }

    @Test
    fun `a refused fetch is a retry rather than a silence`() = runTest {
        coEvery { popularTitles.refreshIfStale() } throws IllegalStateException("refused")

        assertEquals(ListenableWorker.Result.retry(), popularWorker().doWork())
    }

    @Test
    fun `an answered fetch succeeds`() = runTest {
        coEvery { popularTitles.refreshIfStale() } returns true

        assertEquals(ListenableWorker.Result.success(), popularWorker().doWork())
    }

    private companion object {
        val REPORT = SourceReport(parsedEntries = 1, skippedEntries = 0)
    }

    private fun catalogueWorker() =
        TestListenableWorkerBuilder<CatalogueSyncWorker>(context)
            .setWorkerFactory(QuibloWorkerFactory(sources, popularTitles, metadata))
            .build()

    private fun popularWorker(): PopularTitlesWorker {
        every { metadata.apiKey } returns apiKey
        return TestListenableWorkerBuilder<PopularTitlesWorker>(context)
            .setWorkerFactory(QuibloWorkerFactory(sources, popularTitles, metadata))
            .build()
    }
}
