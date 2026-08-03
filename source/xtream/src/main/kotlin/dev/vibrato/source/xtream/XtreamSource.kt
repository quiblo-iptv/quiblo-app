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

package dev.vibrato.source.xtream

import dev.vibrato.core.model.Category
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.Episode
import dev.vibrato.core.model.MediaKind
import dev.vibrato.core.model.Programme
import dev.vibrato.core.model.Season
import dev.vibrato.core.model.SeriesDetails
import dev.vibrato.core.model.SourceKind
import dev.vibrato.core.model.VodDetails
import dev.vibrato.source.api.CredentialStore
import dev.vibrato.source.api.Credentials
import dev.vibrato.source.api.GuideResult
import dev.vibrato.source.api.GuideSource
import dev.vibrato.source.api.MediaSource
import dev.vibrato.source.api.SeriesDetailsResult
import dev.vibrato.source.api.SeriesSource
import dev.vibrato.source.api.SourceError
import dev.vibrato.source.api.SourceReport
import dev.vibrato.source.api.SourceRequest
import dev.vibrato.source.api.SourceResult
import dev.vibrato.source.api.VodDetailsResult
import dev.vibrato.source.api.VodSource
import dev.vibrato.source.xtream.dto.AuthResponse
import dev.vibrato.source.xtream.dto.CategoryDto
import dev.vibrato.source.xtream.dto.EpgListingDto
import dev.vibrato.source.xtream.dto.SeriesInfoResponse
import io.ktor.client.HttpClient
import java.util.Base64

/**
 * Builds an Xtream [MediaSource].
 *
 * The HTTP plumbing stays internal to this module so consumers depend on the
 * `MediaSource` contract and nothing else.
 */
fun createXtreamSource(
    httpClient: HttpClient,
    credentialStore: CredentialStore,
): XtreamSource = XtreamSource(XtreamClient(httpClient), credentialStore)

/**
 * The Xtream Codes implementation of [MediaSource].
 *
 * Adding this required no change to any `:feature:*` module — the whole point of the
 * abstraction in docs/FREEZE.md §4.2.
 */
