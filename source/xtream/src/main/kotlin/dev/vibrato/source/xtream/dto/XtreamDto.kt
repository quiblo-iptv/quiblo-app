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

package dev.vibrato.source.xtream.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the Xtream Codes API.
 *
 * Every scalar goes through a flexible serializer and every field is nullable with a
 * default. A panel omitting or mistyping one field must degrade that field only, never
 * lose the response (AC-XT-06).
 */

@Serializable
internal data class AuthResponse(
    @SerialName("user_info") val userInfo: UserInfo? = null,
    @SerialName("server_info") val serverInfo: ServerInfo? = null,
)

@Serializable
internal data class UserInfo(
    @Serializable(FlexibleStringSerializer::class)
    val username: String? = null,
    @Serializable(FlexibleBooleanSerializer::class)
    val auth: Boolean? = null,
    @Serializable(FlexibleStringSerializer::class)
    val status: String? = null,
    /** Unix seconds. Null or empty means a non-expiring account on most panels. */
    @SerialName("exp_date")
    @Serializable(FlexibleLongSerializer::class)
    val expiryEpochSeconds: Long? = null,
    @SerialName("max_connections")
    @Serializable(FlexibleIntSerializer::class)
    val maxConnections: Int? = null,
) {
    /** True when the panel explicitly reports the account as no longer usable. */
    val isExpired: Boolean
        get() = status?.equals("Expired", ignoreCase = true) == true

    val isBanned: Boolean
        get() = status?.equals("Banned", ignoreCase = true) == true ||
            status?.equals("Disabled", ignoreCase = true) == true
}

@Serializable
internal data class ServerInfo(
    @Serializable(FlexibleStringSerializer::class)
    val url: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val port: String? = null,
    @SerialName("https_port")
    @Serializable(FlexibleStringSerializer::class)
    val httpsPort: String? = null,
)

@Serializable
internal data class CategoryDto(
    @SerialName("category_id")
    @Serializable(FlexibleStringSerializer::class)
    val categoryId: String? = null,
    @SerialName("category_name")
    @Serializable(FlexibleStringSerializer::class)
    val categoryName: String? = null,
)

@Serializable
internal data class LiveStreamDto(
    @SerialName("stream_id")
    @Serializable(FlexibleStringSerializer::class)
    val streamId: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val name: String? = null,
    @SerialName("stream_icon")
    @Serializable(FlexibleStringSerializer::class)
    val streamIcon: String? = null,
    @SerialName("epg_channel_id")
    @Serializable(FlexibleStringSerializer::class)
    val epgChannelId: String? = null,
    @SerialName("category_id")
    @Serializable(FlexibleStringSerializer::class)
    val categoryId: String? = null,
    @Serializable(FlexibleIntSerializer::class)
    val num: Int? = null,
)

@Serializable
internal data class VodStreamDto(
    @SerialName("stream_id")
    @Serializable(FlexibleStringSerializer::class)
    val streamId: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val name: String? = null,
    @SerialName("stream_icon")
    @Serializable(FlexibleStringSerializer::class)
    val streamIcon: String? = null,
    @SerialName("container_extension")
    @Serializable(FlexibleStringSerializer::class)
    val containerExtension: String? = null,
    @SerialName("category_id")
    @Serializable(FlexibleStringSerializer::class)
    val categoryId: String? = null,
)

@Serializable
internal data class SeriesDto(
    @SerialName("series_id")
    @Serializable(FlexibleStringSerializer::class)
    val seriesId: String? = null,
    @SerialName("id")
    @Serializable(FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("stream_id")
    @Serializable(FlexibleStringSerializer::class)
    val streamId: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val name: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val title: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val cover: String? = null,
    @SerialName("stream_icon")
    @Serializable(FlexibleStringSerializer::class)
    val streamIcon: String? = null,
    @SerialName("category_id")
    @Serializable(FlexibleStringSerializer::class)
    val categoryId: String? = null,
) {
    val effectiveId: String? get() = seriesId?.takeIf { it.isNotBlank() }
        ?: id?.takeIf { it.isNotBlank() }
        ?: streamId?.takeIf { it.isNotBlank() }

    val effectiveName: String? get() = name?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }

    val effectiveCover: String? get() = cover?.takeIf { it.isNotBlank() }
        ?: streamIcon?.takeIf { it.isNotBlank() }
}

@Serializable
internal data class EpgResponse(
    @SerialName("epg_listings") val listings: List<EpgListingDto> = emptyList(),
)

@Serializable
internal data class EpgListingDto(
    @Serializable(FlexibleStringSerializer::class)
    val id: String? = null,
    /** Base64 on every panel observed; decoded defensively. */
    @Serializable(FlexibleStringSerializer::class)
    val title: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val description: String? = null,
    /** Unix seconds. Panels also send a formatted `start`, which is not timezone-safe. */
    @SerialName("start_timestamp")
    @Serializable(FlexibleLongSerializer::class)
    val startEpochSeconds: Long? = null,
    @SerialName("stop_timestamp")
    @Serializable(FlexibleLongSerializer::class)
    val stopEpochSeconds: Long? = null,
    @SerialName("epg_id")
    @Serializable(FlexibleStringSerializer::class)
    val epgId: String? = null,
)

@Serializable
internal data class SeriesInfoResponse(
    val seasons: List<SeasonDto> = emptyList(),
    val info: SeriesInfoDto? = null,
    val episodes: Map<String, List<EpisodeDto>> = emptyMap(),
)

@Serializable
internal data class SeriesInfoDto(
    @Serializable(FlexibleStringSerializer::class)
    val name: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val cover: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val plot: String? = null,
)

@Serializable
internal data class SeasonDto(
    @SerialName("season_number")
    @Serializable(FlexibleIntSerializer::class)
    val seasonNumber: Int? = null,
    @Serializable(FlexibleStringSerializer::class)
    val name: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val overview: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val cover: String? = null,
)

@Serializable
internal data class EpisodeDto(
    @Serializable(FlexibleStringSerializer::class)
    val id: String? = null,
    @SerialName("episode_num")
    @Serializable(FlexibleIntSerializer::class)
    val episodeNum: Int? = null,
    @Serializable(FlexibleStringSerializer::class)
    val title: String? = null,
    @SerialName("container_extension")
    @Serializable(FlexibleStringSerializer::class)
    val containerExtension: String? = null,
    @SerialName("season")
    @Serializable(FlexibleIntSerializer::class)
    val season: Int? = null,
    val info: EpisodeInfoDto? = null,
)

@Serializable
internal data class EpisodeInfoDto(
    @SerialName("movie_image")
    @Serializable(FlexibleStringSerializer::class)
    val movieImage: String? = null,
)
