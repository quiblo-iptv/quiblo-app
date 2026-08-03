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

package dev.vibrato.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.vibrato.core.database.entity.CategoryOverrideEntity
import dev.vibrato.core.database.entity.ChannelEntity
import dev.vibrato.core.database.entity.FavoriteEntity
import dev.vibrato.core.database.entity.MovieMetadataEntity
import dev.vibrato.core.database.entity.ProgrammeEntity
import dev.vibrato.core.database.entity.ResumePositionEntity
import dev.vibrato.core.database.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources ORDER BY createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun findById(id: Long): SourceEntity?

    /** A one-shot read of every source, for export (AC-DATA-01). */
    @Query("SELECT * FROM sources ORDER BY createdAtEpochMillis ASC")
    suspend fun allOnce(): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Delete
    suspend fun delete(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sources SET lastRefreshedEpochMillis = :timestamp WHERE id = :id")
    suspend fun markRefreshed(id: Long, timestamp: Long)
}

/**
 * A channel row plus whether the user has favourited it.
 *
 * The favourite flag is joined in SQL rather than combined in Kotlin so that a
 * 20,000-entry list is not re-mapped on every favourite toggle (AC-PL-05).
 */
data class ChannelWithFavorite(
    @Embedded val channel: ChannelEntity,
    val isFavorite: Boolean,
)

/** A category and how many items it holds, computed rather than stored. */
data class CategoryCount(
    val groupTitle: String,
    val itemCount: Int,
)

@Dao
interface ChannelDao {

