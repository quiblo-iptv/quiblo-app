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

package dev.quiblo.core.data

import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * `INC-F6`: four shapes out of two booleans.
 *
 * The item exists because a series with a thousand episodes is unusable if the newest is a
 * thousand rows away. What these assert is the part that is easy to get subtly wrong — the
 * *order*, and the fact that merging must not throw away which season an episode belongs to.
 */
@DisplayName("season arrangement")
class SeriesArrangementTest {

    private val seasons = listOf(
        // Deliberately out of order, and season 10 before season 2: providers return them like
        // this often enough that concatenating in the provider's order is a real bug rather
        // than a hypothetical one.
        season(10, episodes = listOf(1, 2)),
        season(2, episodes = listOf(2, 1)),
    )

    @Test
    fun `by default nothing moves between seasons`() {
        val arranged = seasons.arrangedBy(SeriesPreference())

        assertEquals(listOf(10, 2), arranged.map { it.seasonNumber }, "seasons were reordered")
        // Episodes within a season are sorted even when seasons are not — season 2 arrived as
        // 2 then 1.
        assertEquals(listOf(1, 2), arranged[1].episodes.map { it.episodeNumber })
    }

    @Test
    @DisplayName("merging sorts across seasons, not just within them")
    fun `merged puts season 2 before season 10`() {
        val arranged = seasons.arrangedBy(SeriesPreference(isMerged = true))

        assertEquals(1, arranged.size)
        assertEquals(
            listOf(2 to 1, 2 to 2, 10 to 1, 10 to 2),
            arranged.single().episodes.map { it.seasonNumber to it.episodeNumber },
        )
    }

    @Test
    @DisplayName("a merged list still knows which season each episode is from")
    fun `merging does not flatten the season away`() {
        // The whole honesty rule of this item. Episode 1 of season 2 and episode 1 of season 10
        // sit in one list, and a row that has lost its season number is ambiguous at exactly
        // the point the merged view is most useful.
        val episodes = seasons.arrangedBy(SeriesPreference(isMerged = true)).single().episodes

        assertTrue(episodes.any { it.seasonNumber == 2 })
        assertTrue(episodes.any { it.seasonNumber == 10 })
    }

    @Test
    fun `the merged season carries no name of its own`() {
        // `:core:data` holds no display strings, so the screen names it. The marker is the
        // season number, which no provider uses.
        val merged = seasons.arrangedBy(SeriesPreference(isMerged = true)).single()

        assertEquals(MERGED_SEASON_NUMBER, merged.seasonNumber)
        assertEquals("", merged.name)
    }

    @Test
    fun `newest first reverses the episodes and not the seasons`() {
        val arranged = seasons.arrangedBy(SeriesPreference(isDescending = true))

        assertEquals(listOf(10, 2), arranged.map { it.seasonNumber }, "seasons were reordered")
        assertEquals(listOf(2, 1), arranged[0].episodes.map { it.episodeNumber })
    }

    @Test
    @DisplayName("merged and newest first is the thousand-episode case")
    fun `both together put the latest episode at the top`() {
        val arranged = seasons.arrangedBy(SeriesPreference(isMerged = true, isDescending = true))

        assertEquals(
            listOf(10 to 2, 10 to 1, 2 to 2, 2 to 1),
            arranged.single().episodes.map { it.seasonNumber to it.episodeNumber },
        )
    }

    @Test
    fun `an empty series stays empty rather than gaining a merged season`() {
        // A merged season holding nothing would draw a heading over an empty list, which reads
        // as a screen that failed to load rather than a series with no episodes.
        assertTrue(emptyList<Season>().arrangedBy(SeriesPreference(isMerged = true)).isEmpty())
    }

    private fun season(number: Int, episodes: List<Int>) = Season(
        seasonNumber = number,
        name = "Season $number",
        episodes = episodes.map { episode(number, it) },
    )

    private fun episode(seasonNumber: Int, episodeNumber: Int) = Episode(
        id = "s${seasonNumber}e$episodeNumber",
        title = "Episode $episodeNumber",
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        streamUrl = "https://example.invalid/s${seasonNumber}e$episodeNumber.mp4",
    )
}
