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
     * The playable URL for a live stream.
     *
     * Credentials are part of the path because the Xtream protocol requires it. This is
     * the one place they legitimately appear in a URL, and that URL is handed straight to
     * the player — it is never logged, stored in the database, or exported (AC-XT-04).
     */
    fun liveStream(base: String, username: String, password: String, streamId: String): String =
        "$base/live/$username/$password/$streamId.ts"

    fun vodStream(base: String, username: String, password: String, streamId: String, extension: String): String =
        "$base/movie/$username/$password/$streamId.${extension.ifBlank { "mp4" }}"

    fun seriesStream(base: String, username: String, password: String, streamId: String, extension: String): String =
        "$base/series/$username/$password/$streamId.${extension.ifBlank { "mp4" }}"

    private const val PROTOCOL_SEPARATOR_LENGTH = 3
}
