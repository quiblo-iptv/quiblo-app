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
import dev.vibrato.core.database.dao.FavoriteDao
import dev.vibrato.core.database.dao.ResumePositionDao
import dev.vibrato.core.database.dao.SourceDao
import dev.vibrato.core.database.entity.FavoriteEntity
import dev.vibrato.core.database.entity.ResumePositionEntity
import dev.vibrato.core.model.Category
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.MediaKind
import dev.vibrato.core.model.SourceKind
import dev.vibrato.source.api.MediaSource
import dev.vibrato.source.api.SeriesDetailsResult
import dev.vibrato.source.api.SeriesSource
import dev.vibrato.source.api.SourceError
import dev.vibrato.source.api.SourceRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read access to a source's content, for the browse screens.
 *
 * Every query is scoped by [MediaKind]. Live, Movies and Series are separate
 * destinations, and an Xtream account returns all three from a single load — the case
 * that makes an unfiltered query look correct right up until it is not.
 *
 * Filtering, searching and the favourite join all happen in SQL rather than in
 * composition, so a 20,000-entry playlist neither blocks a frame nor holds a second
 * filtered copy in memory (AC-PL-05, AC-FAV-05).
 */
class ChannelRepository(
    private val channelDao: ChannelDao,
    private val resumePositionDao: ResumePositionDao,
    private val favoriteDao: FavoriteDao,
    private val sourceDao: SourceDao? = null,
    private val mediaSources: Map<SourceKind, MediaSource> = emptyMap(),
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * The browse feed.
     *
     * @param groupTitle null for every category.
     * @param query blank for no search.
     */
    fun observeBrowse(
        sourceId: Long,
        kind: MediaKind,
        groupTitle: String? = null,
        query: String = "",
        favoritesOnly: Boolean = false,
    ): Flow<List<Channel>> =
        channelDao.observeBrowse(
            sourceId = sourceId,
            kind = kind.name,
            groupTitle = groupTitle,
            query = query.trim(),
            favoritesOnly = if (favoritesOnly) 1 else 0,
        ).map { rows -> rows.map { it.channel.toDomain(isFavorite = it.isFavorite) } }

    /** Favourites across every content type (AC-FAV-01). */
    fun observeFavorites(sourceId: Long, query: String = ""): Flow<List<Channel>> =
        channelDao.observeFavorites(sourceId, query.trim())
            .map { rows -> rows.map { it.channel.toDomain(isFavorite = true) } }

    fun observeCategories(sourceId: Long, kind: MediaKind): Flow<List<Category>> =
        channelDao.observeCategoriesByKind(sourceId, kind.name)
            .map { counts -> counts.map { it.toDomain(sourceId) } }

    /**
     * Marks or unmarks a favourite.
     *
     * Keyed by the provider's stable identity, never by row id. That is what lets a
     * favourite survive a refresh in which the stream URL changed and every row was
     * reinserted with a new id (AC-FAV-03).
     */
    suspend fun toggleFavorite(channel: Channel) {
        if (favoriteDao.isFavorite(channel.sourceId, channel.stableKey)) {
            favoriteDao.remove(channel.sourceId, channel.stableKey)
        } else {
            favoriteDao.add(
                FavoriteEntity(
                    sourceId = channel.sourceId,
                    stableKey = channel.stableKey,
                    favoritedAtEpochMillis = now(),
                ),
            )
        }
    }

    suspend fun favoriteCount(sourceId: Long): Int = favoriteDao.countFor(sourceId)

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

    suspend fun getSeriesDetails(channelId: Long): SeriesDetailsResult {
        val channel = findById(channelId) ?: return SeriesDetailsResult.Failure(SourceError.NotFound)
        return getSeriesDetails(channel)
    }

    suspend fun getSeriesDetails(channel: Channel): SeriesDetailsResult {
        val seriesId = channel.providerStreamId ?: return SeriesDetailsResult.Failure(SourceError.NotFound)
        val sourceDao = sourceDao ?: return SeriesDetailsResult.Failure(SourceError.NotFound)
        val source = sourceDao.findById(channel.sourceId)?.toDomain()
            ?: return SeriesDetailsResult.Failure(SourceError.UnreachableHost)
        val seriesSource = mediaSources[source.kind] as? SeriesSource
            ?: return SeriesDetailsResult.Failure(SourceError.NotFound)

        return seriesSource.seriesDetails(
            request = SourceRequest(channel.sourceId, source.url),
            seriesId = seriesId,
        )
    }
}
