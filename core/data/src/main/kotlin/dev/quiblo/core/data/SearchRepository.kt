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

import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.common.isInHiddenScript
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.ChannelTitle
import dev.quiblo.core.database.dao.TitleGenreRow
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.dao.escapeForLike
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.tmdb.TitleIdentity
import dev.quiblo.source.tmdb.titleIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** What one search returned, kept apart by kind because that is how it is read. */
data class SearchResults(
    val live: List<Channel> = emptyList(),
    val movies: List<Channel> = emptyList(),
    val series: List<Channel> = emptyList(),
) {
    val isEmpty: Boolean get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
    val total: Int get() = live.size + movies.size + series.size

    /** Drops results written in a script the viewer has hidden (INC-F14). */
    fun hidingUnreadableScripts(hidden: Set<TitleScript>): SearchResults {
        if (hidden.isEmpty()) return this
        return SearchResults(
            live = live.filterNot { it.name.isInHiddenScript(hidden) },
            movies = movies.filterNot { it.name.isInHiddenScript(hidden) },
            series = series.filterNot { it.name.isInHiddenScript(hidden) },
        )
    }
}

/**
 * Everything about a search except the source and the words typed into it.
 *
 * One value rather than four more parameters. They travel together, they all have sensible
 * defaults, and a call site that wants to change one of them should not have to name the rest.
 */
data class SearchOptions(
    /** Narrows to one genre, which is the only thing advanced search filters by. */
    val genre: String? = null,
    /**
     * Searches what the viewer has hidden as well — both hidden categories and hidden writing
     * systems.
     *
     * One flag for both because they are one question from the viewer's side. Somebody who has
     * hidden a category and then goes looking for something in it wants it found; making them
     * work out which of two settings is responsible would be asking them to know how this is
     * built.
     *
     * Not persisted anywhere. It belongs to one search, and a hiding setting that quietly stopped
     * applying because of something typed last week is worse than no setting.
     */
    val includeHidden: Boolean = false,
    /**
     * Whether live channels are searched at all.
     *
     * True by default, because a plain search across everything is what the screen is for. It is
     * advanced search that turns it off: a live channel has no metadata, so a genre only reaches
     * it through the genre word appearing in the channel's own name — a weak rule filling a
     * column nobody filtering by genre asked for. Off means the query is not made, not that its
     * answer is thrown away.
     */
    val includeLive: Boolean = true,
    val limitPerKind: Int = DEFAULT_LIMIT_PER_KIND,
)

/**
 * How many hits per kind a screen is given.
 *
 * A television shows one row per kind and a viewer walks it with a D-pad; past forty presses
 * nobody is reading, they are giving up. The cap is also what keeps a two-letter term from being
 * a full-table read.
 */
