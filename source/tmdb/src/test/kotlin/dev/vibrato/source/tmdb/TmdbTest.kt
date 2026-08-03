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

import dev.vibrato.source.tmdb.dto.MovieDetailsDto
import dev.vibrato.source.tmdb.dto.toMetadata
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Payloads here are hand-written in TMDB's shape. No response was captured from the service
 * and no API key appears anywhere in this repository (AC-LEGAL-04).
 */
class TmdbTest {

    private val json = TmdbClient.defaultJson

    @Nested
    @DisplayName("provider titles")
    inner class TitleCleanup {

        @Test
        @DisplayName("strips the decoration panels attach to titles")
        fun `cleans a typical provider title`() {
            // This is what actually arrives from a panel, and searching for it verbatim
            // matches nothing — which is the difference between the feature working and
            // appearing to be broken.
            assertEquals("The Matrix", "|AR| The Matrix (1999) HD".cleanedForSearch())
            assertEquals("Dune Part Two", "Dune: Part Two - 4K".cleanedForSearch())
            assertEquals("Inception", "EN | Inception [1080p]".cleanedForSearch())
        }

        @Test
        fun `leaves an already clean title alone`() {
            assertEquals("Arrival", "Arrival".cleanedForSearch())
        }

        @Test
        fun `a title that is only decoration cleans to nothing`() {
            // Must be blank rather than a stray fragment: searching for "HD" returns
            // confident nonsense, which is worse than returning nothing.
            assertTrue("|AR| [1080p] HD".cleanedForSearch().isBlank())
        }

        @Test
        @DisplayName("strips release, codec and audio markers")
        fun `cleans the wider vocabulary panels use`() {
            assertEquals("The Matrix", "The Matrix 4K PURE REMUX".cleanedForSearch())
            assertEquals("Arrival", "Arrival WEB-DL x265 DTS".cleanedForSearch())
            assertEquals("Sicario", "Sicario BluRay 10bit MULTI".cleanedForSearch())
        }

        @Test
        @DisplayName("a non-Latin title cleans to nothing rather than searching in vain")
        fun `drops non latin script`() {
            // TMDB is queried in English, so an Arabic title returns nothing however it is
            // spelled. Cleaning it to blank means the request is never sent at all, which
            // spends none of the user's rate limit on a call that could not have matched.
            assertTrue("مسلسل الاختيار".cleanedForSearch().isBlank())
            assertTrue("Гарри Поттер".cleanedForSearch().isBlank())
        }

        @Test
        fun `keeps the Latin part of a mixed title`() {
            assertEquals("Oppenheimer", "أوبنهايمر Oppenheimer 4K".cleanedForSearch())
        }

        @Test
        fun `does not eat title words that merely resemble markers`() {
            // "Dune" and "Cast" are not release markers; over-eager stripping costs a word
            // of the title, which is a worse failure than a leftover marker.
            assertEquals("Dune Part Two", "Dune Part Two".cleanedForSearch())
            assertEquals("Cast Away", "Cast Away".cleanedForSearch())
        }

        @Test
        fun `reads the year when the title carries one`() {
            assertEquals(1999, "The Matrix (1999)".yearInTitle())
            assertEquals(2024, "Dune Part Two 2024 4K".yearInTitle())
            assertNull("The Matrix".yearInTitle())
        }

        @Test
        fun `does not mistake a resolution for a year`() {
            // 1080 and 720 are not years, and the pattern must not treat them as one.
            assertNull("Movie 1080p".yearInTitle())
        }
    }

