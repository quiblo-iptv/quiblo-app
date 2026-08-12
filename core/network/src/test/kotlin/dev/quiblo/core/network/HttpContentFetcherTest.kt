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

import dev.quiblo.source.api.FetchResult
import dev.quiblo.source.api.SourceError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

/**
 * AC-PL-07: every transport outcome must map to a specific, typed error. A bare
 * "Error" or a leaked exception is a failing result, not a cosmetic issue.
 */
class HttpContentFetcherTest {

    private val online = ConnectivityChecker { true }
    private val offline = ConnectivityChecker { false }

    private val playlistUrl = "http://playlist.example.invalid/list.m3u"

    private fun fetcherReturning(
        status: HttpStatusCode,
        body: String = "",
        contentType: String? = null,
        connectivity: ConnectivityChecker = online,
    ): HttpContentFetcher {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = contentType?.let { headersOf("Content-Type", it) } ?: headersOf(),
            )
        }
        return HttpContentFetcher(HttpClient(engine), connectivity)
    }

    private fun fetcherThrowing(
        error: Throwable,
        connectivity: ConnectivityChecker = online,
    ): HttpContentFetcher {
        val engine = MockEngine { throw error }
        return HttpContentFetcher(HttpClient(engine), connectivity)
    }

    @Test
    fun `handles only http and https locations`() {
        val fetcher = fetcherReturning(HttpStatusCode.OK)

        assertTrue(fetcher.handles("http://playlist.example.invalid/a.m3u"))
        assertTrue(fetcher.handles("HTTPS://playlist.example.invalid/a.m3u"))
        assertTrue(fetcher.handles("  http://playlist.example.invalid/a.m3u  "))
        assertFalse(fetcher.handles("content://documents/playlist.m3u"))
        assertFalse(fetcher.handles("file:///sdcard/playlist.m3u"))
    }

    @Test
    fun `streams the body and declares the content type on success`() = runTest {
        var seenContentType: String? = null

        val result = fetcherReturning(
            status = HttpStatusCode.OK,
            body = "#EXTM3U\n",
            contentType = "audio/x-mpegurl",
        ).fetch(playlistUrl) { body ->
            seenContentType = body.contentType
            body.reader().readText()
        }

        val success = assertInstanceOf(FetchResult.Success::class.java, result)
        assertEquals("#EXTM3U\n", success.value)
        assertTrue(seenContentType!!.contains("audio/x-mpegurl"))
    }

    @Test
    @DisplayName("AC-PL-05 — the body is a stream, not a string the whole playlist has to fit in")
    fun `the body can be read a line at a time`() = runTest {
        val playlist = buildString {
            append("#EXTM3U\n")
            repeat(2_000) { append("#EXTINF:-1,Channel $it\nhttp://host.invalid/$it.ts\n") }
        }

        val result = fetcherReturning(HttpStatusCode.OK, body = playlist).fetch(playlistUrl) { body ->
            // Counting without ever holding the document: the property the parser is built on.
            body.reader().lineSequence().count { it.startsWith("#EXTINF") }
        }

        assertEquals(2_000, assertInstanceOf(FetchResult.Success::class.java, result).value)
    }

    @Test
    fun `the reader block is never entered on a failure`() = runTest {
        var entered = false

        fetcherReturning(HttpStatusCode.NotFound).fetch(playlistUrl) { entered = true }

        assertFalse(entered, "a failed fetch still handed a body to the caller")
    }

    @Test
    @DisplayName("AC-PL-07 — 404 is distinguished from other statuses")
    fun `maps 404 to NotFound`() = runTest {
        val result = fetcherReturning(HttpStatusCode.NotFound).fetch(playlistUrl) { it.reader().readText() }
        assertEquals(SourceError.NotFound, (result as FetchResult.Failure).error)
    }

    @Test
    fun `maps other error statuses to HttpStatus carrying the code`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val result = HttpContentFetcher(HttpClient(engine), online).fetch(playlistUrl) { it.reader().readText() }

        assertEquals(SourceError.HttpStatus(500), (result as FetchResult.Failure).error)
    }

    @Test
    fun `reports no network before attempting a request`() = runTest {
        val result = fetcherReturning(HttpStatusCode.OK, connectivity = offline)
            .fetch(playlistUrl) { it.reader().readText() }
        assertEquals(SourceError.NoNetwork, (result as FetchResult.Failure).error)
    }

    @Test
    fun `maps an unresolvable host to UnreachableHost`() = runTest {
        val result = fetcherThrowing(UnknownHostException("playlist.example.invalid"))
            .fetch(playlistUrl) { it.reader().readText() }
        assertEquals(SourceError.UnreachableHost, (result as FetchResult.Failure).error)
    }

    @Test
    @DisplayName("AC-PL-07 — an unexpected exception never escapes as a crash or a stack trace")
    fun `unexpected failures become an opaque typed error`() = runTest {
        val result = fetcherThrowing(IllegalStateException("boom")).fetch(playlistUrl) { it.reader().readText() }

        val failure = assertInstanceOf(FetchResult.Failure::class.java, result)
        val error = assertInstanceOf(SourceError.Unknown::class.java, failure.error)
        assertEquals("IllegalStateException", error.technicalDetail)
    }

    @Test
    fun `the opaque error detail never carries the requested location`() = runTest {
        val result = fetcherThrowing(IllegalStateException(playlistUrl)).fetch(playlistUrl) { it.reader().readText() }

        val error = (result as FetchResult.Failure).error as SourceError.Unknown
        assertFalse(
            error.technicalDetail.orEmpty().contains("example.invalid"),
            "A host leaked into an error surface",
        )
    }
}
