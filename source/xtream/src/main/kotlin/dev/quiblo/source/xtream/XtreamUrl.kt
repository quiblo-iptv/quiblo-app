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

import dev.quiblo.core.model.LiveFormat

/**
 * Normalises the many shapes a user might type an Xtream base URL in.
 *
 * AC-XT-03 requires that with or without a scheme, with or without a port, and with or
 * without a trailing slash, everything resolves to the same working endpoint. Users
 * copy these out of emails and chat messages, so the input is rarely clean.
 */
object XtreamUrl {

    /**
     * @return the canonical `scheme://host[:port]` base, with no trailing slash and no
     *   path, or null when [input] cannot be understood as a host at all.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null

        // Users often paste a full API URL; keep only the origin.
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val schemeSeparator = withScheme.indexOf("://")
        val scheme = withScheme.substring(0, schemeSeparator).lowercase()
        val authority = withScheme.substring(schemeSeparator + PROTOCOL_SEPARATOR_LENGTH)
            .substringBefore('/')
            .substringBefore('?')

        return buildBase(scheme, authority)
    }

    /** @return the canonical base, or null when [scheme] or [authority] is unusable. */
    private fun buildBase(scheme: String, authority: String): String? {
        if (scheme != "http" && scheme != "https") return null

        // Anything before an @ is userinfo, and it is dropped rather than carried into the base.
        // A panel's credentials belong in the query parameters XtreamClient adds, and a base
        // silently carrying a second copy of them is one more place for them to be logged.
        val hostAndPort = authority.substringAfterLast('@')
        val split = hostAndPort.takeIf { it.isNotEmpty() }?.let(::splitHostAndPort) ?: return null
        val (host, port) = split

        val hostValid = host.isNotEmpty() && !host.contains(' ')
        val portValid = port == null || (port.isNotEmpty() && port.all { it.isDigit() })

        return if (hostValid && portValid) {
            if (port == null) "$scheme://${host.lowercase()}" else "$scheme://${host.lowercase()}:$port"
        } else {
            null
        }
    }

    /**
     * Splits `host:port` into its two halves, with a bracketed IPv6 literal kept whole.
     *
     * The naive "split on the last colon" this replaces is right for a name and wrong for an
     * address: `[::1]` is all colons, so the split landed inside the literal and produced a host
     * of `[:` and a port of `:1]`, which failed the digit check and rejected an address that was
     * perfectly valid. A colon only separates a port when it comes after the closing bracket.
     */
    private fun splitHostAndPort(hostAndPort: String): Pair<String, String?>? =
        if (hostAndPort.startsWith('[')) {
            splitBracketedHost(hostAndPort)
        } else {
            val colonIndex = hostAndPort.lastIndexOf(':')
            if (colonIndex >= 0) {
                hostAndPort.substring(0, colonIndex) to hostAndPort.substring(colonIndex + 1)
            } else {
                hostAndPort to null
            }
        }

    /** The `[…]` form, where only a colon after the closing bracket separates a port. */
    private fun splitBracketedHost(hostAndPort: String): Pair<String, String?>? {
        val close = hostAndPort.indexOf(']')
        if (close < 0) return null

        val host = hostAndPort.substring(0, close + 1)
        val rest = hostAndPort.substring(close + 1)
        return when {
            rest.isEmpty() -> host to null
            rest.startsWith(':') -> host to rest.substring(1)
            else -> null
        }
    }

    /** The `player_api.php` endpoint for an already-normalised [base]. */
    fun playerApi(base: String): String = "$base/player_api.php"

    /**
     * What is stored for a live stream instead of its URL (`BUG-041`): `xtream:live/101`.
     *
     * **The stored form carries no host and no credentials.** The URL a panel serves a stream from
     * has the username and password in its path, and it used to be written to the `channels` table
     * for every channel, film and episode — the password in SQLite in plain text, next to an
     * encrypted store that exists precisely to keep it out of there. A locator names the stream;
     * [resolve] turns it into a URL at the moment of playing, from the credential store, and that
     * URL lives only in memory.
     *
     * Live carries no extension: which container to ask the panel for is decided when it is played.
     */
    fun liveLocator(streamId: String): String = "$LOCATOR_SCHEME:$LIVE/$streamId"

    fun vodLocator(streamId: String, extension: String): String =
        "$LOCATOR_SCHEME:$MOVIE/$streamId.${extension.ifBlank { DEFAULT_EXTENSION }}"

    fun seriesLocator(episodeId: String, extension: String): String =
        "$LOCATOR_SCHEME:$SERIES/$episodeId.${extension.ifBlank { DEFAULT_EXTENSION }}"

    /**
     * The playable URL for [locator], or null when [locator] is not one of this module's.
     *
     * The one place credentials are put into a URL, and that URL is handed straight to the player —
     * never logged, stored or exported (AC-XT-04).
     */
    fun resolve(
        base: String,
        username: String,
        password: String,
        locator: String,
        /** For a live locator: `ts` or `m3u8`, from [liveExtension]. */
        liveExtension: String = TS,
    ): String? {
        val path = locator.takeIf { it.startsWith("$LOCATOR_SCHEME:") }?.substringAfter(':') ?: return null
        val type = path.substringBefore('/', missingDelimiterValue = "")
        val file = path.substringAfter('/', missingDelimiterValue = "")
        return when {
            file.isBlank() || '/' in file -> null
            type == LIVE -> liveStream(base, username, password, file, liveExtension)
            type == MOVIE || type == SERIES -> "$base/$type/$username/$password/$file"
            else -> null
        }
    }

    /**
     * The playable URL for a live stream.
     *
     * Credentials are part of the path because the Xtream protocol requires it. Built only by
     * [resolve], at play time; see [liveLocator].
     */
    fun liveStream(
        base: String,
        username: String,
        password: String,
        streamId: String,
        extension: String = TS,
    ): String = "$base/live/$username/$password/$streamId.$extension"

    /**
     * Which container to ask for a live stream in (`BUG-043`).
     *
     * It was always `.ts`, so an account limited to HLS failed on every live channel, and TS is the
     * less forgiving of the two on a phone's network. **Auto** asks for HLS when the panel says the
     * account may use it, and TS otherwise — including when the panel has not said, which is what
     * every account got before this. The viewer can fix either choice per source.
     */
    fun liveExtension(format: LiveFormat, allowed: Set<String>?): String = when (format) {
        LiveFormat.HLS -> HLS
        LiveFormat.TS -> TS
        LiveFormat.AUTO -> if (allowed?.contains(HLS) == true) HLS else TS
    }

    fun vodStream(base: String, username: String, password: String, streamId: String, extension: String): String =
        "$base/movie/$username/$password/$streamId.${extension.ifBlank { "mp4" }}"

    fun seriesStream(base: String, username: String, password: String, streamId: String, extension: String): String =
        "$base/series/$username/$password/$streamId.${extension.ifBlank { "mp4" }}"

    private const val PROTOCOL_SEPARATOR_LENGTH = 3

    /** The scheme of a stored stream reference. Never a real URL scheme, so never mistaken for one. */
    const val LOCATOR_SCHEME = "xtream"
    private const val LIVE = "live"
    private const val MOVIE = "movie"
    private const val SERIES = "series"
    private const val DEFAULT_EXTENSION = "mp4"
    private const val HLS = "m3u8"
    private const val TS = "ts"
}
