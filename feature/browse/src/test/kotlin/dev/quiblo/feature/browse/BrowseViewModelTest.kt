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

package dev.quiblo.feature.browse

import app.cash.turbine.test
import dev.quiblo.core.data.CategoryRepository
import dev.quiblo.core.data.ChannelLogoRepository
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.GuideOutcome
import dev.quiblo.core.data.GuideRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Source
import dev.quiblo.core.model.SourceKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * That a browse feed subscribes only to what it can display.
 *
 * `observeNowPlaying` asks for every programme airing now across a whole source. Movies and
 * Series render no programme anywhere, so combining it into those feeds bought nothing and
 * cost a query per emission on a screen already slow to appear (#001).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sourceRepository: SourceRepository = mockk()
    private val channelRepository: ChannelRepository = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val guideRepository: GuideRepository = mockk(relaxed = true)
    private val metadataRepository: TitleMetadataRepository = mockk(relaxed = true)
    private val historyRepository: WatchHistoryRepository = mockk(relaxed = true)
    private val channelLogoRepository: ChannelLogoRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Every flow the feed combines is stubbed, not relaxed. A relaxed mock returns a
        // Flow that never emits, which stalls the `combine` — and a stalled combine makes
        // the "does not ask" assertions pass for the wrong reason, by never running at all.
        every { sourceRepository.observeSources() } returns flowOf(listOf(SOURCE))
        every { guideRepository.observeNowPlaying(any()) } returns flowOf(emptyMap())
        every { categoryRepository.observeCategories(any(), any()) } returns flowOf(emptyList())
        every { channelRepository.observeBrowse(any(), any(), any(), any(), any()) } returns
            flowOf(emptyList())
        every { channelRepository.observeFavorites(any(), any()) } returns flowOf(emptyList())
        every { channelRepository.observeRecentlyAdded(any(), any()) } returns flowOf(emptyList())
        every { historyRepository.observeHistory(any(), any()) } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `does not ask for the guide on the films feed`() = runTest {
        val viewModel = viewModelFor(BrowseFeed(MediaKind.VOD))

        viewModel.uiState.drain()

        verify(exactly = 0) { guideRepository.observeNowPlaying(any()) }
    }

    @Test
    fun `does not ask for the guide on the series feed`() = runTest {
        val viewModel = viewModelFor(BrowseFeed(MediaKind.SERIES))

        viewModel.uiState.drain()

        verify(exactly = 0) { guideRepository.observeNowPlaying(any()) }
    }

    @Test
    fun `still asks for the guide on the live feed`() = runTest {
        val viewModel = viewModelFor(BrowseFeed(MediaKind.LIVE))

        viewModel.uiState.drain()

        verify { guideRepository.observeNowPlaying(SOURCE.id) }
    }

    @Test
    fun `still asks for the guide on the favourites feed`() = runTest {
        // Favourites is built with MediaKind.LIVE and renders a programme line for the live
        // channels in it, so it keeps the guide. This is the case a narrower condition
        // would have broken silently.
        val viewModel = viewModelFor(BrowseFeed(MediaKind.LIVE, BrowseScope.FAVOURITES))

        viewModel.uiState.drain()

        verify { guideRepository.observeNowPlaying(SOURCE.id) }
    }

    @Test
    fun `a fresh live list asks for the top of itself without anything being focused`() = runTest {
        // The reported defect. On the television nothing has focus when Live opens, and the
        // guide was fetched only for a row focus had rested on — so every row drew blank,
        // indefinitely, for anybody who did not happen to stop on one.
        every { channelRepository.observeBrowse(any(), any(), any(), any(), any()) } returns
            flowOf(liveChannels(count = 30))

        viewModelFor(BrowseFeed(MediaKind.LIVE)).uiState.drain()

        coVerify(exactly = 10) { guideRepository.refreshGuideFor(any()) }
    }

    @Test
    fun `the prefetch is bounded and does not repeat for a list it has already seen`() = runTest {
        // The bound is the reason this is safe to do at all: "per visible row" against a
        // 20,000-channel account is how this project's provider blocked it, twice. A list that
        // re-emits — a write to the table, a keystroke, a category change — must cost nothing.
        every { channelRepository.observeBrowse(any(), any(), any(), any(), any()) } returns
            flowOf(liveChannels(count = 500), liveChannels(count = 500))

        viewModelFor(BrowseFeed(MediaKind.LIVE)).uiState.drain()

        coVerify(exactly = 10) { guideRepository.refreshGuideFor(any()) }
    }

    @Test
    fun `the films feed prefetches nothing, because a poster has nowhere to put a programme`() = runTest {
        every { channelRepository.observeBrowse(any(), any(), any(), any(), any()) } returns
            flowOf(liveChannels(count = 30).map { it.copy(kind = MediaKind.VOD) })

        viewModelFor(BrowseFeed(MediaKind.VOD)).uiState.drain()

        coVerify(exactly = 0) { guideRepository.refreshGuideFor(any()) }
    }

    @Test
    fun `a refusing panel reaches the screen instead of looking like an empty guide`() = runTest {
        every { channelRepository.observeBrowse(any(), any(), any(), any(), any()) } returns
            flowOf(liveChannels(count = 3))
        coEvery { guideRepository.refreshGuideFor(any()) } returns GuideOutcome.BLOCKED

        val state = viewModelFor(BrowseFeed(MediaKind.LIVE)).uiState
        state.drain()

        assertEquals(GuideOutcome.BLOCKED, state.value.guideOutcome)
    }

    @Test
    fun `one channel with listings is enough to stop reporting trouble`() = runTest {
        // Most channels on a large account have no listing, and a guide that reported itself
        // broken on meeting the first of them would be wrong about every working account.
        every { channelRepository.observeBrowse(any(), any(), any(), any(), any()) } returns
            flowOf(liveChannels(count = 3))
        coEvery { guideRepository.refreshGuideFor(any()) } returnsMany listOf(
            GuideOutcome.EMPTY,
            GuideOutcome.STORED,
            GuideOutcome.EMPTY,
        )

        val state = viewModelFor(BrowseFeed(MediaKind.LIVE)).uiState
        state.drain()

        assertEquals(GuideOutcome.STORED, state.value.guideOutcome)
    }

    private fun liveChannels(count: Int) = (1..count).map { index ->
        Channel(
            id = index.toLong(),
            sourceId = SOURCE.id,
            name = "Channel $index",
            streamUrl = "http://host.invalid/$index",
            kind = MediaKind.LIVE,
            tvgId = "key-$index",
            providerStreamId = index.toString(),
        )
    }

    @Test
    fun `the newest-first feed reads the newest-first query and nothing else`() = runTest {
        val viewModel = viewModelFor(BrowseFeed(MediaKind.VOD, BrowseScope.RECENTLY_ADDED))

        viewModel.uiState.drain()

        verify { channelRepository.observeRecentlyAdded(SOURCE.id, RECENT_LIMIT) }
        // The catalogue query is the one whose absence matters: it returns tens of thousands
        // of rows for a kind this feed does not restrict itself to, and running both would
        // mean the screen paid for a list it never draws.
        verify(exactly = 0) { channelRepository.observeBrowse(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { channelRepository.observeFavorites(any(), any()) }
    }

    @Test
    fun `the newest-first feed subscribes to no guide, no history and no categories`() = runTest {
        val viewModel = viewModelFor(BrowseFeed(MediaKind.VOD, BrowseScope.RECENTLY_ADDED))

        viewModel.uiState.drain()

        // Three queries a single merged row has nowhere to put an answer from. Categories is
        // the one that was already being asked for and discarded on Favourites before this
        // feed existed; asserting it here is what stops it coming back.
        verify(exactly = 0) { guideRepository.observeNowPlaying(any()) }
        verify(exactly = 0) { historyRepository.observeHistory(any(), any()) }
        verify(exactly = 0) { categoryRepository.observeCategories(any(), any()) }
    }

    @Test
    fun `the catalogue feed still asks for its categories`() = runTest {
        // The other half of the assertion above: skipping the query for the feeds that cannot
        // draw a rail must not skip it for the one that can.
        val viewModel = viewModelFor(BrowseFeed(MediaKind.VOD))

        viewModel.uiState.drain()

        verify { categoryRepository.observeCategories(SOURCE.id, MediaKind.VOD) }
    }

    /**
     * Subscribes and lets the feed actually resolve.
     *
     * `stateIn(… WhileSubscribed)` emits its initial value the instant it is collected, so
     * awaiting one item proves nothing: the upstream has not run yet. Draining to the first
     * non-loading state is what makes these assertions mean something.
     */
    private suspend fun StateFlow<BrowseUiState>.drain() = test {
        awaitItem()
        testDispatcher.scheduler.advanceUntilIdle()
        cancelAndIgnoreRemainingEvents()
    }

    private fun viewModelFor(feed: BrowseFeed) = BrowseViewModel(
        feed = feed,
        sourceRepository = sourceRepository,
        channelRepository = channelRepository,
        categoryRepository = categoryRepository,
        guideRepository = guideRepository,
        metadataRepository = metadataRepository,
        historyRepository = historyRepository,
        channelLogoRepository = channelLogoRepository,
    )

    private companion object {
        /** What [BrowseFeed.recentLimit] is, asserted rather than assumed. */
        val RECENT_LIMIT = BrowseFeed(MediaKind.VOD, BrowseScope.RECENTLY_ADDED).recentLimit

        val SOURCE = Source(
            id = 3L,
            name = "Test",
            kind = SourceKind.M3U,
            url = "http://host.invalid/p.m3u",
            createdAtEpochMillis = 0L,
        )
    }
}
