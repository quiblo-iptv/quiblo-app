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

import dev.vibrato.core.model.SeriesDetails

/**
 * A source that can supply details, seasons, and episodes for a TV series.
 *
 * Separate from [MediaSource] so that protocols supporting series containers (like Xtream)
 * can load episodes on demand.
 */
interface SeriesSource {

    /**
     * Fetches details, seasons, and episode streams for a specific TV series.
     *
     * @param request what to load and where from.
     * @param seriesId the provider's series identifier.
     */
    suspend fun seriesDetails(
        request: SourceRequest,
        seriesId: String,
    ): SeriesDetailsResult
}

/** The outcome of a series details fetch. */
sealed interface SeriesDetailsResult {

    data class Success(val details: SeriesDetails) : SeriesDetailsResult

    data class Failure(val error: SourceError) : SeriesDetailsResult
}