    /**
     * The single browse query.
     *
     * Category, search and favourites-only are all optional predicates rather than
     * separate queries, so the four combinations cannot drift apart. Filtering in SQL is
     * what keeps search inside the 200ms budget across 20,000 rows (AC-FAV-05).
     *
     * @param groupTitle null for "all categories".
     * @param query empty for "no search".
     * @param favoritesOnly 1 to restrict to favourites, 0 for everything.
     */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
        WHERE c.sourceId = :sourceId
          AND c.kind = :kind
          AND (:groupTitle IS NULL OR c.groupTitle = :groupTitle)
          AND (:query = '' OR c.name LIKE '%' || :query || '%')
          AND (:favoritesOnly = 0 OR f.stableKey IS NOT NULL)
        ORDER BY c.sortIndex ASC
        """,
    )
    fun observeBrowse(
        sourceId: Long,
        kind: String,
        groupTitle: String?,
        query: String,
        favoritesOnly: Int,
    ): Flow<List<ChannelWithFavorite>>

    /** Favourites across every content type, for the dedicated section (AC-FAV-01). */
    @Query(
        """
        SELECT c.*, 1 AS isFavorite
        FROM channels c
        INNER JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
        WHERE c.sourceId = :sourceId
          AND (:query = '' OR c.name LIKE '%' || :query || '%')
        ORDER BY c.kind ASC, c.sortIndex ASC
        """,
    )
    fun observeFavorites(sourceId: Long, query: String): Flow<List<ChannelWithFavorite>>

    /**
     * Categories in the provider's own order, not alphabetically.
     *
     * Ordered by the position the provider gave the category, falling back to the position
     * of its first item. The fallback is not equivalent and is only for sources that supply
     * no category list of their own: panels return their categories in one order and their
     * *streams* in another, so inferring category order from the streams is right for live
     * — where the two happen to agree — and wrong for films and series, where they do not.
     */
    @Query(
        """
        SELECT groupTitle, COUNT(*) AS itemCount FROM channels
        WHERE sourceId = :sourceId AND kind = :kind
        GROUP BY groupTitle
        ORDER BY MIN(COALESCE(categoryIndex, 2147483647)) ASC, MIN(sortIndex) ASC
        """,
    )
    fun observeCategoriesByKind(sourceId: Long, kind: String): Flow<List<CategoryCount>>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countForSource(sourceId: Long): Int

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun findById(id: Long): ChannelEntity?

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    /**
     * Swaps in a freshly parsed playlist for one source.
     *
     * Runs as a single transaction so a refresh that fails midway cannot leave the user
     * looking at half a channel list. Inserts are chunked because SQLite binds a limited
     * number of variables per statement and a 20,000-entry playlist exceeds it
     * comfortably (AC-PL-05).
     */
    @Transaction
    suspend fun replaceForSource(sourceId: Long, channels: List<ChannelEntity>) {
        deleteForSource(sourceId)
        channels.chunked(INSERT_CHUNK_SIZE).forEach { insertAll(it) }
    }

    companion object {
        private const val INSERT_CHUNK_SIZE = 500
    }
}

@Dao
interface ResumePositionDao {

    @Query("SELECT positionMillis FROM resume_positions WHERE stableKey = :stableKey")
    suspend fun positionFor(stableKey: String): Long?

    /**
     * The most recently watched of [stableKeys], for resuming a series where it was left.
     *
     * Ordered by when it was watched rather than by position: the furthest-through episode
     * is not the one a viewer was last on.
     */
    @Query(
        "SELECT * FROM resume_positions WHERE stableKey IN (:stableKeys) " +
            "ORDER BY updatedAtEpochMillis DESC LIMIT 1",
    )
    suspend fun mostRecentOf(stableKeys: List<String>): ResumePositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ResumePositionEntity)
}

@Dao
interface FavoriteDao {

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE sourceId = :sourceId AND stableKey = :stableKey)")
    suspend fun isFavorite(sourceId: Long, stableKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE sourceId = :sourceId AND stableKey = :stableKey")
    suspend fun remove(sourceId: Long, stableKey: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE sourceId = :sourceId")
    suspend fun countFor(sourceId: Long): Int

    /** Every favourite for one source, for export (AC-DATA-01). */
    @Query("SELECT * FROM favorites WHERE sourceId = :sourceId")
    suspend fun allFor(sourceId: Long): List<FavoriteEntity>
}

@Dao
interface ProgrammeDao {

    /**
     * The programme airing at [nowEpochMillis] on one channel, and the one after it.
     *
     * Ordered by start time and limited to two, which is exactly now and next
     * (AC-EPG-02). Uses the end-time index so the query stays cheap per row.
     */
    @Query(
        """
        SELECT * FROM programmes
        WHERE sourceId = :sourceId AND channelKey = :channelKey AND endEpochMillis > :nowEpochMillis
        ORDER BY startEpochMillis ASC
        LIMIT 2
        """,
    )
    fun observeNowNext(sourceId: Long, channelKey: String, nowEpochMillis: Long): Flow<List<ProgrammeEntity>>

    /** Whatever is on now across a whole source, for rendering list rows (AC-EPG-01). */
    @Query(
        """
        SELECT * FROM programmes
        WHERE sourceId = :sourceId
          AND startEpochMillis <= :nowEpochMillis
          AND endEpochMillis > :nowEpochMillis
        """,
    )
    fun observeNowPlaying(sourceId: Long, nowEpochMillis: Long): Flow<List<ProgrammeEntity>>

    @Query("SELECT COUNT(*) FROM programmes WHERE sourceId = :sourceId AND channelKey = :channelKey")
    suspend fun countFor(sourceId: Long, channelKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND channelKey = :channelKey")
    suspend fun deleteFor(sourceId: Long, channelKey: String)

    /** Drops guide data that has already finished, so the table cannot grow without end. */
    @Query("DELETE FROM programmes WHERE endEpochMillis < :beforeEpochMillis")
    suspend fun deleteFinished(beforeEpochMillis: Long)

    @Transaction
    suspend fun replaceFor(sourceId: Long, channelKey: String, programmes: List<ProgrammeEntity>) {
        deleteFor(sourceId, channelKey)
        insertAll(programmes)
    }
}

@Dao
interface MovieMetadataDao {

    @Query("SELECT * FROM movie_metadata WHERE searchTitle = :searchTitle")
    suspend fun find(searchTitle: String): MovieMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MovieMetadataEntity)

    /** Emptied when the key changes: a different key can return different answers. */
    @Query("DELETE FROM movie_metadata")
    suspend fun clear()
}

@Dao
interface CategoryOverrideDao {

    @Query("SELECT * FROM category_overrides WHERE kind = :kind")
    fun observeForKind(kind: String): Flow<List<CategoryOverrideEntity>>

    @Query("SELECT originalTitle FROM category_overrides WHERE kind = :kind AND isHidden = 1")
    fun observeHiddenTitles(kind: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryOverrideEntity)

    @Query("DELETE FROM category_overrides WHERE kind = :kind AND originalTitle = :originalTitle")
    suspend fun clear(kind: String, originalTitle: String)
}
