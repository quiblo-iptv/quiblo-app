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

package dev.quiblo.source.xtream.dto

import dev.quiblo.source.xtream.XtreamClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Deserialisation tests for the DTO layer (AC-XT-06, AC-NFR-07).
 *
 * Every payload here is synthetic. They are modelled on the *shapes* panels are known to
 * send — a number where a string belongs, `"1.0"` for an integer, an object where a scalar
 * belongs — and never captured from a real panel, which would put a hostname in the
 * repository (AC-LEGAL-04).
 *
 * The point of these tests is the failure mode, not the happy path. The contract is that a
 * malformed field degrades to null and costs you that field, never the whole response.
 */
class XtreamDtoTest {

    private val json = XtreamClient.defaultJson

    @Nested
    @DisplayName("auth response")
    inner class Auth {

        @Test
        fun `parses user and server info`() {
            val parsed = json.decodeFromString<AuthResponse>(
                """
                {
                  "user_info": {
                    "username": "someone",
                    "auth": 1,
                    "status": "Active",
                    "exp_date": "1793404800",
                    "max_connections": "2"
                  },
                  "server_info": {
                    "url": "panel.invalid",
                    "port": 8080,
                    "https_port": "8443"
                  }
                }
                """.trimIndent(),
            )

            assertEquals("someone", parsed.userInfo?.username)
            assertEquals(true, parsed.userInfo?.auth)
            assertEquals(1_793_404_800L, parsed.userInfo?.expiryEpochSeconds)
            assertEquals(2, parsed.userInfo?.maxConnections)
            assertEquals("panel.invalid", parsed.serverInfo?.url)
            // Sent as a JSON number, declared as a String: the flexible serializer is what
            // stops this from throwing.
            assertEquals("8080", parsed.serverInfo?.port)
            assertEquals("8443", parsed.serverInfo?.httpsPort)
        }

        @Test
        fun `an empty object yields nulls rather than throwing`() {
            val parsed = json.decodeFromString<AuthResponse>("{}")

            assertNull(parsed.userInfo)
            assertNull(parsed.serverInfo)
        }

        @Test
        fun `unknown fields are ignored`() {
            val parsed = json.decodeFromString<AuthResponse>(
                """{"user_info":{"username":"a","some_new_panel_field":{"nested":true}}}""",
            )

            assertEquals("a", parsed.userInfo?.username)
        }
    }

    @Nested
    @DisplayName("account status")
    inner class Status {

        @Test
        fun `recognises expired regardless of case`() {
            assertTrue(userWithStatus("Expired").isExpired)
            assertTrue(userWithStatus("EXPIRED").isExpired)
            assertFalse(userWithStatus("Active").isExpired)
        }

        @Test
        fun `treats disabled as banned`() {
            assertTrue(userWithStatus("Banned").isBanned)
            assertTrue(userWithStatus("Disabled").isBanned)
            assertFalse(userWithStatus("Active").isBanned)
        }

        @Test
        fun `an absent status is neither expired nor banned`() {
            val user = json.decodeFromString<UserInfo>("{}")

            assertFalse(user.isExpired)
            assertFalse(user.isBanned)
        }

        private fun userWithStatus(status: String) =
            json.decodeFromString<UserInfo>("""{"status":"$status"}""")
    }

