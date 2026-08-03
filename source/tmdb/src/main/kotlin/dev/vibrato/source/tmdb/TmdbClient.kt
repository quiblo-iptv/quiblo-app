/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.source.tmdb

import dev.vibrato.core.model.MovieMetadata
import dev.vibrato.source.tmdb.dto.MovieDetailsDto
import dev.vibrato.source.tmdb.dto.SearchResponse
import dev.vibrato.source.tmdb.dto.toMetadata
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
 * Entirely optional and off unless the user supplies their own key. Vibrato ships no key
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

/**
 * Strips the decoration IPTV panels add to titles.
 *
 * Provider titles are rarely just a title: they carry a language tag, a quality marker, a
 * year in brackets, sometimes a category prefix. Searching TMDB for `"|AR| The Matrix (1999) HD"`
 * matches nothing, and the failure is silent, so the cleanup is the difference between
 * enrichment working and appearing to be broken.
 */
fun String.cleanedForSearch(): String = this
    .replace(BRACKETED, " ")
    .replace(QUALITY_MARKERS, " ")
    .replace(NON_TITLE_CHARS, " ")
    .replace(WHITESPACE, " ")
    .trim()
    .replace(LEADING_LANGUAGE_TAG, "")
    .trim()
    // A title that is nothing but an uppercase tag is a tag, not a title. Searching for
    // "AR" returns a confident wrong answer, which is worse than returning none.
    //
    // The cost is real and accepted: a film actually titled "IT" is indistinguishable from
    // a language tag by this rule and will not be enriched. Titles in ordinary case — "Up",
    // "Her" — are unaffected, which is what makes the trade worth taking.
    .let { if (it.matches(BARE_TAG)) "" else it }

/** The year in a provider title, when it has one, for narrowing the search. */
fun String.yearInTitle(): Int? =
    YEAR.find(this)?.groupValues?.get(1)?.toIntOrNull()

private val BRACKETED = Regex("""[\[(][^\])]*[\])]""")
private val QUALITY_MARKERS = Regex("""(?i)\b(4k|uhd|fhd|hd|sd|hevc|h265|x265|1080p?|720p?|480p?|multi|vo|vf|sub)\b""")
private val NON_TITLE_CHARS = Regex("""[|_\-–—:]+""")
private val WHITESPACE = Regex("""\s+""")
private val YEAR = Regex("""\b(19\d{2}|20\d{2})\b""")

/** A leading `AR`/`EN`/`FRA` language tag, stripped only when a title follows it. */
private val LEADING_LANGUAGE_TAG = Regex("""^[A-Z]{2,3}\s+(?=\S)""")
private val BARE_TAG = Regex("""^[A-Z]{2,3}$""")
