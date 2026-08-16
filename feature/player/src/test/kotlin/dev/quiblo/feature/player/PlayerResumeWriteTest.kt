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
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.media.PlayableItem
import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.PlayerController
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.PlayerSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * Where a resume point is written from, and how often.
 *
 * Both halves of the same defect: a viewer backs out of a film and the detail screen offers
 * **Play** for something they were four minutes into.
 *
 * The first half is *who owns the write*. On the phone this ViewModel belongs to its navigation
 * entry, so the back press that makes a position worth saving is the same event that clears the
 * store and cancels `viewModelScope` — and the `onCleared` fallback cannot cover it, because
 * androidx closes the scope before calling `onCleared`. The write therefore belongs to something
 * that outlives the screen.
 *
 * The second half is *when*. A position written only at stop is a position lost entirely whenever
 * the process does not get to run its shutdown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerResumeWriteTest {

    private val testDispatcher = StandardTestDispatcher()

    private val controller: PlayerController = mockk(relaxed = true)
    private val channelRepository: ChannelRepository = mockk(relaxed = true)
    private val historyRepository: WatchHistoryRepository = mockk(relaxed = true)
    private val subtitleRepository: SubtitleRepository = mockk(relaxed = true)
    private val settingsRepository: PlayerSettingsRepository = mockk(relaxed = true)
    private val watchEvents: WatchEventRepository = mockk(relaxed = true)

    private val playbackState = MutableStateFlow(PlaybackState())
    private val saved = mutableListOf<HistoryEntry>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { controller.state } returns playbackState
        every { settingsRepository.settings } returns flowOf(PlayerSettings())
        coEvery { channelRepository.findById(CHANNEL.id) } returns CHANNEL
        coEvery { subtitleRepository.forTitle(any()) } returns emptyList()
        coEvery { historyRepository.resumePosition(any()) } returns 0L
        coEvery { historyRepository.saveProgress(capture(saved)) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        saved.clear()
    }

    /**
     * The regression test for the cancelled write, and it works by the clocks being different.
     *
     * The screen's scope runs on the main dispatcher, which in this test is a `StandardTestDispatcher`
     * — nothing on it runs until the test advances it. The application scope runs unconfined, so
     * anything launched on it has already finished by the time the next line executes.
     *
     * So: stop playback, assert immediately, advance nothing. A write on `viewModelScope` has not
     * happened yet and the assertion fails; a write on the application scope has, and it passes.
     * That is the whole difference the fix makes, and on the phone it is the difference between
     * saving a resume point and having it cancelled by the very back press that earned it.
     */
    @Test
    fun `the position is written somewhere the screen going away cannot cancel`() = runTest {
        val viewModel = viewModelPlaying()
        playbackState.value = playbackState.value.copy(positionMillis = FOUR_MINUTES)

        viewModel.onStopped()

        assertEquals(1, saved.size, "the resume point was queued on the screen's own scope")
        assertEquals(FOUR_MINUTES, saved.single().positionMillis)
    }

    /**
     * Every ten seconds of playback, and not once per frame.
     *
     * The in-memory position ticks every 500ms; this is about how often it reaches the database.
     * Driven by the position rather than by a clock, so the count follows what was watched.
     */
    @Test
    fun `a position is written down every ten seconds of playback`() = runTest {
        viewModelPlaying()

        listOf(2_000L, 6_000L, 11_000L, 14_000L, 21_000L).forEach { position ->
            playbackState.value = playbackState.value.copy(positionMillis = position)
            advanceUntilIdle()
        }

        // Two, at the two boundaries crossed. The positions inside a window cost nothing, and the
        // first window is not written at all — a title opened and left within ten seconds is one
        // `onStopped` will record anyway, and one nobody is coming back to.
        assertEquals(2, saved.size)
        assertEquals(listOf(11_000L, 21_000L), saved.map { it.positionMillis })
    }

    @Test
    fun `nothing is written for a live channel, however long it is left on`() = runTest {
        val viewModel = viewModelPlaying(isLive = true)
        playbackState.value = playbackState.value.copy(positionMillis = FOUR_MINUTES)
        advanceUntilIdle()

        viewModel.onStopped()

        // A channel is not something anybody continues, and putting one in continue-watching
        // fills the row with things that cannot be resumed.
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a position still at zero is not written down`() = runTest {
        viewModelPlaying()

        advanceUntilIdle()

        assertTrue(saved.isEmpty())
    }

    /**
     * The regression test for a crash on every press of Play, found on the panel and caused here.
     *
     * `viewModelScope` dispatches on `Dispatchers.Main.immediate`, and *immediate* means what it
     * says: on the main thread the body of a `launch` runs before the call returns. So an `init`
     * block that collects a property declared below it collects a null — the property has not been
     * assigned yet — and the app dies the moment anything is played.
     *
     * **Every other test in this file passed while that was true**, because `StandardTestDispatcher`
     * queues the launch instead of running it, which is exactly the behaviour the real main thread
     * does not have. This one uses an unconfined dispatcher as main, which reproduces the immediate
     * execution, and it fails with a `NullPointerException` if the block moves back above `state`.
     */
    @Test
    fun `constructing the ViewModel on a main thread that runs launches immediately does not crash`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val viewModel = PlayerViewModel(
            controller = controller,
            channelRepository = channelRepository,
            historyRepository = historyRepository,
            subtitleRepository = subtitleRepository,
            settingsRepository = settingsRepository,
            applicationScope = ApplicationScope(CoroutineScope(UnconfinedTestDispatcher())),
            watchEvents = watchEvents,
        )

        // Reaching this line is the assertion. The state is read as well, because a constructor
        // that survives by never touching the property would pass a test that only checked it ran.
        assertEquals(playbackState.value, viewModel.state.value)
    }

    /** A ViewModel with something loaded and playing, which is what makes a position meaningful. */
    private fun TestScope.viewModelPlaying(isLive: Boolean = false): PlayerViewModel {
        val channel = if (isLive) CHANNEL.copy(kind = MediaKind.LIVE) else CHANNEL
        coEvery { channelRepository.findById(channel.id) } returns channel

        val viewModel = PlayerViewModel(
            controller = controller,
            channelRepository = channelRepository,
            historyRepository = historyRepository,
            subtitleRepository = subtitleRepository,
            settingsRepository = settingsRepository,
            // The real one is backed by Dispatchers.IO and outlives everything. Here it is
            // unconfined, which is what makes it distinguishable from the screen's own scope.
            applicationScope = ApplicationScope(CoroutineScope(UnconfinedTestDispatcher())),
            watchEvents = watchEvents,
        )
        viewModel.load(channelId = channel.id)
        advanceUntilIdle()
        playbackState.value = PlaybackState(
            item = PlayableItem(id = channel.stableKey, title = channel.name, url = channel.streamUrl, isLive = isLive),
        )
        return viewModel
    }

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
