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

import dev.quiblo.core.model.VodDetails

/**
 * A source that can describe one film beyond what the catalogue listing carries.
 *
 * Separate from [MediaSource] for the same reason [SeriesSource] is: the catalogue call
 * returns thousands of entries, and a plot for every one of them is a payload nobody asked
 * for. Details are fetched for the single item a user opened.
 *
 * M3U implements neither. A playlist line has a name, a URL and a logo, and nothing that
 * could fill this in — which is why the movie screen degrades to art and a title rather
 * than showing an empty overview (AC-EPG-04 applies the same principle to the guide).
 */
interface VodSource {

    /**
     * Fetches the overview and metadata for one film.
     *
     * @param request what to load and where from.
     * @param vodId the provider's stream identifier for the film.
     */
    suspend fun vodDetails(
        request: SourceRequest,
        vodId: String,
    ): VodDetailsResult
}

/** The outcome of a film details fetch. */
sealed interface VodDetailsResult {

    data class Success(val details: VodDetails) : VodDetailsResult

    data class Failure(val error: SourceError) : VodDetailsResult
}
