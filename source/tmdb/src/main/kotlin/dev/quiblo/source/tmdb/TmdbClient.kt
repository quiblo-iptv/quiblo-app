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

import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.source.tmdb.dto.MovieDetailsDto
import dev.quiblo.source.tmdb.dto.SearchResponse
import dev.quiblo.source.tmdb.dto.SearchResult
import dev.quiblo.source.tmdb.dto.TvDetailsDto
import dev.quiblo.source.tmdb.dto.toMetadata
import dev.quiblo.source.tmdb.dto.toPartialMetadata
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
 */
class TmdbClient(
    private val httpClient: HttpClient,
    private val json: Json = defaultJson,
) {

    /**
     * Finds [title] and returns everything TMDB knows about it: two requests, a search and
     * a full record.
     *
     * Returns null for every failure — no match, a rejected key, a rate limit, an
     * unreachable host. Enrichment is decoration on a screen that already works, so it
     * fails silently rather than putting an error where a plot should be.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun lookup(
        apiKey: String,
        title: String,
        kind: TmdbKind,
        year: Int? = null,
    ): TitleMetadata? = try {
        val cleaned = title.cleanedForSearch()
        if (cleaned.isBlank()) {
            null
        } else {
            search(apiKey, cleaned, kind, year)?.id?.let { id -> details(apiKey, id, kind) }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * The cheap half: one search request, giving a score and artwork but no cast or plot.
     *
     * For poster tiles, which display a number. Fetching a full record per tile would
     * double every request for facts nothing on that screen shows, and the user's rate
     * limit is the thing being spent.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun summary(
        apiKey: String,
        title: String,
        kind: TmdbKind,
        year: Int? = null,
    ): TitleMetadata? = try {
        val cleaned = title.cleanedForSearch()
        if (cleaned.isBlank()) null else search(apiKey, cleaned, kind, year)?.toPartialMetadata()
    } catch (_: Exception) {
        null
    }

    private suspend fun search(apiKey: String, query: String, kind: TmdbKind, year: Int?): SearchResult? {
        val response = httpClient.get("$BASE_URL/search/${kind.searchPath}") {
            authorise(apiKey)
            parameter("query", query)
            parameter("include_adult", false)
            // The year parameter is named differently for television, and sending the wrong
            // one is not an error TMDB reports — it is silently ignored, and the search
            // quietly stops being narrowed.
            if (year != null) parameter(kind.yearParameter, year)
        }
        if (response.status != HttpStatusCode.OK) return null
        return json.decodeFromString<SearchResponse>(response.bodyAsText()).results.firstOrNull()
    }

    private suspend fun details(apiKey: String, id: Int, kind: TmdbKind): TitleMetadata? {
        val response = httpClient.get("$BASE_URL/${kind.detailsPath}/$id") {
            authorise(apiKey)
            // One request instead of three. TMDB rate-limits, and a films screen that fires
            // three calls per open is three times as likely to be throttled.
            parameter("append_to_response", kind.appendToResponse)
        }
        if (response.status != HttpStatusCode.OK) return null
        val body = response.bodyAsText()
        return when (kind) {
            TmdbKind.MOVIE -> json.decodeFromString<MovieDetailsDto>(body).toMetadata()
            TmdbKind.SERIES -> json.decodeFromString<TvDetailsDto>(body).toMetadata()
        }
    }

    /**
     * TMDB takes either a v3 key as a query parameter or a v4 token as a bearer header, and
     * users arrive with one or the other without knowing which they have. A v4 token is a
     * JWT and therefore has dots in it; a v3 key is a bare hex string and never does. That
     * is enough to tell them apart, and saves asking a question the user cannot answer.
     */
    private fun io.ktor.client.request.HttpRequestBuilder.authorise(apiKey: String) {
        if (apiKey.count { it == '.' } == JWT_DOT_COUNT) {
            header("Authorization", "Bearer $apiKey")
        } else {
            parameter("api_key", apiKey)
        }
    }

    /** True when the key is accepted, for the settings screen to report. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun validate(apiKey: String): Boolean = try {
        httpClient.get("$BASE_URL/configuration") { authorise(apiKey) }.status == HttpStatusCode.OK
    } catch (_: Exception) {
        false
    }

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"

        private const val JWT_DOT_COUNT = 2

        val defaultJson: Json = Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
