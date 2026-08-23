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

import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.ChannelTitle
import dev.quiblo.core.database.dao.FavoriteDao
import dev.quiblo.core.database.dao.TitleFactRow
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Opinion
import dev.quiblo.core.model.WatchOrigin
import dev.quiblo.source.tmdb.titleIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

/**
 * The "You May Like" row: what to suggest, and why.
 *
 * Everything difficult about this feature is in [Recommender], which is pure arithmetic and tested
 * as such. This class is the plumbing around it — read the history, read the log, read the cache,
 * match the three against the catalogue by cleaned title, hand the result over — and it is written
 * so that the plumbing has no opinions of its own to get wrong.
 *
 * **Per profile.** Watch history already is, and a suggestion row that was not would leak one
 * person's viewing to another, which is the failure profiles exist to prevent (`AC-PROF-02`).
 *
 * **Silent when it knows nothing, and silent for a while after that.** No history, no metadata
 * key, an unscanned catalogue, or a viewer who has simply not watched enough yet: all of them
 * produce an empty list, and the row above draws nothing rather than an empty shelf. The last of
 * those is `025`'s cold start and it is deliberate — see [Recommender].
 */
// Nine collaborators, and each is a different question the scorer needs answered: what was
// watched, by whom, how often, what they said about it, what the catalogue describes, what it
// holds, what they favourited, which thread to work on, and the clock. A holder around them would
// rename the list rather than shorten it — and would hide which of them a change touches.
@Suppress("LongParameterList")
class RecommendationRepository(
    private val history: WatchHistoryRepository,
    private val profiles: ProfileRepository,
    private val watchEvents: WatchEventRepository,
    private val opinions: TitleOpinionRepository,
    private val titleMetadataDao: TitleMetadataDao,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    /** Sixty thousand titles cleaned and matched. Not the caller's thread. */
    private val matchDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun suggestions(sourceId: Long, limit: Int = Recommender.DEFAULT_LIMIT): List<Suggestion> {
        val profileId = profiles.activeProfileId
        val watched = watchedTitles(sourceId)
        val favourites = favouriteTitles(profileId, sourceId)
        val factRows = titleMetadataDao.allFactRows()

        // Two ways there is nothing honest to say: this profile has said nothing about anything,
        // or nothing in the catalogue has been described yet. Both draw no row at all. The third —
        // "not enough yet" — is the scorer's own, because it is a judgement rather than an absence.
        val saidNothing = watched.isEmpty() && favourites.isEmpty()
        if (saidNothing || factRows.isEmpty()) return emptyList()

        // Hidden categories are left out here, unlike the popular row. A suggestion is the app
        // proposing something unprompted, and proposing out of a shelf the viewer has put away
        // is the app arguing with them. A hidden writing system is the same argument, and it is
        // answered where these ids become rows — `ChannelRepository.channelsByIds`, BUG-031.
        // This viewer's hiding, because these are this viewer's suggestions: a shelf somebody
        // has hidden is a shelf they have said they do not want suggested.
        val titles = channelDao.titlesForMetadata(
            sourceId = sourceId,
            includeHidden = false,
            profileId = profiles.activeProfileId,
        )

        val signals = PlaySignals(
            plays = watchEvents.playsByStableKey(sourceId),
            hours = watchEvents.usualHourByStableKey(sourceId),
            origins = watchEvents.strongestOriginByStableKey(sourceId),
        )
        val opinionsByTitle = opinions.all()

        return withContext(matchDispatcher) {
            val factsByTitle = factRows.associateBy(
                { it.searchTitle to it.kind },
                { it.toFacts() },
            )

            Recommender.suggest(
                watched = watchedSeeds(watched, favourites, factsByTitle, signals, opinionsByTitle) +
                    favouriteSeeds(watched, favourites, factsByTitle, opinionsByTitle),
                candidates = candidateTitles(titles, factsByTitle),
                now = now(),
                hourOfDay = hourOf(now()),
                limit = limit,
            )
        }
    }

    /**
     * The catalogue, as things that can be suggested.
     *
     * A row survives only if the app knows what it is: a kind it can play, a title that cleans to
     * something, a metadata match, and at least one genre behind it. A title with no genres cannot
     * be scored against anything, and offering it would be offering a guess.
     */
    private fun candidateTitles(
        titles: List<ChannelTitle>,
        factsByTitle: Map<Pair<String, String>, TitleFacts>,
    ): List<CandidateTitle> = titles.mapNotNull { row ->
        val kind = row.kind.toMediaKindOrNull() ?: return@mapNotNull null
        val identity = row.name.titleIdentity().takeIf { it.searchTitle.isNotBlank() }
            ?: return@mapNotNull null
        val facts = factsByTitle[identity.searchTitle to row.kind] ?: return@mapNotNull null
        if (facts.genres.isEmpty()) return@mapNotNull null
        CandidateTitle(
            channelId = row.id,
            kind = kind,
            title = identity.searchTitle,
            facts = facts,
        )
    }

    /**
     * What the viewer has watched, as evidence: how much of it, how often, when, and where from.
     */
    private fun watchedSeeds(
        watched: List<HistoryEntry>,
        favourites: List<FavouriteTitle>,
        factsByTitle: Map<Pair<String, String>, TitleFacts>,
        signals: PlaySignals,
        opinionsByTitle: Map<String, Opinion>,
    ): List<WatchedTitle> {
        val favouriteKeys = favourites.mapTo(HashSet()) { it.stableKey }
        return watched.mapNotNull { entry ->
            val identity = entry.title.titleIdentity().takeIf { it.searchTitle.isNotBlank() }
                ?: return@mapNotNull null
            WatchedTitle(
                title = identity.searchTitle,
                kind = entry.kind,
                facts = factsByTitle[identity.searchTitle to entry.kind.name] ?: TitleFacts(),
                fraction = entry.watchedFraction(),
                watchedAtEpochMillis = entry.watchedAtEpochMillis,
                plays = signals.plays[entry.stableKey] ?: 1,
                hourOfDay = signals.hours[entry.stableKey] ?: hourOf(entry.watchedAtEpochMillis),
                origin = signals.origins[entry.stableKey] ?: WatchOrigin.ROW,
                isFavourite = entry.stableKey in favouriteKeys,
                opinion = opinionsByTitle[identity.searchTitle] ?: Opinion.NONE,
            )
        }
    }

    /**
     * The favourites nobody has played, as evidence in their own right (`027` #8).
     *
     * **A starred title the viewer has not got round to watching is still a statement about their
     * taste**, and until now it was worth precisely nothing: the scorer read the watch history and
     * the favourites table was consulted only to *weight* a title that was in both. So a viewer
     * who had starred ten films and finished none of them got no row, which is the report.
     *
     * They are seeded as watched-in-full, and the reasoning is in `hasLearnedEnough`: starring is
     * not a weaker claim than reaching the sixty-percent mark, it is a different and plainer one.
     * The occasion is the moment it was starred, so a favourite added last week outweighs one
     * added last year exactly as a viewing would, and the origin says where it came from — a
     * favourite is chosen, never stumbled into.
     *
     * Anything already in the history is left to [watchedSeeds], which knows more about it: how
     * much was watched, how often, and at what hour.
     */
    private fun favouriteSeeds(
        watched: List<HistoryEntry>,
        favourites: List<FavouriteTitle>,
        factsByTitle: Map<Pair<String, String>, TitleFacts>,
        opinionsByTitle: Map<String, Opinion>,
    ): List<WatchedTitle> {
        val watchedKeys = watched.mapTo(HashSet()) { it.stableKey }
        return favourites.mapNotNull { favourite ->
            if (favourite.stableKey in watchedKeys) return@mapNotNull null
            val identity = favourite.title.titleIdentity().takeIf { it.searchTitle.isNotBlank() }
                ?: return@mapNotNull null
            WatchedTitle(
                title = identity.searchTitle,
                kind = favourite.kind,
                facts = factsByTitle[identity.searchTitle to favourite.kind.name] ?: TitleFacts(),
                fraction = 1.0,
                watchedAtEpochMillis = favourite.favouritedAtEpochMillis,
                hourOfDay = hourOf(favourite.favouritedAtEpochMillis),
                origin = WatchOrigin.FAVOURITE,
                isFavourite = true,
                opinion = opinionsByTitle[identity.searchTitle] ?: Opinion.NONE,
            )
        }
    }

    /** What the event log knows about each watched title, keyed by stable key. */
    private data class PlaySignals(
        val plays: Map<String, Int>,
        val hours: Map<String, Int>,
        val origins: Map<String, WatchOrigin>,
    )

    /**
     * What this profile has watched, films and series together.
     *
     * Two reads because the history is stored per kind, and both are wanted: somebody who watches
     * nothing but series should still be offered a film that shares their genres. Live channels
     * are never in the history at all — the player does not record them.
     */
    private suspend fun watchedTitles(sourceId: Long): List<HistoryEntry> =
        HISTORY_KINDS.flatMap { kind -> history.observeHistory(sourceId, kind).first() }

    /**
     * What this profile has starred on this source, with enough about each to score it.
     *
     * Two reads because the favourites table stores identity and nothing else — it is keyed by the
     * provider's stable key so that starring survives a playlist refresh, which is the same reason
     * the history is. The catalogue is what knows the name and the kind.
     *
     * Live channels are dropped. A starred channel says something true about a viewer and nothing
     * a film database can act on: there is no genre, no year and no runtime behind it, and a
     * suggestion drawn from one would be drawn from an empty record.
     */
    private suspend fun favouriteTitles(profileId: Long, sourceId: Long): List<FavouriteTitle> {
        val rows = favoriteDao.allFor(profileId, sourceId)
        if (rows.isEmpty()) return emptyList()

        val starredAt = rows.associate { it.stableKey to it.favoritedAtEpochMillis }
        return channelDao.findAllByStableKeys(profileId, sourceId, rows.map { it.stableKey })
            .mapNotNull { row ->
                val kind = row.channel.kind.toMediaKindOrNull()?.takeIf { it in HISTORY_KINDS }
                    ?: return@mapNotNull null
                FavouriteTitle(
                    stableKey = row.channel.stableKey,
                    title = row.channel.name,
                    kind = kind,
                    favouritedAtEpochMillis = starredAt[row.channel.stableKey] ?: 0L,
                )
            }
    }

    /**
     * When each watched title was last played, by title.
     *
     * The key is the same title a [Suggestion] names as its cause, which is what lets the cached
     * suggestions row tell that a cause has been watched again since the suggestion was made.
     * Watching something a second time is the strongest signal this app collects, and a row that
     * kept a fortnight-old answer in front of what that signal produced would be ignoring it.
     */
    suspend fun lastWatchedByTitle(sourceId: Long): Map<String, Long> =
        watchedTitles(sourceId)
            .groupBy { it.title }
            .mapValues { (_, entries) -> entries.maxOf { it.watchedAtEpochMillis } }

    /**
     * How much of a title was watched, from 0 to 1.
     *
     * A duration of zero means the player never learned one — a stream that reports no length, or
     * a resume point written before the first buffer. Treated as a full watch rather than as
     * nothing: the viewer opened it and it is the only fact available, and reading it as 0 would
     * throw away the whole of a household's live-adjacent viewing.
     */
    private fun HistoryEntry.watchedFraction(): Double =
        if (durationMillis <= 0L) 1.0 else (positionMillis.toDouble() / durationMillis).coerceIn(0.0, 1.0)

    /** The local hour a moment falls in, which is the only form the time-of-day signal uses. */
    private fun hourOf(epochMillis: Long): Int =
        Calendar.getInstance(TimeZone.getDefault())
            .apply { timeInMillis = epochMillis }
            .get(Calendar.HOUR_OF_DAY)

    /** One starred title, as much of it as the scorer needs. */
    private data class FavouriteTitle(
        val stableKey: String,
        val title: String,
        val kind: MediaKind,
        val favouritedAtEpochMillis: Long,
    )

    private companion object {
        val HISTORY_KINDS = listOf(MediaKind.VOD, MediaKind.SERIES)
    }
}

