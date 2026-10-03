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

import dev.quiblo.source.api.AccountHealth
import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.Credentials
import dev.quiblo.source.api.PanelBlockStore
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

/**
 * What the panel says about an account when a stream has failed (`FEAT-035`).
 *
 * Every answer here is synthetic (AC-LEGAL-04). The clock is fixed so "expired" means the same
 * thing whenever this runs.
 */
class XtreamAccountHealthTest {

    private class FakeStore(private val value: Credentials?) : CredentialStore {
        override suspend fun credentials(sourceId: Long) = value
        override suspend fun put(sourceId: Long, credentials: Credentials) = Unit
        override suspend fun clear(sourceId: Long) = Unit
    }

    private class FakeBlockStore : PanelBlockStore {
        var blockedUntil = 0L

        override suspend fun blockedUntil(): Long = blockedUntil

        override suspend fun setBlockedUntil(epochMillis: Long) {
            blockedUntil = epochMillis
        }
    }

    private val request = SourceRequest(sourceId = 3L, location = "panel.example.invalid:8080")

    private fun source(
        body: String = "{}",
        status: HttpStatusCode = HttpStatusCode.OK,
        credentials: Credentials? = Credentials("user", "pass"),
        blockStore: FakeBlockStore = FakeBlockStore(),
        failWith: Throwable? = null,
    ) = XtreamSource(
        client = XtreamClient(
            HttpClient(
                MockEngine {
                    failWith?.let { throw it }
                    respond(body, status, headersOf("Content-Type", "application/json"))
                },
            ),
        ),
        credentialStore = FakeStore(credentials),
        blockStore = blockStore,
        now = { NOW },
    )

    /**
     * On a real dispatcher, because the check is bounded by a timeout and `runTest`'s virtual clock
     * would otherwise let that timeout fire before the mock engine has answered.
     */
    private suspend fun XtreamSource.health() = withContext(Dispatchers.Default) { accountHealth(request) }

    private fun user(fields: String) = """{"user_info":{$fields}}"""

    @Test
    fun `an active account reports its expiry and its screens`() = runTest {
        val health = source(
            user(""""auth":1,"status":"Active","exp_date":"$LATER","active_cons":"1","max_connections":"2""""),
        ).health()

        assertEquals(AccountHealth.Ok(LATER * 1000, activeConnections = 1, maxConnections = 2), health)
        assertFalse((health as AccountHealth.Ok).isAtConnectionLimit)
    }

    @Test
    fun `every screen in use is a connection limit`() = runTest {
        val health = source(
            user(""""auth":1,"status":"Active","active_cons":2,"max_connections":2"""),
        ).health()

        assertTrue((health as AccountHealth.Ok).isAtConnectionLimit)
    }

    @Test
    fun `missing connection figures are never read as a limit`() = runTest {
        val health = source(user(""""auth":1,"status":"Active"""")).health()

        assertEquals(AccountHealth.Ok(null, null, null), health)
        assertFalse((health as AccountHealth.Ok).isAtConnectionLimit)
    }

    @Test
    fun `an expiry date in the past is expired though the panel still says active`() = runTest {
        val health = source(user(""""auth":1,"status":"Active","exp_date":"$EARLIER"""")).health()

        assertEquals(AccountHealth.Expired(EARLIER * 1000), health)
    }

    @Test
    fun `auth 0 is a rejected password`() = runTest {
        assertEquals(
            AccountHealth.CredentialsRejected,
            source(user(""""auth":0""")).health(),
        )
    }

    @Test
    fun `a 401 is a rejected password`() = runTest {
        assertEquals(
            AccountHealth.CredentialsRejected,
            source(status = HttpStatusCode.Unauthorized).health(),
        )
    }

    @Test
    fun `a banned account is disabled`() = runTest {
        assertEquals(
            AccountHealth.Disabled,
            source(user(""""auth":1,"status":"Banned"""")).health(),
        )
    }

    @Test
    fun `a panel answering 503 has a server error`() = runTest {
        assertEquals(
            AccountHealth.ServerError(503),
            source(status = HttpStatusCode.ServiceUnavailable).health(),
        )
    }

    @Test
    fun `a panel that cannot be found is unreachable`() = runTest {
        assertEquals(
            AccountHealth.Unreachable,
            source(failWith = UnknownHostException("synthetic")).health(),
        )
    }

    @Test
    fun `a firewall refusal is a block, and starts the backoff`() = runTest {
        val blockStore = FakeBlockStore()
        val health = source(status = HttpStatusCode(462, "Blocked"), blockStore = blockStore).health()

        assertEquals(AccountHealth.Blocked, health)
        assertTrue(blockStore.blockedUntil > NOW)
    }

    @Test
    fun `a panel already in backoff is not asked at all`() = runTest {
        val blockStore = FakeBlockStore().apply { blockedUntil = NOW + 60_000L }

        assertEquals(AccountHealth.Blocked, source(blockStore = blockStore).health())
    }

    @Test
    fun `no stored credentials is no evidence, not a verdict`() = runTest {
        assertNull(source(credentials = null).health())
    }

    @Test
    fun `a refresh of an account past its expiry date reports the expiry`() = runTest {
        val result = source(user(""""auth":1,"status":"Active","exp_date":"$EARLIER"""")).load(request)

        assertEquals(SourceResult.Failure(SourceError.SubscriptionExpired), result)
    }

    private companion object {
        /** 2026-10-03, as epoch millis. */
        const val NOW = 1_791_000_000_000L

        /** Unix seconds a year either side of [NOW]. */
        const val EARLIER = 1_759_000_000L
        const val LATER = 1_823_000_000L
    }
}
