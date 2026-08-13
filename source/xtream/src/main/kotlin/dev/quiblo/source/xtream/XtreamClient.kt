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

import dev.quiblo.source.api.Credentials
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.xtream.dto.AuthResponse
import dev.quiblo.source.xtream.dto.CategoryDto
import dev.quiblo.source.xtream.dto.EpgResponse
import dev.quiblo.source.xtream.dto.LiveStreamDto
import dev.quiblo.source.xtream.dto.SeriesDto
import dev.quiblo.source.xtream.dto.SeriesInfoResponse
import dev.quiblo.source.xtream.dto.VodInfoResponse
import dev.quiblo.source.xtream.dto.VodStreamDto
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

/** A typed result carrying either a value or a [SourceError]. */
internal sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val error: SourceError) : ApiResult<Nothing>
}

/**
 * A thin, defensive client for the Xtream Codes API.
 *
 * Two rules run through it. Never trust a field's declared type — every scalar goes
 * through a flexible serializer (AC-XT-06). And never let a credential escape — no
 * request URL, response body, or error value produced here is ever logged, and
 * [SourceError.Unknown] carries only an exception class name (AC-XT-04).
 */
internal class XtreamClient(
    private val client: HttpClient,
    private val json: Json = defaultJson,
    private val rateLimiter: PanelRateLimiter = PanelRateLimiter(),
) {

    suspend fun authenticate(base: String, credentials: Credentials): ApiResult<AuthResponse> =
        request(base, credentials, action = null)

    suspend fun liveCategories(base: String, credentials: Credentials): ApiResult<List<CategoryDto>> =
        request(base, credentials, "get_live_categories")

    suspend fun liveStreams(base: String, credentials: Credentials): ApiResult<List<LiveStreamDto>> =
        request(base, credentials, "get_live_streams")

    suspend fun vodCategories(base: String, credentials: Credentials): ApiResult<List<CategoryDto>> =
        request(base, credentials, "get_vod_categories")

    suspend fun vodStreams(base: String, credentials: Credentials): ApiResult<List<VodStreamDto>> =
        request(base, credentials, "get_vod_streams")

    suspend fun seriesCategories(base: String, credentials: Credentials): ApiResult<List<CategoryDto>> =
        request(base, credentials, "get_series_categories")

    suspend fun series(base: String, credentials: Credentials): ApiResult<List<SeriesDto>> =
        request(base, credentials, "get_series")

    /** Short-range guide for one channel. */
    suspend fun shortEpg(base: String, credentials: Credentials, streamId: String): ApiResult<EpgResponse> =
        request(base, credentials, "get_short_epg") { parameter("stream_id", streamId) }

    /**
     * The whole listing a panel holds for one channel, rather than the next few entries (INC-F4).
     *
     * `get_short_epg` returns a small window — enough for now and next, which is what a list row
     * needs. A timeline needs the rest, and this is the call that has it. Same response shape, so
     * the same DTO reads both.
     *
     * Made on an explicit long press and never on a scroll. A full listing per visible row is how
     * a panel's anti-flood rule trips (AC-TV-05).
     */
    suspend fun fullEpg(base: String, credentials: Credentials, streamId: String): ApiResult<EpgResponse> =
        request(base, credentials, "get_simple_data_table") { parameter("stream_id", streamId) }

    /** Details, including the plot, for one film. */
    suspend fun vodInfo(base: String, credentials: Credentials, vodId: String): ApiResult<VodInfoResponse> =
        request(base, credentials, "get_vod_info") { parameter("vod_id", vodId) }

    /** Detailed info including seasons and episodes for one series. */
    suspend fun seriesInfo(base: String, credentials: Credentials, seriesId: String): ApiResult<SeriesInfoResponse> =
        request(base, credentials, "get_series_info") { parameter("series_id", seriesId) }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T> request(
        base: String,
        credentials: Credentials,
        action: String?,
        crossinline extra: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = runCatchingApi {
        // Every request the app makes to a panel passes through here, which is why the
        // budget lives at this line and not on any one caller. The panel counts them all
        // together and does not care which screen they came from.
        rateLimiter.acquire()

        val response = client.get(XtreamUrl.playerApi(base)) {
            parameter("username", credentials.username)
            parameter("password", credentials.password)
            if (action != null) parameter("action", action)
            extra()
        }

        when {
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                ApiResult.Err(SourceError.Unauthorized)

            response.status == HttpStatusCode.NotFound ->
                ApiResult.Err(SourceError.NotFound)

            response.status.value in PROVIDER_BLOCK_STATUSES ->
                ApiResult.Err(SourceError.ProviderBlocked)

            !response.status.isSuccess() ->
                ApiResult.Err(SourceError.HttpStatus(response.status.value))

            else -> {
                val stream = response.bodyAsChannel().toInputStream().buffered()
                stream.mark(PEEK_BYTES)
                val peekBuffer = ByteArray(PEEK_BYTES)
                val readBytes = stream.read(peekBuffer, 0, PEEK_BYTES)
                stream.reset()
                val peekText = if (readBytes > 0) String(peekBuffer, 0, readBytes) else ""
                if (peekText.trimStart().startsWith("<")) {
                    ApiResult.Err(SourceError.NotAPlaylist)
                } else {
                    ApiResult.Ok(json.decodeFromStream<T>(stream))
                }
            }
        }
    }

    companion object {
        /**
         * How much of the body to sniff before committing to a JSON parse.
         *
         * Enough to see past whitespace and any preamble to the first real character,
         * and small enough to stay inside the buffer's mark limit.
         */
        const val PEEK_BYTES = 1024

        /**
         * Statuses that mean "the panel is refusing this client", not "the request was wrong".
         *
         * 429 is the standard one. The 46x codes are private to Xtream panels — XC_VM
         * serves 462 and 469 from its nginx firewall when anti-flood or anti-share trips —
         * and are indistinguishable from a generic failure unless named here.
         */
        val PROVIDER_BLOCK_STATUSES = setOf(429, 460, 461, 462, 463, 469)

        /**
         * Lenient on purpose. Unknown keys are ignored because panels add fields freely,
         * and malformed values coerce to defaults rather than aborting the parse
         * (docs/PLAN.md §1, AC-XT-06).
         */
        val defaultJson: Json = Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}

/**
 * Runs an API call, converting every failure into a typed error.
 *
 * The caught detail is limited to an exception class name so that no host, username or
 * password can reach a log or a crash trace through this path (AC-XT-04).
 *
 * `CancellationException` is rethrown rather than mapped, on the same reasoning
 * `TmdbClient.fetch` already records: it is not a failure of the request, it is this
 * coroutine being told to stop. Swallowing it turned every cancelled refresh into a
 * `SourceError.Unknown("JobCancellationException")` reported to the user as a load failure,
 * and left the caller running past the point it was cancelled — the rate limiter's own
 * `delay` is one of the places that throws it.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> runCatchingApi(block: () -> ApiResult<T>): ApiResult<T> = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    ApiResult.Err(mapThrowable(error))
}

/**
 * Maps a transport failure to something the UI can say a sentence about.
 *
 * **Matched on type, never on `simpleName`.** Both applications ship with `isMinifyEnabled`,
 * and every class named here except the `java.*` ones is on the program classpath, so R8
 * renames it — which quietly turned every timeout in a release build into
 * `SourceError.Unknown("a")` while the debug build mapped it correctly. A `when (type)` is
 * checked by the compiler and survives minification; a string comparison is neither.
 */
internal fun mapThrowable(error: Throwable): SourceError = when (error) {
    is HttpRequestTimeoutException,
    is ConnectTimeoutException,
    is SocketTimeoutException,
    -> SourceError.Timeout

    is UnresolvedAddressException,
    is UnknownHostException,
    -> SourceError.UnreachableHost

    is SerializationException,
    is IllegalArgumentException,
    -> SourceError.NotAPlaylist

    // Everything else the transport can raise: a refused connection, a reset, a broken
    // pipe. The host is the honest thing to blame and the only thing the user can act on.
    is IOException -> SourceError.UnreachableHost

    else -> SourceError.Unknown(error::class.simpleName)
}