    @Nested
    @DisplayName("flexible scalars")
    inner class Flexible {

        @Test
        fun `reads an integer sent as a decimal string`() {
            // Panels that store connection counts as floats send "1.0" here.
            assertEquals(1, json.decodeFromString<UserInfo>("""{"max_connections":"1.0"}""").maxConnections)
        }

        @Test
        fun `reads a long sent as a decimal string`() {
            assertEquals(
                1_700_000_000L,
                json.decodeFromString<UserInfo>("""{"exp_date":"1700000000.0"}""").expiryEpochSeconds,
            )
        }

        @Test
        fun `blank and whitespace-only scalars become null`() {
            assertNull(json.decodeFromString<UserInfo>("""{"username":""}""").username)
            assertNull(json.decodeFromString<UserInfo>("""{"username":"   "}""").username)
        }

        @Test
        fun `an object where a scalar belongs degrades that field only`() {
            val user = json.decodeFromString<UserInfo>(
                """{"username":{"unexpected":"object"},"status":"Active"}""",
            )

            assertNull(user.username)
            assertEquals("Active", user.status)
        }

        @Test
        fun `an array where a scalar belongs degrades that field only`() {
            val user = json.decodeFromString<UserInfo>("""{"username":[1,2],"status":"Active"}""")

            assertNull(user.username)
            assertEquals("Active", user.status)
        }

        @Test
        fun `explicit null is null`() {
            assertNull(json.decodeFromString<UserInfo>("""{"username":null}""").username)
        }

        @Test
        fun `accepts the several spellings of true`() {
            assertEquals(true, authFlag("1"))
            assertEquals(true, authFlag("\"true\""))
            assertEquals(true, authFlag("\"yes\""))
            assertEquals(true, authFlag("\"Active\""))
            assertEquals(true, authFlag("true"))
        }

        @Test
        fun `accepts the several spellings of false`() {
            assertEquals(false, authFlag("0"))
            assertEquals(false, authFlag("\"false\""))
            assertEquals(false, authFlag("\"no\""))
            assertEquals(false, authFlag("false"))
        }

        @Test
        fun `an unrecognised boolean is null rather than false`() {
            // The distinction matters: "the panel did not say" must not be read as "the
            // panel said no", or an unauthenticated account looks the same as a rejected one.
            assertNull(authFlag("\"maybe\""))
            assertNull(authFlag("{}"))
        }

        private fun authFlag(raw: String) =
            json.decodeFromString<UserInfo>("""{"auth":$raw}""").auth
    }

    @Nested
    @DisplayName("EPG listings")
    inner class Epg {

        @Test
        fun `parses a listing`() {
            val parsed = json.decodeFromString<EpgResponse>(
                """
                {
                  "epg_listings": [
                    {
                      "id": "1",
                      "epg_id": "channel.one",
                      "title": "VGl0bGU=",
                      "description": "RGVzYw==",
                      "start_timestamp": "1700000000",
                      "stop_timestamp": 1700003600
                    }
                  ]
                }
                """.trimIndent(),
            )

            val listing = parsed.listings.single()
            assertEquals("1", listing.id)
            assertEquals("channel.one", listing.epgId)
            // Kept as sent. Base64 decoding is the caller's job, not the DTO's.
            assertEquals("VGl0bGU=", listing.title)
            assertEquals("RGVzYw==", listing.description)
            assertEquals(1_700_000_000L, listing.startEpochSeconds)
            assertEquals(1_700_003_600L, listing.stopEpochSeconds)
        }

        @Test
        fun `a missing listings key yields an empty list`() {
            assertTrue(json.decodeFromString<EpgResponse>("{}").listings.isEmpty())
        }

        @Test
        fun `a listing missing its timestamps still parses`() {
            // A programme with no times is useless to the guide, but it must not take the
            // rest of the channel's listings down with it.
            val parsed = json.decodeFromString<EpgResponse>(
                """{"epg_listings":[{"title":"VGl0bGU="},{"title":"T3RoZXI=","start_timestamp":"1700000000"}]}""",
            )

            assertEquals(2, parsed.listings.size)
            assertNull(parsed.listings[0].startEpochSeconds)
            assertEquals(1_700_000_000L, parsed.listings[1].startEpochSeconds)
        }

        @Test
        fun `a non-numeric timestamp becomes null`() {
            val parsed = json.decodeFromString<EpgResponse>(
                """{"epg_listings":[{"start_timestamp":"not a time"}]}""",
            )

            assertNull(parsed.listings.single().startEpochSeconds)
        }
    }

