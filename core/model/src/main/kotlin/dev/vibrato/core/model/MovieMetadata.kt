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

package dev.vibrato.core.model

/**
 * Enrichment for one film, from a metadata service the user opted into.
 *
 * Everything is optional because everything is genuinely optional: a title may not match,
 * a match may carry no certificate, and a film may have no credited director. A missing
 * field is a missing field, not an error, and the screen shows what it has.
 */
data class MovieMetadata(
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    /** A regional certificate such as "PG-13". Not comparable across countries. */
    val ageRating: String? = null,
    /** Out of ten, as the service scores it. */
    val rating: Double? = null,
    val director: String? = null,
    val topCast: List<String> = emptyList(),
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
) {
    val isEmpty: Boolean
        get() = overview.isNullOrBlank() &&
            genres.isEmpty() &&
            ageRating.isNullOrBlank() &&
            rating == null &&
            director.isNullOrBlank() &&
            topCast.isEmpty()
}
