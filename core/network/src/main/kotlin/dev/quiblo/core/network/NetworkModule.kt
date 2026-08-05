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

package dev.quiblo.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The one OkHttp instance in the app, and therefore the one connection pool.
 *
 * Shared deliberately between the API client and the player. A media stream is a long run
 * of requests to a single host — HLS segments, or ranged reads over a progressive file —
 * and giving each one a fresh socket means a TCP and TLS handshake per chunk. That cost
 * lands as a stall every few seconds, which is what it looked like on a series episode.
 *
 * Sharing does **not** raise concurrency: the pool caps *idle* sockets held open for
 * reuse, and nothing here touches OkHttp's dispatcher. Request volume is still governed
 * where it always was, in `GuideRepository`.
 */
internal fun createOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectionPool(
        ConnectionPool(
            maxIdleConnections = MAX_IDLE_CONNECTIONS,
            keepAliveDuration = KEEP_ALIVE_MINUTES,
            timeUnit = TimeUnit.MINUTES,
        ),
    )
    .connectTimeout(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    .readTimeout(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
    // A pooled socket the server has quietly closed is the normal cost of keep-alive.
    // Without this the first read on a stale connection surfaces as a playback error.
    .retryOnConnectionFailure(true)
    .build()

/**
 * Builds the single HTTP client used for user-configured hosts.
 *
 * There is exactly one client and it is only ever pointed at addresses the user typed in.
 * Quiblo makes no other outbound request: no analytics, no crash reporting, no update
 * check (docs/FREEZE.md §4.5, AC-NFR-03).
 */
internal fun createHttpClient(okHttpClient: OkHttpClient): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = okHttpClient }

    expectSuccess = false
    followRedirects = true

    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
    }

    install(UserAgent) {
        // Some panels gate their API on a recognised player agent. Note this does not by
        // itself avoid an XC_VM 46x firewall response: a panel that has flagged an account
        // answers the same way whatever agent is sent. Request volume is what that
        // responds to, which is handled in GuideRepository rather than here.
        //
        // Carries no device identifier and nothing that could fingerprint the user.
        agent = "IPTVSmartersPlayer"
    }
}

/** [ConnectivityChecker] backed by the platform ConnectivityManager. */
class AndroidConnectivityChecker(private val context: Context) : ConnectivityChecker {

    override fun isOnline(): Boolean {
        // If the service is unavailable we assume online: better to attempt the request
        // and report a real transport error than to block the user on a guess.
        val manager = context.getSystemService<ConnectivityManager>() ?: return true
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}

/**
 * AC-PLAY-05 requires a dead stream to surface an error within 15 seconds, so playlist
 * retrieval is held well inside that budget.
 */
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val REQUEST_TIMEOUT_MILLIS = 30_000L
private const val SOCKET_TIMEOUT_MILLIS = 15_000L

/**
 * Enough for a playing stream plus the API calls around it, without holding sockets open
 * against a panel that counts them.
 */
private const val MAX_IDLE_CONNECTIONS = 8
private const val KEEP_ALIVE_MINUTES = 5L
