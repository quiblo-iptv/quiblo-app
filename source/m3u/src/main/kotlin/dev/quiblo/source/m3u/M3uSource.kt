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
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import java.io.StringReader

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
) : MediaSource {

    override val kind: SourceKind = SourceKind.M3U

    override suspend fun load(request: SourceRequest): SourceResult {
        val fetcher = fetchers.firstOrNull { it.handles(request.location) }
            ?: return SourceResult.Failure(SourceError.Unknown("No transport for the given location"))

        return when (val fetched = fetcher.fetch(request.location)) {
            is FetchResult.Failure -> SourceResult.Failure(fetched.error)
            is FetchResult.Success -> parseBody(fetched, request.sourceId)
        }
    }

    private fun parseBody(fetched: FetchResult.Success, sourceId: Long): SourceResult =
        if (looksLikeHtml(fetched)) {
            SourceResult.Failure(SourceError.NotAPlaylist)
        } else {
            val result = M3uParser.parseToList(StringReader(fetched.body), sourceId)
            when {
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
     */
    private fun looksLikeHtml(fetched: FetchResult.Success): Boolean {
        if (fetched.contentType?.contains("text/html", ignoreCase = true) == true) return true
        val head = fetched.body.trimStart().take(HTML_SNIFF_LENGTH).lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }

    private companion object {
        const val HTML_SNIFF_LENGTH = 64
    }
}
