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

package dev.quiblo.core.media

import androidx.media3.common.PlaybackException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The two decisions behind "sometimes it doesn't play" (`BUG-034`, `T-1`).
 *
 * The first tests in `:core:media`. Everything here is a value in and a value out; no engine is
 * built, and the Media3 error codes are compile-time constants.
 */
class PlaybackDecisionsTest {

    private fun badStatus(status: Int) =
        classify(EngineFailure(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpStatus = status))

    @Nested
    inner class WhatAStatusSays {

        @Test
        fun `404 and 410 are the only statuses that say a stream is gone`() {
            assertEquals(PlaybackError.SOURCE_GONE, badStatus(404))
            assertEquals(PlaybackError.SOURCE_GONE, badStatus(410))
        }

        @Test
        fun `401 is a rejected account, not a missing stream`() {
            assertEquals(PlaybackError.AUTH_REJECTED, badStatus(401))
        }

        @Test
        fun `connection-limit and anti-flood answers are a refusal`() {
            listOf(403, 429, 458, 460, 461, 462, 463, 469, 509).forEach { status ->
                assertEquals(PlaybackError.PROVIDER_REFUSED, badStatus(status), "status $status")
            }
        }

        @Test
        fun `every server error is a refusal`() {
            listOf(500, 502, 503, 504, 599).forEach { status ->
                assertEquals(PlaybackError.PROVIDER_REFUSED, badStatus(status), "status $status")
            }
        }

        @Test
        fun `a status nobody named is not guessed at`() {
            assertEquals(PlaybackError.UNKNOWN, badStatus(400))
            assertEquals(PlaybackError.UNKNOWN, badStatus(418))
            assertEquals(
                PlaybackError.UNKNOWN,
                classify(EngineFailure(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpStatus = null)),
            )
        }
    }

    @Nested
    inner class WhatTheTransportSays {

        @Test
        fun `an unknown host or a refused connection is unreachable`() {
            assertEquals(
                PlaybackError.UNREACHABLE,
                classify(
                    EngineFailure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, hostUnreachable = true),
                ),
            )
        }

        @Test
        fun `a connection that opened and died is a timeout`() {
            assertEquals(
                PlaybackError.TIMEOUT,
                classify(EngineFailure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)),
            )
            assertEquals(
                PlaybackError.TIMEOUT,
                classify(EngineFailure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)),
            )
        }

        @Test
        fun `a container nothing recognises is a format failure`() {
            assertEquals(
                PlaybackError.UNSUPPORTED_FORMAT,
                classify(EngineFailure(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)),
            )
        }

        @Test
        fun `a missing local file is gone`() {
            assertEquals(
                PlaybackError.SOURCE_GONE,
                classify(EngineFailure(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)),
            )
        }

        @Test
        fun `drm is refused plainly`() {
            assertEquals(
                PlaybackError.DRM_UNSUPPORTED,
                classify(EngineFailure(PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED)),
            )
        }
    }

    @Nested
    inner class BeforeTheFirstFrame {

        private fun step(error: PlaybackError, retriesSoFar: Int = 0, elapsedMillis: Long = 1_000L) =
            nextStep(error, hasEverBeenReady = false, retriesSoFar = retriesSoFar, elapsedMillis = elapsedMillis)

        @Test
        fun `a refusal is asked again after two seconds, then four`() {
            assertEquals(NextStep.RetryAfter(2_000L, 1), step(PlaybackError.PROVIDER_REFUSED))
            assertEquals(NextStep.RetryAfter(4_000L, 2), step(PlaybackError.PROVIDER_REFUSED, retriesSoFar = 1))
        }

        @Test
        fun `a refusal is given up on after two retries`() {
            assertEquals(NextStep.GiveUp, step(PlaybackError.PROVIDER_REFUSED, retriesSoFar = 2))
        }

        @Test
        fun `a timeout is asked again too`() {
            assertEquals(NextStep.RetryAfter(2_000L, 1), step(PlaybackError.TIMEOUT))
        }

        @Test
        fun `no retry is started that the watchdog would cut short`() {
            // 9 s in, a 4 s wait would end after the 12 s budget: the error is shown instead.
            assertEquals(
                NextStep.GiveUp,
                step(PlaybackError.PROVIDER_REFUSED, retriesSoFar = 1, elapsedMillis = 9_000L),
            )
            // 10 s in, even the first 2 s wait lands exactly on the budget.
            assertEquals(NextStep.GiveUp, step(PlaybackError.TIMEOUT, elapsedMillis = 10_000L))
        }

        @Test
        fun `every retry ends inside the AC-PLAY-05 budget`() {
            var elapsed = 0L
            var retries = 0
            while (true) {
                val next = step(PlaybackError.PROVIDER_REFUSED, retriesSoFar = retries, elapsedMillis = elapsed)
                if (next !is NextStep.RetryAfter) break
                elapsed += next.delayMillis
                retries = next.attempt
            }
            assertTrue(elapsed < INITIAL_LOAD_TIMEOUT_MILLIS, "retries end at $elapsed ms")
        }

        @Test
        fun `terminal errors and everything else are reported at once`() {
            listOf(
                PlaybackError.SOURCE_GONE,
                PlaybackError.AUTH_REJECTED,
                PlaybackError.UNSUPPORTED_FORMAT,
                PlaybackError.DRM_UNSUPPORTED,
                PlaybackError.UNREACHABLE,
                PlaybackError.NETWORK,
                PlaybackError.UNKNOWN,
            ).forEach { error ->
                assertEquals(NextStep.GiveUp, step(error), "$error")
            }
        }
    }

    @Nested
    inner class AfterTheStreamHasPlayed {

        private fun step(error: PlaybackError, retriesSoFar: Int = 0) =
            nextStep(error, hasEverBeenReady = true, retriesSoFar = retriesSoFar, elapsedMillis = 600_000L)

        @Test
        fun `a drop gets three backed-off attempts, as AC-PLAY-06 asks`() {
            assertEquals(NextStep.RetryAfter(1_500L, 1), step(PlaybackError.NETWORK))
            assertEquals(NextStep.RetryAfter(3_000L, 2), step(PlaybackError.NETWORK, retriesSoFar = 1))
            assertEquals(NextStep.RetryAfter(4_500L, 3), step(PlaybackError.NETWORK, retriesSoFar = 2))
            assertEquals(NextStep.GiveUp, step(PlaybackError.NETWORK, retriesSoFar = 3))
        }

        @Test
        fun `a refusal mid-playback is retried, where it used to be final`() {
            assertEquals(NextStep.RetryAfter(1_500L, 1), step(PlaybackError.PROVIDER_REFUSED))
        }

        @Test
        fun `a stream that is gone, or an account that is rejected, is not retried`() {
            assertEquals(NextStep.GiveUp, step(PlaybackError.SOURCE_GONE))
            assertEquals(NextStep.GiveUp, step(PlaybackError.AUTH_REJECTED))
        }
    }
}
