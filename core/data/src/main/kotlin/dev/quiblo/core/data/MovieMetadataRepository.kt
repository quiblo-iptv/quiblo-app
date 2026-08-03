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

import dev.quiblo.core.database.dao.MovieMetadataDao
import dev.quiblo.core.database.entity.MovieMetadataEntity
import dev.quiblo.core.datastore.TmdbKeyStore
import dev.quiblo.core.model.MovieMetadata
import dev.quiblo.source.tmdb.TmdbClient
import dev.quiblo.source.tmdb.cleanedForSearch
import dev.quiblo.source.tmdb.yearInTitle
import kotlinx.coroutines.flow.StateFlow

/**
 * Optional enrichment for films, from the user's own TMDB key.
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
 * Entries expire so a film that gains a description, a certificate or a score is not stuck
 * without one forever.
 */
class MovieMetadataRepository(
    private val client: TmdbClient,
    private val keyStore: TmdbKeyStore,
    private val dao: MovieMetadataDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Null while no key is configured, which the settings screen renders as "off". */
    val apiKey: StateFlow<String?> = keyStore.apiKey

    val isEnabled: Boolean get() = !keyStore.apiKey.value.isNullOrBlank()

    /**
     * What TMDB knows about [title], or null.
     *
     * Null covers every reason equally — feature off, no match, key rejected, host
     * unreachable — because the caller's response to all of them is identical: show what
     * the provider already supplied and nothing more.
     */
    suspend fun forTitle(title: String): MovieMetadata? {
        val key = keyStore.apiKey.value?.takeIf { it.isNotBlank() } ?: return null

        val cacheKey = title.cleanedForSearch().lowercase()
        if (cacheKey.isBlank()) return null

        val fresh = dao.find(cacheKey)?.takeIf { now() - it.fetchedAtEpochMillis < CACHE_TTL_MILLIS }

        return if (fresh != null) {
            // A cached miss is a cached answer: null, without asking again.
            if (fresh.isMiss) null else fresh.toMetadata()
        } else {
            lookupAndCache(apiKey = key, title = title, cacheKey = cacheKey)
        }
    }

    private suspend fun lookupAndCache(apiKey: String, title: String, cacheKey: String): MovieMetadata? {
        // The year narrows a search that would otherwise match a remake. Provider titles
        // carry it often enough to be worth reading.
        val metadata = client.lookup(apiKey = apiKey, title = title, year = title.yearInTitle())

        dao.upsert(
            metadata?.toEntity(cacheKey, now())
                // A miss is recorded rather than forgotten, so the next visit costs nothing.
                ?: MovieMetadataEntity(searchTitle = cacheKey, fetchedAtEpochMillis = now(), isMiss = true),
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
         * A fortnight. Long enough that browsing costs nothing, short enough that a film
         * which gains a description or a certificate picks it up without the user having to
         * do anything.
         */
        const val CACHE_TTL_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}

/** Newline-separated in one column: a join table for a handful of names is not worth it. */
private const val LIST_SEPARATOR = "\n"

private fun MovieMetadataEntity.toMetadata() = MovieMetadata(
    overview = overview,
    genres = genres?.split(LIST_SEPARATOR).orEmpty().filter { it.isNotBlank() },
    ageRating = ageRating,
    rating = rating,
    director = director,
    topCast = topCast?.split(LIST_SEPARATOR).orEmpty().filter { it.isNotBlank() },
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
)

private fun MovieMetadata.toEntity(searchTitle: String, fetchedAt: Long) = MovieMetadataEntity(
    searchTitle = searchTitle,
    overview = overview,
    genres = genres.joinToString(LIST_SEPARATOR).takeIf { it.isNotBlank() },
    ageRating = ageRating,
    rating = rating,
    director = director,
    topCast = topCast.joinToString(LIST_SEPARATOR).takeIf { it.isNotBlank() },
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    fetchedAtEpochMillis = fetchedAt,
    isMiss = false,
)
