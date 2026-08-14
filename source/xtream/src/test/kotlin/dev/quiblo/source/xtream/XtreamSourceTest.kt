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

package dev.quiblo.source.xtream

import dev.quiblo.core.model.Category
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.Credentials
import dev.quiblo.source.api.GuideResult
import dev.quiblo.source.api.PanelBlockStore
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * All responses here are synthetic and modelled on the quirks documented in
 * docs/PLAN.md §5 — never captured from a real panel (AC-LEGAL-04).
 */
class XtreamSourceTest {

    private val credentials = Credentials("user", "pass")

    private class FakeStore(private val value: Credentials?) : CredentialStore {
        override suspend fun credentials(sourceId: Long) = value
        override suspend fun put(sourceId: Long, credentials: Credentials) = Unit
        override suspend fun clear(sourceId: Long) = Unit
    }

    /** Counts how many HTTP calls actually left the client. */
    private var requestCount = 0

    /** Routes each `action` to a canned body. */
    private fun sourceServing(
        bodies: Map<String?, String>,
        status: HttpStatusCode = HttpStatusCode.OK,
        store: CredentialStore = FakeStore(credentials),
        blockStore: PanelBlockStore = FakeBlockStore(),
    ) = createXtreamSource(
        HttpClient(
            MockEngine { request ->
                requestCount++
                val action = request.url.parameters["action"]
                respond(
                    content = bodies[action] ?: "[]",
                    status = status,
                    headers = headersOf("Content-Type", "application/json"),
                )
            },
        ),
        store,
        blockStore,
    )

    /** No block recorded, and nothing written anywhere a later test could read it. */
    private class FakeBlockStore : PanelBlockStore {
        private var blockedUntil = 0L

        override suspend fun blockedUntil(): Long = blockedUntil

        override suspend fun setBlockedUntil(epochMillis: Long) {
            blockedUntil = epochMillis
        }
    }

    private val request = SourceRequest(sourceId = 5L, location = "panel.example.invalid:8080")

    private val authOk = """{"user_info":{"username":"user","auth":1,"status":"Active","exp_date":"1900000000"}}"""

    // Deliberately inconsistent: numeric stream_id, string category_id, missing fields.
    private val liveStreams = """
        [
          {"num":1,"stream_id":101,"name":"Alpha","stream_icon":"http://logos.example.invalid/a.png",
           "epg_channel_id":"alpha.epg","category_id":"1"},
          {"num":"2","stream_id":"102","name":"Beta","category_id":2},
          {"num":3,"stream_id":null,"name":"No Id","category_id":"1"},
          {"num":4,"stream_id":104,"name":"","category_id":"1"}
        ]
    """.trimIndent()

    private val liveCategories = """
        [{"category_id":"1","category_name":"News"},{"category_id":"2","category_name":"Sports"}]
    """.trimIndent()

    @Test
    @DisplayName("AC-XT-01 — categories and streams populate after authentication")
    fun `loads live streams with categories`() = runTest {
        val result = sourceServing(
            mapOf(
                null to authOk,
                "get_live_categories" to liveCategories,
                "get_live_streams" to liveStreams,
            ),
        ).load(request)

        val success = assertInstanceOf(SourceResult.Success::class.java, result)
        assertEquals(2, success.channels.size)
        assertEquals(2, success.report.skippedEntries)

        val alpha = success.channels.first()
        assertEquals("Alpha", alpha.name)
        assertEquals("News", alpha.groupTitle)
        assertEquals("alpha.epg", alpha.tvgId)
        assertEquals(MediaKind.LIVE, alpha.kind)
        assertEquals("http://panel.example.invalid:8080/live/user/pass/101.ts", alpha.streamUrl)
    }