    @Nested
    @DisplayName("details mapping")
    inner class Mapping {

        @Test
        fun `maps the fields the screen shows`() {
            val dto = json.decodeFromString<MovieDetailsDto>(
                """
                {
                  "overview": "A hacker learns the truth.",
                  "genres": [{"name": "Action"}, {"name": "Science Fiction"}],
                  "vote_average": 8.2,
                  "poster_path": "/poster.jpg",
                  "credits": {
                    "cast": [
                      {"name": "Second Billed", "order": 1},
                      {"name": "Top Billed", "order": 0}
                    ],
                    "crew": [
                      {"name": "Some Editor", "job": "Editor"},
                      {"name": "The Director", "job": "Director"}
                    ]
                  },
                  "release_dates": {
                    "results": [
                      {"iso_3166_1": "GB", "release_dates": [{"certification": "15"}]},
                      {"iso_3166_1": "US", "release_dates": [{"certification": "R"}]}
                    ]
                  }
                }
                """.trimIndent(),
            )

            val metadata = dto.toMetadata()

            assertEquals("A hacker learns the truth.", metadata.overview)
            assertEquals(listOf("Action", "Science Fiction"), metadata.genres)
            assertEquals(8.2, metadata.rating)
            assertEquals("The Director", metadata.director)
            // Billing order, not the order the API happened to list them in.
            assertEquals(listOf("Top Billed", "Second Billed"), metadata.topCast)
            // US is preferred, because certificates are national and not comparable.
            assertEquals("R", metadata.ageRating)
            assertTrue(metadata.posterUrl!!.endsWith("/poster.jpg"))
        }

        @Test
        fun `falls back to any country that has a certificate`() {
            val dto = json.decodeFromString<MovieDetailsDto>(
                """{"release_dates":{"results":[{"iso_3166_1":"DE","release_dates":[{"certification":"12"}]}]}}""",
            )

            assertEquals("12", dto.toMetadata().ageRating)
        }

        @Test
        fun `an unrated film has no score rather than a score of zero`() {
            // TMDB returns 0.0 for a film nobody has rated, which would render as a genuine
            // zero out of ten — an actively wrong statement about the film.
            val dto = json.decodeFromString<MovieDetailsDto>("""{"vote_average": 0.0}""")

            assertNull(dto.toMetadata().rating)
        }

        @Test
        fun `an empty response maps to empty metadata rather than throwing`() {
            assertTrue(json.decodeFromString<MovieDetailsDto>("{}").toMetadata().isEmpty)
        }

        @Test
        fun `unknown fields are ignored`() {
            val dto = json.decodeFromString<MovieDetailsDto>(
                """{"overview":"Kept","some_new_tmdb_field":{"nested":[1,2]}}""",
            )

            assertEquals("Kept", dto.toMetadata().overview)
        }
    }

    @Nested
    @DisplayName("failure handling")
    inner class Failures {

        private fun clientReturning(status: HttpStatusCode, body: String = "{}") = TmdbClient(
            HttpClient(MockEngine { respond(body, status, headersOf("Content-Type", "application/json")) }),
        )

        @Test
        @DisplayName("a rejected key yields no metadata rather than an error")
        fun `unauthorised returns null`() = runTest {
            // Enrichment is decoration on a screen that already works. Every failure has the
            // same correct response: show what the provider supplied and nothing more.
            assertNull(clientReturning(HttpStatusCode.Unauthorized).lookup("bad-key", "The Matrix"))
        }

        @Test
        fun `a rate limit yields no metadata`() = runTest {
            assertNull(clientReturning(HttpStatusCode.TooManyRequests).lookup("key", "The Matrix"))
        }

        @Test
        fun `a title with nothing searchable never reaches the network`() = runTest {
            var requests = 0
            val client = TmdbClient(
                HttpClient(
                    MockEngine {
                        requests++
                        respond("{}", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                    },
                ),
            )

            assertNull(client.lookup("key", "|AR| [1080p] HD"))
            assertEquals(0, requests, "a blank search must not be sent")
        }

        @Test
        fun `no match yields no metadata`() = runTest {
            assertNull(clientReturning(HttpStatusCode.OK, """{"results":[]}""").lookup("key", "Nonexistent"))
        }
    }
}
