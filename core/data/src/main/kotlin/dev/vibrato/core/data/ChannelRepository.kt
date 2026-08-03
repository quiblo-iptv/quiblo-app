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

package dev.vibrato.core.data

import dev.vibrato.core.database.dao.ChannelDao
import dev.vibrato.core.database.dao.ResumePositionDao
import dev.vibrato.core.database.entity.ResumePositionEntity
import dev.vibrato.core.model.Category
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.MediaKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read access to a source's content, for the browse screens.
 *
 * Every query is scoped by [MediaKind]. Live, Movies and Series are separate
 * destinations, and an Xtream account returns all three from a single load — the case
 * that makes an unfiltered query look correct right up until it is not.
 *
 * Filtering happens in SQL rather than in composition, so a 20,000-entry playlist neither
 * blocks a frame nor holds a second filtered copy in memory (AC-PL-05).
 */
class ChannelRepository(
    private val channelDao: ChannelDao,
    private val resumePositionDao: ResumePositionDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeChannels(sourceId: Long, kind: MediaKind): Flow<List<Channel>> =
        channelDao.observeBySourceAndKind(sourceId, kind.name)
            .map { entities -> entities.map { it.toDomain() } }

    fun observeChannelsInGroup(sourceId: Long, kind: MediaKind, groupTitle: String): Flow<List<Channel>> =
        channelDao.observeByGroupAndKind(sourceId, kind.name, groupTitle)
            .map { entities -> entities.map { it.toDomain() } }

    fun observeCategories(sourceId: Long, kind: MediaKind): Flow<List<Category>> =
        channelDao.observeCategoriesByKind(sourceId, kind.name)
            .map { counts -> counts.map { it.toDomain(sourceId) } }

    suspend fun channelCount(sourceId: Long): Int = channelDao.countForSource(sourceId)

    suspend fun findById(channelId: Long): Channel? = channelDao.findById(channelId)?.toDomain()

    /** Where the user got to in this item, or 0 if it has never been played (AC-PLAY-03). */
    suspend fun resumePosition(stableKey: String): Long = resumePositionDao.positionFor(stableKey) ?: 0L

    suspend fun saveResumePosition(stableKey: String, positionMillis: Long) {
        resumePositionDao.upsert(
            ResumePositionEntity(
                stableKey = stableKey,
                positionMillis = positionMillis,
                updatedAtEpochMillis = now(),
            ),
        )
    }
}
