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

package dev.quiblo.core.data.diagnostics

import dev.quiblo.source.api.AccountHealth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Every row of the `FEAT-035` verdict table, and the honesty rule beneath it: no side is blamed
 * without evidence.
 */
class PlaybackVerdictTest {

    private val healthy = AccountEvidence.Answered(AccountHealth.Ok(null, activeConnections = 1, maxConnections = 2))
    private val full = AccountEvidence.Answered(AccountHealth.Ok(null, activeConnections = 2, maxConnections = 2))

    private fun status(code: Int) = StreamEvidence(StreamFault.BAD_STATUS, httpStatus = code)
    private val timedOutEmpty = StreamEvidence(StreamFault.TIMEOUT)
    private val unreachable = StreamEvidence(StreamFault.UNREACHABLE)

    private fun xtream(health: AccountHealth, stream: StreamEvidence = status(403)) =
        verdict(deviceOnline = true, AccountEvidence.Answered(health), stream)

    @Nested
    inner class TheDevice {

        @Test
        fun `offline outranks everything, whatever else is known`() {
            listOf(healthy, AccountEvidence.NotApplicable, AccountEvidence.Unavailable).forEach { account ->
                assertEquals(Verdict.DEVICE_OFFLINE, verdict(deviceOnline = false, account, status(403)))
            }
        }
    }

    @Nested
    inner class TheSubscription {

        @Test
        fun `an expired account is the subscription, whatever the stream said`() {
            val anything = listOf(
                status(404),
                status(403),
                timedOutEmpty,
                StreamEvidence(StreamFault.FORMAT, bytesReceived = 10),
            )
            anything.forEach {
                assertEquals(Verdict.SUBSCRIPTION_EXPIRED, xtream(AccountHealth.Expired(1L), it))
            }
        }

        @Test
        fun `a disabled account`() {
            assertEquals(Verdict.ACCOUNT_DISABLED, xtream(AccountHealth.Disabled))
        }

        @Test
        fun `rejected credentials`() {
            assertEquals(Verdict.CREDENTIALS_REJECTED, xtream(AccountHealth.CredentialsRejected))
        }

        @Test
        fun `every screen in use and a refusal is the connection limit`() {
            listOf(403, 429, 458, 509).forEach { code ->
                assertEquals(Verdict.CONNECTION_LIMIT, verdict(true, full, status(code)), "status $code")
            }
        }

        @Test
        fun `a refusal with screens to spare is not called a connection limit`() {
            assertEquals(Verdict.UNDETERMINED, verdict(true, healthy, status(403)))
        }

        @Test
        fun `every screen in use without a refusal is not called a connection limit`() {
            assertEquals(Verdict.CHANNEL_OFFLINE, verdict(true, full, status(404)))
            assertEquals(Verdict.UNDETERMINED, verdict(true, full, unreachable))
        }
    }

    @Nested
    inner class TheProvider {

        @Test
        fun `a panel that does not answer while the device is online is down`() {
            assertEquals(Verdict.PROVIDER_DOWN, xtream(AccountHealth.Unreachable))
            assertEquals(Verdict.PROVIDER_DOWN, xtream(AccountHealth.ServerError(503)))
        }

        @Test
        fun `a panel firewall is the provider blocking`() {
            assertEquals(Verdict.PROVIDER_BLOCKING, xtream(AccountHealth.Blocked))
        }

        @Test
        fun `a healthy account and a missing stream is the channel`() {
            assertEquals(Verdict.CHANNEL_OFFLINE, verdict(true, healthy, status(404)))
            assertEquals(Verdict.CHANNEL_OFFLINE, verdict(true, healthy, status(410)))
        }

        @Test
        fun `a healthy account and nothing arriving before the timeout is the channel`() {
            assertEquals(Verdict.CHANNEL_OFFLINE, verdict(true, healthy, timedOutEmpty))
        }
    }

    @Nested
    inner class Quiblo {

        @Test
        fun `data that arrived and could not be decoded is a format Quiblo cannot play`() {
            val stream = StreamEvidence(StreamFault.FORMAT, bytesReceived = 188_000L)
            assertEquals(Verdict.FORMAT_UNSUPPORTED, verdict(true, healthy, stream))
        }

        @Test
        fun `an unexpected engine error on a stream that worked is Quiblo's`() {
            val stream = StreamEvidence(StreamFault.OTHER, engineCode = "ERROR_CODE_UNSPECIFIED", hadPlayed = true)
            assertEquals(Verdict.APP_ERROR, verdict(true, healthy, stream))
        }

        @Test
        fun `Quiblo is never blamed for a stream that sent nothing`() {
            assertEquals(Verdict.UNDETERMINED, verdict(true, healthy, StreamEvidence(StreamFault.FORMAT)))
            assertEquals(Verdict.UNDETERMINED, verdict(true, healthy, StreamEvidence(StreamFault.OTHER)))
        }

        @Test
        fun `Quiblo is never blamed without an account check`() {
            val decodedBadly = StreamEvidence(StreamFault.FORMAT, bytesReceived = 188_000L)
            assertEquals(Verdict.UNDETERMINED, verdict(true, AccountEvidence.NotApplicable, decodedBadly))
            assertEquals(Verdict.UNDETERMINED, verdict(true, AccountEvidence.Unavailable, decodedBadly))
        }
    }

    @Nested
    inner class APlaylist {

        private fun m3u(stream: StreamEvidence) = verdict(true, AccountEvidence.NotApplicable, stream)

        @Test
        fun `a 401 is rejected credentials`() {
            assertEquals(Verdict.CREDENTIALS_REJECTED, m3u(status(401)))
        }

        @Test
        fun `a host that cannot be reached while online is the provider`() {
            assertEquals(Verdict.PROVIDER_DOWN, m3u(unreachable))
        }

        @Test
        fun `a 404 is the channel`() {
            assertEquals(Verdict.CHANNEL_OFFLINE, m3u(status(404)))
        }

        @Test
        fun `everything else is undetermined`() {
            listOf(status(403), status(503), timedOutEmpty, StreamEvidence(StreamFault.NETWORK)).forEach {
                assertEquals(Verdict.UNDETERMINED, m3u(it), "$it")
            }
        }
    }

    @Nested
    inner class NoEvidence {

        @Test
        fun `an account check that produced nothing blames nobody`() {
            listOf(status(401), status(404), unreachable, timedOutEmpty).forEach {
                assertEquals(Verdict.UNDETERMINED, verdict(true, AccountEvidence.Unavailable, it), "$it")
            }
        }

        @Test
        fun `a healthy account and an unexplained failure blames nobody`() {
            listOf(unreachable, StreamEvidence(StreamFault.NETWORK), status(500)).forEach {
                assertEquals(Verdict.UNDETERMINED, verdict(true, healthy, it), "$it")
            }
        }
    }
}
