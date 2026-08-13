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

package dev.quiblo.source.xtream

import dev.quiblo.core.common.SubtitleFormat
import dev.quiblo.core.common.languageCodeOf
import dev.quiblo.core.common.subtitleFormatOfName
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Programme
import dev.quiblo.core.model.Season
import dev.quiblo.core.model.SeriesDetails
import dev.quiblo.core.model.SourceKind
import dev.quiblo.core.model.SubtitleFile
import dev.quiblo.core.model.SubtitleOrigin
import dev.quiblo.core.model.VodDetails
import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.Credentials
import dev.quiblo.source.api.GuideResult
import dev.quiblo.source.api.GuideSource
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.PanelBlockStore
import dev.quiblo.source.api.SeriesDetailsResult
import dev.quiblo.source.api.SeriesSource
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceReport
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import dev.quiblo.source.api.VodDetailsResult
import dev.quiblo.source.api.VodSource
import dev.quiblo.source.xtream.dto.AuthResponse
import dev.quiblo.source.xtream.dto.CategoryDto
import dev.quiblo.source.xtream.dto.EpgListingDto
import dev.quiblo.source.xtream.dto.SeriesInfoResponse
import dev.quiblo.source.xtream.dto.XtreamSubtitle
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
    blockStore: PanelBlockStore,
): XtreamSource = XtreamSource(XtreamClient(httpClient), credentialStore, blockStore)

/**
 * The Xtream Codes implementation of [MediaSource].
 *
 * Adding this required no change to any `:feature:*` module — the whole point of the
 * abstraction in docs/FREEZE.md §4.2.
 */
