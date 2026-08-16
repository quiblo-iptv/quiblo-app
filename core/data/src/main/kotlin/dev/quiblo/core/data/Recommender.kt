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
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * What a title *is*, past what kind of file it is.
 *
 * `MediaKind` says film or series, which a provider already knows. This says the thing a viewer
 * means when they say what they watch — and the gap between the two is the whole of the complaint
 * this round answers: somebody who had watched one anime series was offered three American
 * superhero dramas, because at the level of "series, Action & Adventure" that is what they had
 * asked for.
 */
enum class TitleForm {
    FILM,
    SERIES,
    ANIME,
    TALK_SHOW,
}

/**
 * Everything known about one title that a suggestion can be built from.
 *
 * All of it comes from the metadata cache, and any of it can be missing — a provider that has
 * never been scanned has none of it. Every signal below is written to be worth zero when its
 * input is absent rather than to guess, because a scorer that fills gaps with averages produces
 * confident nonsense.
 */
data class TitleFacts(
    val genres: List<String> = emptyList(),
    /** Words from the description, already cleaned and stopped. See `keywordsOf`. */
    val keywords: Set<String> = emptySet(),
    /** TMDB's two-letter code for the language it was made in. */
    val language: String? = null,
    val releaseYear: Int? = null,
    val runtimeMinutes: Int? = null,
    val rating: Double? = null,
    val popularity: Double? = null,
) {

    /**
     * Film, series, anime or talk show.
     *
     * **Anime is Animation made in Japanese**, which is the one rule that separates it from a
     * Western cartoon using only fields the cache already holds. It is deliberately narrow: a
     * viewer who watches anime is saying something specific, and widening this to "any animation"
     * would answer One Piece with a Pixar film, which is the same mistake in a nicer costume.
     *
     * A talk show is Talk or News, which TMDB files consistently and which nobody browsing for a
     * drama wants offered.
     */
    fun form(kind: MediaKind): TitleForm {
        val lowered = genres.map { it.lowercase() }
        return when {
            lowered.any { it in TALK_GENRES } -> TitleForm.TALK_SHOW
            lowered.any { it == "animation" } && language == JAPANESE -> TitleForm.ANIME
            kind == MediaKind.SERIES -> TitleForm.SERIES
            else -> TitleForm.FILM
        }
    }

    private companion object {
        val TALK_GENRES = setOf("talk", "news", "reality")
        const val JAPANESE = "ja"
    }
}

/**
 * Something the viewer has watched, and everything about the occasion that says how much it counts.
 *
 * [fraction] is how much of it was watched. [plays] is how many times — a film seen five times is
 * a comfort film and the strongest statement anybody makes without typing. [origin] is where they
 * were when they chose it, [isFavourite] whether they said so twice, and [opinion] whether they
 * said so in words.
 */
data class WatchedTitle(
    val title: String,
    val kind: MediaKind,
    val facts: TitleFacts,
    val fraction: Double,
    val watchedAtEpochMillis: Long,
    val plays: Int = 1,
    /** The hour it is usually started, 0–23. */
    val hourOfDay: Int = 0,
    val origin: WatchOrigin = WatchOrigin.ROW,
    val isFavourite: Boolean = false,
    val opinion: Opinion = Opinion.NONE,
)

/** Something the viewer could watch, and what the metadata cache says it is. */
data class CandidateTitle(
    val channelId: Long,
    val kind: MediaKind,
    val title: String,
    val facts: TitleFacts,
)

/**
 * One suggestion, and the title that caused it.
 *
 * [becauseOf] is the whole argument for this method over a model. A scoring function knows exactly
 * which of the viewer's own choices produced each answer and can say so on the tile; a trained
 * model cannot.
 */
data class Suggestion(val channelId: Long, val kind: MediaKind, val becauseOf: String)

