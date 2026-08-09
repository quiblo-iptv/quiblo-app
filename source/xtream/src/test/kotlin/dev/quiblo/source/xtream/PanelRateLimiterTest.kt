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

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The budget that stands between a scrolling list and an anti-flood block.
 *
 * Driven on a fake clock, so these assert the *policy* rather than how fast the machine
 * running them happens to be.
 */
class PanelRateLimiterTest {

    private var clock = 0L
    private var slept = 0L

    private val limiter = PanelRateLimiter(
        now = { clock },
        pause = { millis ->
            slept += millis
            clock += millis
        },
    )

    @Test
    @DisplayName("a catalogue refresh is not slowed down at all")
    fun `the burst covers a whole refresh`() = runTest {
        // Authenticate plus six catalogue calls. This is a deliberate action with a user
        // waiting on it, and throttling it would be throttling the wrong thing.
        repeat(7) { limiter.acquire() }

        assertEquals(0L, slept, "a refresh was made to wait")
    }

    @Test
    @DisplayName("sustained asking is spaced out once the burst is spent")
    fun `beyond the burst requests are paced`() = runTest {
        repeat(8) { limiter.acquire() }
        assertEquals(0L, slept)

        limiter.acquire()

        // This is the whole point: a list that keeps asking gets a request every 400 ms,
        // not thirty a second. Three requests in flight sounds modest and is, in fact,
        // thirty a second when each takes 100 ms.
        assertTrue(slept >= 400L, "the ninth request went out unthrottled")
    }

    @Test
    @DisplayName("a long scroll is paced at the documented rate, not twice it")
    fun `the sustained rate is one request per interval`() = runTest {
        repeat(8) { limiter.acquire() }
        slept = 0L

        repeat(20) { limiter.acquire() }

        // Twenty requests at one per 400 ms. This is a regression test with a story: the
        // balance used to stop at zero rather than going negative, so the wait accrued a
        // token that the next caller found and spent immediately. Requests left in pairs and
        // the real pacing was 200 ms — half the figure this guard was documented, reviewed
        // and trusted to enforce after two account blocks.
        assertTrue(slept >= 7_600L, "20 requests were paced in only ${slept}ms")
    }

    @Test
    fun `waiting refills the budget`() = runTest {
        repeat(8) { limiter.acquire() }
        // A user reading one screen for four seconds has earned the burst back.
        clock += 4_000L
        slept = 0L

        repeat(8) { limiter.acquire() }

        assertEquals(0L, slept, "an idle period did not restore the budget")
    }
}
