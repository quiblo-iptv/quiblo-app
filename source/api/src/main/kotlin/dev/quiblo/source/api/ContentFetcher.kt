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

package dev.quiblo.source.api

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Retrieves the raw bytes behind a location, whatever the transport.
 *
 * This is the seam that keeps `:source:m3u` a plain JVM module with no Android and no
 * HTTP client dependency: the parser is handed text and never learns where it came from.
 * `:core:network` supplies the HTTP implementation and `:core:data` the local-file one
 * (AC-PL-02 requires both paths to produce identical results).
 *
 * Implementations must translate their own failures into a [SourceError] rather than
 * throwing, so no transport exception can reach the UI as a stack trace (AC-PL-07).
 */
interface ContentFetcher {

    /** True when this fetcher handles [location]. */
    fun handles(location: String): Boolean

    /**
     * Retrieves [location] and hands the open body to [read].
     *
     * **The body is a stream, and that is the whole shape of this method.** It used to be
     * returned as a `String`, which undid the one property `M3uParser` is built around: the
     * parser reads line by line so a large playlist never exists twice in memory, but by the
     * time it was handed a `StringReader` the entire playlist was already on the heap as a
     * Java `String` — UTF-16, so roughly twice the bytes that came off the wire. Against the
     * 67,567-channel account this project is tested on, that is tens of megabytes allocated to
     * feed a parser written specifically not to need them.
     *
     * Passing a block rather than returning the stream is what lets the transport close the
     * response afterwards, which matters more here than usual: leaving a connection open to a
     * panel is exactly the sort of thing that gets an account noticed.
     *
     * [read] is called at most once, and only on success.
     */
    suspend fun <T> fetch(location: String, read: suspend (FetchedBody) -> T): FetchResult<T>
}

/**
 * An open playlist body, plus whatever the transport was willing to say about it.
 *
 * Valid only inside the [ContentFetcher.fetch] block that supplied it; the underlying stream
 * is closed on the way out.
 *
 * @property contentType the declared MIME type, when the transport supplies one. Used to
 *   detect a provider serving an HTML page in place of a playlist (AC-PL-07).
 */
class FetchedBody(
    val contentType: String?,
    private val stream: InputStream,
    private val charset: Charset = Charsets.UTF_8,
) {
    /**
     * The body as characters, buffered.
     *
     * Buffered because the caller needs `mark`/`reset` to sniff the first few characters
     * before committing to a parse, and because reading a stream a character at a time across
     * a network socket is its own kind of slow.
     */
    fun reader(): BufferedReader = BufferedReader(InputStreamReader(stream, charset))
}

/** The outcome of a [ContentFetcher.fetch]. */
sealed interface FetchResult<out T> {

    /** @property value whatever the caller's block made of the body. */
    data class Success<T>(val value: T) : FetchResult<T>

    data class Failure(val error: SourceError) : FetchResult<Nothing>
}