    @Test
    @DisplayName("AC-XT-06 — string and numeric forms of the same field both parse")
    fun `tolerates mistyped fields`() = runTest {
        val result = sourceServing(
            mapOf(
                null to authOk,
                "get_live_categories" to liveCategories,
                "get_live_streams" to liveStreams,
            ),
        ).load(request)

        val channels = assertInstanceOf(SourceResult.Success::class.java, result).channels
        // stream_id 101 arrived as a number, 102 as a string; both must resolve.
        assertTrue(channels.any { it.streamUrl.endsWith("/101.ts") })
        assertTrue(channels.any { it.streamUrl.endsWith("/102.ts") })
        // category_id "1" as string and 2 as number both resolve to their names.
        assertEquals(setOf("News", "Sports"), channels.map { it.groupTitle }.toSet())
    }

    @Test
    fun `entries with no category fall back to the ungrouped bucket`() = runTest {
        val result = sourceServing(
            mapOf(
                null to authOk,
                "get_live_categories" to "[]",
                "get_live_streams" to """[{"stream_id":1,"name":"Orphan"}]""",
            ),
        ).load(request)

        val channels = assertInstanceOf(SourceResult.Success::class.java, result).channels
        assertEquals(Category.UNGROUPED_TITLE, channels.single().groupTitle)
    }

    @Test
    @DisplayName("AC-XT-02 — rejected credentials are distinguished from a network error")
    fun `reports an auth failure when the panel denies access`() = runTest {
        val result = sourceServing(mapOf(null to """{"user_info":{"auth":0}}""")).load(request)
        assertEquals(SourceError.Unauthorized, (result as SourceResult.Failure).error)
    }

    @Test
    fun `reports an auth failure on a 401`() = runTest {
        val result = sourceServing(mapOf(null to "{}"), status = HttpStatusCode.Unauthorized).load(request)
        assertEquals(SourceError.Unauthorized, (result as SourceResult.Failure).error)
    }

    @Test
    fun `reports an auth failure when no credentials are stored`() = runTest {
        val result = sourceServing(mapOf(null to authOk), store = FakeStore(null)).load(request)
        assertEquals(SourceError.Unauthorized, (result as SourceResult.Failure).error)
    }

    @Test
    @DisplayName("AC-XT-05 — an expired subscription is surfaced as itself")
    fun `reports an expired subscription`() = runTest {
        val expired = """{"user_info":{"auth":1,"status":"Expired","exp_date":"1000000000"}}"""
        val result = sourceServing(mapOf(null to expired)).load(request)
        assertEquals(SourceError.SubscriptionExpired, (result as SourceResult.Failure).error)
    }

    @Test
    fun `reports a banned account distinctly from an expired one`() = runTest {
        val banned = """{"user_info":{"auth":"true","status":"Banned"}}"""
        val result = sourceServing(mapOf(null to banned)).load(request)
        assertEquals(SourceError.AccountDisabled, (result as SourceResult.Failure).error)
    }

    @Test
    fun `treats an HTML response as not a playlist`() = runTest {
        val result = sourceServing(mapOf(null to "<!DOCTYPE html><html><body>login</body></html>")).load(request)
        assertEquals(SourceError.NotAPlaylist, (result as SourceResult.Failure).error)
    }

    @Test
    fun `rejects a base url that cannot be understood`() = runTest {
        val result = sourceServing(mapOf(null to authOk))
            .load(SourceRequest(1L, "not a host"))
        assertEquals(SourceError.UnreachableHost, (result as SourceResult.Failure).error)
    }

    @Test
    fun `a panel with no content at all reports empty rather than success`() = runTest {
        val result = sourceServing(mapOf(null to authOk)).load(request)
        assertEquals(SourceError.EmptyPlaylist, (result as SourceResult.Failure).error)
    }

