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
package dev.quiblo.feature.player

import dev.quiblo.core.data.ApplicationScope
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.ProviderSubtitleResult
import dev.quiblo.core.data.SubtitleRepository
import dev.quiblo.core.data.WatchEventRepository
import dev.quiblo.core.media.PlayableItem
import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.PlayerController
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SubtitleFile
import dev.quiblo.core.model.VodDetails
import dev.quiblo.source.api.VodDetailsResult
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A panel's subtitle never reaches the engine as a URL (`BUG-045`).
 *
 * A sidecar the engine cannot load fails the whole item, so a dead subtitle link stopped a film
 * that was playing fine. Every URL here is synthetic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerOffersPanelSubtitlesTest {

    private val controller: PlayerController = mockk(relaxed = true)
    private val channelRepository: ChannelRepository = mockk(relaxed = true)
    private val settingsRepository: PlayerSettingsRepository = mockk(relaxed = true)
    private val subtitleRepository: SubtitleRepository = mockk(relaxed = true)
    private val playbackState = MutableStateFlow(PlaybackState())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { controller.state } returns playbackState
        every { settingsRepository.settings } returns flowOf(PlayerSettings())
        coEvery { subtitleRepository.forTitle(any()) } returns emptyList()
        coEvery { channelRepository.findById(FILM.id) } returns FILM
        coEvery { channelRepository.playbackUrl(any(), any()) } returns FILM.streamUrl
        coEvery { channelRepository.getVodDetails(FILM.id) } returns
            VodDetailsResult.Success(VodDetails(vodId = "2", title = FILM.name, subtitles = listOf(PANEL_SUBTITLE)))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlayerViewModel(
        controller = controller,
        channelRepository = channelRepository,
        historyRepository = mockk(relaxed = true),
        subtitleRepository = subtitleRepository,
        settingsRepository = settingsRepository,
        applicationScope = ApplicationScope(CoroutineScope(UnconfinedTestDispatcher())),
        watchEvents = mockk<WatchEventRepository>(relaxed = true),
        diagnoser = mockk(relaxed = true),
    )

    private fun preparedItems(): List<PlayableItem> {
        val items = mutableListOf<PlayableItem>()
        verify { controller.prepare(capture(items)) }
        return items
    }

    @Test
    fun `a film starts without the panel's subtitle, which is offered instead`() = runTest {
        val viewModel = viewModel()

        viewModel.load(channelId = FILM.id)
        advanceUntilIdle()

        assertTrue(preparedItems().single().subtitles.isEmpty())
        assertEquals(listOf(PANEL_SUBTITLE), viewModel.offeredSubtitles.value.map { it.subtitle })
    }

    @Test
    fun `choosing it fetches it and restarts the film where it was, showing it`() = runTest {
        val local = PANEL_SUBTITLE.copy(uri = "file:///data/subtitles/provider/abc.srt")
        coEvery { subtitleRepository.fetchProviderSubtitle(PANEL_SUBTITLE) } returns
            ProviderSubtitleResult.Fetched(local)
        val viewModel = viewModel()
        viewModel.load(channelId = FILM.id)
        advanceUntilIdle()
        playbackState.value = PlaybackState(positionMillis = 754_000L)
        clearMocks(controller, answers = false)

        viewModel.selectTrack(TrackMenuKind.SUBTITLES, viewModel.offeredSubtitles.value.single().id)
        advanceUntilIdle()

        val restarted = slot<PlayableItem>()
        verify(exactly = 1) { controller.prepare(capture(restarted)) }
        assertEquals(listOf(local.copy(selectOnStart = true)), restarted.captured.subtitles)
        assertEquals(754_000L, restarted.captured.startPositionMillis)
        assertTrue(viewModel.offeredSubtitles.value.isEmpty(), "a fetched subtitle is a track now, not an offer")
        verify(exactly = 0) { controller.selectTextTrack(any()) }
    }

    @Test
    fun `one that does not arrive leaves the film alone and says so`() = runTest {
        coEvery { subtitleRepository.fetchProviderSubtitle(any()) } returns ProviderSubtitleResult.Unavailable
        val viewModel = viewModel()
        viewModel.load(channelId = FILM.id)
        advanceUntilIdle()
        clearMocks(controller, answers = false)

        viewModel.selectTrack(TrackMenuKind.SUBTITLES, viewModel.offeredSubtitles.value.single().id)
        advanceUntilIdle()

        verify(exactly = 0) { controller.prepare(any()) }
        assertEquals(OfferedSubtitleStatus.UNAVAILABLE, viewModel.offeredSubtitles.value.single().status)
        assertEquals(SubtitleNotice.SUBTITLE_FAILED, viewModel.subtitleNotice.value)
    }

    @Test
    fun `a subtitle the engine dropped is explained`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        playbackState.value = PlaybackState(subtitleDropped = true)
        advanceUntilIdle()

        assertEquals(SubtitleNotice.SUBTITLE_FAILED, viewModel.subtitleNotice.value)
    }

    private companion object {
        val FILM = Channel(
            id = 2L,
            sourceId = 1L,
            name = "A Film",
            streamUrl = "http://panel.example.invalid/movie/u/p/2.mkv",
            kind = MediaKind.VOD,
            tvgId = "xtream-vod-2",
        )
        val PANEL_SUBTITLE = SubtitleFile(
            uri = "http://panel.example.invalid/subtitle/12345",
            label = "English",
            mimeType = "application/x-subrip",
            language = "en",
        )
    }
}