class XtreamSource internal constructor(
    private val client: XtreamClient,
    private val credentialStore: CredentialStore,
    private val now: () -> Long = System::currentTimeMillis,
) : MediaSource, GuideSource, SeriesSource, VodSource {

    override val kind: SourceKind = SourceKind.XTREAM

    /**
     * When the panel last refused us, plus a cooling-off period.
     *
     * A panel that has tripped its anti-flood rule answers everything with a block, and
     * every further request while blocked is another strike — so continuing to ask is how
     * a short block becomes a long one. This gate lives here, on the one class that talks
     * to the panel, so it covers the catalogue refresh, the guide, series details and film
     * details alike. It used to exist only around the guide, which left the other three
     * free to keep knocking.
     */
    private var blockedUntilEpochMillis: Long = 0L

    private val isBlocked: Boolean get() = now() < blockedUntilEpochMillis

    /** Records a block so the other call paths stop too, and passes the error through. */
    private fun <T> noteBlocked(error: SourceError, failure: (SourceError) -> T): T {
        if (error == SourceError.ProviderBlocked) {
            blockedUntilEpochMillis = now() + BLOCK_BACKOFF_MILLIS
        }
        return failure(error)
    }

    override suspend fun load(request: SourceRequest): SourceResult {
        if (isBlocked) return SourceResult.Failure(SourceError.ProviderBlocked)

        val base = XtreamUrl.normalize(request.location)
            ?: return SourceResult.Failure(SourceError.UnreachableHost)

        val credentials = credentialStore.credentials(request.sourceId)
            ?: return SourceResult.Failure(SourceError.Unauthorized)

        return when (val auth = client.authenticate(base, credentials)) {
            is ApiResult.Err -> noteBlocked(auth.error, SourceResult::Failure)
            is ApiResult.Ok -> authorised(auth.value)?.let { SourceResult.Failure(it) }
                ?: collect(base, credentials, request.sourceId)
        }
    }

    /** @return the reason to reject this account, or null when it is usable. */
    private fun authorised(auth: AuthResponse): SourceError? {
        val user = auth.userInfo ?: return SourceError.Unauthorized
        return when {
            user.auth == false -> SourceError.Unauthorized
            user.isBanned -> SourceError.AccountDisabled
            user.isExpired -> SourceError.SubscriptionExpired
            else -> null
        }
    }

    /** One content type's contribution: what was usable, and how much was not. */
    private data class Batch(val channels: List<Channel>, val skipped: Int)

    private suspend fun collect(base: String, credentials: Credentials, sourceId: Long): SourceResult {
        val ctx = Context(base, credentials, sourceId)

        val live = when (val result = client.liveStreams(base, credentials)) {
            // Live is the point of the account. If it fails, the load failed.
            is ApiResult.Err -> return noteBlocked(result.error, SourceResult::Failure)
            is ApiResult.Ok -> mapLive(result.value, ctx, client.liveCategories(base, credentials).orEmpty())
        }

        // VOD and series are optional: plenty of accounts carry neither, and a panel that
        // 404s on them is still perfectly usable for live TV.
        val vod = (client.vodStreams(base, credentials) as? ApiResult.Ok)
            ?.let { mapVod(it.value, ctx, client.vodCategories(base, credentials).orEmpty()) }
            ?: Batch(emptyList(), 0)

        val series = (client.series(base, credentials) as? ApiResult.Ok)
            ?.let { mapSeries(it.value, ctx, client.seriesCategories(base, credentials).orEmpty()) }
            ?: Batch(emptyList(), 0)

        val channels = live.channels + vod.channels + series.channels
        val skipped = live.skipped + vod.skipped + series.skipped

        return if (channels.isEmpty()) {
            SourceResult.Failure(SourceError.EmptyPlaylist)
        } else {
            SourceResult.Success(
                channels = channels,
                report = SourceReport(parsedEntries = channels.size, skippedEntries = skipped),
            )
        }
    }

    private data class Context(val base: String, val credentials: Credentials, val sourceId: Long)

    private fun mapLive(
        streams: List<dev.vibrato.source.xtream.dto.LiveStreamDto>,
        ctx: Context,
        categories: List<CategoryDto>,
    ): Batch {
        val channels = mutableListOf<Channel>()
        var skipped = 0
        streams.forEach { dto ->
            val id = dto.streamId
            val name = dto.name
            if (id.isNullOrBlank() || name.isNullOrBlank()) {
                skipped++
            } else {
                channels += Channel(
                    id = 0L,
                    sourceId = ctx.sourceId,
                    name = name,
                    streamUrl = XtreamUrl.liveStream(ctx.base, ctx.credentials.username, ctx.credentials.password, id),
                    kind = MediaKind.LIVE,
                    // The EPG key, which ties a channel to its guide and lets a favourite
                    // survive a refresh (AC-FAV-03, AC-EPG-01).
                    tvgId = dto.epgChannelId?.takeIf { it.isNotBlank() } ?: "xtream-live-$id",
                    logoUrl = dto.streamIcon,
                    groupTitle = categories.titleFor(dto.categoryId),
                    providerStreamId = id,
                )
            }
        }
        return Batch(channels, skipped)
    }

    private fun mapVod(
        streams: List<dev.vibrato.source.xtream.dto.VodStreamDto>,
        ctx: Context,
        categories: List<CategoryDto>,
    ): Batch {
        val channels = mutableListOf<Channel>()
        var skipped = 0
        streams.forEach { dto ->
            val id = dto.streamId
            val name = dto.name
            if (id.isNullOrBlank() || name.isNullOrBlank()) {
                skipped++
            } else {
                channels += Channel(
                    id = 0L,
                    sourceId = ctx.sourceId,
                    name = name,
                    streamUrl = XtreamUrl.vodStream(
                        ctx.base, ctx.credentials.username, ctx.credentials.password, id,
                        dto.containerExtension.orEmpty(),
                    ),
                    kind = MediaKind.VOD,
                    tvgId = "xtream-vod-$id",
                    logoUrl = dto.streamIcon,
                    groupTitle = categories.titleFor(dto.categoryId),
                )
            }
        }
        return Batch(channels, skipped)
    }

    private fun mapSeries(
        entries: List<dev.vibrato.source.xtream.dto.SeriesDto>,
        ctx: Context,
        categories: List<CategoryDto>,
    ): Batch {
        val channels = mutableListOf<Channel>()
        var skipped = 0
        entries.forEach { dto ->
            val id = dto.effectiveId
            val name = dto.effectiveName
            if (id.isNullOrBlank() || name.isNullOrBlank()) {
                skipped++
            } else {
                channels += Channel(
                    id = 0L,
                    sourceId = ctx.sourceId,
                    name = name,
                    // A series is a container, not a stream. Episode URLs are resolved on
                    // demand, so it carries no playable URL of its own.
                    streamUrl = "",
                    kind = MediaKind.SERIES,
                    tvgId = "xtream-series-$id",
                    logoUrl = dto.effectiveCover,
                    groupTitle = categories.titleFor(dto.categoryId),
                    providerStreamId = id,
                )
            }
        }
        return Batch(channels, skipped)
    }

    /**
     * Fetches now/next for one channel.
     *
     * Panels base64-encode programme titles and descriptions, though not always, so the
     * decode is attempted and falls back to the raw text rather than showing mojibake.
     *
     * Only `start_timestamp`/`stop_timestamp` are used. Panels also send pre-formatted
     * local strings, which carry no offset and cannot be converted safely — storing UTC
     * and formatting at render time is what makes AC-EPG-03 hold.
     */
    override suspend fun guideFor(
        request: SourceRequest,
        channelKey: String,
        providerStreamId: String,
    ): GuideResult {
        if (isBlocked) return GuideResult.Failure(SourceError.ProviderBlocked)

        val base = XtreamUrl.normalize(request.location)
            ?: return GuideResult.Failure(SourceError.UnreachableHost)
        val credentials = credentialStore.credentials(request.sourceId)
            ?: return GuideResult.Failure(SourceError.Unauthorized)

        return when (val result = client.shortEpg(base, credentials, providerStreamId)) {
            is ApiResult.Err -> noteBlocked(result.error, GuideResult::Failure)

            is ApiResult.Ok -> GuideResult.Success(
                result.value.listings.mapNotNull { it.toProgramme(request.sourceId, channelKey) },
            )
        }
    }

    override suspend fun seriesDetails(
        request: SourceRequest,
        seriesId: String,
    ): SeriesDetailsResult {
        if (isBlocked) return SeriesDetailsResult.Failure(SourceError.ProviderBlocked)

        val base = XtreamUrl.normalize(request.location)
            ?: return SeriesDetailsResult.Failure(SourceError.UnreachableHost)
        val credentials = credentialStore.credentials(request.sourceId)
            ?: return SeriesDetailsResult.Failure(SourceError.Unauthorized)

        return when (val result = client.seriesInfo(base, credentials, seriesId)) {
            is ApiResult.Err -> noteBlocked(result.error, SeriesDetailsResult::Failure)
            is ApiResult.Ok -> SeriesDetailsResult.Success(
                result.value.toSeriesDetails(base, credentials, seriesId),
            )
        }
    }

    override suspend fun vodDetails(
        request: SourceRequest,
        vodId: String,
    ): VodDetailsResult {
        if (isBlocked) return VodDetailsResult.Failure(SourceError.ProviderBlocked)

        val base = XtreamUrl.normalize(request.location)
            ?: return VodDetailsResult.Failure(SourceError.UnreachableHost)
        val credentials = credentialStore.credentials(request.sourceId)
            ?: return VodDetailsResult.Failure(SourceError.Unauthorized)

        return when (val result = client.vodInfo(base, credentials, vodId)) {
            is ApiResult.Err -> noteBlocked(result.error, VodDetailsResult::Failure)
            is ApiResult.Ok -> VodDetailsResult.Success(
                VodDetails(
                    vodId = vodId,
                    title = result.value.movieData?.name?.takeIf { it.isNotBlank() }.orEmpty(),
                    overview = result.value.info?.effectivePlot,
                    coverUrl = result.value.info?.effectiveCover,
                    releaseDate = result.value.info?.effectiveReleaseDate,
                    genre = result.value.info?.genre,
                    rating = result.value.info?.rating,
                    durationSeconds = result.value.info?.durationSeconds,
                ),
            )
        }
    }

    private fun SeriesInfoResponse.toSeriesDetails(
        base: String,
        credentials: Credentials,
        seriesId: String,
    ): SeriesDetails {
        val seriesTitle = info?.name.orEmpty()
        val coverUrl = info?.cover
        val overview = info?.plot

        val seasonsMap = mutableMapOf<Int, MutableList<Episode>>()

        episodes.forEach { (seasonKey, dtoList) ->
            val seasonNum = seasonKey.toIntOrNull() ?: 1
            val episodeList = seasonsMap.getOrPut(seasonNum) { mutableListOf() }
            dtoList.forEach { dto ->
                val epId = dto.id ?: return@forEach
                val epNum = dto.episodeNum ?: (episodeList.size + 1)
                val epTitle = dto.title?.takeIf { it.isNotBlank() } ?: "Episode $epNum"
                val ext = dto.containerExtension.orEmpty()
                val streamUrl = XtreamUrl.seriesStream(base, credentials.username, credentials.password, epId, ext)
                val logo = dto.info?.movieImage

                episodeList.add(
                    Episode(
                        id = epId,
                        title = epTitle,
                        seasonNumber = seasonNum,
                        episodeNumber = epNum,
                        streamUrl = streamUrl,
                        logoUrl = logo,
                    )
                )
            }
        }

        val seasonsList = seasons.map { seasonDto ->
            val num = seasonDto.seasonNumber ?: 1
            val name = seasonDto.name?.takeIf { it.isNotBlank() } ?: "Season $num"
            val eps = seasonsMap[num]?.sortedBy { it.episodeNumber }.orEmpty()
            Season(seasonNumber = num, name = name, episodes = eps)
        }.ifEmpty {
            seasonsMap.keys.sorted().map { num ->
                val eps = seasonsMap[num]?.sortedBy { it.episodeNumber }.orEmpty()
                Season(seasonNumber = num, name = "Season $num", episodes = eps)
            }
        }

        return SeriesDetails(
            seriesId = seriesId,
            title = seriesTitle,
            overview = overview,
            coverUrl = coverUrl,
            seasons = seasonsList,
        )
    }

    /**
     * Converts one listing, dropping anything unusable.
     *
     * A programme with no title, no timestamps, or a stop before its start is not worth
     * showing and would render as a broken row.
     */
    private fun EpgListingDto.toProgramme(sourceId: Long, channelKey: String): Programme? {
        val start = startEpochSeconds ?: return null
        val end = stopEpochSeconds ?: return null
        if (end <= start) return null
        val name = title?.decodeMaybeBase64()?.takeIf { it.isNotBlank() } ?: return null

        return Programme(
            id = 0L,
            sourceId = sourceId,
            channelKey = channelKey,
            title = name,
            description = description?.decodeMaybeBase64()?.takeIf { it.isNotBlank() },
            startEpochMillis = start * MILLIS_PER_SECOND,
            endEpochMillis = end * MILLIS_PER_SECOND,
        )
    }

    /**
     * Decodes base64 when the value actually is base64, otherwise returns it unchanged.
     *
     * Panels usually base64-encode programme titles, but not all of them do, and some do
     * it inconsistently within one response. Decoding blindly turns plain text into
     * mojibake, so a decode that yields control characters is rejected as a false
     * positive and the original is kept.
     */
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private fun String.decodeMaybeBase64(): String = try {
        val decoded = String(Base64.getDecoder().decode(this), Charsets.UTF_8)
        val looksLikeText = decoded.none { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
        if (looksLikeText) decoded else this
    } catch (_: IllegalArgumentException) {
        this
    } catch (_: Exception) {
        this
    }

    private fun ApiResult<List<CategoryDto>>.orEmpty(): List<CategoryDto> =
        (this as? ApiResult.Ok)?.value ?: emptyList()

    private fun List<CategoryDto>.titleFor(categoryId: String?): String =
        firstOrNull { it.categoryId == categoryId }
            ?.categoryName
            ?.takeIf { it.isNotBlank() }
            ?: Category.UNGROUPED_TITLE

    private companion object {
        const val MILLIS_PER_SECOND = 1000L

        /**
         * How long to stop asking after the panel refuses us.
         *
         * Long enough that an anti-flood counter has time to decay, short enough that a
         * user who waits a moment and retries is not told to come back tomorrow.
         */
        const val BLOCK_BACKOFF_MILLIS = 15L * 60L * 1000L
    }
}
