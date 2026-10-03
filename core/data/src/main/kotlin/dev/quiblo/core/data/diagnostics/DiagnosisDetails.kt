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

package dev.quiblo.core.data.diagnostics

import dev.quiblo.source.api.AccountHealth
import java.net.URI
import java.net.URISyntaxException

/**
 * The host of [url], and nothing else from it.
 *
 * An Xtream stream URL carries the username and password in its path, and an M3U URL often
 * carries them in its query. Neither may reach a screen, a clipboard or a log (AC-XT-04), so the
 * one part of a URL a diagnosis is allowed to name is extracted here and the rest is discarded
 * unread. Userinfo (`user:pass@`) is not part of `URI.host`, so it is dropped too.
 */
fun hostOf(url: String): String? = try {
    URI(url.trim()).host?.takeIf { it.isNotBlank() }
} catch (_: URISyntaxException) {
    null
}

/**
 * One line of evidence for a bug report, such as
 * `HTTP 403 · ERROR_CODE_IO_BAD_HTTP_STATUS · no data · 2 retries · account OK (2/2) · host x.invalid`.
 *
 * Built only from values that cannot carry a credential: numbers, the engine's error name, the
 * account's state and a host. Nothing the caller passes as free text reaches it.
 */
fun detailsLine(stream: StreamEvidence, account: AccountEvidence, host: String?): String = buildList {
    stream.httpStatus?.let { add("HTTP $it") }
    add(stream.engineCode ?: "no engine error (load timed out)")
    add(if (stream.receivedData) "data received" else "no data")
    if (stream.retries > 0) add("${stream.retries} ${if (stream.retries == 1) "retry" else "retries"}")
    add(accountText(account))
    host?.let { add("host $it") }
}.joinToString(SEPARATOR)

private fun accountText(account: AccountEvidence): String = when (account) {
    AccountEvidence.NotApplicable -> "no account check (playlist)"
    AccountEvidence.Unavailable -> "account check unavailable"
    is AccountEvidence.Answered -> when (val health = account.health) {
        is AccountHealth.Ok -> "account OK" + screens(health)
        is AccountHealth.Expired -> "account expired"
        AccountHealth.Disabled -> "account disabled"
        AccountHealth.CredentialsRejected -> "credentials rejected"
        AccountHealth.Unreachable -> "panel unreachable"
        is AccountHealth.ServerError -> "panel HTTP ${health.code}"
        AccountHealth.Blocked -> "panel blocking requests"
    }
}

private fun screens(health: AccountHealth.Ok): String =
    if (health.activeConnections != null && health.maxConnections != null) {
        " (${health.activeConnections}/${health.maxConnections})"
    } else {
        ""
    }

private const val SEPARATOR = " · "