    @Test
    fun `vod and series are collected alongside live`() = runTest {
        val result = sourceServing(
            mapOf(
                null to authOk,
                "get_live_categories" to liveCategories,
                "get_live_streams" to """[{"stream_id":1,"name":"Live One","category_id":"1"}]""",
                "get_vod_streams" to """[{"stream_id":"7","name":"A Movie","container_extension":"mkv"}]""",
                "get_series" to """[{"series_id":9,"name":"A Series"}]""",
            ),
        ).load(request)

        val channels = assertInstanceOf(SourceResult.Success::class.java, result).channels
        assertEquals(1, channels.count { it.kind == MediaKind.LIVE })
        assertEquals(1, channels.count { it.kind == MediaKind.VOD })
        assertEquals(1, channels.count { it.kind == MediaKind.SERIES })
        assertTrue(channels.first { it.kind == MediaKind.VOD }.streamUrl.endsWith("/movie/user/pass/7.mkv"))
    }

    @Test
    fun `films and series carry the date the panel added them, in millis`() = runTest {
        val result = sourceServing(
            mapOf(
                null to authOk,
                "get_live_streams" to "[]",
                "get_vod_streams" to """[{"stream_id":"7","name":"A Movie","added":"1700000000"}]""",
                "get_series" to """[{"series_id":9,"name":"A Series","last_modified":1700003600}]""",
            ),
        ).load(request)

        val channels = assertInstanceOf(SourceResult.Success::class.java, result).channels
        assertEquals(1_700_000_000_000L, channels.first { it.kind == MediaKind.VOD }.addedAtEpochMillis)
        assertEquals(1_700_003_600_000L, channels.first { it.kind == MediaKind.SERIES }.addedAtEpochMillis)
    }

    @Test
    fun `a panel that gives no date leaves it null rather than 1970`() = runTest {
        val result = sourceServing(
            mapOf(
                null to authOk,
                "get_live_streams" to "[]",
                // No field at all, and the zero a panel sends when it has nothing to say. Both
                // must reach the catalogue as "unknown": a zero would sort as a real date and
                // pin these titles to the end of the newest-first row instead of leaving them
                // out of it.
                "get_vod_streams" to """[{"stream_id":"7","name":"A Movie"}]""",
                "get_series" to """[{"series_id":9,"name":"A Series","added":"0"}]""",
            ),
        ).load(request)

        val channels = assertInstanceOf(SourceResult.Success::class.java, result).channels
        assertNull(channels.first { it.kind == MediaKind.VOD }.addedAtEpochMillis)
        assertNull(channels.first { it.kind == MediaKind.SERIES }.addedAtEpochMillis)
    }

    @Test
    @DisplayName("AC-XT-04 — credentials never appear in an error value")
    fun `errors carry no credential material`() = runTest {
        val result = sourceServing(mapOf(null to "{ this is not json"), status = HttpStatusCode.OK).load(request)
        val error = (result as SourceResult.Failure).error
        val rendered = error.toString()
        assertFalse(rendered.contains("pass"), "A password reached an error value: $rendered")
        assertFalse(rendered.contains("user"), "A username reached an error value: $rendered")
    }

    @Test
    fun `credentials never render themselves`() {
        val rendered = Credentials("alice", "hunter2").toString()
        assertFalse(rendered.contains("alice"))
        assertFalse(rendered.contains("hunter2"))
    }

    @Test
    fun `fetches series details and episode stream urls`() = runTest {
        val seriesInfoJson = """
            {
              "seasons": [{"season_number": 1, "name": "Season 1"}],
              "info": {"name": "Breaking Bad", "plot": "A chemistry teacher..."},
              "episodes": {
                "1": [
                  {"id": "501", "episode_num": 1, "title": "Pilot", "container_extension": "mp4"}
                ]
              }
            }
        """.trimIndent()

        val source = sourceServing(
            mapOf("get_series_info" to seriesInfoJson),
        )

        val result = source.seriesDetails(request, seriesId = "99")
        val details = assertInstanceOf(dev.quiblo.source.api.SeriesDetailsResult.Success::class.java, result).details
        assertEquals("Breaking Bad", details.title)
        assertEquals("A chemistry teacher...", details.overview)
        assertEquals(1, details.seasons.size)
        assertEquals("Season 1", details.seasons[0].name)

        val episode = details.seasons[0].episodes[0]
        assertEquals("501", episode.id)
        assertEquals("Pilot", episode.title)
        assertEquals(1, episode.episodeNumber)
        assertTrue(episode.streamUrl.endsWith("/series/user/pass/501.mp4"))
    }

