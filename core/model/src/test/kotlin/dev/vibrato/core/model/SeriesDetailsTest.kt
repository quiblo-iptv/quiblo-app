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

package dev.vibrato.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeriesDetailsTest {

    @Test
    fun `episode constructs with expected fields and default logoUrl`() {
        val episode = Episode(
            id = "ep101",
            title = "Episode 1",
            seasonNumber = 1,
            episodeNumber = 1,
            streamUrl = "http://stream.example.invalid/ep101.mp4",
        )

        assertEquals("ep101", episode.id)
        assertEquals("Episode 1", episode.title)
        assertEquals(1, episode.seasonNumber)
        assertEquals(1, episode.episodeNumber)
        assertEquals("http://stream.example.invalid/ep101.mp4", episode.streamUrl)
        assertNull(episode.logoUrl)
    }

    @Test
    fun `season holds ordered list of episodes`() {
        val ep1 = Episode("1", "E1", 1, 1, "http://url1")
        val ep2 = Episode("2", "E2", 1, 2, "http://url2")
        val season = Season(seasonNumber = 1, name = "Season 1", episodes = listOf(ep1, ep2))

        assertEquals(1, season.seasonNumber)
        assertEquals("Season 1", season.name)
        assertEquals(2, season.episodes.size)
        assertEquals("E1", season.episodes[0].title)
        assertEquals("E2", season.episodes[1].title)
    }

    @Test
    fun `series details defaults to empty seasons and null optional fields`() {
        val details = SeriesDetails(seriesId = "s123", title = "My Show")

        assertEquals("s123", details.seriesId)
        assertEquals("My Show", details.title)
        assertNull(details.overview)
        assertNull(details.coverUrl)
        assertTrue(details.seasons.isEmpty())
    }
}
