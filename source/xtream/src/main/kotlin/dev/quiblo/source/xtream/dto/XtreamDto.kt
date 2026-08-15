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
    /**
     * When the panel added this film to the service. Unix seconds.
     *
     * Null on a panel that omits it, and that stays null all the way to the screen rather
     * than becoming a zero — a zero sorts as 1970 and would park every dateless title at the
     * bottom of a list that claims to be ordered by date.
     */
    @SerialName("added")
    @Serializable(FlexibleLongSerializer::class)
    val addedEpochSeconds: Long? = null,
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
    /** Unix seconds. Sent by the panels that treat a series the way they treat a film. */
    @SerialName("added")
    @Serializable(FlexibleLongSerializer::class)
    val addedEpochSeconds: Long? = null,
    /**
     * Unix seconds, and the field most panels actually send for a series.
     *
     * It means "the last time this series changed", which for a series is when an episode
     * landed — the thing a viewer looking for what is new wants to know about a series, and
     * closer to it than the date the container was first created.
     */
    @SerialName("last_modified")
    @Serializable(FlexibleLongSerializer::class)
    val lastModifiedEpochSeconds: Long? = null,
) {
    val effectiveId: String? get() = seriesId?.takeIf { it.isNotBlank() }
        ?: id?.takeIf { it.isNotBlank() }
        ?: streamId?.takeIf { it.isNotBlank() }

    val effectiveName: String? get() = name?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }

    val effectiveCover: String? get() = cover?.takeIf { it.isNotBlank() }
        ?: streamIcon?.takeIf { it.isNotBlank() }

    /**
     * When this series last changed, whichever field the panel chose to say it in.
     *
     * `added` wins where both are present: it is the field a panel sets deliberately, and
     * `last_modified` is touched by edits that are not new episodes — a renamed category or
     * a re-scraped cover would otherwise push an unchanged series to the front of a row
     * that promises new arrivals.
     */
    val effectiveAddedEpochSeconds: Long? get() = addedEpochSeconds ?: lastModifiedEpochSeconds
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
    // Spelled both ways across panels, exactly as it is on a film. See VodInfoDto.
    @SerialName("releaseDate")
    @Serializable(FlexibleStringSerializer::class)
    val releaseDate: String? = null,
    @SerialName("release_date")
    @Serializable(FlexibleStringSerializer::class)
    val releaseDateAlt: String? = null,
) {
    val effectiveReleaseDate: String? get() = releaseDate?.takeIf { it.isNotBlank() }
        ?: releaseDateAlt?.takeIf { it.isNotBlank() }
}

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
    /** Seconds, on the panels that count in seconds. */
    @SerialName("duration_secs")
    @Serializable(FlexibleIntSerializer::class)
    val durationSeconds: Int? = null,
    /**
     * `HH:MM:SS`, on the panels that write it out.
     *
     * Both fields are read because panels send one, the other, or both, and a series whose
     * episodes all say nothing because the app looked for the wrong spelling is the sort of
     * gap nobody reports — it looks like a provider that does not supply lengths.
     */
    @Serializable(FlexibleStringSerializer::class)
    val duration: String? = null,
) {
    /**
     * The length in seconds, from whichever field carries it.
     *
     * Zero and negative become null: a panel sending `"duration": "00:00:00"` is saying it does
     * not know, and an episode listed as no seconds long is not a fact worth drawing.
     */
    val effectiveDurationSeconds: Int? get() =
        (durationSeconds ?: duration?.let(::parseClockDuration))?.takeIf { it > 0 }
}

/**
 * `HH:MM:SS` or `MM:SS` to seconds, or null for anything else.
 *
 * Written out rather than handed to a date parser: this is not a time of day, and a formatter
 * that accepts one would happily read `25:00:00` as a wrapped clock rather than as the
 * twenty-five hours a mislabelled box set claims to be.
 */
private fun parseClockDuration(value: String): Int? {
    val parts = value.trim().split(':')
    val numbers = parts.map { it.toIntOrNull() }
    return when {
        parts.size < SHORTEST_CLOCK || parts.size > LONGEST_CLOCK -> null
        numbers.any { it == null || it < 0 } -> null
        else -> numbers.filterNotNull().fold(0) { total, part -> total * SECONDS_PER_UNIT + part }
    }
}

/** `MM:SS`. */
private const val SHORTEST_CLOCK = 2

/** `HH:MM:SS`. */
private const val LONGEST_CLOCK = 3

private const val SECONDS_PER_UNIT = 60

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