    @Test
    fun `series with alternate property keys are mapped defensively`() = runTest {
        val seriesJson = """
            [
              {"id": "77", "title": "Game of Thrones", "stream_icon": "http://img.png", "category_id": "5"}
            ]
        """.trimIndent()

        val source = sourceServing(
            mapOf(null to authOk, "get_series" to seriesJson, "get_series_categories" to "[]"),
        )

        val result = source.load(request)
        val success = assertInstanceOf(SourceResult.Success::class.java, result)
        val channels = success.channels
        assertEquals(1, channels.size)
        assertEquals("Game of Thrones", channels[0].name)
        assertEquals("77", channels[0].providerStreamId)
        assertEquals("http://img.png", channels[0].logoUrl)
    }

    @Test
    fun `series details handles empty array for episodes gracefully`() = runTest {
        val seriesInfoJson = """
            {
              "seasons": [],
              "info": {"name": "Empty Series"},
              "episodes": []
            }
        """.trimIndent()

        val source = sourceServing(
            mapOf("get_series_info" to seriesInfoJson),
        )

        val result = source.seriesDetails(request, seriesId = "10")
        val details = assertInstanceOf(dev.quiblo.source.api.SeriesDetailsResult.Success::class.java, result).details
        assertEquals("Empty Series", details.title)
        assertTrue(details.seasons.isEmpty())
    }

    /**
     * A panel firewall answering 469 is not a generic HTTP failure. Reported as one, it
     * reaches the user as "the server responded with an error (469)", which tells them
     * nothing they can act on, and nothing in the app knows to stop retrying.
     */
    @Test
    @DisplayName("AC-PL-07 — a panel firewall status maps to ProviderBlocked, not a raw status")
    fun `series details reports a firewall status as provider blocked`() = runTest {
        val source = sourceServing(
            bodies = emptyMap(),
            status = HttpStatusCode(469, "Blocked"),
        )

        val result = source.seriesDetails(request, seriesId = "10")

        val failure = assertInstanceOf(dev.quiblo.source.api.SeriesDetailsResult.Failure::class.java, result)
        assertEquals(SourceError.ProviderBlocked, failure.error)
    }

    @Test
    @DisplayName("AC-PL-07 — a refresh blocked by the panel firewall is distinguishable too")
    fun `load reports a firewall status as provider blocked`() = runTest {
        val result = sourceServing(
            bodies = mapOf(null to authOk),
            status = HttpStatusCode(462, "Blocked"),
        ).load(request)

        val failure = assertInstanceOf(SourceResult.Failure::class.java, result)
        assertEquals(SourceError.ProviderBlocked, failure.error)
    }

    // --- Guide (AC-EPG-*) ---------------------------------------------------------------

    private fun guideServing(body: String) = sourceServing(
        mapOf(null to authOk, "get_short_epg" to body),
    )

    /** "Morning News" / "Weather" / "The Late Show", base64 as panels send them. */
    private val epgListings = """
        {"epg_listings":[
          {"id":"1","title":"TW9ybmluZyBOZXdz","description":"SGVhZGxpbmVz",
           "start_timestamp":"1700000000","stop_timestamp":"1700003600"},
          {"id":"2","title":"V2VhdGhlcg==","start_timestamp":1700003600,"stop_timestamp":1700005400}
        ]}
    """.trimIndent()

