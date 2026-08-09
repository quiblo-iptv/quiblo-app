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

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * What stands between an hour-long catalogue scan and the user's own key being throttled.
 *
 * On a fake clock, so these assert the policy rather than how fast the machine running them
 * happens to be. The number that matters is the sustained one: a scan is thirty thousand
 * requests, and the only thing deciding whether that is polite is the spacing.
 */
class TmdbRateLimiterTest {

    private var clock = 0L
    private var slept = 0L

    private val limiter = TmdbRateLimiter(
        now = { clock },
        pause = { millis ->
            slept += millis
            clock += millis
        },
    )

    @Test
    @DisplayName("a poster row settling is not slowed down")
    fun `the burst covers a screenful`() = runTest {
        repeat(16) { limiter.acquire() }

        assertEquals(0L, slept, "browsing was made to wait")
    }

    @Test
    @DisplayName("a scan is paced once the burst is spent")
    fun `beyond the burst requests are spaced out`() = runTest {
        repeat(16) { limiter.acquire() }
        assertEquals(0L, slept)

        limiter.acquire()

        // Eight a second, sustained. Four workers with no limiter would be whatever the
        // network allows — which is the shape that got a panel blocked twice.
        assertTrue(slept >= 125L, "the seventeenth request went out unthrottled")
    }

    @Test
    fun `an hour of scanning stays at the sustained rate`() = runTest {
        repeat(16) { limiter.acquire() }
        slept = 0L

        repeat(80) { limiter.acquire() }

        // Ten seconds of budget for eighty requests, give or take the burst already spent.
        assertTrue(slept >= 9_000L, "80 requests took only ${slept}ms of budget")
    }

    @Test
    fun `waiting refills the budget`() = runTest {
        repeat(16) { limiter.acquire() }
        clock += 4_000L
        slept = 0L

        repeat(16) { limiter.acquire() }

        assertEquals(0L, slept, "an idle period did not restore the budget")
    }

    @Test
    @DisplayName("a refusal empties the bucket, so the pause asked for is actually taken")
    fun `spending the budget makes the next request wait`() = runTest {
        limiter.spend()

        limiter.acquire()

        assertTrue(slept > 0L, "the request after a refusal went out immediately")
    }
}