/**
 * Suggestions from what has been watched — `013` INC-F2, rebuilt in `025`.
 *
 * **Not machine learning, and not a daemon.** Collaborative filtering — the thing behind the rows
 * on a commercial service — compares one viewer against millions of others, which needs a server
 * collecting what everybody watched. `FREEZE.md` §2 rules out a backend and §4.5 says the app
 * never phones home. That removes the entire class, and it removes BellKor with it: a matrix
 * factorisation over one household's ratings is factorising a single row.
 *
 * **What replaced it is not "genre, and hope".** The version this rewrites scored on one axis, and
 * it showed: a viewer who had watched nothing but One Piece was offered The Boys, The Umbrella
 * Academy and a dubbed Arabic family drama, because at the level of "series, Action & Adventure"
 * those are the same thing. Thirteen signals, each of them a fact the cache already holds:
 *
 * | Signal | Where it comes from |
 * | :---- | :---- |
 * | Form — film, series, **anime**, talk show | genres, plus the original language |
 * | Genre overlap | the metadata cache |
 * | Description keywords | the overview, tokenised and weighted by rarity |
 * | Language | the original language |
 * | Release year | how close in time |
 * | Runtime | how close in length |
 * | Popularity | rare or common, matched to what they watch |
 * | Rating | a gentle nudge towards the better of two equal matches |
 * | How much was watched | the resume position against the duration |
 * | How recently | halved every month |
 * | How many times | the watch log |
 * | Where it was chosen from | the watch log — search outranks a shelf |
 * | Favourite, and thumbs | the favourites table and the opinions table |
 *
 * The first eight are how alike two titles are. The last five are how much one watched title
 * counts as evidence — they weight a seed, they do not describe a candidate.
 *
 * **Seed-oriented, four apiece.** Each of the strongest few watched titles produces its own best
 * four, rather than every candidate being scored against a single blended profile of the viewer.
 * A blend is what makes an anime fan who also watched one cookery programme get suggestions that
 * are neither: the average of two tastes is a taste nobody has.
 *
 * **It refuses to answer until it has something to say.** Below [MINIMUM_DISTINCT_TITLES] watched
 * titles, or [MINIMUM_FINISHED] watched most of the way through, it returns nothing and the row is
 * not drawn. That is the direct answer to "it should wait a bit and learn": one watched title
 * produces four confident, unrelated suggestions, and no amount of weighting fixes that — only
 * declining to answer does.
 *
 * **Pure, and deliberately so.** No Android types, no repositories, no clock — the time is passed
 * in. Everything that can be quietly wrong is in here, so all of it is directly testable.
 */
object Recommender {

    /**
     * The best suggestions for a viewer with this history, or nothing at all.
     *
     * @param now the wall clock, for decay.
     * @param hourOfDay the hour it is now, 0–23, for the time-of-day signal.
     * @param limit how long the row may be.
     */
    fun suggest(
        watched: List<WatchedTitle>,
        candidates: List<CandidateTitle>,
        now: Long,
        hourOfDay: Int = 0,
        limit: Int = DEFAULT_LIMIT,
    ): List<Suggestion> {
        if (!hasLearnedEnough(watched)) return emptyList()

        // A thumbs-down is not a weak seed, it is not a seed. Scoring it at a low weight would
        // still let it choose tiles whenever nothing else was competing for them.
        val seeds = watched
            .filter { it.opinion != Opinion.DOWN && it.facts.genres.isNotEmpty() }
            .map { it to it.weight(now, hourOfDay) }
            .filter { (_, weight) -> weight > 0.0 }
            .sortedByDescending { it.second }
            .take(SEED_COUNT)
        if (seeds.isEmpty()) return emptyList()

        val rejected = watched.filter { it.opinion == Opinion.DOWN }.mapTo(HashSet()) { it.title.lowercase() }
        val seen = watched.mapTo(HashSet()) { it.title.lowercase() }
        val open = candidates
            .filter { it.title.lowercase() !in seen && it.title.lowercase() !in rejected }
            .toMutableList()

        val rarity = rarityOf(open)
        val chosen = mutableListOf<Suggestion>()
        val fatigue = mutableMapOf<String, Double>()

        // Round by round rather than seed by seed, so a row that runs out of room still has
        // something from each of the viewer's tastes rather than four of the first one.
        repeat(PER_SEED) {
            seeds.forEach { (seed, weight) ->
                if (chosen.size >= limit) return@forEach
                val best = open
                    .map { it to weight * seed.affinityTo(it, rarity) * it.fatigued(fatigue) }
                    .filter { (_, score) -> score > 0.0 }
                    // The id breaks ties, so the same input gives the same row twice running. A
                    // suggestions row that reshuffles on its own reads as a bug whatever caused it.
                    .maxWithOrNull(compareBy({ it.second }, { -it.first.channelId }))
                    ?: return@forEach

                val candidate = best.first
                open.remove(candidate)
                chosen += Suggestion(candidate.channelId, candidate.kind, seed.title)
                candidate.facts.genres.forEach { genre ->
                    val key = genre.lowercase()
                    fatigue[key] = (fatigue[key] ?: 1.0) * SATURATION
                }
            }
        }

        return chosen
    }