    @Test
    @DisplayName("AC-EPG-01 — listings map to programmes with decoded titles")
    fun `guide returns decoded programmes`() = runTest {
        val result = guideServing(epgListings).guideFor(request, channelKey = "ch-1", providerStreamId = "101")

        val success = assertInstanceOf(GuideResult.Success::class.java, result)
        assertEquals(2, success.programmes.size)

        val first = success.programmes.first()
        assertEquals("Morning News", first.title)
        assertEquals("Headlines", first.description)
        assertEquals("ch-1", first.channelKey)
        assertEquals(5L, first.sourceId)
        // Seconds in, milliseconds out.
        assertEquals(1_700_000_000_000L, first.startEpochMillis)
        assertEquals(1_700_003_600_000L, first.endEpochMillis)
        assertNull(success.programmes[1].description)
    }

    @Test
    @DisplayName("a title that is not base64 is left alone")
    fun `guide keeps plain text titles`() = runTest {
        // Decoding this blindly would yield mojibake rather than a title.
        val result = guideServing(
            """{"epg_listings":[{"title":"Live Football","start_timestamp":1,"stop_timestamp":2}]}""",
        ).guideFor(request, channelKey = "ch-1", providerStreamId = "101")

        val success = assertInstanceOf(GuideResult.Success::class.java, result)
        assertEquals("Live Football", success.programmes.single().title)
    }

    @Test
    @DisplayName("AC-EPG-04 — unusable listings are dropped, not rendered broken")
    fun `guide drops listings it cannot render`() = runTest {
        val result = guideServing(
            """
            {"epg_listings":[
              {"title":"VGl0bGU=","stop_timestamp":2},
              {"title":"VGl0bGU=","start_timestamp":1},
              {"title":"VGl0bGU=","start_timestamp":5,"stop_timestamp":5},
              {"title":"VGl0bGU=","start_timestamp":9,"stop_timestamp":4},
              {"start_timestamp":1,"stop_timestamp":2},
              {"title":"","start_timestamp":1,"stop_timestamp":2},
              {"title":"S2VwdA==","start_timestamp":1,"stop_timestamp":2}
            ]}
            """.trimIndent(),
        ).guideFor(request, channelKey = "ch-1", providerStreamId = "101")

        val success = assertInstanceOf(GuideResult.Success::class.java, result)
        assertEquals("Kept", success.programmes.single().title)
    }

    @Test
    fun `guide returns an empty list when the panel has no data for the channel`() = runTest {
        val result = guideServing("""{"epg_listings":[]}""")
            .guideFor(request, channelKey = "ch-1", providerStreamId = "101")

        val success = assertInstanceOf(GuideResult.Success::class.java, result)
        assertTrue(success.programmes.isEmpty())
    }

    @Test
    fun `guide reports missing credentials rather than calling the panel`() = runTest {
        val source = sourceServing(mapOf(null to authOk), store = FakeStore(null))

        val result = source.guideFor(request, channelKey = "ch-1", providerStreamId = "101")

        val failure = assertInstanceOf(GuideResult.Failure::class.java, result)
        assertEquals(SourceError.Unauthorized, failure.error)
    }

    @Test
    fun `guide reports an unusable location as an unreachable host`() = runTest {
        val result = guideServing(epgListings).guideFor(
            SourceRequest(sourceId = 5L, location = "   "),
            channelKey = "ch-1",
            providerStreamId = "101",
        )

        val failure = assertInstanceOf(GuideResult.Failure::class.java, result)
        assertEquals(SourceError.UnreachableHost, failure.error)
    }

    @Test
    @DisplayName("a panel block surfaces on the guide path too")
    fun `guide reports provider blocked`() = runTest {
        val result = sourceServing(
            bodies = mapOf(null to authOk),
            status = HttpStatusCode(462, "Blocked"),
        ).guideFor(request, channelKey = "ch-1", providerStreamId = "101")

        val failure = assertInstanceOf(GuideResult.Failure::class.java, result)
        assertEquals(SourceError.ProviderBlocked, failure.error)
    }

