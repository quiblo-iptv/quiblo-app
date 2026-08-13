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

package dev.quiblo.source.xtream.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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

@Serializable(with = SeriesInfoResponseSerializer::class)
internal data class SeriesInfoResponse(
    val seasons: List<SeasonDto> = emptyList(),
    val info: SeriesInfoDto? = null,
    val episodes: Map<String, List<EpisodeDto>> = emptyMap(),
)

/**
 * Reads `get_series_info`, whose shape varies more than most.
 *
 * `seasons` arrives as an array on some panels and as an object keyed by season number on
 * others. `episodes` is usually an object keyed by season, but a panel with no episodes
 * frequently sends `[]` instead of `{}` — and a generated serializer rejects the whole
 * response for that alone, losing the series title and artwork with it. Every element is
 * decoded independently so one malformed episode costs only that episode.
 */
internal object SeriesInfoResponseSerializer : KSerializer<SeriesInfoResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("SeriesInfoResponse")

    override fun deserialize(decoder: Decoder): SeriesInfoResponse {
        val input = decoder as? JsonDecoder ?: return SeriesInfoResponse()
        val root = input.decodeJsonElement() as? JsonObject ?: return SeriesInfoResponse()
        val json = input.json

        return SeriesInfoResponse(
            seasons = json.decodeEach(root["seasons"], SeasonDto.serializer()),
            info = json.decodeOrNull(root["info"], SeriesInfoDto.serializer()),
            episodes = json.decodeEpisodes(root["episodes"]),
        )
    }

    override fun serialize(encoder: Encoder, value: SeriesInfoResponse) {
        // Responses only; the app never sends this type.
        throw UnsupportedOperationException("SeriesInfoResponse is read-only")
    }

    /** One element, or null when the panel sent something unusable in its place. */
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private fun <T> Json.decodeOrNull(element: JsonElement?, serializer: KSerializer<T>): T? =
        if (element is JsonObject) {
            try {
                decodeFromJsonElement(serializer, element)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

    /** Every element of an array or of an object's values, dropping the unusable ones. */
    private fun <T> Json.decodeEach(element: JsonElement?, serializer: KSerializer<T>): List<T> {
        val candidates = when (element) {
            is JsonArray -> element
            is JsonObject -> element.values
            else -> emptyList()
        }
        return candidates.mapNotNull { decodeOrNull(it, serializer) }
    }

    /**
     * Episodes keyed by season.
     *
     * An array is accepted as well, grouped by each episode's own season field, because a
     * panel that flattens the map still carries the season on every entry.
     */
    private fun Json.decodeEpisodes(element: JsonElement?): Map<String, List<EpisodeDto>> =
        when (element) {
            is JsonObject -> element.mapValues { (_, value) ->
                decodeEach(value, EpisodeDto.serializer())
            }

            is JsonArray -> decodeEach(element, EpisodeDto.serializer())
                .groupBy { (it.season ?: 1).toString() }

            else -> emptyMap()
        }
}

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

/**
 * `get_vod_info` — details for one film.
 *
 * Panels put almost everything under `info`, but not consistently: the container extension
 * that makes the stream URL playable lives beside it under `movie_data` on most, and
 * inside `info` on some. Both are read.
 */
@Serializable
internal data class VodInfoResponse(
    val info: VodInfoDto? = null,
    @SerialName("movie_data") val movieData: VodMovieDataDto? = null,
)

@Serializable
internal data class VodInfoDto(
    @Serializable(FlexibleStringSerializer::class)
    val plot: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val description: String? = null,
    @SerialName("movie_image")
    @Serializable(FlexibleStringSerializer::class)
    val movieImage: String? = null,
    @SerialName("cover_big")
    @Serializable(FlexibleStringSerializer::class)
    val coverBig: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val genre: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val rating: String? = null,
    @SerialName("releasedate")
    @Serializable(FlexibleStringSerializer::class)
    val releaseDate: String? = null,
    @SerialName("release_date")
    @Serializable(FlexibleStringSerializer::class)
    val releaseDateAlt: String? = null,
    @SerialName("duration_secs")
    @Serializable(FlexibleIntSerializer::class)
    val durationSeconds: Int? = null,
    /**
     * Sidecar subtitle files the panel says it has for this film (INC-F10).
     *
     * Absent on most panels and empty on most of the rest. Read anyway, because when it is
     * populated it is the one subtitle a viewer gets without going to find a file themselves.
     */
    @Serializable(FlexibleSubtitleListSerializer::class)
    val subtitles: List<XtreamSubtitle> = emptyList(),
) {
    /** Panels use either key for the same field, and some send both with one blank. */
    val effectivePlot: String? get() = plot?.takeIf { it.isNotBlank() }
        ?: description?.takeIf { it.isNotBlank() }

    val effectiveCover: String? get() = coverBig?.takeIf { it.isNotBlank() }
        ?: movieImage?.takeIf { it.isNotBlank() }

    val effectiveReleaseDate: String? get() = releaseDate?.takeIf { it.isNotBlank() }
        ?: releaseDateAlt?.takeIf { it.isNotBlank() }
}

@Serializable
internal data class VodMovieDataDto(
    @SerialName("stream_id")
    @Serializable(FlexibleStringSerializer::class)
    val streamId: String? = null,
    @Serializable(FlexibleStringSerializer::class)
    val name: String? = null,
)
