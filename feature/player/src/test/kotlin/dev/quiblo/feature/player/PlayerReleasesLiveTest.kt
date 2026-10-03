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
import dev.quiblo.core.data.SubtitleRepository
import dev.quiblo.core.data.WatchEventRepository
import dev.quiblo.core.media.PlayableItem
import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.PlaybackStatus
import dev.quiblo.core.media.PlayerController
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.PlayerSettings
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A live channel the viewer is not watching does not hold the account's connection (`BUG-037`).
 *
 * On an account allowed one screen, a paused channel in the background *is* that screen, and every
 * other device — and this one, after a channel change — is refused for as long as it sits there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerReleasesLiveTest {

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
        coEvery { channelRepository.findById(LIVE.id) } returns LIVE
        coEvery { channelRepository.findById(FILM.id) } returns FILM
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

    private fun playing(channel: Channel) {
        playbackState.value = PlaybackState(
            status = PlaybackStatus.PLAYING,
            item = PlayableItem(
                id = channel.stableKey,
                title = channel.name,
                url = channel.streamUrl,
                isLive = channel.kind == MediaKind.LIVE,
            ),
        )
    }

    @Test
    fun `a live channel sent to the background lets go of its connection`() = runTest {
        val viewModel = viewModel()
        playing(LIVE)

        viewModel.onStopped()

        verify(exactly = 1) { controller.stop() }
        verify(exactly = 0) { controller.pause() }
    }

    @Test
    fun `a film sent to the background is paused, and keeps its place`() = runTest {
        val viewModel = viewModel()
        playing(FILM)

        viewModel.onStopped()

        verify(exactly = 1) { controller.pause() }
        verify(exactly = 0) { controller.stop() }
    }

    @Test
    fun `coming back to a live channel starts it again`() = runTest {
        val viewModel = viewModel()
        playing(LIVE)
        viewModel.onStopped()

        viewModel.onStarted()

        verify(exactly = 1) { controller.retry() }
    }

    @Test
    fun `coming back does nothing when nothing was let go`() = runTest {
        val viewModel = viewModel()
        playing(FILM)
        viewModel.onStopped()

        viewModel.onStarted()
        // The first ON_START a screen sees, when its observer is added, must not restart anything.
        viewModel.onStarted()

        verify(exactly = 0) { controller.retry() }
    }

    @Test
    fun `leaving the player lets go of a live channel and lets the same channel load again`() = runTest {
        val viewModel = viewModel()
        viewModel.load(channelId = LIVE.id)
        advanceUntilIdle()
        playing(LIVE)

        viewModel.onLeft()
        verify(exactly = 1) { controller.stop() }

        // Choosing the same channel again is a fresh load, not a request found already "loaded".
        clearMocks(controller, answers = false)
        viewModel.load(channelId = LIVE.id)
        advanceUntilIdle()
        verify(exactly = 1) { controller.prepare(any()) }

        // And nothing restarts the old one on the way back in.
        viewModel.onStarted()
        verify(exactly = 0) { controller.retry() }
    }

    private companion object {
        val LIVE = Channel(
            id = 1L,
            sourceId = 1L,
            name = "News",
            streamUrl = "http://panel.example.invalid/live/u/p/1.ts",
            kind = MediaKind.LIVE,
            tvgId = "news.epg",
        )
        val FILM = Channel(
            id = 2L,
            sourceId = 1L,
            name = "A Film",
            streamUrl = "http://panel.example.invalid/movie/u/p/2.mp4",
            kind = MediaKind.VOD,
            tvgId = "xtream-vod-2",
        )
    }
}
