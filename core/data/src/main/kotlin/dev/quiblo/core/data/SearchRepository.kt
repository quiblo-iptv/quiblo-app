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

import dev.quiblo.core.common.SCRIPT_MASK_UNKNOWN
import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.common.isInHiddenScript
import dev.quiblo.core.common.toMask
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.dao.escapeForLike
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
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
    /** Narrows to one genre. */
    val genre: String? = null,
    /**
     * Narrows to one year of release, as the metadata service dates it.
     *
     * Null is "any year". A year and a genre narrow together — they are two answers to the same
     * question, not two questions — and either alone works as well as both.
     *
     * Live channels are left out whenever a year is chosen, for the reason [includeLive] gives
     * about genres: a television channel is not from a year, and matching one on the digits in
     * its name would fill a column nobody asked for.
     */
    val year: Int? = null,
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
data class FilterIndex(
    val genres: List<String> = emptyList(),
    /**
     * Every year the cache has a title for, newest first.
     *
     * Newest first because that is the order a viewer looks for a year in, and because the top of
     * the strip is the only part reachable without walking: a catalogue that reaches back to the
     * fifties has seventy of these.
     */
    val years: List<Int> = emptyList(),
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
        if (term.isBlank() && options.genre.isNullOrBlank() && options.year == null) return SearchResults()

        // Read once for this search rather than per result list, so all three lists are
        // filtered against the same answer even if the setting changes mid-query.
        val hidden = if (options.includeHidden) emptySet() else hiddenScripts.first()
        val ask = Ask(sourceId, term, options.limitPerKind, options.includeHidden, hidden)
        return if (options.genre.isNullOrBlank() && options.year == null) {
            SearchResults(
                live = if (options.includeLive) ask.matches(MediaKind.LIVE, term) else emptyList(),
                movies = ask.matches(MediaKind.VOD, term),
                series = ask.matches(MediaKind.SERIES, term),
            )
        } else {
            byMetadata(ask, options)
        }
    }

    /**
     * What advanced search can filter by — genres and years — and how much of the catalogue is
     * described.
     *
     * Derived from the cache rather than from fixed lists, because a genre or a year only means
     * anything here if something the viewer owns is filed under it. Offering "Western" against a
     * catalogue holding none, or 1974 against a catalogue that starts in 1990, is a control that
     * can only disappoint.
     */
    suspend fun filterIndex(sourceId: Long): FilterIndex {
        if (!metadataRepository.isEnabled) return FilterIndex(isMetadataDisabled = true)

        val cached = titleMetadataDao.allFilterRows()

        // Two counts rather than a pass over the catalogue. This used to clean every film and
        // series title in Kotlin to find out how many of them the cache knew — the same fifty
        // thousand regex passes the genre filter was paying, run again the moment the search
        // screen opened. The cleaned key is a column now, so SQLite counts distinct keys and
        // counts how many join.
        val wanted = channelDao.countDistinctTitles(sourceId)
        val known = channelDao.countDescribedTitles(sourceId)

        return withContext(matchDispatcher) {
            val described = cached.filterNot { it.isMiss }

            val genres = described
                .asSequence()
                .flatMap { it.genres.orEmpty().splitGenres() }
                .distinct()
                .sorted()
                .toList()

            // The service's year, and the provider's only where the service gave none — the same
            // order the query matches in, so every chip offered finds something.
            val years = described
                .asSequence()
                .mapNotNull { row -> row.releaseYear ?: row.year.takeIf { it > 0 } }
                .filter { it in EARLIEST_YEAR..LATEST_YEAR }
                .distinct()
                .sortedDescending()
                .toList()

            FilterIndex(
                genres = genres,
                years = years,
                coveragePercent = coverage(wanted = wanted, known = known),
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
     * a complete cache look like a broken one. Both rules are now in the two `COUNT`s that
     * supply these numbers rather than in a pass over the whole catalogue here.
     *
     * A catalogue mid-backfill reads low: rows with no computed key are excluded, so the figure
     * climbs as the backfill runs. Under-reporting is the right direction for a number whose
     * whole job is to say the filter is telling less than the whole truth.
     */
    private fun coverage(wanted: Int, known: Int): Int {
        if (wanted <= 0) return 0
        return (known * PERCENT / wanted).coerceIn(0, PERCENT)
    }

    /**
     * One kind's worth of matches, already thinned of anything the viewer cannot read.
     *
     * **The script filter is inside the query now** — a bitmask test against the column `021`
     * added — so a row the viewer cannot read is not read, not mapped, and not walked character
     * by character to decide. Before that it ran in Kotlin after the SQL `LIMIT`, which meant
     * hiding a writing system quietly shortened every page of results: a term matching forty
     * Arabic titles and ten Latin ones returned forty rows, discarded thirty-nine of them, and
     * showed a viewer one hit for a search that had plenty.
     *
     * The overscan survives, and only for rows written before schema 19. Those carry no mask, the
     * query cannot decide about them, and [hidingUncomputedScripts] decides in Kotlin — which is
     * the old shape and needs the old allowance. It is dead weight once
     * `CatalogueIdentityBackfill` has been through, and free on a fresh install.
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
            .search(
                profileId = profiles.activeProfileId,
                sourceId = sourceId,
                kind = kind.name,
                query = escapeForLike(text),
                limit = asked,
                includeHidden = includeHidden,
                hiddenMask = hidden.toMask(),
                unknownMask = SCRIPT_MASK_UNKNOWN,
            )
            .hidingUncomputedScripts(hidden, { it.channel.scriptMask }, { it.channel.name })
            .asSequence()
            .map { it.channel.toDomain(isFavorite = it.isFavorite) }
            .take(keep)
            .toList()
    }

    /**
     * The genre and year filters, which are one query.
     *
     * Films and series are matched through the metadata cache, which is keyed by a cleaned
     * title — so the catalogue's titles are cleaned the same way and looked up. Live channels
     * have no metadata and never will, so a genre reaches them only through the genre word
     * appearing in the channel's own name. That is a weaker rule and deliberately so: it is how
     * a channel called "CRIME NETWORK HD" comes back for "Crime", which is what a viewer
     * expects, and the alternative is a live column that is always empty.
     *
     * **A year has no such fallback and gets none.** The digits in a channel's name are its
     * number, its bitrate or its quality far more often than they are a year, so a year filter
     * leaves the live column out rather than filling it with coincidences.
     */
    private suspend fun byMetadata(ask: Ask, options: SearchOptions): SearchResults {
        val genre = options.genre.orEmpty()
        val year = options.year ?: ANY_YEAR

        // A year excludes live outright; a genre alone still reaches live channels through their
        // names, which is the weaker rule [liveByGenre] exists for.
        val live = when {
            !options.includeLive || options.year != null -> emptyList()
            genre.isBlank() -> emptyList()
            else -> ask.liveByGenre(genre)
        }

        return SearchResults(
            live = live,
            movies = ask.inMetadata(MediaKind.VOD, genre, year),
            series = ask.inMetadata(MediaKind.SERIES, genre, year),
        )
    }

    /**
     * One column of a genre search, as one indexed query.
     *
     * **This is what `021` replaced, and the size of it is the point.** The previous version read
     * every film and series the source carried — fifty thousand rows on this project's own
     * provider — cleaned each title in Kotlin to a cache key, and intersected that with the
     * cached genres. Cleaning a title is eight regex passes, so one press of a genre chip was
     * four hundred thousand regex applications, nothing was kept between presses, and the screen
     * looked like it had hung. The cleaned key is now a column, so this is a join.
     *
     * A cap per kind rather than one shared cap, which was `019`'s fix and is kept: `LIMIT` is
     * applied per query here, so neither column can starve the other whatever order SQLite
     * returns rows in.
     */
    private suspend fun Ask.inMetadata(kind: MediaKind, genre: String, year: Int): List<Channel> {
        val asked = if (hidden.isEmpty()) limit else limit * SCRIPT_OVERSCAN
        return channelDao
            .searchByMetadata(
                profileId = profiles.activeProfileId,
                sourceId = sourceId,
                kind = kind.name,
                genre = genre,
                year = year,
                query = escapeForLike(term),
                limit = asked,
                includeHidden = includeHidden,
                hiddenMask = hidden.toMask(),
                unknownMask = SCRIPT_MASK_UNKNOWN,
            )
            .hidingUncomputedScripts(hidden, { it.channel.scriptMask }, { it.channel.name })
            .asSequence()
            .map { it.channel.toDomain(isFavorite = it.isFavorite) }
            .take(limit)
            .toList()
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

        /** What the query reads as "any year". Zero is never a year a title was released in. */
        const val ANY_YEAR = 0

        /**
         * The window a year chip can fall in.
         *
         * A provider's title carries whatever somebody typed into it, and `Alien 2` has been
         * filed as a 1979 film and as a year 2 one. Both ends are here so a junk row cannot put
         * a chip on the strip that finds nothing.
         */
        const val EARLIEST_YEAR = 1888
        const val LATEST_YEAR = 2100
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