/** The cache's row as the scorer wants it. */
private fun TitleFactRow.toFacts() = TitleFacts(
    genres = genres.orEmpty().splitToGenres(),
    keywords = keywordsOf(overview),
    language = originalLanguage?.takeIf { it.isNotBlank() },
    releaseYear = releaseYear ?: year.takeIf { it > 0 },
    runtimeMinutes = runtimeMinutes,
    rating = rating,
    popularity = popularity,
)

/**
 * The cache's newline-separated genre string, as a list.
 *
 * Its own function rather than [String.split] at each call site, because "what separates two
 * genres" is a fact about the cache's format and belongs in one place.
 */
private fun String.splitToGenres(): List<String> =
    split('\n').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * The words in a description worth comparing two titles by.
 *
 * **From the overview this app already stores, and not from a keyword endpoint.** TMDB has one,
 * and using it would be one request per title across a sixty-thousand-title catalogue — this
 * project's provider account has been blocked twice over requests it did not need, and there is no
 * reason to learn that lesson again against a second host.
 *
 * Lowercased, stripped of punctuation, short words and stop words dropped. No stemming: an English
 * stemmer applied to a catalogue that is half Arabic and half French produces confident nonsense,
 * and the rarity weighting in [Recommender] already discounts the words that vary by inflection
 * most. What survives is nouns — *pirate*, *assassin*, *ninja*, *heist* — which is exactly what
 * makes one adventure story different from another.
 */
