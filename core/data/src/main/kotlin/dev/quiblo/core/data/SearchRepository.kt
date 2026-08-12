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
import dev.quiblo.core.database.dao.TitleGenreRow
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.tmdb.TitleIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What one search returned, kept apart by kind because that is how it is read. */
data class SearchResults(
    val live: List<Channel> = emptyList(),
    val movies: List<Channel> = emptyList(),
    val series: List<Channel> = emptyList(),
) {
    val isEmpty: Boolean get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
    val total: Int get() = live.size + movies.size + series.size
}

/**
 * The genres a catalogue can currently be filtered by, and how much of it has been described.
 *
 * [coveragePercent] is quoted to the viewer rather than hidden, because a genre filter built
 * on a cache that has seen a tenth of a catalogue is telling less than the whole truth, and a
 * filter that silently omits nine films in ten is worse than one that says so.
 */
data class GenreIndex(
    val genres: List<String> = emptyList(),
    val coveragePercent: Int = 0,
    /** True when no metadata key is configured at all, which is a different emptiness. */
    val isMetadataDisabled: Boolean = false,
)

/**
 * Searching, across every kind at once.
 *
 * Separate from [ChannelRepository] — which answers "what is in this category" for one
 * destination at a time — because a search is the one question that ignores the division the
 * rest of the app is built on. A viewer looking for *Fargo* does not know or care whether
 * their provider filed it as a film or a series, and three screens each searching their own
 * kind is precisely the phone behaviour this improves on.
 *
 * Every read here is one-shot. Search is a question asked and answered, not a subscription:
 * three open Flows would re-run on every write to the channel table while somebody is still
 * typing, and the answer would be recomputed for a term they had already moved past.
 */