    // --- Category ordering ---------------------------------------------------------------

    @Test
    @DisplayName("category order comes from the panel's category list, not from the streams")
    fun `films carry the provider category order`() = runTest {
        // The panel lists categories deliberately — Action, then Drama — but returns the
        // streams in an unrelated order. Inferring category order from the streams gets
        // this backwards, which is exactly what it did.
        val result = sourceServing(
            mapOf(
                null to authOk,
                "get_vod_categories" to
                    """[{"category_id":"9","category_name":"Action"},""" +
                    """{"category_id":"4","category_name":"Drama"}]""",
                "get_vod_streams" to
                    """[{"stream_id":1,"name":"A Drama","category_id":"4"},""" +
                    """{"stream_id":2,"name":"An Action","category_id":"9"}]""",
                "get_live_streams" to "[]",
            ),
        ).load(request)

        val success = assertInstanceOf(SourceResult.Success::class.java, result)
        val byName = success.channels.associateBy { it.name }
        assertEquals(0, byName["An Action"]?.categoryIndex, "Action is first in the category list")
        assertEquals(1, byName["A Drama"]?.categoryIndex, "Drama is second")
    }

    @Test
    fun `a stream in no listed category has no category index`() = runTest {
        val result = sourceServing(
            mapOf(
                null to authOk,
                "get_vod_categories" to """[{"category_id":"9","category_name":"Action"}]""",
                "get_vod_streams" to """[{"stream_id":1,"name":"Orphan","category_id":"77"}]""",
                "get_live_streams" to "[]",
            ),
        ).load(request)

        val success = assertInstanceOf(SourceResult.Success::class.java, result)
        assertNull(success.channels.single { it.name == "Orphan" }.categoryIndex)
    }

    // --- Film details --------------------------------------------------------------------

    @Test
    fun `vodDetails maps a film`() = runTest {
        val source = sourceServing(
            mapOf(
                null to authOk,
                "get_vod_info" to """
                    {"info":{"plot":"A plot.","cover_big":"http://art.invalid/b.jpg","genre":"Drama",
                     "duration_secs":5400},"movie_data":{"stream_id":42,"name":"A Film"}}
                """.trimIndent(),
            ),
        )

        val result = source.vodDetails(request, vodId = "42")

        val success = assertInstanceOf(dev.quiblo.source.api.VodDetailsResult.Success::class.java, result)
        assertEquals("42", success.details.vodId)
        assertEquals("A Film", success.details.title)
        assertEquals("A plot.", success.details.overview)
        assertEquals("http://art.invalid/b.jpg", success.details.coverUrl)
        assertEquals(5400, success.details.durationSeconds)
    }

    @Test
    fun `vodDetails survives a panel that returns almost nothing`() = runTest {
        // A film with no metadata is a normal case, not a failure: the screen still has
        // the artwork and title it came in with.
        val source = sourceServing(mapOf(null to authOk, "get_vod_info" to "{}"))

        val result = source.vodDetails(request, vodId = "42")

        val success = assertInstanceOf(dev.quiblo.source.api.VodDetailsResult.Success::class.java, result)
        assertNull(success.details.overview)
        assertEquals("", success.details.title)
    }

    @Test
    fun `vodDetails reports missing credentials without calling the panel`() = runTest {
        val source = sourceServing(mapOf(null to authOk), store = FakeStore(null))

        val result = source.vodDetails(request, vodId = "42")

        val failure = assertInstanceOf(dev.quiblo.source.api.VodDetailsResult.Failure::class.java, result)
        assertEquals(SourceError.Unauthorized, failure.error)
    }

    // --- Anti-flood backoff --------------------------------------------------------------

