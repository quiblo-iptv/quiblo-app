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
import dev.quiblo.core.model.Opinion
import dev.quiblo.core.model.WatchOrigin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The arithmetic behind "You may like", one signal at a time.
 *
 * **This is the file that answers the complaint.** A viewer who had watched One Piece was offered
 * The Boys, The Umbrella Academy and a dubbed Arabic family drama — three titles that share
 * "series" and "Action & Adventure" with it and nothing else anybody would recognise. Every test
 * below is a fact the old scorer could not see and the new one can, asserted on its own so that
 * losing one is a named failure rather than a slightly worse row.
 *
 * A whole-row assertion is deliberately rare here: a row is the product of thirteen weights, and a
 * test that pins the row pins the weights, which makes tuning any of them a test failure rather
 * than a measurement.
 */
class RecommenderTest {

    @Test
    @DisplayName("one watched title is not enough to answer, and saying nothing is the answer")
    fun `a cold start draws nothing`() {
        val row = Recommender.suggest(
            watched = listOf(watched("one piece", genres = listOf("Animation"))),
            candidates = listOf(candidate(1, "naruto", genres = listOf("Animation"))),
            now = NOW,
        )

        // Not a worse row. No row: four confident suggestions from one watched title is exactly
        // what was reported, and no weighting fixes it — only declining to answer does.
        assertTrue(row.isEmpty())
    }

    @Test
    fun `browsing twenty things without finishing any is not learning either`() {
        val row = Recommender.suggest(
            watched = (1..20).map { watched("title $it", genres = listOf("Drama"), fraction = 0.1) },
            candidates = listOf(candidate(1, "another", genres = listOf("Drama"))),
            now = NOW,
        )

        assertTrue(row.isEmpty())
    }

    /**
     * Ten titles starred and none of them finished is enough to answer — `027` #8.
     *
     * **The reported case, exactly as it happened.** A viewer opened ten films, left each of them
     * part-way, and marked ten titles as favourites; the row never appeared, because the rule read
     * "watched most of the way through" and nothing else. Starring is not weaker evidence than
     * reaching the sixty-percent mark — nobody stars a title by accident, and plenty of people
     * leave a film running while falling asleep.
     */
    @Test
    fun `titles that were starred rather than finished are enough to answer`() {
        val starred = (1..Recommender.MINIMUM_DISTINCT_TITLES).map {
            watched("starred $it", ANIME, fraction = 0.1, isFavourite = true)
        }

        val row = Recommender.suggest(starred, listOf(candidate(1, "naruto", ANIME)), NOW)

        assertTrue(
            row.isNotEmpty(),
            "Five starred titles produced no suggestions, which is the report: a viewer who has " +
                "said what they like ten times over is told nothing (`027` #8).",
        )
    }

    /** And a thumb up says the same thing in the other vocabulary the app collects. */
    @Test
    fun `titles given a thumbs up are enough to answer`() {
        val liked = (1..Recommender.MINIMUM_DISTINCT_TITLES).map {
            watched("liked $it", ANIME, fraction = 0.1, opinion = Opinion.UP)
        }

        assertTrue(Recommender.suggest(liked, listOf(candidate(1, "naruto", ANIME)), NOW).isNotEmpty())
    }

    @Test
    fun `once there is enough, it answers`() {
        assertTrue(Recommender.suggest(learned(), listOf(candidate(1, "naruto", ANIME)), NOW).isNotEmpty())
    }

    /**
     * The headline: anime is answered with anime, not with a superhero series.
     *
     * Both candidates are series, both are Animation-or-Action, and the old scorer had no way to
     * tell them apart. The form signal does: Animation plus Japanese is a different thing from
     * Action & Adventure in English, and a viewer who watches one is not asking for the other.
     */
    @Test
    fun `an anime viewer is offered anime rather than a superhero series`() {
        val row = Recommender.suggest(
            watched = learned(seed = watched("one piece", ANIME, fraction = 1.0)),
            candidates = listOf(
                candidate(1, "the boys", TitleFacts(genres = listOf("Action & Adventure"), language = "en")),
                candidate(2, "naruto", ANIME),
            ),
            now = NOW,
        )

        assertEquals(2L, row.first().channelId)
    }

