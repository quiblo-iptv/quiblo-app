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

package dev.quiblo.feature.vod

import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.TitleOpinionRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Play or Resume, and the race that used to decide it.
 *
 * A viewer watches four minutes of a film and presses back. The detail screen returns to the
 * foreground at the same moment the player above it is being disposed and is writing the position
 * it finished with — and the screen used to answer the question by reading once, from a lifecycle
 * effect, with nothing ordering the read against the write. When the read won, the screen offered
 * **Play**. Having read once, it never asked again, so the only fix available to the viewer was to
 * leave the screen and come back.
 *
 * The fix is not a better ordering. It is not needing one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailResumeTest {

    private val testDispatcher = StandardTestDispatcher()

    private val channelRepository: ChannelRepository = mockk(relaxed = true)
    private val metadataRepository: TitleMetadataRepository = mockk(relaxed = true)
    private val historyRepository: WatchHistoryRepository = mockk(relaxed = true)
    private val opinions: TitleOpinionRepository = mockk(relaxed = true)

    /** The database, as the screen sees it: a value that can change after the screen has read it. */
    private val resumePosition = MutableStateFlow(0L)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { channelRepository.findById(CHANNEL.id) } returns CHANNEL
        every { channelRepository.observeIsFavorite(any()) } returns flowOf(false)
        every { historyRepository.observeResumePosition(CHANNEL.stableKey) } returns resumePosition
        coEvery { historyRepository.resumePosition(CHANNEL.stableKey) } returns resumePosition.value
        every { metadataRepository.apiKey } returns MutableStateFlow(null)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The regression test for the whole defect, and the shape of it is the point.
     *
     * The screen is opened and settled *before* the position arrives — which is exactly the order
     * that used to produce "Play" for a film four minutes in. Nothing re-enters the screen and
     * nothing asks again; the write simply lands, and the button follows it.
     */
    @Test
    fun `a position written after the screen has settled still turns Play into Resume`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.ready().canResume, "there was nothing watched yet")

        resumePosition.value = FOUR_MINUTES
        advanceUntilIdle()

        assertTrue(viewModel.ready().canResume, "the position arrived and the screen did not notice")
        assertEquals(FOUR_MINUTES, viewModel.ready().resumePositionMillis)
    }

    /**
     * And the other direction, which is the same mechanism read backwards.
     *
     * Forgetting a film from the history has to take the Resume button away, and it did — through
     * a hand-written state update beside the delete. Watching the row instead means the button
     * follows the database whatever changed it.
     */
    @Test
    fun `a position removed while the screen is open takes Resume away`() = runTest {
        resumePosition.value = FOUR_MINUTES
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.ready().canResume)

        resumePosition.value = 0L
        advanceUntilIdle()

        assertFalse(viewModel.ready().canResume)
    }

    /** Under ten seconds is not a resume point: it would drop the viewer back at the start. */
    @Test
    fun `a few seconds in is still Play`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        resumePosition.value = 4_000L
        advanceUntilIdle()

        assertFalse(viewModel.ready().canResume)
    }

    private fun viewModel() = MovieDetailViewModel(
        channelId = CHANNEL.id,
        channelRepository = channelRepository,
        metadataRepository = metadataRepository,
        historyRepository = historyRepository,
        opinions = opinions,
    )

    private fun MovieDetailViewModel.ready() = uiState.value as MovieDetailUiState.Ready

    private companion object {
        val CHANNEL = Channel(
            id = 12L,
            sourceId = 1L,
            name = "Dune",
            streamUrl = "https://example.invalid/dune",
            kind = MediaKind.VOD,
        )

        const val FOUR_MINUTES = 240_000L
    }
}
