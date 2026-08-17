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
 * Which of the For You rows a tile belongs to.
 *
 * Here rather than in the screen that draws them, because it is also the key the cached rows are
 * stored under and the two must be the same word. Two enums that had to agree would be a rename
 * away from a row that is written under one name and read under another — which looks exactly like
 * a cache that never warms.
 *
 * Recently Added is not one of these. It is a query against the catalogue that answers in
 * milliseconds and is always true; these three are worked out from a service's list or from a
 * scoring pass over the whole catalogue, which is what makes them worth keeping.
 */
enum class FeedRowId {
    POPULAR_MOVIES,
    POPULAR_SERIES,
    YOU_MAY_LIKE,
    ;

    /** Whether this row is a ranking, which is also whether its cached form is replaced wholesale. */
    val isRanked: Boolean get() = this != YOU_MAY_LIKE
}

/**
 * One remembered tile, in the form it was worked out rather than the form it is drawn in.
 *
 * [stableKey] is null for a popular title the provider does not carry, and is the provider's own
 * identity rather than a row id for every other tile — a refresh reassigns every id in the
 * catalogue, and a remembered id would resolve to nothing the first time anybody refreshed.
 *
 * [becauseOf] is the watched title that caused a suggestion, and it is what makes the suggestions
 * row's cache maintainable: a suggestion whose cause has since been watched again is stale in a
 * way no timestamp on the row itself could express.
 */
data class FeedRowEntry(
    val rowId: FeedRowId,
    val position: Int,
    val stableKey: String?,
    /** The provider's title, or the metadata service's for a title the provider does not carry. */
    val title: String,
    val posterUrl: String? = null,
    val kind: MediaKind = MediaKind.VOD,
    val rank: Int? = null,
    val becauseOf: String? = null,
)
