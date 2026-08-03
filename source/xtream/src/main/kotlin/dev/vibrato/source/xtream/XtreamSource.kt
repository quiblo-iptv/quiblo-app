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
import dev.vibrato.core.model.MediaKind
import dev.vibrato.core.model.Programme
import dev.vibrato.core.model.SourceKind
import dev.vibrato.source.api.CredentialStore
import dev.vibrato.source.api.Credentials
import dev.vibrato.source.api.GuideResult
import dev.vibrato.source.api.GuideSource
import dev.vibrato.source.api.MediaSource
import dev.vibrato.source.api.SourceError
import dev.vibrato.source.api.SourceReport
import dev.vibrato.source.api.SourceRequest
import dev.vibrato.source.api.SourceResult
import dev.vibrato.source.xtream.dto.AuthResponse
import dev.vibrato.source.xtream.dto.CategoryDto
import dev.vibrato.source.xtream.dto.EpgListingDto
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
) : MediaSource, GuideSource {

    override val kind: SourceKind = SourceKind.XTREAM

    override suspend fun load(request: SourceRequest): SourceResult {
        val base = XtreamUrl.normalize(request.location)
            ?: return SourceResult.Failure(SourceError.UnreachableHost)

        val credentials = credentialStore.credentials(request.sourceId)
            ?: return SourceResult.Failure(SourceError.Unauthorized)

        return when (val auth = client.authenticate(base, credentials)) {
            is ApiResult.Err -> SourceResult.Failure(auth.error)
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
            is ApiResult.Err -> return SourceResult.Failure(result.error)
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
            val id = dto.seriesId
            val name = dto.name
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
                    logoUrl = dto.cover,
                    groupTitle = categories.titleFor(dto.categoryId),
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
        val base = XtreamUrl.normalize(request.location)
            ?: return GuideResult.Failure(SourceError.UnreachableHost)
        val credentials = credentialStore.credentials(request.sourceId)
            ?: return GuideResult.Failure(SourceError.Unauthorized)

        return when (val result = client.shortEpg(base, credentials, providerStreamId)) {
            is ApiResult.Err -> GuideResult.Failure(result.error)

            is ApiResult.Ok -> GuideResult.Success(
                result.value.listings.mapNotNull { it.toProgramme(request.sourceId, channelKey) },
            )
        }
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
    }
}