    /**
     * Whether there is enough here to be worth answering.
     *
     * Two conditions, and both are needed. A viewer who has opened twenty things and finished none
     * has told us what they browse rather than what they watch; a viewer who has finished one
     * thing three times has told us about one thing.
     */
    private fun hasLearnedEnough(watched: List<WatchedTitle>): Boolean {
        val distinct = watched.distinctBy { it.title.lowercase() }
        return distinct.size >= MINIMUM_DISTINCT_TITLES &&
            distinct.count { it.fraction >= FINISHED_FRACTION } >= MINIMUM_FINISHED
    }

    /**
     * How much this viewing counts as evidence.
     *
     * Five things multiplied, and each of them is a fact about the occasion rather than about the
     * title. **How much was watched**, floored rather than zeroed — opening something at all is a
     * weak signal and should stay weak rather than vanish. **How long ago**, halving every
     * [HALF_LIFE_DAYS] so last month matters less than last week without anything dropping off a
     * cliff. **How many times**, with diminishing returns: a second viewing says a great deal and
     * a ninth says little the second did not. **Where it was chosen from**, because typing a title
     * into a search box is a stronger statement than pressing the first tile of a row. And
     * **whether they said so** — a favourite, or a thumbs up.
     *
     * The hour is a small nudge rather than a filter: somebody who watches comedies at eleven at
     * night is telling us something real, and somebody who happens to have watched one thing late
     * once is not.
     */
    private fun WatchedTitle.weight(now: Long, hourOfDay: Int): Double {
        val watchedThrough = fraction.coerceIn(MINIMUM_FRACTION, 1.0)
        val ageDays = ((now - watchedAtEpochMillis).coerceAtLeast(0L)).toDouble() / DAY_MILLIS
        val recency = DECAY_BASE.pow(ageDays / HALF_LIFE_DAYS)
        val repeat = 1.0 + REPEAT_WEIGHT * ln(plays.coerceAtLeast(1).toDouble())
        val chosen = when (origin) {
            WatchOrigin.SEARCH -> SEARCH_WEIGHT
            WatchOrigin.FAVOURITE -> FAVOURITE_ORIGIN_WEIGHT
            WatchOrigin.ROW, WatchOrigin.CONTINUE -> 1.0
        }
        val said = when {
            opinion == Opinion.UP -> THUMBS_UP_WEIGHT
            isFavourite -> FAVOURITE_WEIGHT
            else -> 1.0
        }
        val timeOfDay = if (hoursApart(this.hourOfDay, hourOfDay) <= HOUR_TOLERANCE) HOUR_WEIGHT else 1.0

        return watchedThrough * recency * repeat * chosen * said * timeOfDay
    }

