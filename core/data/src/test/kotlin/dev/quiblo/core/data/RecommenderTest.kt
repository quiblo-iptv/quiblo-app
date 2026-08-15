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

import dev.quiblo.core.model.MediaKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The whole of the suggestions feature, which is a scoring function and nothing else.
 *
 * `013` INC-F2 argued this should be arithmetic rather than a model, and one of the reasons was
 * that arithmetic can be checked. This is that check: no database, no Android, no clock — the
 * time is a parameter — so every property below is asserted directly rather than inferred from a
 * row on a screen.
 */
class RecommenderTest {

    @Test
    @DisplayName("a viewer with no history is offered nothing")
    fun `no history means no suggestions`() {
        assertTrue(Recommender.suggest(emptyList(), catalogue(), NOW).isEmpty())
    }

    @Test
    @DisplayName("an unscanned catalogue is offered nothing")
    fun `candidates with no genres are not suggested`() {
        val watched = listOf(watched("fargo", listOf("Crime")))
        val undescribed = listOf(
            CandidateTitle(channelId = 1L, kind = MediaKind.VOD, title = "heat", genres = emptyList()),
        )

        assertTrue(Recommender.suggest(watched, undescribed, NOW).isEmpty())
    }

    @Test
    @DisplayName("watching a crime film gets you crime films")
    fun `the strongest genre match wins`() {
        val watched = listOf(watched("fargo", listOf("Crime", "Drama")))

        val suggestions = Recommender.suggest(watched, catalogue(), NOW, limit = 1)

        assertEquals(listOf(CRIME_FILM), suggestions.map { it.channelId })
    }

    @Test
    @DisplayName("the row says which of your own choices caused each tile")
    fun `each suggestion names the title that caused it`() {
        val watched = listOf(
            watched("fargo", listOf("Crime")),
            watched("alien", listOf("Horror")),
        )

        val suggestions = Recommender.suggest(watched, catalogue(), NOW)

        assertEquals("fargo", suggestions.first { it.channelId == CRIME_FILM }.becauseOf)
        assertEquals("alien", suggestions.first { it.channelId == HORROR_FILM }.becauseOf)
    }

    @Test
    @DisplayName("nothing already watched is suggested back")
    fun `watched titles are excluded`() {
        val watched = listOf(watched("crime one", listOf("Crime")))

        val suggestions = Recommender.suggest(watched, catalogue(), NOW)

        assertTrue(suggestions.none { it.channelId == CRIME_FILM })
    }

    /**
     * The reason suggestions are chosen one at a time.
     *
     * Taking the top five in one pass produces five thrillers: the genres that score best score
     * best for everything, and nothing in the arithmetic notices it has already said that. Here
     * the viewer's history is overwhelmingly crime, and the second-strongest genre still has to
     * reach the row.
     */
    @Test
    @DisplayName("a row is not five of the same thing")
    fun `the saturation penalty breaks a run of one genre`() {
        val watched = listOf(
            watched("fargo", listOf("Crime")),
            watched("heat", listOf("Crime")),
            watched("alien", listOf("Horror")),
        )
        val crimeShelf = (1..5).map { index ->
            CandidateTitle(index.toLong(), MediaKind.VOD, "crime $index", listOf("Crime"))
        }
        val oneHorror = CandidateTitle(99L, MediaKind.VOD, "horror one", listOf("Horror"))

        val suggestions = Recommender.suggest(watched, crimeShelf + oneHorror, NOW, limit = 3)

        assertTrue(
            suggestions.any { it.channelId == 99L },
            "the horror film never reached a three-tile row against five crime films",
        )
    }

    /**
     * Recent viewing counts for more than old viewing, and neither ever drops off a cliff.
     *
     * Two genres watched equally, one of them a year ago. The half-life is a month, so the recent
     * one should be worth more than a thousand times the old one — but the old one is still worth
     * something, which is what stops a row emptying because somebody was away.
     */
    @Test
    @DisplayName("last week counts for more than last year")
    fun `age decays a taste without deleting it`() {
        val watched = listOf(
            watched("fargo", listOf("Crime"), watchedAt = NOW - YEAR_MILLIS),
            watched("alien", listOf("Horror"), watchedAt = NOW),
        )

        val suggestions = Recommender.suggest(watched, catalogue(), NOW)

        assertEquals(HORROR_FILM, suggestions.first().channelId)
        assertTrue(
            suggestions.any { it.channelId == CRIME_FILM },
            "an old taste disappeared entirely rather than fading",
        )
    }

    /**
     * A title opened and abandoned is weak evidence, not equal evidence.
     *
     * Both were watched at the same moment, so the only difference is how much of each was
     * actually seen — and the one watched through must win.
     */
    @Test
    @DisplayName("something you bounced off does not steer the row")
    fun `how much was watched changes what it is worth`() {
        val watched = listOf(
            watched("fargo", listOf("Crime"), fraction = 0.02),
            watched("alien", listOf("Horror"), fraction = 1.0),
        )

        val suggestions = Recommender.suggest(watched, catalogue(), NOW)

        assertEquals(HORROR_FILM, suggestions.first().channelId)
    }

    /** A row that reshuffles between two recompositions reads as a bug whatever produced it. */
    @Test
    @DisplayName("the same history gives the same row twice running")
    fun `the result is stable`() {
        val watched = listOf(watched("fargo", listOf("Crime", "Drama")))

        val first = Recommender.suggest(watched, catalogue(), NOW)
        val second = Recommender.suggest(watched, catalogue(), NOW)

        assertEquals(first, second)
    }

    /** Films and series together, because the row mixes them and the badge says which. */
    @Test
    @DisplayName("a series can be suggested from a film's genres")
    fun `both kinds are candidates`() {
        val watched = listOf(watched("fargo", listOf("Crime")))

        val suggestions = Recommender.suggest(watched, catalogue(), NOW)

        assertTrue(suggestions.any { it.kind == MediaKind.SERIES })
    }

    private fun catalogue() = listOf(
        CandidateTitle(CRIME_FILM, MediaKind.VOD, "crime one", listOf("Crime")),
        CandidateTitle(HORROR_FILM, MediaKind.VOD, "horror one", listOf("Horror")),
        CandidateTitle(CRIME_SERIES, MediaKind.SERIES, "crime series", listOf("Crime")),
        CandidateTitle(WESTERN_FILM, MediaKind.VOD, "western one", listOf("Western")),
    )

    private fun watched(
        title: String,
        genres: List<String>,
        fraction: Double = 1.0,
        watchedAt: Long = NOW,
    ) = WatchedTitle(title, genres, fraction, watchedAt)

    private companion object {
        const val NOW = 1_770_000_000_000L
        const val YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000

        const val CRIME_FILM = 10L
        const val HORROR_FILM = 20L
        const val CRIME_SERIES = 30L
        const val WESTERN_FILM = 40L
    }
}