const val DEFAULT_LIMIT_PER_KIND = 40

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
    /** The writing systems this viewer has hidden — see [ScriptFilterRepository]. */
    private val hiddenScripts: Flow<Set<TitleScript>> = flowOf(emptySet()),
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
        options: SearchOptions = SearchOptions(),
    ): SearchResults {
        val term = query.trim()
        if (term.isBlank() && options.genre.isNullOrBlank()) return SearchResults()

        // Read once for this search rather than per result list, so all three lists are
        // filtered against the same answer even if the setting changes mid-query.
        val hidden = if (options.includeHidden) emptySet() else hiddenScripts.first()
        val ask = Ask(sourceId, term, options.limitPerKind, options.includeHidden, hidden)
        val results = if (options.genre.isNullOrBlank()) {
            SearchResults(
                live = if (options.includeLive) ask.matches(MediaKind.LIVE, term) else emptyList(),
                movies = ask.matches(MediaKind.VOD, term),
                series = ask.matches(MediaKind.SERIES, term),
            )
        } else {
            byGenre(ask, options.genre, options.includeLive)
        }
        return results.hidingUnreadableScripts(hidden)
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
        // The whole catalogue, hidden categories included. This figure answers "how much of what
        // you own has been described", and a viewer's hiding choices do not change that.
        val titles = channelDao.titlesForMetadata(sourceId, includeHidden = true)

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

    /**
     * One kind's worth of matches, already thinned of anything the viewer cannot read.
     *
     * **The script filter runs here rather than only on the way out**, and the query overscans to
     * pay for it. It used to run after the SQL `LIMIT`, which meant hiding a writing system
     * quietly shortened every page of results: a term matching forty Arabic titles and ten Latin
     * ones returned forty rows, discarded thirty-nine of them, and showed a viewer one hit for a
     * search that had plenty.
     */
    private suspend fun Ask.matches(
        kind: MediaKind,
        /** What to search for, which is the term for most callers and the genre word for live. */
        text: String,
        overscan: Int = 1,
    ): List<Channel> {
        if (text.isBlank()) return emptyList()
        val keep = limit * overscan
        val asked = if (hidden.isEmpty()) keep else keep * SCRIPT_OVERSCAN
        // Escaped so % and _ are searched for rather than acted on. See escapeForLike.
        return channelDao
            .search(profiles.activeProfileId, sourceId, kind.name, escapeForLike(text), asked, includeHidden)
            .asSequence()
            .map { it.channel.toDomain(isFavorite = it.isFavorite) }
            .filterNot { it.name.isInHiddenScript(hidden) }
            .take(keep)
            .toList()
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
    private suspend fun byGenre(ask: Ask, genre: String, includeLive: Boolean): SearchResults {
        val term = ask.term
        val limit = ask.limit
        val cached = titleMetadataDao.allGenreRows()
        val titles = channelDao.titlesForMetadata(ask.sourceId, ask.includeHidden)

        val wantedIds = withContext(matchDispatcher) {
            val inGenre = cached.asSequence()
                .filterNot { it.isMiss }
                .filter { row -> row.genres.orEmpty().splitGenres().any { it.equals(genre, ignoreCase = true) } }
                .mapTo(HashSet()) { it.identity() }

            val matching = titles.asSequence()
                .filter { term.isBlank() || it.name.contains(term, ignoreCase = true) }
                .filter { title -> title.name.cacheIdentity(title.kind) in inGenre }
                .toList()

            /*
             * **A cap per kind, taken before the split rather than after it.**
             *
             * This used to be one cap of `limit * 2` over the whole matching sequence, with the
             * split into columns happening afterwards — and the comment on it said the cap was
             * shared "so a genre held mostly by series still returns films". It did the opposite.
             *
             * `titlesForMetadata` has no `ORDER BY`, so SQLite returns rowid order; rows are
             * inserted live, then films, then series, so rowid order puts *every* film ahead of
             * *every* series. On a small catalogue eighty rows reach the series. On a real one
             * they do not, and the series column is empty — or the films column is, on an account
             * whose series were inserted first. Which one is empty depends on nothing a viewer
             * can see, which is why this was reported as random.
             *
             * Taking each kind's own cap cannot starve either, whatever order the rows arrive in.
             */
            KIND_COLUMNS.flatMap { kind ->
                matching.asSequence()
                    .filter { it.kind == kind.name }
                    .take(limit)
                    .map { it.id }
                    .toList()
            }
        }

        val rows = if (wantedIds.isEmpty()) {
            emptyList()
        } else {
            channelDao.findAllByIds(profiles.activeProfileId, wantedIds)
                .map { it.channel.toDomain(isFavorite = it.isFavorite) }
        }

        return SearchResults(
            live = if (includeLive) ask.liveByGenre(genre) else emptyList(),
            movies = rows.filter { it.kind == MediaKind.VOD }.take(limit),
            series = rows.filter { it.kind == MediaKind.SERIES }.take(limit),
        )
    }

    private suspend fun Ask.liveByGenre(genre: String): List<Channel> =
        if (term.isBlank()) {
            matches(MediaKind.LIVE, genre)
        } else {
            matches(MediaKind.LIVE, term, overscan = LIVE_OVERSCAN)
                .filter { it.name.contains(genre, ignoreCase = true) }
                .take(limit)
        }

    /**
     * One search, as one value.
     *
     * Five of these travel together through every private function here, and passing them
     * separately put three of them over detekt's parameter limit — which is the tool noticing
     * what the shape of the code already said: these are not five arguments, they are one
     * question asked in five parts.
     */
    private data class Ask(
        val sourceId: Long,
        val term: String,
        val limit: Int,
        val includeHidden: Boolean,
        val hidden: Set<TitleScript>,
    )

    private companion object {
        /**
         * The two columns a genre search fills from the metadata cache.
         *
         * Live is not among them: a television channel has no metadata and never will, so it is
         * matched on its own name and capped on its own.
         */
        val KIND_COLUMNS = listOf(MediaKind.VOD, MediaKind.SERIES)

        /**
         * How many live matches are read before the genre word is applied to their names.
         *
         * The filter runs after the query, so the cap has to be loose enough that a term
         * with many matches still leaves some once the genre has thinned them.
         */
        const val LIVE_OVERSCAN = 5

        /**
         * How many rows are read per kind when a writing system is hidden.
         *
         * The filter runs in Kotlin — SQLite cannot ask what script a string is in — so the query
         * has to bring back more than it will keep. Twice is enough for a catalogue where one
         * hidden script is the majority of it, which is the case this exists for.
         */
        const val SCRIPT_OVERSCAN = 2

        const val PERCENT = 100
    }
}

/** Newline-separated, as `TitleMetadataRepository` writes them. */
private fun String.splitGenres(): Sequence<String> =
    splitToSequence('\n').map { it.trim() }.filter { it.isNotBlank() }

/**
 * What makes two catalogue titles the same suggestion.
 *
 * `INC-F1`. A provider that lists one film in four qualities should offer **one** suggestion, and
 * the cleaner that already decides that for the metadata cache decides it here too — one cleaner,
 * one place, which is the rule `014` states and `016` proved the cost of breaking.
 *
 * Exposed from `:core:data` rather than letting a feature reach into `:source:tmdb`: `PLAN.md` §2
 * says features talk to this layer and nothing else.
 */
fun String.suggestionKey(): String = titleIdentity().searchTitle

/** The cache row this cached projection stands for, so the two sides join on the whole key. */
private fun TitleGenreRow.identity() = CacheIdentity(TitleIdentity(searchTitle, year), kind)