    @Test
    @DisplayName("a description about pirates beats one that merely shares a genre")
    fun `rare words in the description carry`() {
        val piracy = TitleFacts(
            genres = listOf("Adventure"),
            keywords = setOf("pirate", "crew", "treasure"),
        )
        val row = Recommender.suggest(
            watched = learned(seed = watched("one piece", piracy, fraction = 1.0)),
            candidates = listOf(
                // Same genre, nothing else in common, and its words are ones half the catalogue has.
                candidate(1, "generic quest", TitleFacts(genres = listOf("Adventure"), keywords = setOf("journey"))),
                candidate(2, "black sails", piracy.copy(keywords = setOf("pirate", "crew", "navy"))),
            ) + noise(),
            now = NOW,
        )

        assertEquals(2L, row.first().channelId)
    }

    @Test
    fun `a K-drama viewer is offered Korean before English`() {
        val kdrama = TitleFacts(genres = listOf("Drama"), language = "ko")
        val row = Recommender.suggest(
            watched = learned(seed = watched("squid game", kdrama, fraction = 1.0)),
            candidates = listOf(
                candidate(1, "an american drama", TitleFacts(genres = listOf("Drama"), language = "en")),
                candidate(2, "another korean drama", kdrama),
            ),
            now = NOW,
        )

        assertEquals(2L, row.first().channelId)
    }

    @Test
    fun `a talk show is never the answer to a drama`() {
        val row = Recommender.suggest(
            watched = learned(seed = watched("a drama", TitleFacts(genres = listOf("Drama")), fraction = 1.0)),
            candidates = listOf(
                candidate(1, "a chat show", TitleFacts(genres = listOf("Talk", "Drama"))),
                candidate(2, "another drama", TitleFacts(genres = listOf("Drama"))),
            ),
            now = NOW,
        )

        assertEquals(2L, row.first().channelId)
    }

    @Test
    fun `something watched five times outweighs something watched once`() {
        val comfort = watched("comfort film", TitleFacts(genres = listOf("Comedy")), fraction = 1.0, plays = 5)
        val once = watched("seen once", TitleFacts(genres = listOf("Horror")), fraction = 1.0)

        val row = Recommender.suggest(
            watched = learned(seed = comfort) + once,
            candidates = listOf(
                candidate(1, "a horror", TitleFacts(genres = listOf("Horror"))),
                candidate(2, "a comedy", TitleFacts(genres = listOf("Comedy"))),
            ),
            now = NOW,
        )

        assertEquals(2L, row.first().channelId)
    }

    @Test
    fun `a title that was searched for outweighs one taken off a shelf`() {
        val typed = watched(
            "searched for",
            TitleFacts(genres = listOf("Comedy")),
            fraction = 1.0,
            origin = WatchOrigin.SEARCH,
        )
        val offered = watched("taken off a row", TitleFacts(genres = listOf("Horror")), fraction = 1.0)

        val row = Recommender.suggest(
            watched = learned(seed = typed) + offered,
            candidates = listOf(
                candidate(1, "a horror", TitleFacts(genres = listOf("Horror"))),
                candidate(2, "a comedy", TitleFacts(genres = listOf("Comedy"))),
            ),
            now = NOW,
        )

        assertEquals(2L, row.first().channelId)
    }

    @Test
    fun `a thumbs down stops a title suggesting anything`() {
        val disliked = watched(
            "disliked",
            TitleFacts(genres = listOf("Horror")),
            fraction = 1.0,
            opinion = Opinion.DOWN,
        )

        val row = Recommender.suggest(
            watched = learned(seed = watched("liked", TitleFacts(genres = listOf("Comedy")), fraction = 1.0)) +
                disliked,
            candidates = listOf(
                candidate(1, "a horror", TitleFacts(genres = listOf("Horror"))),
                candidate(2, "a comedy", TitleFacts(genres = listOf("Comedy"))),
            ),
            now = NOW,
        )

        // Not merely ranked lower. A weak seed still chooses tiles when nothing competes for them,
        // and "I did not like this" should never produce a suggestion at all.
        assertTrue(row.none { it.becauseOf == "disliked" })
    }

