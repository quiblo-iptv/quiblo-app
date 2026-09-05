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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Not asking a panel questions it has already answered — `FEAT-031`.
 *
 * The catalogue sync moved from every four days to every four hours, which is six times the
 * traffic against a panel that answers "too often" with a block. **There is no endpoint that
 * says what is new**: `player_api.php` has a fixed set of actions and none of them takes a date.
 * What the stream lists do carry is a count and, on films and series, when the panel last added
 * to them — enough to tell "nothing has changed" from "something has", after four requests
 * rather than seven.
 *
 * So these are about *which requests are spent*, not only about what comes back. Responses are
 * synthetic (AC-LEGAL-04).
 */
class XtreamFingerprintTest {

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

    /** Every `action` this load asked for, in order. */
    private val asked = mutableListOf<String?>()

    private fun sourceServing(bodies: Map<String?, String>) = createXtreamSource(
        HttpClient(
            MockEngine { request ->
                val action = request.url.parameters["action"]
                asked += action
                respond(
                    content = bodies[action] ?: "[]",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )
            },
        ),
        FakeStore(credentials),
        FakeBlockStore(),
    )

    private val authOk = """{"user_info":{"username":"user","auth":1,"status":"Active","exp_date":"1900000000"}}"""

    private val account = mapOf(
        null to authOk,
        "get_live_streams" to """[{"stream_id":101,"name":"Alpha","category_id":"1"}]""",
        "get_live_categories" to """[{"category_id":"1","category_name":"News"}]""",
        "get_vod_streams" to """[{"stream_id":7,"name":"A Film","category_id":"9","added":"1700000000"}]""",
        "get_vod_categories" to """[{"category_id":"9","category_name":"Action"}]""",
        "get_series" to """[{"series_id":9,"name":"A Series","category_id":"3","last_modified":1700003600}]""",
        "get_series_categories" to """[{"category_id":"3","category_name":"Drama"}]""",
    )

    private val request = SourceRequest(sourceId = 5L, location = "panel.example.invalid:8080")

    @Test
    @DisplayName("FEAT-031 — a first load returns a fingerprint to compare against next time")
    fun `a load carries its own fingerprint`() = runTest {
        val result = sourceServing(account).load(request)

        val success = assertInstanceOf(SourceResult.Success::class.java, result)
        assertNotEquals(null, success.fingerprint)
        assertTrue(asked.contains("get_live_categories"), "a first load must fetch the grouping")
    }

    @Test
    @DisplayName("FEAT-031 — an account that has not moved costs four requests, not seven")
    fun `an unchanged account skips the grouping`() = runTest {
        val fingerprint = (sourceServing(account).load(request) as SourceResult.Success).fingerprint
        asked.clear()

        val again = sourceServing(account).load(request.copy(knownFingerprint = fingerprint))

        assertInstanceOf(SourceResult.Unchanged::class.java, again)
        // auth, live, films, series. The three category calls are the saving.
        assertEquals(listOf(null, "get_live_streams", "get_vod_streams", "get_series"), asked)
    }

    @Test
    @DisplayName("FEAT-031 — a film the panel has added is not mistaken for nothing")
    fun `a new film changes the fingerprint`() = runTest {
        val fingerprint = (sourceServing(account).load(request) as SourceResult.Success).fingerprint
        asked.clear()

        val grown = account + mapOf(
            "get_vod_streams" to """[{"stream_id":7,"name":"A Film","category_id":"9","added":"1700000000"},""" +
                """{"stream_id":8,"name":"A Newer Film","category_id":"9","added":"1800000000"}]""",
        )
        val again = sourceServing(grown).load(request.copy(knownFingerprint = fingerprint))

        assertInstanceOf(SourceResult.Success::class.java, again)
        assertTrue(asked.contains("get_vod_categories"), "a changed account must fetch the grouping")
    }

    /**
     * The manual path, which is also how a viewer fixes a catalogue that is wrong — see
     * `BUG-033` for how one got into that state. A refresh that decided there was nothing to do
     * could not fix it, so the repository passes no fingerprint on that path at all.
     */
    @Test
    @DisplayName("FEAT-031 — a refresh with no fingerprint always does the full load")
    fun `no known fingerprint means the long way round`() = runTest {
        sourceServing(account).load(request)
        asked.clear()

        val again = sourceServing(account).load(request)

        assertInstanceOf(SourceResult.Success::class.java, again)
        assertFalse(asked.isEmpty())
        assertTrue(asked.contains("get_live_categories"))
    }
}
