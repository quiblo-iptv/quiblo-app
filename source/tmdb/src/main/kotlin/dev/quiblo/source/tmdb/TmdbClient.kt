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

package dev.quiblo.source.tmdb

import dev.quiblo.source.tmdb.dto.GenreListResponse
import dev.quiblo.source.tmdb.dto.MovieDetailsDto
import dev.quiblo.source.tmdb.dto.SearchResponse
import dev.quiblo.source.tmdb.dto.SearchResult
import dev.quiblo.source.tmdb.dto.TvDetailsDto
import dev.quiblo.source.tmdb.dto.toMetadata
import dev.quiblo.source.tmdb.dto.toPartialMetadata
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Which half of TMDB to ask.
 *
 * Films and series are separate catalogues with separate endpoints, separate search
 * parameters and separate names for a certificate. They are not variants of one request,
 * which is why this is a parameter rather than something inferred from the response.
 */
enum class TmdbKind(
    internal val searchPath: String,
    internal val detailsPath: String,
    internal val yearParameter: String,
    internal val appendToResponse: String,
) {
    MOVIE(
        searchPath = "movie",
        detailsPath = "movie",
        yearParameter = "year",
        appendToResponse = "credits,release_dates",
    ),

    SERIES(
        searchPath = "tv",
        detailsPath = "tv",
        yearParameter = "first_air_date_year",
        appendToResponse = "credits,content_ratings",
    ),
}

/**
 * A read-only client for The Movie Database.
 *
 * Entirely optional and off unless the user supplies their own key. Quiblo ships no key
 * of its own: a bundled key would be a shared credential in an open-source repository, and
 * it would make every install phone the same third party whether its owner wanted that or
 * not (AC-NFR-03).
 *
 * Nothing here is a `MediaSource`. TMDB supplies no streams and no playlists — it annotates
 * items the user's own provider already returned.
 *
 * Every call returns a [TmdbAnswer] rather than a nullable record, because the difference
 * between "nothing matches" and "I could not ask" is the difference between an answer worth
 * caching and a failure that must never be written down. See [TmdbAnswer].
 */