    @Test
    fun `a thumbs-down title is itself never suggested`() {
        val row = Recommender.suggest(
            watched = learned() + watched("naruto", ANIME, fraction = 0.2, opinion = Opinion.DOWN),
            candidates = listOf(candidate(1, "naruto", ANIME), candidate(2, "bleach", ANIME)),
            now = NOW,
        )

        assertTrue(row.none { it.channelId == 1L })
    }

    @Test
    fun `something watched last year counts, and counts less than last week`() {
        val old = watched("old favourite", TitleFacts(genres = listOf("Horror")), fraction = 1.0, at = NOW - YEAR)
        val recent = watched(
            "watched last week",
            TitleFacts(genres = listOf("Comedy")),
            fraction = 1.0,
            at = NOW - WEEK,
        )

        val row = Recommender.suggest(
            watched = learned(seed = recent) + old,
            candidates = listOf(
                candidate(1, "a horror", TitleFacts(genres = listOf("Horror"))),
                candidate(2, "a comedy", TitleFacts(genres = listOf("Comedy"))),
            ),
            now = NOW,
        )

        assertEquals(2L, row.first().channelId)
        // And not deleted: a year-old taste is still a taste, and the row still has room for it.
        assertTrue(row.any { it.channelId == 1L })
    }

    @Test
    fun `something already watched is never suggested`() {
        val row = Recommender.suggest(
            watched = learned(),
            candidates = listOf(candidate(1, "learned 1", ANIME), candidate(2, "naruto", ANIME)),
            now = NOW,
        )

        assertTrue(row.none { it.channelId == 1L })
    }

    /** Item 5, and the reason the row is built seed by seed rather than from a blended profile. */
    @Test
    fun `each of the strongest few watched titles gets its own four`() {
        val row = Recommender.suggest(
            watched = learned(),
            candidates = (1..40).map { candidate(it.toLong(), "candidate $it", ANIME) },
            now = NOW,
        )

        val perSeed = row.groupingBy { it.becauseOf }.eachCount()
        assertTrue(perSeed.values.all { it <= Recommender.PER_SEED }, "a seed proposed more than its share")
        assertTrue(perSeed.size > 1, "one seed produced the whole row")
    }

    @Test
    fun `every suggestion names one of the viewer's own choices`() {
        val row = Recommender.suggest(learned(), (1..10).map { candidate(it.toLong(), "c$it", ANIME) }, NOW)

        val watchedTitles = learned().map { it.title }.toSet()
        assertTrue(row.all { it.becauseOf in watchedTitles })
    }

    @Test
    fun `the same input gives the same row twice running`() {
        val candidates = (1..10).map { candidate(it.toLong(), "c$it", ANIME) }

        assertEquals(
            Recommender.suggest(learned(), candidates, NOW),
            Recommender.suggest(learned(), candidates, NOW),
        )
    }

    @Test
    fun `a row does not fill up with one genre`() {
        val row = Recommender.suggest(
            watched = learned(seed = watched("a thriller", TitleFacts(genres = listOf("Thriller")), fraction = 1.0)),
            candidates = (1..10).map {
                candidate(it.toLong(), "thriller $it", TitleFacts(genres = listOf("Thriller")))
            } + candidate(99, "a comedy", TitleFacts(genres = listOf("Comedy"))),
            now = NOW,
        )

        assertTrue(row.any { it.channelId == 99L }, "ten thrillers crowded out the only other thing")
    }

