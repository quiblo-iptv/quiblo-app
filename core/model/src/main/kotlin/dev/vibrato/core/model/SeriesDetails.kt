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
 * An individual episode of a TV series.
 *
 * @property id stable episode identifier from the provider.
 * @property title title of the episode.
 * @property seasonNumber season number.
 * @property episodeNumber episode number within the season.
 * @property streamUrl playable URL for the episode stream.
 * @property logoUrl optional thumbnail or cover artwork for the episode.
 */
data class Episode(
    val id: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val streamUrl: String,
    val logoUrl: String? = null,
)

/**
 * A season containing an ordered list of [Episode] items.
 */
data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodes: List<Episode>,
)

/**
 * Detailed information for a TV series including overview, cover art, and [Season] list.
 */
data class SeriesDetails(
    val seriesId: String,
    val title: String,
    val overview: String? = null,
    val coverUrl: String? = null,
    val seasons: List<Season> = emptyList(),
)
