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

package dev.quiblo.source.m3u

import dev.quiblo.core.model.SourceKind
import dev.quiblo.source.api.ContentFetcher
import dev.quiblo.source.api.FetchResult
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class M3uSourceTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")).use {
            it.readBytes().toString(Charsets.UTF_8)
        }

    /** A fetcher that returns whatever the test tells it to, for any location. */
    private class FakeFetcher(private val result: FetchResult) : ContentFetcher {
        override fun handles(location: String): Boolean = true
        override suspend fun fetch(location: String): FetchResult = result
    }

    private fun sourceReturning(result: FetchResult) = M3uSource(listOf(FakeFetcher(result)))

    private fun sourceServing(body: String, contentType: String? = null) =
        sourceReturning(FetchResult.Success(body, contentType))

    private val request = SourceRequest(sourceId = 3L, location = "http://playlist.example.invalid/list.m3u")

    @Test
    fun `declares the M3U kind`() {
        assertEquals(SourceKind.M3U, sourceServing("").kind)
    }

    @Test
    fun `parses a served playlist into channels`() = runTest {
        val result = sourceServing(fixture("valid-basic.m3u")).load(request)

        val success = assertInstanceOf(SourceResult.Success::class.java, result)
        assertEquals(3, success.channels.size)
        assertEquals(3, success.report.parsedEntries)
        assertTrue(success.channels.all { it.sourceId == 3L })
    }

    @Test
    @DisplayName("AC-PL-02 — the local-file path parses identically to the remote path")
    fun `both transports produce identical results`() = runTest {
        val body = fixture("valid-basic.m3u")

        val remote = sourceServing(body).load(SourceRequest(1L, "http://playlist.example.invalid/list.m3u"))
        val local = sourceServing(body).load(SourceRequest(1L, "content://documents/playlist.m3u"))

        val remoteChannels = assertInstanceOf(SourceResult.Success::class.java, remote).channels
        val localChannels = assertInstanceOf(SourceResult.Success::class.java, local).channels
        assertEquals(remoteChannels, localChannels)
    }

    @Test
    @DisplayName("AC-PL-04 — a partial parse is still a success, with the skipped count carried")
    fun `partial parse succeeds and reports skipped entries`() = runTest {
        val result = sourceServing(fixture("mixed-garbage.m3u")).load(request)

        val success = assertInstanceOf(SourceResult.Success::class.java, result)
        assertEquals(3, success.channels.size)
        assertEquals(2, success.report.skippedEntries)
        assertTrue(success.report.hasSkipped)
    }

    @Test
    @DisplayName("AC-PL-07 — HTML served in place of a playlist is a specific error")
    fun `detects an HTML page by body sniffing`() = runTest {
        val result = sourceServing(fixture("html-login-page.html")).load(request)

        val failure = assertInstanceOf(SourceResult.Failure::class.java, result)
        assertEquals(SourceError.NotAPlaylist, failure.error)
    }

    @Test
    fun `detects an HTML page by declared content type`() = runTest {
        val result = sourceServing("#EXTM3U", contentType = "text/html; charset=utf-8").load(request)

        val failure = assertInstanceOf(SourceResult.Failure::class.java, result)
        assertEquals(SourceError.NotAPlaylist, failure.error)
    }

    @Test
    fun `an empty but well-formed playlist is reported as empty, not as junk`() = runTest {
        val result = sourceServing(fixture("header-only.m3u")).load(request)

        val failure = assertInstanceOf(SourceResult.Failure::class.java, result)
        assertEquals(SourceError.EmptyPlaylist, failure.error)
    }

    @Test
    fun `arbitrary text with no header and no entries is not a playlist`() = runTest {
        val result = sourceServing("just some text\nand another line").load(request)

        val failure = assertInstanceOf(SourceResult.Failure::class.java, result)
        assertEquals(SourceError.NotAPlaylist, failure.error)
    }

    @Test
    @DisplayName("AC-PL-07 — transport failures propagate unchanged, never as an exception")
    fun `transport errors are passed through`() = runTest {
        val errors = listOf(
            SourceError.NotFound,
            SourceError.Timeout,
            SourceError.NoNetwork,
            SourceError.UnreachableHost,
            SourceError.HttpStatus(500),
            SourceError.FileUnreadable,
        )

        errors.forEach { error ->
            val result = sourceReturning(FetchResult.Failure(error)).load(request)
            val failure = assertInstanceOf(SourceResult.Failure::class.java, result)
            assertEquals(error, failure.error)
        }
    }

    @Test
    fun `fails cleanly when no transport handles the location`() = runTest {
        val noFetchers = M3uSource(emptyList())
        val result = noFetchers.load(request)
        assertInstanceOf(SourceResult.Failure::class.java, result)
    }
}