    @Test
    @DisplayName("a block on one path stops every other path too")
    fun `blocking is remembered across call types`() = runTest {
        val source = sourceServing(
            bodies = mapOf(null to authOk),
            status = HttpStatusCode(462, "Blocked"),
        )

        // First call takes the block from the panel.
        assertEquals(
            SourceError.ProviderBlocked,
            assertInstanceOf(SourceResult.Failure::class.java, source.load(request)).error,
        )
        val afterFirst = requestCount

        // Everything else must now refuse locally. Continuing to ask a panel that is
        // already refusing is what turns a short block into a long one.
        assertEquals(
            SourceError.ProviderBlocked,
            assertInstanceOf(GuideResult.Failure::class.java, source.guideFor(request, "ch-1", "101")).error,
        )
        assertEquals(
            SourceError.ProviderBlocked,
            assertInstanceOf(
                dev.quiblo.source.api.SeriesDetailsResult.Failure::class.java,
                source.seriesDetails(request, "10"),
            ).error,
        )
        assertEquals(
            SourceError.ProviderBlocked,
            assertInstanceOf(
                dev.quiblo.source.api.VodDetailsResult.Failure::class.java,
                source.vodDetails(request, "20"),
            ).error,
        )
        assertEquals(
            SourceError.ProviderBlocked,
            assertInstanceOf(SourceResult.Failure::class.java, source.load(request)).error,
        )

        // The point of the whole exercise: not one further byte went to the panel.
        assertEquals(afterFirst, requestCount, "requests were still sent while blocked")
    }

    @Test
    @DisplayName("a refresh stops the moment the panel refuses, mid-catalogue")
    fun `a block during the catalogue walk ends the refresh`() = runTest {
        // Live answers; everything after it is refused. The refresh used to treat that as
        // "this account has no films or series" and carry on, spending four more requests
        // on a panel that was refusing precisely because it was being asked too often.
        var blockFrom = false
        val source = createXtreamSource(
            HttpClient(
                MockEngine { request ->
                    requestCount++
                    val action = request.url.parameters["action"]
                    if (action == "get_vod_streams") blockFrom = true
                    when {
                        blockFrom -> respond(
                            content = "",
                            status = HttpStatusCode(469, "Blocked"),
                            headers = headersOf("Content-Type", "application/json"),
                        )

                        else -> respond(
                            content = when (action) {
                                null -> authOk
                                "get_live_streams" -> liveStreams
                                "get_live_categories" -> liveCategories
                                else -> "[]"
                            },
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type", "application/json"),
                        )
                    }
                },
            ),
            FakeStore(credentials),
            FakeBlockStore(),
        )

        val result = assertInstanceOf(SourceResult.Failure::class.java, source.load(request))
        assertEquals(SourceError.ProviderBlocked, result.error)

        // auth, live streams, live categories, then the one refusal that stopped it.
        assertEquals(4, requestCount, "the refresh kept asking after being refused")
    }

    @Test
    @DisplayName("a block recorded before this launch is still in force")
    fun `a stored block survives a restart`() = runTest {
        // A user told their provider is refusing them force-stops the app and opens it
        // again. An in-memory-only backoff is cleared by exactly that, which turns the
        // most likely reaction to a block into the thing that extends it.
        val store = FakeBlockStore()
        store.setBlockedUntil(System.currentTimeMillis() + 60_000L)

        val source = sourceServing(bodies = mapOf(null to authOk), blockStore = store)

        assertEquals(
            SourceError.ProviderBlocked,
            assertInstanceOf(SourceResult.Failure::class.java, source.load(request)).error,
        )
        assertEquals(0, requestCount, "a fresh process ignored a block it had already been given")
    }

    @Test
    fun `an unblocked source still reaches the panel`() = runTest {
        val source = sourceServing(mapOf(null to authOk, "get_short_epg" to epgListings))

        source.guideFor(request, channelKey = "ch-1", providerStreamId = "101")

        assertTrue(requestCount > 0, "a healthy source must not be gated")
    }
}