class TmdbClient(
    private val httpClient: HttpClient,
    private val json: Json = defaultJson,
) {

    /**
     * Owned rather than injected, because there is one TMDB and one key: a limiter handed in
     * per caller would be one budget per caller, which is the thing it exists to prevent.
     * Its own pacing is asserted directly in `TmdbRateLimiterTest`.
     */
    private val rateLimiter = TmdbRateLimiter()

    /**
     * Genre ids to names, per catalogue, learned once.
     *
     * A search hit names no genres — it carries `genre_ids`, and the names live behind a
     * list endpoint. Two requests for the whole vocabulary, held for the life of the
     * process, is what lets a genre arrive with the cheap one-request record instead of
     * costing a second request per title.
     */
    private val genreNames = mutableMapOf<TmdbKind, Map<Int, String>>()
    private val genreMutex = Mutex()

    /**
     * Finds [title] and returns everything TMDB knows about it: two requests, a search and
     * a full record.
     */
    suspend fun lookup(
        apiKey: String,
        title: String,
        kind: TmdbKind,
        year: Int? = null,
    ): TmdbAnswer {
        val cleaned = title.cleanedForSearch()
        if (cleaned.isBlank()) return TmdbAnswer.NoMatch

        return when (val hit = search(apiKey, cleaned, kind, year)) {
            is Hit.Refused -> hit.answer
            Hit.None -> TmdbAnswer.NoMatch
            is Hit.Found -> hit.result.id?.let { details(apiKey, it, kind) } ?: TmdbAnswer.NoMatch
        }
    }

    /**
     * The cheap half: one search request, giving a score, artwork and genres but no cast or
     * plot.
     *
     * For poster tiles, which display a number, and for the catalogue scan, which wants
     * genres for everything and a plot for nothing. Fetching a full record per title would
     * double every request for facts nothing on those screens shows, and the user's rate
     * limit is the thing being spent.
     */
    suspend fun summary(
        apiKey: String,
        title: String,
        kind: TmdbKind,
        year: Int? = null,
    ): TmdbAnswer {
        val cleaned = title.cleanedForSearch()
        if (cleaned.isBlank()) return TmdbAnswer.NoMatch

        return when (val hit = search(apiKey, cleaned, kind, year)) {
            is Hit.Refused -> hit.answer
            Hit.None -> TmdbAnswer.NoMatch
            is Hit.Found -> {
                // A hit with no genre ids needs no vocabulary, and asking for one would be a
                // request spent on a translation of nothing.
                val names = if (hit.result.genreIds.isEmpty()) emptyMap() else genreNamesFor(apiKey, kind)
                TmdbAnswer.Found(hit.result.toPartialMetadata(names))
            }
        }
    }

    /** True when the key is accepted, for the settings screen to report. */
    suspend fun validate(apiKey: String): Boolean =
        fetch(apiKey, "/configuration") is Reply.Body

    private suspend fun search(apiKey: String, query: String, kind: TmdbKind, year: Int?): Hit {
        val reply = fetch(apiKey, "/search/${kind.searchPath}") {
            parameter("query", query)
            parameter("include_adult", false)
            // The year parameter is named differently for television, and sending the wrong
            // one is not an error TMDB reports — it is silently ignored, and the search
            // quietly stops being narrowed.
            if (year != null) parameter(kind.yearParameter, year)
        }

        return when (reply) {
            is Reply.Failed -> Hit.Refused(reply.refusal)
            is Reply.Body -> {
                val results = reply.decode<SearchResponse>()?.results
                    // A body that will not parse is a failure to ask, not an absence of the
                    // title: recording it as a miss would cache a bug for a fortnight.
                    ?: return Hit.Refused(TmdbAnswer.Refused(TmdbRefusal.UNAVAILABLE))
                results.firstOrNull()?.let { Hit.Found(it) } ?: Hit.None
            }
        }
    }

    private suspend fun details(apiKey: String, id: Int, kind: TmdbKind): TmdbAnswer {
        val reply = fetch(apiKey, "/${kind.detailsPath}/$id") {
            // One request instead of three. TMDB rate-limits, and a films screen that fires
            // three calls per open is three times as likely to be throttled.
            parameter("append_to_response", kind.appendToResponse)
        }

        return when (reply) {
            is Reply.Failed -> reply.refusal
            is Reply.Body -> {
                val metadata = when (kind) {
                    TmdbKind.MOVIE -> reply.decode<MovieDetailsDto>()?.toMetadata()
                    TmdbKind.SERIES -> reply.decode<TvDetailsDto>()?.toMetadata()
                }
                metadata?.let { TmdbAnswer.Found(it) }
                    ?: TmdbAnswer.Refused(TmdbRefusal.UNAVAILABLE)
            }
        }
    }

    /**
     * The genre vocabulary for one catalogue, fetched at most once.
     *
     * An empty result is deliberately *not* remembered. A refusal here would otherwise stick
     * for the life of the process and quietly cost every title fetched afterwards its
     * genres — a whole scan producing a genre filter with nothing in it, for one bad moment
     * on the network.
     */
    private suspend fun genreNamesFor(apiKey: String, kind: TmdbKind): Map<Int, String> {
        genreNames[kind]?.let { return it }

        return genreMutex.withLock {
            genreNames[kind] ?: run {
                val names = when (val reply = fetch(apiKey, "/genre/${kind.searchPath}/list")) {
                    is Reply.Failed -> emptyMap()
                    is Reply.Body -> reply.decode<GenreListResponse>()
                        ?.genres
                        .orEmpty()
                        .mapNotNull { genre ->
                            val id = genre.id ?: return@mapNotNull null
                            val name = genre.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            id to name
                        }
                        .toMap()
                }
                if (names.isNotEmpty()) genreNames[kind] = names
                names
            }
        }
    }

    /**
     * One request, paced, with every way it can fail mapped to something a caller can act on.
     *
     * `CancellationException` is rethrown rather than swallowed. Without that, cancelling the
     * catalogue scan would look to every worker in flight like an unreachable host, and the
     * scan would report itself as having failed rather than as having been stopped.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun fetch(
        apiKey: String,
        path: String,
        parameters: HttpRequestBuilder.() -> Unit = {},
    ): Reply = try {
        rateLimiter.acquire()
        val response = httpClient.get("$BASE_URL$path") {
            authorise(apiKey)
            parameters()
        }
        when (response.status) {
            HttpStatusCode.OK -> Reply.Body(response.bodyAsText())

            HttpStatusCode.TooManyRequests -> {
                // Emptied rather than merely reported. The scan stops on a refusal, but
                // browsing does not, and a poster row that carries on asking at full speed
                // after a 429 is how one refusal becomes a run of them.
                rateLimiter.spend()
                Reply.Failed(
                    TmdbAnswer.Refused(
                        reason = TmdbRefusal.RATE_LIMITED,
                        // TMDB says how long to wait. Obeying it is cheaper than guessing.
                        retryAfterSeconds = response.headers[RETRY_AFTER_HEADER]?.toLongOrNull(),
                    ),
                )
            }

            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                Reply.Failed(TmdbAnswer.Refused(TmdbRefusal.KEY_REJECTED))

            else -> Reply.Failed(TmdbAnswer.Refused(TmdbRefusal.UNAVAILABLE))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Reply.Failed(TmdbAnswer.Refused(TmdbRefusal.UNAVAILABLE))
    }

    /** Null on a body that will not parse, which callers treat as a refusal rather than a miss. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun <reified T> Reply.Body.decode(): T? = try {
        json.decodeFromString<T>(text)
    } catch (_: Exception) {
        null
    }

    /**
     * TMDB takes either a v3 key as a query parameter or a v4 token as a bearer header, and
     * users arrive with one or the other without knowing which they have. A v4 token is a
     * JWT and therefore has dots in it; a v3 key is a bare hex string and never does. That
     * is enough to tell them apart, and saves asking a question the user cannot answer.
     */
    private fun HttpRequestBuilder.authorise(apiKey: String) {
        if (apiKey.count { it == '.' } == JWT_DOT_COUNT) {
            header("Authorization", "Bearer $apiKey")
        } else {
            parameter("api_key", apiKey)
        }
    }

    /** What one HTTP call produced, before anyone tries to read meaning into the body. */
    private sealed interface Reply {
        data class Body(val text: String) : Reply
        data class Failed(val refusal: TmdbAnswer.Refused) : Reply
    }

    /** What a search produced: a first hit, an empty result set, or no usable reply. */
    private sealed interface Hit {
        data class Found(val result: SearchResult) : Hit
        data object None : Hit
        data class Refused(val answer: TmdbAnswer.Refused) : Hit
    }

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"

        private const val JWT_DOT_COUNT = 2
        private const val RETRY_AFTER_HEADER = "Retry-After"

        val defaultJson: Json = Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
