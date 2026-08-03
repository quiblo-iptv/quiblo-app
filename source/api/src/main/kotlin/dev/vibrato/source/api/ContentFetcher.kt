/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.source.api

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
     * Retrieves [location] in full.
     *
     * The body is read into memory. At the 20,000-entry scale this project targets
     * (AC-PL-05) a playlist is a few megabytes of text, which is cheaper to hold briefly
     * than the complexity of keeping a streaming HTTP response open across the parse.
     */
    suspend fun fetch(location: String): FetchResult
}

/** The outcome of a [ContentFetcher.fetch]. */
sealed interface FetchResult {

    /**
     * @param body the retrieved text.
     * @param contentType the declared MIME type, when the transport supplies one. Used
     *   to detect a provider serving an HTML page in place of a playlist (AC-PL-07).
     */
    data class Success(val body: String, val contentType: String? = null) : FetchResult

    data class Failure(val error: SourceError) : FetchResult
}