    /**
     * How alike two titles are, from 0 to about 1.
     *
     * A weighted sum rather than a product, so that one missing fact costs its own share and not
     * the answer. The weights are in [Weights] with the sentence that justifies each.
     */
    private fun WatchedTitle.affinityTo(candidate: CandidateTitle, rarity: Map<String, Double>): Double {
        val mine = facts
        val theirs = candidate.facts

        val genre = overlap(mine.genres.map { it.lowercase() }, theirs.genres.map { it.lowercase() })
        val keyword = weightedOverlap(mine.keywords, theirs.keywords, rarity)
        val form = if (mine.form(kind) == theirs.form(candidate.kind)) 1.0 else 0.0
        val language = when {
            mine.language == null || theirs.language == null -> 0.0
            mine.language == theirs.language -> 1.0
            else -> 0.0
        }
        val year = closeness(mine.releaseYear?.toDouble(), theirs.releaseYear?.toDouble(), YEAR_SPAN)
        val runtime = closeness(mine.runtimeMinutes?.toDouble(), theirs.runtimeMinutes?.toDouble(), RUNTIME_SPAN)
        val popularity = closeness(
            mine.popularity?.let { ln(it + 1.0) },
            theirs.popularity?.let { ln(it + 1.0) },
            POPULARITY_SPAN,
        )
        // Not a similarity: a straight preference for the better of two equal matches, and small
        // enough that it can never outrank being about the same thing.
        val quality = theirs.rating?.let { (it / MAX_RATING).coerceIn(0.0, 1.0) } ?: 0.0

        return Weights.GENRE * genre +
            Weights.KEYWORD * keyword +
            Weights.FORM * form +
            Weights.LANGUAGE * language +
            Weights.YEAR * year +
            Weights.RUNTIME * runtime +
            Weights.POPULARITY * popularity +
            Weights.QUALITY * quality
    }

    /**
     * How much a genre is still worth once the row has used it.
     *
     * Applied to the candidate rather than to the taste, because the row is what is being kept
     * varied. Without it the strongest seed's four tiles are four of one genre — the arithmetic
     * has no way to notice it has already said that.
     */
    private fun CandidateTitle.fatigued(fatigue: Map<String, Double>): Double {
        if (facts.genres.isEmpty()) return 1.0
        return facts.genres.map { fatigue[it.lowercase()] ?: 1.0 }.average()
    }

    /**
     * Shared members over the square root of the candidate's count.
     *
     * The square root rather than the count: dividing by the count is a mean, and a mean punishes
     * a title that matches one loved genre strongly and carries three the viewer has no opinion
     * about — which is most titles. Not dividing at all lets anything filed under eight genres win
     * by breadth.
     */
    private fun overlap(mine: List<String>, theirs: List<String>): Double {
        if (mine.isEmpty() || theirs.isEmpty()) return 0.0
        val shared = theirs.count { it in mine }
        return shared / sqrt(theirs.size.toDouble())
    }

    /**
     * The same, with each shared word worth how rare it is.
     *
     * "the", "story" and "life" are shared by half a catalogue and say nothing; "pirate",
     * "assassin" and "ninja" are shared by a handful and say almost everything. Weighting by
     * rarity is what makes a description a signal rather than noise, and it is the difference
     * between One Piece answering with another pirate story and answering with any adventure.
     */
    private fun weightedOverlap(mine: Set<String>, theirs: Set<String>, rarity: Map<String, Double>): Double {
        if (mine.isEmpty() || theirs.isEmpty()) return 0.0
        val shared = mine.intersect(theirs)
        if (shared.isEmpty()) return 0.0
        val score = shared.sumOf { rarity[it] ?: 0.0 }
        return min(score / KEYWORD_SATURATION, 1.0)
    }

    /**
     * How rare each keyword is across the candidates, as inverse document frequency.
     *
     * Computed over the catalogue in front of us rather than from a fixed list, so it describes
     * *this* provider: a service that carries nothing but Arabic drama has a different set of
     * common words to one that carries nothing but Hollywood films, and a rarity table shipped
     * with the app would be wrong for both.
     */
    private fun rarityOf(candidates: List<CandidateTitle>): Map<String, Double> {
        if (candidates.isEmpty()) return emptyMap()
        val counts = mutableMapOf<String, Int>()
        candidates.forEach { candidate ->
            candidate.facts.keywords.forEach { word -> counts[word] = (counts[word] ?: 0) + 1 }
        }
        val total = candidates.size.toDouble()
        return counts.mapValues { (_, count) -> ln(total / count) }
    }

