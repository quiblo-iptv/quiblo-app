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

package dev.quiblo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.quiblo.core.database.entity.CategoryOverrideEntity
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.database.entity.ChannelLogoEntity
import dev.quiblo.core.database.entity.FavoriteEntity
import dev.quiblo.core.database.entity.ProgrammeEntity
import dev.quiblo.core.database.entity.ResumePositionEntity
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.database.entity.TitleMetadataEntity
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

/**
 * Just enough of a channel to decide whether it belongs in an answer.
 *
 * A projection rather than the whole row, because the genre filter has to consider *every*
 * film and series a source carries — the metadata cache is keyed by a cleaned title, and no
 * SQL predicate can clean a title. Reading three columns of 60,000 rows is a list of strings;
 * reading the rows themselves is the browse screen's whole working set, twice over, for
 * something that will be narrowed to a screenful.
 */
data class ChannelTitle(
    val id: Long,
    val name: String,
    val kind: String,
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

    /**
     * One kind's worth of matches for a search term, capped.
     *
     * A one-shot read rather than a [Flow], because search is a question asked once per
     * keystroke and answered once — a subscription per kind that stays open would re-run
     * three queries on every write to the table while the viewer is still typing.
     *
     * The cap is what makes searching a 67,000-channel account safe: a two-letter term
     * matches thousands of rows, and nobody scrolls past the first screenful of them.
     */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
        WHERE c.sourceId = :sourceId
          AND c.kind = :kind
          AND c.name LIKE '%' || :query || '%'
        ORDER BY c.sortIndex ASC
        LIMIT :limit
        """,
    )
    suspend fun search(sourceId: Long, kind: String, query: String, limit: Int): List<ChannelWithFavorite>

    /**
     * Every film and series title a source carries, as strings.
     *
     * For the genre filter, which matches a channel against the metadata cache by cleaned
     * title — an operation SQLite cannot express, so the comparison happens in Kotlin and
     * this is the cheapest thing that can be handed to it. Live channels are excluded because
     * nothing looks a television channel up in a film database.
     */
    @Query("SELECT id, name, kind FROM channels WHERE sourceId = :sourceId AND kind IN ('VOD', 'SERIES')")
    suspend fun titlesForMetadata(sourceId: Long): List<ChannelTitle>

    /** The full rows behind ids the genre filter has already chosen. */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
        WHERE c.id IN (:ids)
        ORDER BY c.sortIndex ASC
        """,
    )
    suspend fun findAllByIds(ids: List<Long>): List<ChannelWithFavorite>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countForSource(sourceId: Long): Int

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun findById(id: Long): ChannelEntity?

    /**
     * The current row for a provider identity.
     *
     * How anything holding a stable key — a favourite, a history entry — gets back to a
     * playable row after a refresh has reassigned every id.
     */
    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND stableKey = :stableKey LIMIT 1")
    suspend fun findByStableKey(sourceId: Long, stableKey: String): ChannelEntity?

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

    /**
     * Everything watched of one kind, most recent first.
     *
     * Episodes are *not* collapsed here. One row per series is what a list wants, and SQL
     * that picks the newest row per group either needs a correlated subquery per row or
     * relies on SQLite's bare-column behaviour, both of which are more machinery than a
     * `distinctBy` over a short, already-ordered list. [limit] caps the scan instead.
     *
     * Rows with no title are pre-v8 resume points, which carry a position and nothing to
     * render — see [ResumePositionEntity].
     */
    @Query(
        """
        SELECT * FROM resume_positions
        WHERE sourceId = :sourceId AND kind = :kind AND title != ''
        ORDER BY updatedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    fun observeHistory(sourceId: Long, kind: String, limit: Int): Flow<List<ResumePositionEntity>>

    @Query("DELETE FROM resume_positions WHERE stableKey = :stableKey")
    suspend fun delete(stableKey: String)

    /** Forgets every episode of one series, which is what "remove from history" means there. */
    @Query("DELETE FROM resume_positions WHERE seriesStableKey = :seriesStableKey")
    suspend fun deleteForSeries(seriesStableKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ResumePositionEntity)
}

@Dao
interface FavoriteDao {

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE sourceId = :sourceId AND stableKey = :stableKey)")
    suspend fun isFavorite(sourceId: Long, stableKey: String): Boolean

    /**
     * The same fact as a stream, for a screen that both shows and changes it.
     *
     * A detail screen cannot read it once: it owns the toggle, so a one-shot read would
     * leave the heart it just filled in showing the state from before the tap.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE sourceId = :sourceId AND stableKey = :stableKey)")
    fun observeIsFavorite(sourceId: Long, stableKey: String): Flow<Boolean>

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

/**
 * What the metadata cache knows about one title, minus everything a filter has no use for.
 *
 * The plot, the cast and two artwork URLs are the bulk of a cached row and none of them
 * answer "which of these is a crime film". Read as a projection so the genre index stays a
 * few kilobytes rather than the whole cache.
 */
data class TitleGenreRow(
    val searchTitle: String,
    val kind: String,
    val genres: String?,
    val isMiss: Boolean,
)

@Dao
interface TitleMetadataDao {

    @Query("SELECT * FROM title_metadata WHERE searchTitle = :searchTitle AND kind = :kind")
    suspend fun find(searchTitle: String, kind: String): TitleMetadataEntity?

    /**
     * Every answer the cache holds, misses included.
     *
     * Misses are part of the answer to "how much of your catalogue has been looked up",
     * which is the number the search screen quotes. A title that matched nothing has been
     * asked about; it is known, and pretending otherwise would make the figure creep
     * upward forever without ever arriving.
     */
    @Query("SELECT searchTitle, kind, genres, isMiss FROM title_metadata")
    suspend fun allGenreRows(): List<TitleGenreRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TitleMetadataEntity)

    /** Emptied when the key changes: a different key can return different answers. */
    @Query("DELETE FROM title_metadata")
    suspend fun clear()
}

@Dao
interface ChannelLogoDao {

    @Query("SELECT logoUrl FROM channel_logos WHERE matchKey = :matchKey")
    suspend fun logoFor(matchKey: String): String?

    @Query("SELECT COUNT(*) FROM channel_logos")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logos: List<ChannelLogoEntity>)

    @Query("DELETE FROM channel_logos")
    suspend fun clear()

    /**
     * Swaps in a freshly downloaded index.
     *
     * One transaction, so a refresh interrupted halfway cannot leave the user with a
     * quarter of a logo list and no way to tell. Chunked because SQLite binds a limited
     * number of variables per statement and this index runs to tens of thousands of rows.
     */
    @Transaction
    suspend fun replaceAll(logos: List<ChannelLogoEntity>) {
        clear()
        logos.chunked(INSERT_CHUNK_SIZE).forEach { insertAll(it) }
    }

    companion object {
        private const val INSERT_CHUNK_SIZE = 500
    }
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
