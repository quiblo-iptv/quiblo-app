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

package dev.quiblo.core.data

import dev.quiblo.core.common.scriptMask
import dev.quiblo.core.database.dao.CategoryCount
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.database.entity.ProgrammeEntity
import dev.quiblo.core.database.entity.ResumePositionEntity
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Programme
import dev.quiblo.core.model.Source
import dev.quiblo.core.model.SourceKind
import dev.quiblo.source.tmdb.titleIdentity

internal fun SourceEntity.toDomain(): Source = Source(
    id = id,
    name = name,
    kind = runCatching { SourceKind.valueOf(kind) }.getOrDefault(SourceKind.M3U),
    url = url,
    createdAtEpochMillis = createdAtEpochMillis,
    lastRefreshedEpochMillis = lastRefreshedEpochMillis,
)

internal fun Source.toEntity(): SourceEntity = SourceEntity(
    id = id,
    name = name,
    kind = kind.name,
    url = url,
    createdAtEpochMillis = createdAtEpochMillis,
    lastRefreshedEpochMillis = lastRefreshedEpochMillis,
)

internal fun ChannelEntity.toDomain(isFavorite: Boolean = false): Channel = Channel(
    id = id,
    sourceId = sourceId,
    name = name,
    streamUrl = streamUrl,
    kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.LIVE),
    tvgId = tvgId,
    logoUrl = logoUrl,
    groupTitle = groupTitle,
    categoryIndex = categoryIndex,
    isFavorite = isFavorite,
    providerStreamId = providerStreamId,
    addedAtEpochMillis = addedAtEpochMillis,
)

/**
 * @param sortIndex preserves the order the provider listed items in. Playlists are often
 *   ordered meaningfully and re-sorting them alphabetically is a surprise, not a feature.
 *
 * **Where the cleaned identity and the script mask are worked out.** Once per row, on the way in,
 * because neither answer can change while the row exists and both used to be recomputed on every
 * read of every browse page and every press of a genre chip. This is the whole point of `021`:
 * one import pays what fifty thousand reads were paying.
 */
internal fun Channel.toEntity(sortIndex: Int): ChannelEntity {
    val identity = name.titleIdentity()
    return ChannelEntity(
        id = 0L,
        sourceId = sourceId,
        name = name,
        streamUrl = streamUrl,
        kind = kind.name,
        tvgId = tvgId,
        logoUrl = logoUrl,
        groupTitle = groupTitle,
        stableKey = stableKey,
        sortIndex = sortIndex,
        categoryIndex = categoryIndex,
        providerStreamId = providerStreamId,
        addedAtEpochMillis = addedAtEpochMillis,
        searchTitle = identity.searchTitle,
        identityYear = identity.year,
        scriptMask = name.scriptMask(),
    )
}

internal fun ResumePositionEntity.toDomain(): HistoryEntry = HistoryEntry(
    stableKey = stableKey,
    sourceId = sourceId,
    kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.VOD),
    title = title,
    artworkUrl = artworkUrl,
    positionMillis = positionMillis,
    durationMillis = durationMillis,
    watchedAtEpochMillis = updatedAtEpochMillis,
    seriesStableKey = seriesStableKey,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
)

internal fun CategoryCount.toDomain(sourceId: Long): Category = Category(
    id = groupTitle.hashCode().toLong(),
    sourceId = sourceId,
    title = groupTitle,
    itemCount = itemCount,
)

internal fun ProgrammeEntity.toDomain(): Programme = Programme(
    id = id,
    sourceId = sourceId,
    channelKey = channelKey,
    title = title,
    description = description,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
)

internal fun Programme.toEntity(): ProgrammeEntity = ProgrammeEntity(
    id = 0L,
    sourceId = sourceId,
    channelKey = channelKey,
    title = title,
    description = description,
    startEpochMillis = startEpochMillis,
    endEpochMillis = endEpochMillis,
)