    @Test
    fun `both kinds are candidates`() {
        val row = Recommender.suggest(
            watched = learned(),
            candidates = listOf(
                candidate(1, "a film", ANIME, kind = MediaKind.VOD),
                candidate(2, "a series", ANIME, kind = MediaKind.SERIES),
            ),
            now = NOW,
        )

        assertEquals(2, row.size)
    }

    @Test
    fun `a candidate the cache knows nothing about is not offered`() {
        val row = Recommender.suggest(
            watched = learned(),
            candidates = listOf(candidate(1, "undescribed", TitleFacts())),
            now = NOW,
        )

        assertTrue(row.isEmpty())
    }

    @Test
    fun `something watched at this hour is worth a little more than something watched at another`() {
        val evening = watched(
            "evening comedy",
            TitleFacts(genres = listOf("Comedy")),
            fraction = 1.0,
            hourOfDay = EVENING,
        )
        val morning = watched(
            "morning horror",
            TitleFacts(genres = listOf("Horror")),
            fraction = 1.0,
            hourOfDay = MORNING,
        )

        val candidates = listOf(
            candidate(1, "a horror", TitleFacts(genres = listOf("Horror"))),
            candidate(2, "a comedy", TitleFacts(genres = listOf("Comedy"))),
        )

        val atNight = Recommender.suggest(learned(seed = evening) + morning, candidates, NOW, hourOfDay = EVENING)
        val atBreakfast = Recommender.suggest(learned(seed = evening) + morning, candidates, NOW, hourOfDay = MORNING)

        assertEquals(2L, atNight.first().channelId)
        assertNotEquals(atNight.first().channelId, atBreakfast.first().channelId)
    }

    /**
     * Enough history for the scorer to answer at all, plus whatever the test is really about.
     *
     * Every test above needs the cold start satisfied before it can assert anything else, and
     * spelling that out five times per test would bury the one line that matters.
     */
    private fun learned(seed: WatchedTitle? = null): List<WatchedTitle> {
        val base = (1..Recommender.MINIMUM_DISTINCT_TITLES).map {
            watched("learned $it", ANIME, fraction = 1.0, at = NOW - WEEK * 4)
        }
        return if (seed == null) base else listOf(seed) + base
    }

    /** Filler, so a rarity table has something to be rare against. */
    private fun noise() = (50..70).map {
        candidate(it.toLong(), "noise $it", TitleFacts(genres = listOf("Adventure"), keywords = setOf("journey")))
    }

    @Suppress("LongParameterList")
    private fun watched(
        title: String,
        facts: TitleFacts = TitleFacts(),
        genres: List<String> = emptyList(),
        fraction: Double = 1.0,
        at: Long = NOW - WEEK,
        plays: Int = 1,
        hourOfDay: Int = EVENING,
        origin: WatchOrigin = WatchOrigin.ROW,
        opinion: Opinion = Opinion.NONE,
        isFavourite: Boolean = false,
    ) = WatchedTitle(
        title = title,
        kind = MediaKind.SERIES,
        facts = if (genres.isEmpty()) facts else facts.copy(genres = genres),
        fraction = fraction,
        watchedAtEpochMillis = at,
        plays = plays,
        hourOfDay = hourOfDay,
        origin = origin,
        opinion = opinion,
        isFavourite = isFavourite,
    )

    private fun candidate(
        id: Long,
        title: String,
        facts: TitleFacts = TitleFacts(),
        genres: List<String> = emptyList(),
        kind: MediaKind = MediaKind.SERIES,
    ) = CandidateTitle(
        channelId = id,
        kind = kind,
        title = title,
        facts = if (genres.isEmpty()) facts else facts.copy(genres = genres),
    )

    private companion object {
        const val NOW = 1_780_000_000_000L
        const val WEEK = 7L * 24 * 60 * 60 * 1000
        const val YEAR = 365L * 24 * 60 * 60 * 1000

        /** Animation, in Japanese. The one rule that separates anime from a Western cartoon. */
        val ANIME = TitleFacts(genres = listOf("Animation", "Action & Adventure"), language = "ja")

        const val EVENING = 21
        const val MORNING = 8
    }
}
