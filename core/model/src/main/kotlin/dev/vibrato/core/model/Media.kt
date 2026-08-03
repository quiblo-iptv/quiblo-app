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
 * The three kinds of content Vibrato handles (docs/FREEZE.md §3).
 *
 * M3U playlists do not distinguish these; everything from an M3U is [LIVE] unless a
 * future heuristic says otherwise. Xtream reports the kind explicitly.
 */
enum class MediaKind {
    LIVE,
    VOD,
    SERIES,
}

/** How a [Source] obtains its content. */
enum class SourceKind {
    /** An M3U/M3U8 playlist, fetched from a URL or read from a local file. */
    M3U,

    /** An Xtream Codes panel, authenticated with a username and password. */
    XTREAM,
}

/**
 * A user-configured content source.
 *
 * Vibrato ships with none of these and never creates one on the user's behalf
 * (docs/FREEZE.md §2). Credentials are never stored on this type — they live encrypted
 * in `:core:datastore`, keyed by [id] (AC-XT-04).
 *
 * @property id stable local identifier, assigned by the database.
 * @property name the user's own label for this source.
 * @property kind whether this is an M3U playlist or an Xtream account.
 * @property url the playlist URL, the local document URI, or the Xtream base URL.
 * @property createdAtEpochMillis when the user added it.
 * @property lastRefreshedEpochMillis when its content was last successfully loaded, or
 *   null if it has never been refreshed.
 */
data class Source(
    val id: Long,
    val name: String,
    val kind: SourceKind,
    val url: String,
    val createdAtEpochMillis: Long,
    val lastRefreshedEpochMillis: Long? = null,
) {
    companion object {
        /** The id used for a source that has not been persisted yet. */
        const val UNSAVED_ID: Long = 0L
    }
}

/**
 * A grouping of channels, from an M3U `group-title` attribute or an Xtream category.
 *
 * @property title the display name. Entries carrying no group are collected under
 *   [UNGROUPED_TITLE] rather than being hidden (AC-PL-06).
 */
data class Category(
    val id: Long,
    val sourceId: Long,
    /** The provider's own name. The stable identity, and what an override is keyed by. */
    val title: String,
    val itemCount: Int = 0,
    /** A local rename, or null to use the provider's name. Never sent anywhere. */
    val customName: String? = null,
    val isHidden: Boolean = false,
) {
    /** What to put on screen. */
    val displayTitle: String get() = customName?.takeIf { it.isNotBlank() } ?: title

    companion object {
        /**
         * The single bucket for entries with no `group-title`.
         *
         * Not localised on purpose: it is a stable storage key. The UI substitutes a
         * translated label at render time (AC-NFR-08).
         */
        const val UNGROUPED_TITLE: String = "__ungrouped__"
    }
}

/**
 * One playable item: a live channel, a movie, or a series episode.
 *
 * @property tvgId the provider's stable identifier, from the M3U `tvg-id` attribute.
 *   This is what lets a favourite survive a refresh in which the stream URL changed
 *   (AC-FAV-03), so it is preferred over [streamUrl] for identity.
 * @property streamUrl the URL to hand to the player. Not an identity field: providers
 *   rotate these.
 * @property logoUrl from `tvg-logo`. Load failures must never block a row (AC-PL-03).
 * @property groupTitle the raw group this item belongs to.
 */
data class Channel(
    val id: Long,
    val sourceId: Long,
    val name: String,
    val streamUrl: String,
    val kind: MediaKind = MediaKind.LIVE,
    val tvgId: String? = null,
    val logoUrl: String? = null,
    val groupTitle: String = Category.UNGROUPED_TITLE,
    val isFavorite: Boolean = false,
    /**
     * The provider's own stream identifier, when it has one.
     *
     * Xtream needs this to be asked for a channel's guide. M3U supplies nothing
     * equivalent, which is precisely why an M3U source shows no guide (AC-EPG-04).
     */
    val providerStreamId: String? = null,
    /**
     * Where this item's category sat in the provider's own category list.
     *
     * Kept because a category's position cannot be recovered from the items in it. Panels
     * return their category list in a deliberate order, then return the *streams* in some
     * other order entirely, so inferring category order from the first item of each group
     * gets films and series wrong. Null when the source has no category list of its own —
     * an M3U invents groups from `group-title` as it reads.
     */
    val categoryIndex: Int? = null,
) {
    /**
     * The identity used to match this item across refreshes.
     *
     * Falls back to the stream URL only when the provider supplied no `tvg-id`, which is
     * common in hand-written playlists.
     */
    val stableKey: String get() = tvgId?.takeIf { it.isNotBlank() } ?: streamUrl
}
