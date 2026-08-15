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
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Something the viewer has watched, reduced to what a suggestion can be built from.
 *
 * [fraction] is how much of it was actually watched, from 0 to 1. A title opened for ninety
 * seconds and abandoned is evidence *against* its genres as much as for them, and treating it the
 * same as one watched to the end is how a row fills up with things somebody bounced off.
 */
data class WatchedTitle(
    val title: String,
    val genres: List<String>,
    val fraction: Double,
    val watchedAtEpochMillis: Long,
)

/** Something the viewer could watch, and what the metadata cache says it is. */
data class CandidateTitle(
    val channelId: Long,
    val kind: MediaKind,
    val title: String,
    val genres: List<String>,
)

/**
 * One suggestion, and the title that caused it.
 *
 * [becauseOf] is the whole argument for this method over a model. A scoring function knows
 * exactly which of the viewer's own choices produced each answer, and can say so on the card; a
 * trained model cannot, which is what `013` INC-F2 means by saying it costs the ability to
 * explain itself.
 */
data class Suggestion(val channelId: Long, val kind: MediaKind, val becauseOf: String)

/**
 * Suggestions from what has been watched — `013` INC-F2, built as it was designed.
 *
 * **Not machine learning, and not a daemon.** Collaborative filtering — the thing that powers the
 * rows on a commercial service — works by comparing one viewer against millions of others, which
 * needs a server collecting what everybody watched. `FREEZE.md` §2 rules out a backend and §4.5
 * says the app never phones home; that is a locked decision and it removes the entire class. An
 * on-device model is the wrong tool for the opposite reason: it would learn from one household's
 * few hundred watch events, where a scoring function does as well and can explain itself.
 *
 * So this is arithmetic over rows already in the database. It runs when the row is composed, on a
 * background dispatcher, and needs nothing running in the background — which is the answer to the
 * "daemon" half of the intake: a daemon would be a process burning battery to precompute
 * something that takes milliseconds on demand.
 *
 * **Pure, and deliberately so.** No Android types, no repositories, no clock — the time is passed
 * in. Everything interesting about this feature is in these forty lines of arithmetic, and it is
 * the one part that can be got wrong quietly, so it is the part that is directly testable.
 */
object Recommender {

    /**
     * The best [limit] candidates for a viewer with this history.
     *
     * Empty when there is nothing honest to say — no history, no genres, or nothing left once
     * what has been watched is taken out. The row above this draws nothing at all in that case
     * rather than an empty shelf, which is the same rule the popular row follows.
     */
    fun suggest(
        watched: List<WatchedTitle>,
        candidates: List<CandidateTitle>,
        now: Long,
        limit: Int = DEFAULT_LIMIT,
    ): List<Suggestion> {
        val evidence = watched
            .filter { it.genres.isNotEmpty() }
            .map { it to it.weight(now) }
            .filter { (_, weight) -> weight > 0.0 }
        if (evidence.isEmpty()) return emptyList()

        // What the viewer likes, as a weight per genre, and who to credit for each.
        val taste = mutableMapOf<String, Double>()
        val credit = mutableMapOf<String, Pair<String, Double>>()
        evidence.forEach { (title, weight) ->
            title.genres.forEach { genre ->
                val key = genre.lowercase()
                taste[key] = (taste[key] ?: 0.0) + weight
                val held = credit[key]
                if (held == null || weight > held.second) credit[key] = title.title to weight
            }
        }

        val seen = watched.mapTo(HashSet()) { it.title.lowercase() }
        val open = candidates
            .filter { it.genres.isNotEmpty() && it.title.lowercase() !in seen }
            .toMutableList()

        // Chosen one at a time so that each choice can push the next one away from it. Picking
        // the top N in one pass is what produces a row of five thrillers: the genres that scored
        // best score best for everything, and nothing in the arithmetic notices it has already
        // said that.
        val chosen = mutableListOf<Suggestion>()
        val fatigue = mutableMapOf<String, Double>()

        while (chosen.size < limit && open.isNotEmpty()) {
            val best = open
                .map { it to it.score(taste, fatigue) }
                .filter { (_, score) -> score > 0.0 }
                // The id breaks ties, so the row is the same row twice running. A suggestion that
                // reshuffles on every recomposition reads as a bug whatever produced it.
                .maxWithOrNull(compareBy({ it.second }, { -it.first.channelId }))
                ?: break

            val candidate = best.first
            open.remove(candidate)
            chosen += Suggestion(
                channelId = candidate.channelId,
                kind = candidate.kind,
                becauseOf = candidate.cause(taste, credit),
            )
            candidate.genres.forEach { genre ->
                val key = genre.lowercase()
                fatigue[key] = (fatigue[key] ?: 1.0) * SATURATION
            }
        }

        return chosen
    }

    /**
     * How much this viewing counts for.
     *
     * Two things multiplied. **How much of it was watched**, floored rather than zeroed — opening
     * something at all is a weak signal and should stay weak rather than vanish. And **how long
     * ago**, halving every [HALF_LIFE_DAYS] so that last month matters less than last week
     * without any of it ever dropping off a cliff.
     */
    private fun WatchedTitle.weight(now: Long): Double {
        val watched = fraction.coerceIn(MINIMUM_FRACTION, 1.0)
        val ageDays = ((now - watchedAtEpochMillis).coerceAtLeast(0L)).toDouble() / DAY_MILLIS
        return watched * DECAY_BASE.pow(ageDays / HALF_LIFE_DAYS)
    }

    /**
     * How well this candidate matches, with the genres already used pushed down.
     *
     * Divided by the square root of the genre count rather than by the count itself. Dividing by
     * the count is a mean, and a mean punishes a title that matches one loved genre strongly and
     * carries three the viewer has no opinion about — which is most titles. Not dividing at all
     * lets anything filed under eight genres win by breadth. The square root sits between the two
     * and is the ordinary answer to this shape of problem.
     */
    private fun CandidateTitle.score(taste: Map<String, Double>, fatigue: Map<String, Double>): Double {
        val total = genres.sumOf { genre ->
            val key = genre.lowercase()
            (taste[key] ?: 0.0) * (fatigue[key] ?: 1.0)
        }
        return total / sqrt(genres.size.toDouble())
    }

    /** The watched title that contributed most to this candidate's score. */
    private fun CandidateTitle.cause(
        taste: Map<String, Double>,
        credit: Map<String, Pair<String, Double>>,
    ): String = genres
        .mapNotNull { genre ->
            val key = genre.lowercase()
            val cause = credit[key] ?: return@mapNotNull null
            cause.first to (taste[key] ?: 0.0)
        }
        .maxByOrNull { it.second }
        ?.first
        .orEmpty()

    /** How many suggestions a row asks for. A D-pad row nobody reaches the end of. */
    const val DEFAULT_LIMIT = 20

    /**
     * What a genre is worth once it has already appeared in the row.
     *
     * Halved each time, so a third thriller has to beat a first comedy by a factor of four. Firm
     * enough to break a run and gentle enough that a viewer who genuinely watches one genre still
     * gets it back after a couple of tiles.
     */
    private const val SATURATION = 0.5

    /** A title barely started still counts for something, and never for nothing. */
    private const val MINIMUM_FRACTION = 0.1

    /** A month. Long enough to survive a fortnight away, short enough to follow a taste. */
    private const val HALF_LIFE_DAYS = 30.0

    private const val DECAY_BASE = 0.5
    private const val DAY_MILLIS = 24.0 * 60 * 60 * 1000
}
