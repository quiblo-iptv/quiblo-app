/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.feature.series

import dev.vibrato.core.data.ChannelRepository
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.Episode
import dev.vibrato.core.model.MediaKind
import dev.vibrato.core.model.Season
import dev.vibrato.core.model.SeriesDetails
import dev.vibrato.source.api.SeriesDetailsResult
import dev.vibrato.source.api.SourceError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val channelRepository: ChannelRepository = mockk()

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
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads series details successfully`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(10L) } returns sampleChannel
        coEvery { channelRepository.getSeriesDetails(sampleChannel) } returns SeriesDetailsResult.Success(sampleDetails)

        val viewModel = SeriesDetailViewModel(10L, channelRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = assertInstanceOf(SeriesDetailUiState.Success::class.java, viewModel.uiState.value)
        assertEquals(sampleChannel, state.channel)
        assertEquals(sampleDetails, state.details)
        assertEquals(1, state.details.seasons.size)
        assertEquals("Pilot", state.details.seasons[0].episodes[0].title)
    }

    @Test
    fun `emits error when channel is not found`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(99L) } returns null

        val viewModel = SeriesDetailViewModel(99L, channelRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = assertInstanceOf(SeriesDetailUiState.Error::class.java, viewModel.uiState.value)
        assertEquals(SourceError.NotFound, state.error)
    }

    @Test
    fun `emits error when series details fetch fails`() = runTest(testDispatcher) {
        coEvery { channelRepository.findById(10L) } returns sampleChannel
        coEvery { channelRepository.getSeriesDetails(sampleChannel) } returns SeriesDetailsResult.Failure(SourceError.UnreachableHost)

        val viewModel = SeriesDetailViewModel(10L, channelRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = assertInstanceOf(SeriesDetailUiState.Error::class.java, viewModel.uiState.value)
        assertEquals(SourceError.UnreachableHost, state.error)
    }
}
