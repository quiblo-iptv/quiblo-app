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

import dev.quiblo.source.api.ContentFetcher
import dev.quiblo.source.api.FetchResult
import dev.quiblo.source.api.SourceError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

/**
 * Fetches playlists over HTTP(S).
 *
 * Every transport failure is translated into a typed [SourceError] here, so that no
 * exception, status code or stack trace can reach the UI as a message (AC-PL-07). The UI
 * maps the type to a localised string.
 *
 * @param connectivity consulted before dialling out, so an offline device reports
 *   "no connection" rather than a misleading DNS failure.
 */
class HttpContentFetcher(
    private val client: HttpClient,
    private val connectivity: ConnectivityChecker,
) : ContentFetcher {

    override fun handles(location: String): Boolean {
        val normalized = location.trim().lowercase()
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetch(location: String): FetchResult {
        if (!connectivity.isOnline()) {
            return FetchResult.Failure(SourceError.NoNetwork)
        }

        return try {
            val response = client.get(location.trim())

            when {
                response.status.isSuccess() -> FetchResult.Success(
                    body = response.bodyAsText(),
                    contentType = response.headers[HttpHeaders.ContentType],
                )

                response.status == HttpStatusCode.NotFound ->
                    FetchResult.Failure(SourceError.NotFound)

                else -> FetchResult.Failure(SourceError.HttpStatus(response.status.value))
            }
        } catch (_: HttpRequestTimeoutException) {
            FetchResult.Failure(SourceError.Timeout)
        } catch (_: SocketTimeoutException) {
            FetchResult.Failure(SourceError.Timeout)
        } catch (_: UnresolvedAddressException) {
            FetchResult.Failure(SourceError.UnreachableHost)
        } catch (_: UnknownHostException) {
            FetchResult.Failure(SourceError.UnreachableHost)
        } catch (_: IOException) {
            FetchResult.Failure(SourceError.UnreachableHost)
        } catch (error: Exception) {
            // Deliberately opaque: the detail is for logs, never for the screen, and must
            // never carry a host or credential.
            FetchResult.Failure(SourceError.Unknown(error::class.simpleName))
        }
    }
}

/**
 * Whether the device currently has a usable network.
 *
 * Abstracted so `:core:network` stays unit-testable without an Android framework stub.
 */
fun interface ConnectivityChecker {
    fun isOnline(): Boolean
}
