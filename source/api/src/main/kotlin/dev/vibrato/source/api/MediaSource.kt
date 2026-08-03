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

import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.SourceKind

/**
 * The abstraction every content protocol implements (docs/FREEZE.md §4.2).
 *
 * Adding Stalker, XMLTV or any future protocol means adding one implementation of this
 * interface and changing zero feature modules. Implementations live in `:source:m3u`,
 * `:source:xtream`, and so on; nothing in `:feature:*` ever references them directly.
 */
interface MediaSource {

    /** Which kind of source this implementation handles. */
    val kind: SourceKind

    /**
     * Loads every item the source exposes.
     *
     * Implementations must not throw for malformed provider data. A playlist with broken
     * entries is the normal case, not an exceptional one: return the entries that did
     * parse together with a [SourceReport] describing what was dropped (AC-PL-04).
     * Reserve [SourceResult.Failure] for the cases where nothing could be obtained at
     * all — the host was unreachable, the response was not a playlist, credentials were
     * rejected.
     *
     * @param request what to load and where from.
     * @return the parsed content, or a typed failure.
     */
    suspend fun load(request: SourceRequest): SourceResult
}

/**
 * A request to load one source.
 *
 * @param sourceId the local database id to stamp onto every produced item.
 * @param location the playlist URL, local document URI, or Xtream base URL.
 */
data class SourceRequest(
    val sourceId: Long,
    val location: String,
)

/** The outcome of a [MediaSource.load] call. */
sealed interface SourceResult {

    /**
     * Content was obtained. [report] may still describe skipped entries — a partial
     * success is a success, and the count is surfaced to the user (AC-PL-04).
     */
    data class Success(
        val channels: List<Channel>,
        val report: SourceReport,
    ) : SourceResult

    /** Nothing usable could be obtained. */
    data class Failure(val error: SourceError) : SourceResult
}

/**
 * What happened during a load, for surfacing to the user and for diagnostics.
 *
 * @property parsedEntries entries that produced a usable item.
 * @property skippedEntries entries that were recognised but unusable — no URL, no name,
 *   truncated at end of file. Surfaced in the UI (AC-PL-04).
 * @property hadHeader whether the playlist opened with `#EXTM3U`. A missing header is
 *   tolerated, not fatal.
 */
data class SourceReport(
    val parsedEntries: Int,
    val skippedEntries: Int,
    val hadHeader: Boolean = true,
) {
    /** True when at least one entry was dropped and the user should be told. */
    val hasSkipped: Boolean get() = skippedEntries > 0

    companion object {
        val EMPTY = SourceReport(parsedEntries = 0, skippedEntries = 0)
    }
}

/**
 * Why a load failed.
 *
 * Deliberately carries no user-facing text. The UI maps each case to a localised string
 * so that messages stay translatable and out of the domain layer (AC-NFR-08), and so no
 * raw stack trace or bare "Error" can ever reach the screen (AC-PL-07).
 */
sealed interface SourceError {

    /** The device has no usable network connection. */
    data object NoNetwork : SourceError

    /** The host did not respond in time. */
    data object Timeout : SourceError

    /** The host could not be resolved or refused the connection. */
    data object UnreachableHost : SourceError

    /** The URL returned 404. */
    data object NotFound : SourceError

    /** The URL returned some other non-success status. */
    data class HttpStatus(val code: Int) : SourceError

    /**
     * The response arrived but is not a playlist — typically a provider's HTML login or
     * error page served with status 200 (AC-PL-07).
     */
    data object NotAPlaylist : SourceError

    /** The response parsed but contained no usable entries at all. */
    data object EmptyPlaylist : SourceError

    /** A local file could not be read, or the user revoked access to it. */
    data object FileUnreadable : SourceError

    /**
     * The credentials were rejected.
     *
     * AC-XT-02 requires this to be distinguishable from a network failure: "wrong
     * password" and "server unreachable" demand completely different actions from the
     * user, and conflating them is the single most common failure in this category.
     */
    data object Unauthorized : SourceError

    /**
     * The account authenticated but its subscription has lapsed.
     *
     * Surfaced explicitly rather than being allowed to present as a generic playback
     * failure later on (AC-XT-05).
     */
    data object SubscriptionExpired : SourceError

    /** The panel reports the account as banned or disabled. */
    data object AccountDisabled : SourceError

    /**
     * The panel's firewall refused the request rather than the credentials.
     *
     * Xtream panels answer their anti-flood and anti-share rules with private status
     * codes in the 46x range (and occasionally 429) — the account still exists and its
     * streams may still play, but the API is shut to this client for a while. That is a
     * different problem from [Unauthorized] or [AccountDisabled] and needs a different
     * instruction to the user, so hammering the panel is not the remedy.
     */
    data object ProviderBlocked : SourceError

    /**
     * Anything not otherwise classified.
     *
     * [technicalDetail] is for logs and bug reports only and must never be rendered as
     * the primary user-facing message. It must never contain a host or credential.
     */
    data class Unknown(val technicalDetail: String? = null) : SourceError
}
