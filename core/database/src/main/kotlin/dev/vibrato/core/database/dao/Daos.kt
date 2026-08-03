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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.vibrato.core.database.entity.ChannelEntity
import dev.vibrato.core.database.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources ORDER BY createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun findById(id: Long): SourceEntity?

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

/** A category and how many items it holds, computed rather than stored. */
data class CategoryCount(
    val groupTitle: String,
    val itemCount: Int,
)

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY sortIndex ASC")
    fun observeBySource(sourceId: Long): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT * FROM channels
        WHERE sourceId = :sourceId AND kind = :kind
        ORDER BY sortIndex ASC
        """,
    )
    fun observeBySourceAndKind(sourceId: Long, kind: String): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT * FROM channels
        WHERE sourceId = :sourceId AND kind = :kind AND groupTitle = :groupTitle
        ORDER BY sortIndex ASC
        """,
    )
    fun observeByGroupAndKind(sourceId: Long, kind: String, groupTitle: String): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT groupTitle, COUNT(*) AS itemCount FROM channels
        WHERE sourceId = :sourceId AND kind = :kind
        GROUP BY groupTitle
        ORDER BY groupTitle ASC
        """,
    )
    fun observeCategoriesByKind(sourceId: Long, kind: String): Flow<List<CategoryCount>>

    @Query(
        """
        SELECT * FROM channels
        WHERE sourceId = :sourceId AND groupTitle = :groupTitle
        ORDER BY sortIndex ASC
        """,
    )
    fun observeByGroup(sourceId: Long, groupTitle: String): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT groupTitle, COUNT(*) AS itemCount FROM channels
        WHERE sourceId = :sourceId
        GROUP BY groupTitle
        ORDER BY groupTitle ASC
        """,
    )
    fun observeCategories(sourceId: Long): Flow<List<CategoryCount>>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countForSource(sourceId: Long): Int

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
