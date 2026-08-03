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
 * What a provider can say about one film beyond its name and artwork.
 *
 * Every field past the id is optional because every field is optional in practice. Panels
 * disagree about which of these they populate, and a film with no plot is a film with no
 * plot — not an error, and not a reason to fail the screen.
 *
 * @property coverUrl artwork from the details call, which is often larger than the
 *   thumbnail the catalogue listing carried.
 * @property durationSeconds runtime, where the panel reports one.
 */
data class VodDetails(
    val vodId: String,
    val title: String,
    val overview: String? = null,
    val coverUrl: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val durationSeconds: Int? = null,
)
