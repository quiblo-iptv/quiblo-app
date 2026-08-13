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
import dev.quiblo.source.api.FetchedBody
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader

/**
 * The M3U/M3U8 implementation of [MediaSource].
 *
 * Both the remote-URL and local-file paths run through the same [ContentFetcher]
 * abstraction and the same parser, which is what makes them produce identical results
 * (AC-PL-02).
 *
 * @param fetchers the transports to try, in order. The first one that [ContentFetcher.handles]
 *   the location wins.
 */
class M3uSource(
    private val fetchers: List<ContentFetcher>,
    /**
     * Where the playlist is parsed.
     *
     * **Not the caller's thread, and that is a fix rather than a preference.** `load` is
     * reached from `SourcesViewModel` through `viewModelScope`, which is `Main.immediate`, so
     * parsing ran on the main thread — tens of thousands of lines of it, on the frame the user
     * was looking at. The fetch suspended politely and then the parse blocked. Injected so a
     * test can keep everything on one thread.
     */
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaSource {

    override val kind: SourceKind = SourceKind.M3U

    override suspend fun load(request: SourceRequest): SourceResult {
        val fetcher = fetchers.firstOrNull { it.handles(request.location) }
            ?: return SourceResult.Failure(SourceError.Unknown("No transport for the given location"))

        val fetched = fetcher.fetch(request.location) { body ->
            withContext(parseDispatcher) { parseBody(body, request.sourceId) }
        }

        return when (fetched) {
            is FetchResult.Failure -> SourceResult.Failure(fetched.error)
            is FetchResult.Success -> fetched.value
        }
    }

    private fun parseBody(body: FetchedBody, sourceId: Long): SourceResult {
        val reader = body.reader()
        if (looksLikeHtml(body.contentType, reader)) return SourceResult.Failure(SourceError.NotAPlaylist)

        val result = M3uParser.parseToList(reader, sourceId)
        return when {
            result.channels.isNotEmpty() ->
                SourceResult.Success(channels = result.channels, report = result.report)

            // Entries were recognised but all unusable: it was a playlist, just a bad one.
            result.report.hasSkipped || result.report.hadHeader ->
                SourceResult.Failure(SourceError.EmptyPlaylist)

            // No header and nothing that even looked like an entry: not a playlist at all.
            else -> SourceResult.Failure(SourceError.NotAPlaylist)
        }
    }

    /**
     * Detects a provider serving its HTML login or error page with a success status,
     * which is a common enough failure that AC-PL-07 calls it out by name.
     *
     * Reads the opening characters and puts them back, so the parser still sees the whole
     * document. [PEEK_LENGTH] is generously larger than [HTML_SNIFF_LENGTH] because the
     * comparison is made after leading whitespace is trimmed, and a page can carry a good deal
     * of it before the first tag.
     */
    private fun looksLikeHtml(contentType: String?, reader: BufferedReader): Boolean {
        if (contentType?.contains("text/html", ignoreCase = true) == true) return true

        reader.mark(PEEK_LENGTH)
        val peeked = CharArray(PEEK_LENGTH)
        val count = reader.read(peeked, 0, PEEK_LENGTH)
        reader.reset()

        if (count <= 0) return false
        val head = String(peeked, 0, count).trimStart().take(HTML_SNIFF_LENGTH).lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }

    private companion object {
        const val HTML_SNIFF_LENGTH = 64
        const val PEEK_LENGTH = 1024
    }
}
