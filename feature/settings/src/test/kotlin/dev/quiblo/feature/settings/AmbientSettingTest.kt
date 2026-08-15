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

package dev.quiblo.feature.settings

import dev.quiblo.core.data.CategoryRepository
import dev.quiblo.core.data.ChannelLogoRepository
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.ScriptFilterRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.TitleMetadataScanner
import dev.quiblo.core.data.backup.BackupRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The ambient switch, and the one thing about it that can be quietly wrong.
 *
 * The feature is on by default, which means every layer that mirrors the stored value has to
 * start from `true` as well. A layer seeding `false` is not a bug anybody would find by reading
 * it: the store answers a moment later and the value corrects itself, so all it produces is a
 * switch that flickers off when the settings screen opens, and — in the player — a flash of dead
 * black bars at the start of every film. That is the whole of what is asserted here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientSettingTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playerSettingsRepository: PlayerSettingsRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("the switch reads as on before the store has answered")
    fun `the initial value is on, not off`() = runTest {
        // A store that never answers. Whatever the ViewModel reports now is the seed, which is
        // exactly the moment being tested — the frame before DataStore has read from disk.
        every { playerSettingsRepository.ambientPlayer } returns flow { }

        assertTrue(viewModel().ambientPlayer.value)
    }

    @Test
    fun `switching it off is reported and written through`() = runTest {
        val stored = MutableStateFlow(true)
        every { playerSettingsRepository.ambientPlayer } returns stored

        val viewModel = viewModel()
        // Subscribed, because the flow is shared `WhileSubscribed` — without a collector the
        // upstream is never read and the value stays at its seed, which would make the
        // assertion below pass for the wrong reason.
        backgroundScope.launch { viewModel.ambientPlayer.collect { } }

        viewModel.setAmbientPlayer(false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { playerSettingsRepository.setAmbientPlayer(false) }

        // And the screen follows the store rather than its own copy: the write above is faked,
        // so the value only moves when the store says it has.
        stored.value = false
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.ambientPlayer.value)
    }

    private fun viewModel() = SettingsViewModel(
        backupRepository = mockk<BackupRepository>(relaxed = true),
        playerSettingsRepository = playerSettingsRepository,
        metadataRepository = mockk<TitleMetadataRepository>(relaxed = true),
        categoryRepository = mockk<CategoryRepository>(relaxed = true),
        sourceRepository = mockk<SourceRepository>(relaxed = true),
        channelLogoRepository = mockk<ChannelLogoRepository>(relaxed = true),
        metadataScanner = mockk<TitleMetadataScanner>(relaxed = true),
        scriptFilter = mockk<ScriptFilterRepository>(relaxed = true),
    )
}
