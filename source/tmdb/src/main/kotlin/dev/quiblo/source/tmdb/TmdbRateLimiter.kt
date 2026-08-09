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

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How fast this app is willing to talk to TMDB, at all, for any reason.
 *
 * The same token bucket as `PanelRateLimiter` and for the same hard-won reason: a
 * concurrency cap is not a rate limit, and it was a concurrency cap that got a user's panel
 * blocked twice. What is new here is that the key is the *user's own*, so the cost of
 * getting it throttled is not a slow app — it is their key, on their account, for everything
 * else they use it for.
 *
 * Sitting on the client means every path pays it: a poster tile, a detail screen, and a scan
 * walking thirty thousand titles. TMDB counts them together and does not care which screen
 * they came from.
 */
internal class TmdbRateLimiter(
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {

    private val mutex = Mutex()
    private var tokens: Double = BURST_CAPACITY.toDouble()
    private var lastRefillEpochMillis: Long = now()

    /**
     * Suspends until this request is within budget.
     *
     * Also the scan's pacing: it needs no throttle of its own, because a worker that cannot
     * get a token simply waits here, and the sustained rate is whatever this allows.
     */
    suspend fun acquire() {
        val waitMillis = mutex.withLock {
            refill()
            // The token is always taken, and the balance is allowed to go negative — the
            // caller then waits off exactly what it borrowed.
            //
            // Stopping at zero instead would halve the spacing: the wait accrues a fresh
            // token, and the *next* caller finds it and goes straight through, so two
            // requests leave per interval rather than one. That is the sustained rate being
            // quietly double what the constant below says, which is the same class of
            // mistake as counting requests in flight instead of requests a second.
            tokens -= 1.0
            if (tokens < 0) (-tokens * MILLIS_PER_TOKEN).toLong() else 0L
        }
        if (waitMillis > 0) pause(waitMillis)
    }

    /**
     * Empties the bucket after a refusal, so the pause TMDB asked for is actually taken.
     *
     * Without this a rate-limited scan resumes at full speed the moment its backoff ends,
     * which is how one 429 becomes a series of them.
     */
    suspend fun spend() = mutex.withLock {
        refill()
        tokens = 0.0
    }

    private fun refill() {
        val timestamp = now()
        val elapsed = timestamp - lastRefillEpochMillis
        if (elapsed > 0) {
            tokens = (tokens + elapsed / MILLIS_PER_TOKEN).coerceAtMost(BURST_CAPACITY.toDouble())
            lastRefillEpochMillis = timestamp
        }
    }

    private companion object {
        /**
         * Sustained rate: one request every 125 ms, so eight a second.
         *
         * Deliberately far below what TMDB tolerates, and chosen against the one job that
         * runs long enough for a sustained rate to mean anything — the catalogue scan. Eight
         * a second walks thirty thousand titles in about an hour, which is a background job
         * a viewer starts and forgets. Twice that would halve the wait and double the chance
         * of a user's own key being throttled for the rest of the day; that is not a trade
         * this app gets to make on their behalf.
         */
        const val MILLIS_PER_TOKEN = 125.0

        /**
         * Enough that browsing never waits.
         *
         * A poster row settling fetches a handful of scores at once and then goes quiet,
         * which is exactly the shape a burst is for. The scan drains this in its first two
         * seconds and spends the rest of its life at the sustained rate.
         */
        const val BURST_CAPACITY = 16
    }
}