    /** 1 when two numbers are equal, 0 when they are [span] apart or either is missing. */
    private fun closeness(mine: Double?, theirs: Double?, span: Double): Double {
        if (mine == null || theirs == null) return 0.0
        return (1.0 - abs(mine - theirs) / span).coerceIn(0.0, 1.0)
    }

    /** Distance round a 24-hour clock: 23:00 and 01:00 are two hours apart, not twenty-two. */
    private fun hoursApart(a: Int, b: Int): Int {
        val direct = abs(a - b)
        return min(direct, HOURS_IN_DAY - direct)
    }

    /**
     * What each part of "alike" is worth.
     *
     * They sum to one, and the order is the argument. **Genre and keywords carry more than half of
     * it between them**, because they are what "like this one" means. **Form is third and is
     * heavier than it looks**: it is what stops an anime being answered with a superhero drama,
     * which is the specific failure this round exists to fix. Language is fourth for the same
     * reason — a K-drama viewer means K-drama. Year, runtime and popularity are texture: real, and
     * never enough to outrank being about the same thing. Quality is last and is deliberately the
     * smallest: it decides between two equal matches and nothing else, because a row of
     * highly-rated titles nobody asked for is what every naive recommender produces.
     */
    private object Weights {
        const val GENRE = 0.32
        const val KEYWORD = 0.22
        const val FORM = 0.16
        const val LANGUAGE = 0.11
        const val YEAR = 0.07
        const val RUNTIME = 0.05
        const val POPULARITY = 0.04
        const val QUALITY = 0.03
    }

    /** How many suggestions a row asks for. A D-pad row nobody reaches the end of. */
    const val DEFAULT_LIMIT = 20

    /** How many watched titles get to propose, and how many each proposes — `025`'s item 5. */
    const val SEED_COUNT = 5
    const val PER_SEED = 4

    /** Below these, the row is not drawn at all. See [hasLearnedEnough]. */
    const val MINIMUM_DISTINCT_TITLES = 5
    const val MINIMUM_FINISHED = 3
    private const val FINISHED_FRACTION = 0.6

    /**
     * What a genre is worth once it has already appeared in the row.
     *
     * Halved each time, so a third thriller has to beat a first comedy by a factor of four. Firm
     * enough to break a run and gentle enough that somebody who genuinely watches one genre still
     * gets it back after a couple of tiles.
     */
    private const val SATURATION = 0.5

    /** A title barely started still counts for something, and never for nothing. */
    private const val MINIMUM_FRACTION = 0.1

    private const val DECAY_BASE = 0.5
    private const val HALF_LIFE_DAYS = 30.0
    private const val DAY_MILLIS = 24.0 * 60 * 60 * 1000

    /** Diminishing: a second viewing says a great deal, a ninth little the second did not. */
    private const val REPEAT_WEIGHT = 0.6

    private const val SEARCH_WEIGHT = 1.4
    private const val FAVOURITE_ORIGIN_WEIGHT = 1.2
    private const val FAVOURITE_WEIGHT = 1.3
    private const val THUMBS_UP_WEIGHT = 1.6

    /** Within this many hours counts as "the same time of day". */
    private const val HOUR_TOLERANCE = 3
    private const val HOUR_WEIGHT = 1.15
    private const val HOURS_IN_DAY = 24

    /** Twenty years apart is unrelated; two years apart is nearly the same shelf. */
    private const val YEAR_SPAN = 20.0

    /** An hour's difference in length is a different sort of evening. */
    private const val RUNTIME_SPAN = 60.0

    /** On a log scale, so the gap between famous and very famous is not the gap that matters. */
    private const val POPULARITY_SPAN = 4.0

    /** Enough shared rare words to call it a match. Above this, more of them changes nothing. */
    private const val KEYWORD_SATURATION = 12.0

    private const val MAX_RATING = 10.0
}
