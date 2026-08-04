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

import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.entity.TitleMetadataEntity
import dev.quiblo.core.datastore.TmdbKeyStore
import dev.quiblo.core.model.AuthorLabel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.source.tmdb.TmdbClient
import dev.quiblo.source.tmdb.TmdbKind
import dev.quiblo.source.tmdb.cleanedForSearch
import dev.quiblo.source.tmdb.yearInTitle
import kotlinx.coroutines.flow.StateFlow

/**
 * Optional enrichment for films and series, from the user's own TMDB key.
 *
 * Off unless a key is set. When it is off nothing here touches the network at all, which
 * is what keeps AC-NFR-03 true: the only outbound hosts are the ones the user configured,
 * and TMDB becomes one of those only by the user pasting a key in.
 *
 * Answers are cached in the database, not in memory. TMDB rate-limits per key, and the key
 * belongs to the user — a cache that empties on every relaunch would spend their quota
 * re-learning what it already knew. Misses are cached too: "this title matches nothing" is
 * an answer, and re-asking it on every visit would be the most wasteful thing here.
 *
 * Entries expire so a title that gains a description, a certificate or a score is not stuck
 * without one forever.
 */
class TitleMetadataRepository(
    private val client: TmdbClient,
    private val keyStore: TmdbKeyStore,
    private val dao: TitleMetadataDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Null while no key is configured, which the settings screen renders as "off". */
    val apiKey: StateFlow<String?> = keyStore.apiKey

    val isEnabled: Boolean get() = !keyStore.apiKey.value.isNullOrBlank()

    /**
     * Everything TMDB knows about [title], or null.
     *
     * Null covers every reason equally — feature off, no match, key rejected, host
     * unreachable — because the caller's response to all of them is identical: show what
     * the provider already supplied and nothing more.
     *
     * A record that only a poster tile ever asked for is *not* an answer here: it carries a
     * score and no cast. Those are upgraded in place on first open rather than left beside
     * a second row for the same title.
     */
    suspend fun forTitle(title: String, kind: MediaKind): TitleMetadata? =
        cachedOrFetched(title, kind, acceptPartial = false)

    /**
     * Just the score, for a poster tile.
     *
     * One request rather than two on a miss, and nothing at all once cached. A record
     * already held in full answers this without asking anyone.
     */
    suspend fun ratingFor(title: String, kind: MediaKind): Double? =
        cachedOrFetched(title, kind, acceptPartial = true)?.rating

    private suspend fun cachedOrFetched(
        title: String,
        kind: MediaKind,
        acceptPartial: Boolean,
    ): TitleMetadata? {
        val key = keyStore.apiKey.value?.takeIf { it.isNotBlank() }
        val tmdbKind = kind.toTmdbKind()
        val cacheKey = title.cleanedForSearch().lowercase().takeIf { it.isNotBlank() }

        // Three ways there is nothing to look up: the feature is off, the item is a live
        // channel, or the title cleaned down to nothing worth searching for.
        if (key == null || tmdbKind == null || cacheKey == null) return null

        return resolve(key, tmdbKind, title, kind, cacheKey, acceptPartial)
    }

    @Suppress("LongParameterList")
    private suspend fun resolve(
        apiKey: String,
        tmdbKind: TmdbKind,
        title: String,
        kind: MediaKind,
        cacheKey: String,
        acceptPartial: Boolean,
    ): TitleMetadata? {
        val fresh = dao.find(cacheKey, kind.name)
            ?.takeIf { now() - it.fetchedAtEpochMillis < CACHE_TTL_MILLIS }

        if (fresh != null && fresh.answers(acceptPartial)) {
            return if (fresh.isMiss) null else fresh.toMetadata()
        }

        return fetchAndCache(
            apiKey = apiKey,
            title = title,
            kind = kind,
            tmdbKind = tmdbKind,
            cacheKey = cacheKey,
            partial = acceptPartial,
        )
    }

    @Suppress("LongParameterList")
    private suspend fun fetchAndCache(
        apiKey: String,
        title: String,
        kind: MediaKind,
        tmdbKind: TmdbKind,
        cacheKey: String,
        partial: Boolean,
    ): TitleMetadata? {
        // The year narrows a search that would otherwise match a remake. Provider titles
        // carry it often enough to be worth reading.
        val year = title.yearInTitle()
        val metadata = if (partial) {
            client.summary(apiKey = apiKey, title = title, kind = tmdbKind, year = year)
        } else {
            client.lookup(apiKey = apiKey, title = title, kind = tmdbKind, year = year)
        }

        dao.upsert(
            metadata?.toEntity(cacheKey, kind.name, now())
                // A miss is recorded rather than forgotten, so the next visit costs nothing.
                ?: TitleMetadataEntity(
                    searchTitle = cacheKey,
                    kind = kind.name,
                    fetchedAtEpochMillis = now(),
                    isMiss = true,
                ),
        )
        return metadata
    }

    suspend fun setApiKey(apiKey: String?) {
        keyStore.set(apiKey)
        // A different key can return different results — a different language, a different
        // region's certificates — so nothing carries over.
        dao.clear()
    }

    suspend fun validate(apiKey: String): Boolean = client.validate(apiKey)

    suspend fun load() = keyStore.load()

    private companion object {
        /**
         * How long a cached answer stands.
         *
         * A fortnight. Long enough that browsing costs nothing, short enough that a title
         * which gains a description or a certificate picks it up without the user having to
         * do anything.
         */
        const val CACHE_TTL_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}

/**
 * Which TMDB catalogue, if any, describes this kind of item.
 *
 * Live channels return null and are never looked up. A television channel is not a title —
 * searching for one matches whatever film happens to share its name, and the guide already
 * says what is on it.
 */
private fun MediaKind.toTmdbKind(): TmdbKind? = when (this) {
    MediaKind.VOD -> TmdbKind.MOVIE
    MediaKind.SERIES -> TmdbKind.SERIES
    MediaKind.LIVE -> null
}

/**
 * Whether a cached row is an answer to the question being asked.
 *
 * A miss answers everything: a title that matched nothing has no fuller version to go and
 * fetch, so a detail screen must not mistake it for a partial record and re-ask on every
 * open. Otherwise a partial row answers a poster tile and not a detail screen.
 */
private fun TitleMetadataEntity.answers(acceptPartial: Boolean): Boolean =
    isMiss || acceptPartial || !isPartial

/** Newline-separated in one column: a join table for a handful of names is not worth it. */
private const val LIST_SEPARATOR = "\n"

private fun TitleMetadataEntity.toMetadata() = TitleMetadata(
    overview = overview,
    genres = genres?.split(LIST_SEPARATOR).orEmpty().filter { it.isNotBlank() },
    ageRating = ageRating,
    rating = rating,
    author = author,
    authorLabel = if (kind == MediaKind.SERIES.name) AuthorLabel.CREATOR else AuthorLabel.DIRECTOR,
    topCast = topCast?.split(LIST_SEPARATOR).orEmpty().filter { it.isNotBlank() },
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    isPartial = isPartial,
)

private fun TitleMetadata.toEntity(searchTitle: String, kind: String, fetchedAt: Long) = TitleMetadataEntity(
    searchTitle = searchTitle,
    kind = kind,
    overview = overview,
    genres = genres.joinToString(LIST_SEPARATOR).takeIf { it.isNotBlank() },
    ageRating = ageRating,
    rating = rating,
    author = author,
    topCast = topCast.joinToString(LIST_SEPARATOR).takeIf { it.isNotBlank() },
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    fetchedAtEpochMillis = fetchedAt,
    isMiss = false,
    isPartial = isPartial,
)
