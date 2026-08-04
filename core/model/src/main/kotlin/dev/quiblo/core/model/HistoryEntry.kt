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
 * Something the user started and can carry on with.
 *
 * Self-describing on purpose. A resume point used to be a position and nothing else, which
 * is enough to answer "where was I in this film" when you already have the film — and not
 * enough to *list* what someone has been watching. A series episode makes the gap plain:
 * its identity is the episode's stream URL, which belongs to no row in the channel table,
 * so a history row that carried only a key would have no title and no artwork to render.
 *
 * @property stableKey the provider identity playback was recorded against: a film's own
 *   key, or an episode's stream URL.
 * @property seriesStableKey the parent series' identity when this is an episode, and null
 *   for a film. This is what collapses six watched episodes into one "continue watching"
 *   tile, and what a "remove from history" on a series screen deletes by.
 * @property title the series' or film's name — never the episode's. The tile shows the
 *   thing being recognised, with [seasonNumber] and [episodeNumber] beneath it.
 * @property durationMillis the item's total length, or zero when the player never reported
 *   one. Zero means the progress bar is omitted rather than drawn at zero.
 */
data class HistoryEntry(
    val stableKey: String,
    val sourceId: Long,
    val kind: MediaKind,
    val title: String,
    val artworkUrl: String? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val watchedAtEpochMillis: Long = 0L,
    val seriesStableKey: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
) {

    /** True when this row records an episode rather than a whole title. */
    val isEpisode: Boolean get() = seriesStableKey != null

    /**
     * What identifies the *title* this entry belongs to.
     *
     * An episode answers with its series, so two episodes of one series are one thing in a
     * history list. A film answers with itself.
     */
    val titleKey: String get() = seriesStableKey ?: stableKey

    /**
     * How far through, from 0 to 1, or null when the length is unknown.
     *
     * Null rather than zero because the two are different on screen: an unknown length has
     * no bar at all, where zero would draw an empty one and claim the viewer got nowhere.
     */
    val progress: Float?
        get() = if (durationMillis > 0L) {
            (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
}
