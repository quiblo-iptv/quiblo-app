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

package dev.quiblo.core.model

/**
 * Enrichment for one film or series, from a metadata service the user opted into.
 *
 * One shape for both because the screens want the same facts about either, and the service
 * returns them under different names rather than in different kinds. What differs is only
 * where they came from — see [authorLabel], which is the one field whose *meaning* changes.
 *
 * Everything is optional because everything is genuinely optional: a title may not match,
 * a match may carry no certificate, and a film may have no credited director. A missing
 * field is a missing field, not an error, and the screen shows what it has.
 */
data class TitleMetadata(
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    /** A regional certificate such as "PG-13". Not comparable across countries. */
    val ageRating: String? = null,
    /** Out of ten, as the service scores it. */
    val rating: Double? = null,
    /** The director of a film, or the creator of a series. See [authorLabel]. */
    val author: String? = null,
    /**
     * Which of those [author] is.
     *
     * Carried rather than inferred from the screen showing it, because the same metadata
     * reaches a poster tile, a detail screen and (later) a search result, and only the
     * fetch knows whether it asked about a film or a series.
     */
    val authorLabel: AuthorLabel = AuthorLabel.DIRECTOR,
    val topCast: List<String> = emptyList(),
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    /**
     * True when only the cheap half was fetched — a score and artwork, no cast or plot.
     *
     * A poster tile needs a number and nothing else, and fetching a full record per tile
     * would spend the user's rate limit on facts nothing is displaying. A partial record is
     * therefore a legitimate cached answer for a tile and never one for a detail screen,
     * which upgrades it in place on first open.
     */
    val isPartial: Boolean = false,
) {
    val isEmpty: Boolean
        get() = overview.isNullOrBlank() &&
            genres.isEmpty() &&
            ageRating.isNullOrBlank() &&
            rating == null &&
            author.isNullOrBlank() &&
            topCast.isEmpty()
}

/** Whether [TitleMetadata.author] names a film's director or a series' creator. */
enum class AuthorLabel {
    DIRECTOR,
    CREATOR,
}
