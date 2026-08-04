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

package dev.quiblo.source.iptvorg.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One channel in the iptv-org reference list.
 *
 * Every field but [id] and [name] is optional, and deliberately so: this is a
 * community-maintained dataset that gains and loses columns between releases, and a strict
 * DTO would turn a harmless upstream addition into a feature that stops working with no
 * way for a user to tell why.
 *
 * [logo] is nullable for the same reason. It has moved in and out of this file across
 * revisions of the API, so the client reads it when present and falls back to the separate
 * logo list when it is not.
 */
@Serializable
data class IptvOrgChannelDto(
    val id: String,
    val name: String,
    @SerialName("alt_names") val altNames: List<String> = emptyList(),
    val logo: String? = null,
    /**
     * The date a channel shut down, when it has one.
     *
     * Read so that a defunct channel's logo does not outrank a live one with a similar
     * name. Its presence, not its value, is what matters here.
     */
    val closed: String? = null,
)

/**
 * One logo, from the separate logo list.
 *
 * @property channel the [IptvOrgChannelDto.id] this belongs to.
 * @property tags variants such as a horizontal or monochrome cut. An untagged logo is the
 *   channel's ordinary one, which is what a list row wants.
 */
@Serializable
data class IptvOrgLogoDto(
    val channel: String? = null,
    val url: String,
    val tags: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
)
