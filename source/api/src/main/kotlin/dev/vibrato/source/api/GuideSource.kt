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

import dev.vibrato.core.model.Programme

/**
 * A source that can also supply programme data.
 *
 * Deliberately separate from [MediaSource] rather than an optional method on it. Only
 * Xtream implements it in v1, so an M3U source cannot produce a guide even by accident —
 * which is what makes "no guide UI and no broken placeholder for M3U" (AC-EPG-04) a
 * structural property rather than a UI check someone can forget.
 *
 * Nothing here is Xtream-specific. An XMLTV implementation would satisfy the same
 * interface, which is the point of FREEZE §4.3.
 */
interface GuideSource {

    /**
     * Fetches the guide for one channel.
     *
     * Called on demand for channels the user can actually see, not for every entry: an
     * account with 20,000 channels would otherwise mean 20,000 requests.
     *
     * @param channelKey the identity to stamp on the returned programmes, matching
     *   `Channel.stableKey` so the guide survives a playlist refresh.
     * @param providerStreamId the provider's own id for the channel, from
     *   `Channel.providerStreamId`.
     */
    suspend fun guideFor(
        request: SourceRequest,
        channelKey: String,
        providerStreamId: String,
    ): GuideResult
}

/** The outcome of a guide fetch. */
sealed interface GuideResult {

    data class Success(val programmes: List<Programme>) : GuideResult

    data class Failure(val error: SourceError) : GuideResult
}