internal fun keywordsOf(overview: String?): Set<String> {
    if (overview.isNullOrBlank()) return emptySet()
    return overview.lowercase()
        // Split on a regex of separators rather than a spread of chars: a spread copies its array
        // on every call, and this runs once per catalogue title.
        .split(WORD_SEPARATORS)
        .asSequence()
        .map { it.trim('\'', '’') }
        .filter { it.length >= MINIMUM_KEYWORD_LENGTH }
        .filterNot { it in STOP_WORDS }
        .filterNot { it.all(Char::isDigit) }
        .take(MAX_KEYWORDS)
        .toSet()
}

private val WORD_SEPARATORS = Regex("[\\s.,;:!?\"()\\[\\]{}\\-\u2014/\\\\\u2026]+")

private const val MINIMUM_KEYWORD_LENGTH = 4

/**
 * A ceiling on how many words one description contributes.
 *
 * A synopsis is a paragraph; a plot summary somebody pasted in is a page. Without a cap the second
 * would overlap with everything by sheer volume, which is the same failure as not weighting by
 * rarity at all.
 */
private const val MAX_KEYWORDS = 40

/**
 * Words a description shares with half the catalogue.
 *
 * English only, and short on purpose. The rarity weighting does most of this work already — a word
 * in every second overview scores near zero whether or not it is on this list — so the list exists
 * to keep the *sets* small rather than to correct the scores. Adding a language's worth of stop
 * words here would be a maintenance burden for a rounding error.
 */
private val STOP_WORDS = setOf(
    "about", "after", "again", "against", "their", "there", "these", "those", "which", "while",
    "with", "from", "into", "over", "under", "between", "before", "being", "been", "have", "has",
    "had", "that", "this", "they", "them", "then", "than", "when", "what", "will", "would",
    "could", "should", "must", "must", "only", "also", "just", "more", "most", "some", "such",
    "very", "much", "many", "each", "every", "other", "another", "himself", "herself", "itself",
    "themselves", "story", "series", "film", "movie", "season", "episode", "episodes", "based",
    "life", "lives", "years", "year", "time", "world", "people", "young", "family", "friends",
    "back", "away", "down", "through", "where", "who", "whose", "him", "her", "his", "she",
    "must", "find", "finds", "help", "helps", "make", "makes", "take", "takes", "come", "comes",
)