class XtreamSource internal constructor(
    private val client: XtreamClient,
    private val credentialStore: CredentialStore,
    private val blockStore: PanelBlockStore,
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
    private var blockedUntilEpochMillis: Long = NOT_YET_READ

    private suspend fun isBlocked(): Boolean {
        if (blockedUntilEpochMillis == NOT_YET_READ) {
            // Read once per process. A user told their provider is refusing them will
            // force-stop the app and open it again, and an in-memory-only backoff is
            // cleared by exactly that — which turns the most likely reaction to a block
            // into the thing that extends it.
            blockedUntilEpochMillis = blockStore.blockedUntil()
        }
        return now() < blockedUntilEpochMillis
    }

    /** Records a block so the other call paths stop too, and passes the error through. */
    private suspend fun <T> noteBlocked(error: SourceError, failure: (SourceError) -> T): T {
        if (error == SourceError.ProviderBlocked) {
            beginBackoff()
        }
        return failure(error)
    }

    private suspend fun beginBackoff() {
        blockedUntilEpochMillis = now() + BLOCK_BACKOFF_MILLIS
        blockStore.setBlockedUntil(blockedUntilEpochMillis)
    }

    override suspend fun load(request: SourceRequest): SourceResult {
        if (isBlocked()) return SourceResult.Failure(SourceError.ProviderBlocked)

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

        return when (val result = client.liveStreams(base, credentials)) {
            // Live is the point of the account. If it fails, the load failed.
            is ApiResult.Err -> noteBlocked(result.error, SourceResult::Failure)
            is ApiResult.Ok -> withOptionalCatalogues(
                ctx = ctx,
                live = mapLive(result.value, ctx, categories(client.liveCategories(base, credentials))),
            )
        }
    }

    /**
     * Adds films and series to the live catalogue, if the panel is still talking to us.
     *
     * VOD and series are optional: plenty of accounts carry neither, and a panel that 404s
     * on them is perfectly usable for live TV. A *block* is not an optional-content
     * failure, though, and used to be swallowed as one — so a refresh against an
     * already-blocked panel carried on and spent four more requests on it, each one
     * another strike against an account that was being refused precisely for asking too
     * often. Each step now stops the moment the panel says no.
     */
    private suspend fun withOptionalCatalogues(ctx: Context, live: Batch): SourceResult {
        val vod = if (isBlocked()) {
            null
        } else {
            optionalBatch(client.vodStreams(ctx.base, ctx.credentials)) { streams ->
                mapVod(streams, ctx, categories(client.vodCategories(ctx.base, ctx.credentials)))
            }
        }

        val series = if (vod == null || isBlocked()) {
            null
        } else {
            optionalBatch(client.series(ctx.base, ctx.credentials)) { entries ->
                mapSeries(entries, ctx, categories(client.seriesCategories(ctx.base, ctx.credentials)))
            }
        }

        return when {
            vod == null || series == null -> SourceResult.Failure(SourceError.ProviderBlocked)
            else -> assemble(live, vod, series)
        }
    }

    /**
     * A mapped batch, an empty one, or null.
     *
     * Null means only one thing — the panel is refusing us — which is what lets the caller
     * tell "this account has no films" apart from "stop asking".
     */
    private suspend fun <T> optionalBatch(
        result: ApiResult<List<T>>,
        map: suspend (List<T>) -> Batch,
    ): Batch? = when (result) {
        is ApiResult.Ok -> map(result.value)
        is ApiResult.Err -> if (result.error == SourceError.ProviderBlocked) {
            beginBackoff()
            null
        } else {
            Batch(emptyList(), 0)
        }
    }

    private fun assemble(live: Batch, vod: Batch, series: Batch): SourceResult {
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
        streams: List<dev.quiblo.source.xtream.dto.LiveStreamDto>,
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
                    categoryIndex = categories.indexFor(dto.categoryId),
                    providerStreamId = id,
                )
            }
        }
        return Batch(channels, skipped)
    }

    private fun mapVod(
        streams: List<dev.quiblo.source.xtream.dto.VodStreamDto>,
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
                    categoryIndex = categories.indexFor(dto.categoryId),
                )
            }
        }
        return Batch(channels, skipped)
    }

    private fun mapSeries(
        entries: List<dev.quiblo.source.xtream.dto.SeriesDto>,
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
                    categoryIndex = categories.indexFor(dto.categoryId),
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
        if (isBlocked()) return GuideResult.Failure(SourceError.ProviderBlocked)

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

    override suspend fun fullGuideFor(
        request: SourceRequest,
        channelKey: String,
        providerStreamId: String,
    ): GuideResult {
        if (isBlocked()) return GuideResult.Failure(SourceError.ProviderBlocked)

        val base = XtreamUrl.normalize(request.location)
            ?: return GuideResult.Failure(SourceError.UnreachableHost)
        val credentials = credentialStore.credentials(request.sourceId)
            ?: return GuideResult.Failure(SourceError.Unauthorized)

        return when (val result = client.fullEpg(base, credentials, providerStreamId)) {
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
        if (isBlocked()) return SeriesDetailsResult.Failure(SourceError.ProviderBlocked)

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
        if (isBlocked()) return VodDetailsResult.Failure(SourceError.ProviderBlocked)

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
                    subtitles = result.value.info?.subtitles
                        .orEmpty()
                        .map { it.toSubtitleFile(base) },
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

    /**
     * A category list, or none.
     *
     * A category list that fails to load costs the user their grouping and nothing more, so
     * this one failure genuinely is optional. A block is still recorded, because the caller
     * checks [isBlocked] immediately afterwards and stops.
     */
    private suspend fun categories(result: ApiResult<List<CategoryDto>>): List<CategoryDto> = when (result) {
        is ApiResult.Ok -> result.value
        is ApiResult.Err -> {
            if (result.error == SourceError.ProviderBlocked) beginBackoff()
            emptyList()
        }
    }

    /**
     * Where this category sat in the provider's list, or null when it is not in it.
     *
     * This is the ordering the panel intends. It cannot be recovered from the streams,
     * because `get_vod_streams` and `get_series` return items in an order unrelated to the
     * category list — which is why films and series came out alphabetical while live,
     * whose streams happen to arrive grouped, looked correct.
     */
    private fun List<CategoryDto>.indexFor(categoryId: String?): Int? =
        indexOfFirst { it.categoryId == categoryId }.takeIf { it >= 0 }

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

        /** Distinguishes "no block recorded" from "the stored deadline has not been read". */
        const val NOT_YET_READ = -1L
    }
}

/**
 * One panel-supplied subtitle entry, as something the player can load (INC-F10).
 *
 * **The format is a guess when the URL does not carry an extension, and the guess is SubRip.**
 * Panels routinely serve a subtitle from a path like `/subtitle/12345` with no hint of what is
 * behind it, and refusing those would drop most of the few subtitles panels actually supply.
 * SubRip is what nearly all of them are. A wrong guess costs a text track that renders nothing,
 * which is visible and recoverable; refusing costs a subtitle that existed and was never offered.
 *
 * A relative path is resolved against the panel, which is where it came from.
 */
private fun XtreamSubtitle.toSubtitleFile(base: String): SubtitleFile {
    val absolute = if (url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
    ) {
        url
    } else {
        base.trimEnd('/') + "/" + url.trimStart('/')
    }

    return SubtitleFile(
        uri = absolute,
        label = label,
        mimeType = (subtitleFormatOfName(absolute) ?: SubtitleFormat.SubRip).mimeType,
        language = language?.let(::languageCodeOf),
        origin = SubtitleOrigin.PROVIDER,
    )
}
