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

import dev.quiblo.core.database.dao.CategoryCount
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.database.entity.ProgrammeEntity
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Programme
import dev.quiblo.core.model.Source
import dev.quiblo.core.model.SourceKind

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
)

/**
 * @param sortIndex preserves the order the provider listed items in. Playlists are often
 *   ordered meaningfully and re-sorting them alphabetically is a surprise, not a feature.
 */
internal fun Channel.toEntity(sortIndex: Int): ChannelEntity = ChannelEntity(
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