class SearchRepository(
    private val channelDao: ChannelDao,
    /** Whose favourites the results should show as favourited. */
    private val profiles: ProfileRepository,
    private val titleMetadataDao: TitleMetadataDao,
    private val metadataRepository: TitleMetadataRepository,
    /**
     * Where titles are cleaned and matched.
     *
     * The genre filter compares a cleaned form of every film and series title in the
     * catalogue against the metadata cache. On the account this project is tested against
     * that is 60,000 regex passes, and doing them on the caller's thread would drop frames
     * on a television for the length of the filter.
     */
    private val matchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Everything matching [query], optionally narrowed to one [genre].
     *
     * A blank query with no genre returns nothing rather than the whole catalogue: an empty
     * search box is not a request for 67,000 rows, and the browse tabs already exist for
     * looking at everything.
     */
    suspend fun search(
        sourceId: Long,
        query: String,
        genre: String? = null,
        limitPerKind: Int = DEFAULT_LIMIT_PER_KIND,
    ): SearchResults {
        val term = query.trim()
        if (term.isBlank() && genre.isNullOrBlank()) return SearchResults()

        return if (genre.isNullOrBlank()) {
            SearchResults(
                live = matches(sourceId, MediaKind.LIVE, term, limitPerKind),
                movies = matches(sourceId, MediaKind.VOD, term, limitPerKind),
                series = matches(sourceId, MediaKind.SERIES, term, limitPerKind),
            )
        } else {
            byGenre(sourceId, term, genre, limitPerKind)
        }
    }

    /**
     * Which genres can be filtered by, and how much of the catalogue is described.
     *
     * Derived from the cache rather than from a fixed list of genre names, because a genre
     * only means anything here if something the viewer owns is filed under it. Offering
     * "Western" against a catalogue holding none is a control that can only disappoint.
     */
    suspend fun genreIndex(sourceId: Long): GenreIndex {
        if (!metadataRepository.isEnabled) return GenreIndex(isMetadataDisabled = true)

        val cached = titleMetadataDao.allGenreRows()
        val titles = channelDao.titlesForMetadata(sourceId)

        return withContext(matchDispatcher) {
            val genres = cached
                .asSequence()
                .filterNot { it.isMiss }
                .flatMap { it.genres.orEmpty().splitGenres() }
                .distinct()
                .sorted()
                .toList()

            GenreIndex(
                genres = genres,
                coveragePercent = coverage(titles, cached.mapTo(HashSet()) { it.identity() }),
            )
        }
    }

    /**
     * How much of the catalogue the metadata cache has an answer for, as a percentage.
     *
     * Counted over *distinct cleaned titles* rather than over rows. A provider that lists the
     * same film four times in four qualities has one title to look up, and counting rows
     * would report a quarter of the coverage actually held.
     *
     * Titles that clean away to nothing — a name written entirely in a non-Latin script, a
     * bare language tag — are excluded from both halves. They will never be looked up, so
     * leaving them in the denominator would cap the figure below 100% permanently and make
     * a complete cache look like a broken one.
     */
    private fun coverage(titles: List<ChannelTitle>, cachedKeys: Set<CacheIdentity>): Int {
        val wanted = titles.asSequence()
            .mapNotNull { it.name.cacheIdentity(it.kind) }
            .toSet()

        if (wanted.isEmpty()) return 0
        val known = wanted.count { it in cachedKeys }
        return (known * PERCENT / wanted.size).coerceIn(0, PERCENT)
    }

    private suspend fun matches(sourceId: Long, kind: MediaKind, term: String, limit: Int): List<Channel> {
        if (term.isBlank()) return emptyList()
        return channelDao.search(profiles.activeProfileId, sourceId, kind.name, term, limit)
            .map { it.channel.toDomain(isFavorite = it.isFavorite) }
    }

    /**
     * The genre filter.
     *
     * Films and series are matched through the metadata cache, which is keyed by a cleaned
     * title — so the catalogue's titles are cleaned the same way and looked up. Live channels
     * have no metadata and never will, so they are matched on the genre word appearing in the
     * channel's own name. That is a weaker rule and deliberately so: it is how a channel
     * called "CRIME NETWORK HD" comes back for "Crime", which is what a viewer expects, and
     * the alternative is a live column that is always empty.
     */
    private suspend fun byGenre(sourceId: Long, term: String, genre: String, limit: Int): SearchResults {
        val cached = titleMetadataDao.allGenreRows()
        val titles = channelDao.titlesForMetadata(sourceId)

        val wantedIds = withContext(matchDispatcher) {
            val inGenre = cached.asSequence()
                .filterNot { it.isMiss }
                .filter { row -> row.genres.orEmpty().splitGenres().any { it.equals(genre, ignoreCase = true) } }
                .mapTo(HashSet()) { it.identity() }

            titles.asSequence()
                .filter { term.isBlank() || it.name.contains(term, ignoreCase = true) }
                .filter { title -> title.name.cacheIdentity(title.kind) in inGenre }
                // Two kinds share one cap so a genre held mostly by series still returns
                // films, and the split back into columns happens after the rows are read.
                .take(limit * KINDS_WITH_METADATA)
                .map { it.id }
                .toList()
        }

        val rows = if (wantedIds.isEmpty()) {
            emptyList()
        } else {
            channelDao.findAllByIds(profiles.activeProfileId, wantedIds)
                .map { it.channel.toDomain(isFavorite = it.isFavorite) }
        }

        return SearchResults(
            live = liveByGenre(sourceId, term, genre, limit),
            movies = rows.filter { it.kind == MediaKind.VOD }.take(limit),
            series = rows.filter { it.kind == MediaKind.SERIES }.take(limit),
        )
    }

    private suspend fun liveByGenre(sourceId: Long, term: String, genre: String, limit: Int): List<Channel> =
        if (term.isBlank()) {
            matches(sourceId, MediaKind.LIVE, genre, limit)
        } else {
            matches(sourceId, MediaKind.LIVE, term, limit * LIVE_OVERSCAN)
                .filter { it.name.contains(genre, ignoreCase = true) }
                .take(limit)
        }

    private companion object {
        /**
         * How many hits per kind a screen is given.
         *
         * A television shows one row per kind and a viewer walks it with a D-pad; past
         * forty presses nobody is reading, they are giving up. The cap is also what keeps a
         * two-letter term from being a full-table read.
         */
        const val DEFAULT_LIMIT_PER_KIND = 40

        /** Films and series. Live is matched by name and capped on its own. */
        const val KINDS_WITH_METADATA = 2

        /**
         * How many live matches are read before the genre word is applied to their names.
         *
         * The filter runs after the query, so the cap has to be loose enough that a term
         * with many matches still leaves some once the genre has thinned them.
         */
        const val LIVE_OVERSCAN = 5

        const val PERCENT = 100
    }
}

/** Newline-separated, as `TitleMetadataRepository` writes them. */
private fun String.splitGenres(): Sequence<String> =
    splitToSequence('\n').map { it.trim() }.filter { it.isNotBlank() }

/** The cache row this cached projection stands for, so the two sides join on the whole key. */
private fun TitleGenreRow.identity() = CacheIdentity(TitleIdentity(searchTitle, year), kind)
