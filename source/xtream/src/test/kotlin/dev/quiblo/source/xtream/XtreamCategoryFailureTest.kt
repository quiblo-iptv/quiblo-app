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

import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.Credentials
import dev.quiblo.source.api.PanelBlockStore
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * What a panel that will not say how its catalogue is grouped costs — `BUG-033`.
 *
 * A category list that failed used to be swallowed as an optional loss: every stream was stored
 * as `__ungrouped__`, the load still reported success, and the scheduled sync wrote that over a
 * catalogue that had been grouped correctly. Categories are `channels.groupTitle` and nothing
 * else, so there was nothing left to recover from — a household saw everything it owned in one
 * heap until somebody refreshed by hand.
 *
 * **These assert on the load's outcome, not on `groupTitle`.** An assertion about the grouping
 * could be satisfied by a mapper that guesses a name; only refusing to report success protects
 * what is already stored, because `SourceRepository.store` writes nothing on a failure.
 *
 * In its own class rather than in `XtreamSourceTest`, which is already at the size detekt will
 * accept. Responses here are synthetic (AC-LEGAL-04).
 */
class XtreamCategoryFailureTest {

    private val credentials = Credentials("user", "pass")

    private class FakeStore(private val value: Credentials?) : CredentialStore {
        override suspend fun credentials(sourceId: Long) = value
        override suspend fun put(sourceId: Long, credentials: Credentials) = Unit
        override suspend fun clear(sourceId: Long) = Unit
    }

    private class FakeBlockStore : PanelBlockStore {
        private var blockedUntil = 0L

        override suspend fun blockedUntil(): Long = blockedUntil

        override suspend fun setBlockedUntil(epochMillis: Long) {
            blockedUntil = epochMillis
        }
    }

    /** Everything answers from [bodies], except [failing], which the panel refuses with [status]. */
    private fun sourceRefusing(
        failing: String,
        bodies: Map<String?, String>,
        status: HttpStatusCode = HttpStatusCode.BadGateway,
    ) = createXtreamSource(
        HttpClient(
            MockEngine { request ->
                val action = request.url.parameters["action"]
                val refused = action == failing
                respond(
                    content = if (refused) "" else bodies[action] ?: "[]",
                    status = if (refused) status else HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )
            },
        ),
        FakeStore(credentials),
        FakeBlockStore(),
    )

    private val request = SourceRequest(sourceId = 5L, location = "panel.example.invalid:8080")

    private val authOk = """{"user_info":{"username":"user","auth":1,"status":"Active","exp_date":"1900000000"}}"""

    private val liveStreams = """[{"num":1,"stream_id":101,"name":"Alpha","category_id":"1"}]"""

    private val liveCategories = """[{"category_id":"1","category_name":"News"}]"""

    @Test
    @DisplayName("BUG-033 — live categories that fail take the whole load with them")
    fun `a failed live category list fails the load`() = runTest {
        val result = sourceRefusing(
            failing = "get_live_categories",
            bodies = mapOf(null to authOk, "get_live_streams" to liveStreams),
        ).load(request)

        assertInstanceOf(SourceResult.Failure::class.java, result)
    }

    @Test
    @DisplayName("BUG-033 — film categories that fail take the whole load with them")
    fun `a failed film category list fails the load`() = runTest {
        val result = sourceRefusing(
            failing = "get_vod_categories",
            bodies = mapOf(
                null to authOk,
                "get_live_streams" to liveStreams,
                "get_live_categories" to liveCategories,
                "get_vod_streams" to """[{"stream_id":1,"name":"A Film","category_id":"9"}]""",
            ),
        ).load(request)

        assertInstanceOf(SourceResult.Failure::class.java, result)
    }

    @Test
    @DisplayName("BUG-033 — an account with no films is not broken by its film category endpoint")
    fun `an empty film list needs no categories`() = runTest {
        // A live-only account answers both film endpoints unhelpfully and is perfectly healthy.
        // Only a list that arrived and cannot be grouped is a broken load. Without this case the
        // fix could be tightened into rejecting those accounts and no test would notice.
        val result = sourceRefusing(
            failing = "get_vod_categories",
            bodies = mapOf(
                null to authOk,
                "get_live_streams" to liveStreams,
                "get_live_categories" to liveCategories,
                "get_vod_streams" to "[]",
            ),
        ).load(request)

        val success = assertInstanceOf(SourceResult.Success::class.java, result)
        assertEquals("News", success.channels.single().groupTitle)
    }
}