    @Nested
    @DisplayName("catalogue entries")
    inner class Catalogue {

        @Test
        fun `parses a category`() {
            val parsed = json.decodeFromString<CategoryDto>(
                """{"category_id":7,"category_name":"News"}""",
            )

            assertEquals("7", parsed.categoryId)
            assertEquals("News", parsed.categoryName)
        }

        @Test
        fun `parses a live stream`() {
            val parsed = json.decodeFromString<LiveStreamDto>(
                """{"stream_id":"42","name":"Channel","stream_icon":"logo.invalid/a.png","category_id":7}""",
            )

            assertEquals("42", parsed.streamId)
            assertEquals("Channel", parsed.name)
        }

        @Test
        fun `parses a vod stream`() {
            val parsed = json.decodeFromString<VodStreamDto>(
                """{"stream_id":9,"name":"Film","container_extension":"mkv"}""",
            )

            assertEquals("9", parsed.streamId)
            assertEquals("Film", parsed.name)
        }

        @Test
        fun `parses episode info`() {
            val parsed = json.decodeFromString<EpisodeDto>(
                """{"id":"5","title":"Ep","info":{"movie_image":"still.invalid/a.png"}}""",
            )

            assertEquals("5", parsed.id)
            assertEquals("still.invalid/a.png", parsed.info?.movieImage)
        }

        @Test
        fun `an episode without an info block parses`() {
            assertNull(json.decodeFromString<EpisodeDto>("""{"id":"5"}""").info)
        }
    }

    @Nested
    @DisplayName("film details")
    inner class VodInfo {

        @Test
        fun `parses plot, artwork and metadata`() {
            val parsed = json.decodeFromString<VodInfoResponse>(
                """
                {
                  "info": {
                    "plot": "A chemistry teacher turns to crime.",
                    "cover_big": "http://art.invalid/big.jpg",
                    "movie_image": "http://art.invalid/small.jpg",
                    "genre": "Drama",
                    "rating": "8.5",
                    "releasedate": "2008-01-20",
                    "duration_secs": "3600"
                  },
                  "movie_data": {"stream_id": 42, "name": "Breaking Bad: The Movie"}
                }
                """.trimIndent(),
            )

            assertEquals("A chemistry teacher turns to crime.", parsed.info?.effectivePlot)
            // cover_big wins: it is the larger of the two and this is a detail screen.
            assertEquals("http://art.invalid/big.jpg", parsed.info?.effectiveCover)
            assertEquals("2008-01-20", parsed.info?.effectiveReleaseDate)
            assertEquals(3600, parsed.info?.durationSeconds)
            assertEquals("42", parsed.movieData?.streamId)
            assertEquals("Breaking Bad: The Movie", parsed.movieData?.name)
        }

        @Test
        fun `falls back from plot to description and from cover to movie_image`() {
            val parsed = json.decodeFromString<VodInfoResponse>(
                """{"info":{"description":"Only a description.","movie_image":"http://art.invalid/s.jpg"}}""",
            )

            assertEquals("Only a description.", parsed.info?.effectivePlot)
            assertEquals("http://art.invalid/s.jpg", parsed.info?.effectiveCover)
        }

        @Test
        fun `prefers a populated plot over a blank one`() {
            val parsed = json.decodeFromString<VodInfoResponse>(
                """{"info":{"plot":"   ","description":"The real one."}}""",
            )

            assertEquals("The real one.", parsed.info?.effectivePlot)
        }

        @Test
        fun `reads the alternate release_date spelling`() {
            val parsed = json.decodeFromString<VodInfoResponse>(
                """{"info":{"release_date":"1999-03-31"}}""",
            )

            assertEquals("1999-03-31", parsed.info?.effectiveReleaseDate)
        }

        @Test
        fun `a film with no details at all still parses`() {
            val parsed = json.decodeFromString<VodInfoResponse>("{}")

            assertNull(parsed.info)
            assertNull(parsed.movieData)
        }

        @Test
        fun `an empty info block yields nulls rather than blanks`() {
            val parsed = json.decodeFromString<VodInfoResponse>("""{"info":{}}""")

            assertNull(parsed.info?.effectivePlot)
            assertNull(parsed.info?.effectiveCover)
            assertNull(parsed.info?.effectiveReleaseDate)
        }
    }

