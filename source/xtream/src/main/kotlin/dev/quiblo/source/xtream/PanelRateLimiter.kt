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

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Elapsed milliseconds from a monotonic source.
 *
 * The origin is arbitrary and meaningless; only the difference between two readings is ever
 * used, which is all a token bucket needs. `nanoTime` rather than `SystemClock.elapsedRealtime`
 * so this module stays a plain JVM library with no Android dependency.
 */
internal fun monotonicMillis(): Long = System.nanoTime() / NANOS_PER_MILLI

/**
 * How fast this app is willing to talk to one panel, at all, for any reason.
 *
 * A token bucket rather than a concurrency cap, because the two are not the same thing and
 * only one of them is what a panel's firewall measures. Three requests in flight sounds
 * modest and is, in fact, thirty requests a second when each takes 100 ms — which is
 * exactly how a fling through a channel list turned into an anti-flood block.
 *
 * The bucket allows a burst so that a user-initiated refresh, which is seven calls back to
 * back and then silence, is not slowed down at all. What it stops is the *sustained* rate:
 * once the burst is spent, requests are spaced out, so a list scrolled for a minute costs
 * a hundred requests rather than a thousand.
 *
 * Placed on the client rather than on any one call path, because the panel counts them all
 * together and does not care which screen they came from.
 */
internal class PanelRateLimiter(
    /**
     * A **monotonic** millisecond clock, and that word is the whole of this parameter.
     *
     * This used to be `System::currentTimeMillis`, which is the wall clock and can move
     * backwards. When it does — an NTP correction after boot, which is routine on the cheap
     * television boxes this app runs on, since they have no battery-backed clock — `elapsed`
     * goes negative, [refill] declines to run, and tokens never come back while the debt keeps
     * growing. Every subsequent request waits longer than the last, for as long as it takes the
     * wall clock to catch up to where it used to be. `Media3PlayerController` already avoids
     * exactly this for its load timer, for exactly this reason.
     */
    private val now: () -> Long = ::monotonicMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {

    private val mutex = Mutex()
    private var tokens: Double = BURST_CAPACITY.toDouble()
    private var lastRefillMillis: Long = now()

    /** Suspends until this request is within budget. */
    suspend fun acquire() {
        val waitMillis = mutex.withLock {
            refill()
            // Take the token anyway and pay for it by waiting. Ordering does not matter
            // here — every caller is a background prefetch or one of a refresh's seven
            // calls, and none of them is a user waiting on a single answer.
            //
            // The balance goes negative rather than stopping at zero, and that detail is the
            // sustained rate. Clamping at zero let the wait accrue a token the *next* caller
            // found waiting for it, so requests left in pairs and the real pacing was one
            // every 200 ms rather than the 400 ms below. Corrected 2026-08-09; it had been
            // running at twice the documented rate since the guard was written.
            tokens -= 1.0
            if (tokens < 0) (-tokens * MILLIS_PER_TOKEN).toLong() else 0L
        }
        if (waitMillis > 0) pause(waitMillis)
    }

    private fun refill() {
        val timestamp = now()
        val elapsed = timestamp - lastRefillMillis
        if (elapsed > 0) {
            tokens = (tokens + elapsed / MILLIS_PER_TOKEN).coerceAtMost(BURST_CAPACITY.toDouble())
            lastRefillMillis = timestamp
        }
    }

    private companion object {

        /**
         * Sustained rate: one request every 400 ms, so two and a half a second.
         *
         * Chosen against what the guide prefetch actually needs rather than against what a
         * panel will tolerate, which is unknowable and varies per provider. A viewer
         * reading a channel list settles on a few rows a second at most; anything faster is
         * a scroll passing through, and rows passed through do not need a guide.
         */
        const val MILLIS_PER_TOKEN = 400.0

        /**
         * Enough for a full catalogue refresh — authenticate plus six catalogue calls —
         * to go out at once, since that is a deliberate action a user is waiting on.
         */
        const val BURST_CAPACITY = 8
    }
}
