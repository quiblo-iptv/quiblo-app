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

package dev.quiblo.feature.series

import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Season
import dev.quiblo.core.model.SeriesDetails
import dev.quiblo.source.api.SeriesDetailsResult
import dev.quiblo.source.api.SourceError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val channelRepository: ChannelRepository = mockk()
    private val metadataRepository: TitleMetadataRepository = mockk(relaxed = true)
    private val historyRepository: WatchHistoryRepository = mockk(relaxed = true)

    private val sampleChannel = Channel(
        id = 10L,
        sourceId = 1L,
        name = "Breaking Bad",
        streamUrl = "",
        kind = MediaKind.SERIES,
        providerStreamId = "99",
    )

    private val sampleDetails = SeriesDetails(
        seriesId = "99",
        title = "Breaking Bad",
        overview = "A chemistry teacher...",
        coverUrl = "http://cover.example.invalid/bb.jpg",
        seasons = listOf(
            Season(
                seasonNumber = 1,
                name = "Season 1",
                episodes = listOf(
                    Episode(
                        id = "501",
                        title = "Pilot",
                        seasonNumber = 1,
                        episodeNumber = 1,
                        streamUrl = "http://stream.example.invalid/501.mp4",
                    ),
                ),
            ),
        ),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The screen watches the favourite flag and asks the metadata service about the
        // title; neither is what any test here is about, so both answer flatly.
        every { channelRepository.observeIsFavorite(any()) } returns flowOf(false)
        coEvery { metadataRepository.forTitle(any(), any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `offers the last watched episode for resume`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(10L) } returns sampleChannel
        coEvery { channelRepository.getSeriesDetails(sampleChannel) } returns SeriesDetailsResult.Success(sampleDetails)
        // Resume points are keyed by the episode's stream URL, because that is what the
        // player records against when it is handed a custom URL.
        coEvery { historyRepository.mostRecentlyWatched(any()) } returns
            ("http://stream.example.invalid/501.mp4" to 900_000L)

        val viewModel = SeriesDetailViewModel(10L, channelRepository, metadataRepository, historyRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = assertInstanceOf(SeriesDetailUiState.Success::class.java, viewModel.uiState.value)
        assertEquals("501", state.resumeEpisode?.id)
        assertEquals(900_000L, state.resumePositionMillis)
    }

    @Test
    fun `offers the first listed episode to start from the beginning`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(10L) } returns sampleChannel
        coEvery { channelRepository.getSeriesDetails(sampleChannel) } returns SeriesDetailsResult.Success(sampleDetails)
        coEvery { historyRepository.mostRecentlyWatched(any()) } returns
            ("http://stream.example.invalid/501.mp4" to 900_000L)

        val viewModel = SeriesDetailViewModel(10L, channelRepository, metadataRepository, historyRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Episode one of season one, not the current episode from zero. Starting a series
        // over means going back to its beginning.
        val state = assertInstanceOf(SeriesDetailUiState.Success::class.java, viewModel.uiState.value)
        assertEquals("501", state.firstEpisode?.id)
    }

    @Test
    fun `removing from history forgets the whole series and clears the resume button`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(10L) } returns sampleChannel
        coEvery { channelRepository.getSeriesDetails(sampleChannel) } returns SeriesDetailsResult.Success(sampleDetails)
        coEvery { historyRepository.mostRecentlyWatched(any()) } returns
            ("http://stream.example.invalid/501.mp4" to 900_000L)
        coEvery { historyRepository.removeSeriesFromHistory(any()) } returns Unit

        val viewModel = SeriesDetailViewModel(10L, channelRepository, metadataRepository, historyRepository)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.removeFromHistory()
        testDispatcher.scheduler.advanceUntilIdle()

        // Keyed by the series, not by the episode: deleting one episode would leave the
        // series in "continue watching" at the one before it, which reads as a dead button.
        coVerify { historyRepository.removeSeriesFromHistory(sampleChannel.stableKey) }
        val state = assertInstanceOf(SeriesDetailUiState.Success::class.java, viewModel.uiState.value)
        assertNull(state.resumeEpisode)
        assertEquals(0L, state.resumePositionMillis)
    }

    @Test
    fun `a resume key that matches no episode leaves nothing to resume`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(10L) } returns sampleChannel
        coEvery { channelRepository.getSeriesDetails(sampleChannel) } returns SeriesDetailsResult.Success(sampleDetails)
        // A stale position from an episode the provider has since removed must not crash
        // or resurrect a button pointing at nothing.
        coEvery { historyRepository.mostRecentlyWatched(any()) } returns ("http://gone.invalid/9.mp4" to 60_000L)

        val viewModel = SeriesDetailViewModel(10L, channelRepository, metadataRepository, historyRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = assertInstanceOf(SeriesDetailUiState.Success::class.java, viewModel.uiState.value)
        assertNull(state.resumeEpisode)
    }

    @Test
    fun `loads series details successfully`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(10L) } returns sampleChannel
        coEvery { channelRepository.getSeriesDetails(sampleChannel) } returns SeriesDetailsResult.Success(sampleDetails)
        coEvery { historyRepository.mostRecentlyWatched(any()) } returns null

        val viewModel = SeriesDetailViewModel(10L, channelRepository, metadataRepository, historyRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = assertInstanceOf(SeriesDetailUiState.Success::class.java, viewModel.uiState.value)
        assertEquals(sampleChannel, state.channel)
        assertEquals(sampleDetails, state.details)
        // Nothing watched yet, so nothing to resume — the button must not appear.
        assertNull(state.resumeEpisode)
        assertEquals(1, state.details.seasons.size)
        assertEquals("Pilot", state.details.seasons[0].episodes[0].title)
    }

    @Test
    fun `emits error when channel is not found`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(99L) } returns null

        val viewModel = SeriesDetailViewModel(99L, channelRepository, metadataRepository, historyRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = assertInstanceOf(SeriesDetailUiState.Error::class.java, viewModel.uiState.value)
        assertEquals(SourceError.NotFound, state.error)
    }

    @Test
    fun `emits error when series details fetch fails`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(10L) } returns sampleChannel
        coEvery {
            channelRepository.getSeriesDetails(sampleChannel)
        } returns SeriesDetailsResult.Failure(SourceError.UnreachableHost)

        val viewModel = SeriesDetailViewModel(10L, channelRepository, metadataRepository, historyRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = assertInstanceOf(SeriesDetailUiState.Error::class.java, viewModel.uiState.value)
        assertEquals(SourceError.UnreachableHost, state.error)
    }
}
