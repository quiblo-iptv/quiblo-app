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

import dev.quiblo.core.data.FilterIndex
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.SearchOptions
import dev.quiblo.core.data.SearchRepository
import dev.quiblo.core.data.SearchResults
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.model.Source
import dev.quiblo.core.model.SourceKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * The two questions the search screen asks that are not the search term — `027` #5 and #7.
 *
 * Both are here rather than in a screen test because both are about what is *queried*: whether a
 * genre stays chosen, and whether live channels are looked at. A chip drawn in the right state
 * over a query that disagrees with it is the failure this file exists to catch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sourceRepository: SourceRepository = mockk()
    private val searchRepository: SearchRepository = mockk()
    private val metadataRepository: TitleMetadataRepository = mockk(relaxed = true)
    private val playerSettings: PlayerSettingsRepository = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sourceRepository.observeSources() } returns flowOf(listOf(SOURCE))
        // Off, which is the shipped default: an advanced search leaves live channels out unless
        // somebody has said otherwise.
        every { playerSettings.showLiveInSearch } returns flowOf(false)
        coEvery { searchRepository.filterIndex(any()) } returns FilterIndex(genres = GENRES, years = YEARS)
        coEvery { searchRepository.search(any(), any(), any()) } returns SearchResults()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * A genre chip chooses, and choosing again chooses the same thing.
     *
     * It used to clear on a second press, which on a remote is a trap rather than a shortcut: the
     * chip a viewer walks onto first is the one they are already filtering by, and one stray press
     * emptied the filter with nothing on screen saying it had.
     */
    @Test
    fun `pressing the chosen genre again keeps it chosen`() = runTest {
        val viewModel = searchViewModel()
        advanceUntilIdle()

        viewModel.selectGenre("Drama")
        advanceUntilIdle()
        assertEquals("Drama", viewModel.uiState.value.selectedGenre)

        viewModel.selectGenre("Drama")
        advanceUntilIdle()

        assertEquals(
            "Drama",
            viewModel.uiState.value.selectedGenre,
            "A second press of the chosen genre cleared the filter (`027` #7).",
        )
    }

    /** And Clear is what unchooses, which is why the chip does not have to. */
    @Test
    fun `clear puts the genre back to nothing`() = runTest {
        val viewModel = searchViewModel()
        advanceUntilIdle()
        viewModel.selectGenre("Drama")
        advanceUntilIdle()

        viewModel.clear()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.selectedGenre)
    }

    /**
     * A year chip does toggle, and that is deliberate.
     *
     * Unlike the genres, nothing else in that strip means "any year" — Clear empties the whole
     * search — and the chosen year is the chip a remote arrives on. Pressing it again is the
     * shortest way back out of a year, and there is no second meaning to be trapped by: the chip
     * either has a year on it or it does not.
     */
    @Test
    fun `pressing the chosen year again clears it`() = runTest {
        val viewModel = searchViewModel()
        advanceUntilIdle()

        viewModel.selectYear(2019)
        advanceUntilIdle()
        assertEquals(2019, viewModel.uiState.value.selectedYear)

        viewModel.selectYear(2019)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.selectedYear)
    }

    /** A year alone is a question, so it is asked without anything being typed. */
    @Test
    fun `a year with no term still searches`() = runTest {
        val viewModel = searchViewModel()
        advanceUntilIdle()

        viewModel.selectYear(1996)
        advanceUntilIdle()

        assertEquals(1996, optionsOfLastSearch().year)
        assertTrue(viewModel.uiState.value.isActive)
    }

    /** The years the strip offers are the cache's, and they reach the screen. */
    @Test
    fun `the years the cache holds are on the state`() = runTest {
        val viewModel = searchViewModel()
        advanceUntilIdle()

        assertEquals(YEARS, viewModel.uiState.value.years)
    }

    /**
     * Opening the filters drops live channels, and the switch puts them back.
     *
     * The first half is the existing rule and the second is `027` #5. Both are asserted against
     * the *query* rather than against the flag, because the flag is only ever a picture of it.
     */
    @Test
    fun `the live switch decides whether live channels are searched`() = runTest {
        val viewModel = searchViewModel()
        advanceUntilIdle()

        viewModel.search("fargo")
        viewModel.setAdvanced(true)
        advanceUntilIdle()
        assertFalse(
            optionsOfLastSearch().includeLive,
            "An advanced search asked for live channels with the setting off.",
        )
        assertFalse(viewModel.uiState.value.includeLive)

        viewModel.setIncludeLive(true)
        advanceUntilIdle()

        assertTrue(
            optionsOfLastSearch().includeLive,
            "The switch was turned on and the query still left live channels out (`027` #5).",
        )
        assertTrue(viewModel.uiState.value.includeLive)
    }

    /** And a plain search — no filters open — still looks everywhere, as it always has. */
    @Test
    fun `a search with the filters shut looks at live channels`() = runTest {
        val viewModel = searchViewModel()
        advanceUntilIdle()

        viewModel.search("fargo")
        advanceUntilIdle()

        assertTrue(optionsOfLastSearch().includeLive)
        assertTrue(viewModel.uiState.value.includeLive)
    }

    /**
     * Clearing forgets the switch rather than turning it off.
     *
     * "Off" is not what it was before anybody touched it — with the filters shut it was on — so a
     * clear that wrote `false` would leave the next search narrower than a fresh one.
     */
    @Test
    fun `clear forgets the live switch rather than turning it off`() = runTest {
        val viewModel = searchViewModel()
        advanceUntilIdle()
        viewModel.setAdvanced(true)
        viewModel.setIncludeLive(true)
        advanceUntilIdle()

        viewModel.clear()
        viewModel.setAdvanced(false)
        viewModel.search("fargo")
        advanceUntilIdle()

        assertTrue(optionsOfLastSearch().includeLive)
    }

    /**
     * The options the repository was last asked with, which is the only honest witness here.
     *
     * Every call is captured rather than the last, because a case that reads this twice — before
     * and after touching a switch — is the case worth writing, and a single slot holds one
     * invocation whatever it was asked for.
     */
    private fun optionsOfLastSearch(): SearchOptions {
        val options = mutableListOf<SearchOptions>()
        coVerify { searchRepository.search(any(), any(), capture(options)) }
        return options.last()
    }

    /**
     * A view model with somebody listening to it.
     *
     * The collector is the load-bearing half. `uiState` and the results behind it are
     * `WhileSubscribed`, which is right in the app and a trap in a test: with nobody collecting,
     * `value` stays at its initial value and no query is ever made, so every assertion here would
     * pass while measuring nothing at all.
     */
    private fun TestScope.searchViewModel(): SearchViewModel {
        val viewModel = SearchViewModel(
            sourceRepository = sourceRepository,
            searchRepository = searchRepository,
            metadataRepository = metadataRepository,
            playerSettingsRepository = playerSettings,
        )
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect { } }
        return viewModel
    }

    private companion object {
        val SOURCE = Source(
            id = 1L,
            name = "A provider",
            kind = SourceKind.M3U,
            url = "https://example.invalid/list.m3u",
            createdAtEpochMillis = 0L,
        )

        val GENRES = listOf("Drama", "Comedy")
        val YEARS = listOf(2019, 1996)
    }
}
