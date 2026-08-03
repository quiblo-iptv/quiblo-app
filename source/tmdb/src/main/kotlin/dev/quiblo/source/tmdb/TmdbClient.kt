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

import dev.quiblo.core.model.MovieMetadata
import dev.quiblo.source.tmdb.dto.MovieDetailsDto
import dev.quiblo.source.tmdb.dto.SearchResponse
import dev.quiblo.source.tmdb.dto.toMetadata
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

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
     * Finds [title] and returns what TMDB knows about it.
     *
     * Returns null for every failure — no match, a rejected key, a rate limit, an
     * unreachable host. Enrichment is decoration on a screen that already works, so it
     * fails silently rather than putting an error where a plot should be.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun lookup(apiKey: String, title: String, year: Int? = null): MovieMetadata? = try {
        val cleaned = title.cleanedForSearch()
        if (cleaned.isBlank()) {
            null
        } else {
            searchFirstId(apiKey, cleaned, year)?.let { id -> details(apiKey, id) }
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun searchFirstId(apiKey: String, query: String, year: Int?): Int? {
        val response = httpClient.get("$BASE_URL/search/movie") {
            authorise(apiKey)
            parameter("query", query)
            parameter("include_adult", false)
            if (year != null) parameter("year", year)
        }
        if (response.status != HttpStatusCode.OK) return null
        return json.decodeFromString<SearchResponse>(response.bodyAsText()).results.firstOrNull()?.id
    }

    private suspend fun details(apiKey: String, id: Int): MovieMetadata? {
        val response = httpClient.get("$BASE_URL/movie/$id") {
            authorise(apiKey)
            // One request instead of three. TMDB rate-limits, and a films screen that fires
            // three calls per open is three times as likely to be throttled.
            parameter("append_to_response", "credits,release_dates")
        }
        if (response.status != HttpStatusCode.OK) return null
        return json.decodeFromString<MovieDetailsDto>(response.bodyAsText()).toMetadata()
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