    /**
     * Regression cover for 916f271. Panels disagree on which key carries a series' id,
     * name and artwork, and picking the wrong one silently produced an unopenable series.
     */
    @Nested
    @DisplayName("series key fallbacks")
    inner class Series {

        @Test
        fun `prefers series_id, then id, then stream_id`() {
            assertEquals("a", seriesFrom("""{"series_id":"a","id":"b","stream_id":"c"}""").effectiveId)
            assertEquals("b", seriesFrom("""{"id":"b","stream_id":"c"}""").effectiveId)
            assertEquals("c", seriesFrom("""{"stream_id":"c"}""").effectiveId)
        }

        @Test
        fun `skips a blank id rather than returning it`() {
            assertEquals("b", seriesFrom("""{"series_id":"   ","id":"b"}""").effectiveId)
        }

        @Test
        fun `falls back from name to title`() {
            assertEquals("N", seriesFrom("""{"name":"N","title":"T"}""").effectiveName)
            assertEquals("T", seriesFrom("""{"title":"T"}""").effectiveName)
        }

        @Test
        fun `falls back from cover to stream_icon`() {
            assertEquals("c.png", seriesFrom("""{"cover":"c.png","stream_icon":"s.png"}""").effectiveCover)
            assertEquals("s.png", seriesFrom("""{"stream_icon":"s.png"}""").effectiveCover)
        }

        @Test
        fun `an entry carrying none of the keys yields nulls`() {
            val series = seriesFrom("""{"category_id":"1"}""")

            assertNull(series.effectiveId)
            assertNull(series.effectiveName)
            assertNull(series.effectiveCover)
        }

        private fun seriesFrom(raw: String) = json.decodeFromString<SeriesDto>(raw)
    }

    @Nested
    @DisplayName("vod subtitles")
    inner class Subtitles {

        private fun parse(field: String): List<XtreamSubtitle> =
            json.decodeFromString<VodInfoResponse>("""{"info":{"subtitles":$field}}""")
                .info
                ?.subtitles
                .orEmpty()

        @Test
        fun `absent, null and empty all mean no subtitles`() {
            assertTrue(json.decodeFromString<VodInfoResponse>("""{"info":{}}""").info?.subtitles.orEmpty().isEmpty())
            assertTrue(parse("null").isEmpty())
            assertTrue(parse("[]").isEmpty())
        }

        @Test
        fun `an array of bare urls is read`() {
            val parsed = parse("""["http://panel.invalid/sub/1.srt", "  ", ""]""")

            assertEquals(1, parsed.size)
            assertEquals("http://panel.invalid/sub/1.srt", parsed.first().url)
            assertNull(parsed.first().language)
        }

        @Test
        fun `the url key differs between panels and all of them are read`() {
            val parsed = parse(
                """
                [
                  {"url": "a.srt"},
                  {"link": "b.srt"},
                  {"file": "c.srt"},
                  {"src": "d.srt"}
                ]
                """.trimIndent(),
            )

            assertEquals(listOf("a.srt", "b.srt", "c.srt", "d.srt"), parsed.map { it.url })
        }

        @Test
        fun `language and label are taken where the panel supplies them`() {
            val parsed = parse("""[{"url": "a.srt", "lang": "Arabic", "title": "Arabic (full)"}]""")

            assertEquals("Arabic", parsed.first().language)
            assertEquals("Arabic (full)", parsed.first().label)
        }

        @Test
        fun `an entry with no url costs that entry and nothing else`() {
            // The rule the whole DTO layer follows: one odd field never loses the response.
            val parsed = parse("""[{"lang": "Arabic"}, {"url": "b.srt"}, 42]""")

            assertEquals(listOf("b.srt"), parsed.map { it.url })
        }

        @Test
        fun `an object where an array belongs is treated as no subtitles`() {
            assertTrue(parse("""{"0": {"url": "a.srt"}}""").isEmpty())
        }
    }
}
