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

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Transaction
import androidx.room.Update
import dev.quiblo.core.database.entity.CategoryOverrideEntity
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.database.entity.ChannelLogoEntity
import dev.quiblo.core.database.entity.FavoriteEntity
import dev.quiblo.core.database.entity.FeedRowEntity
import dev.quiblo.core.database.entity.PickedSubtitleEntity
import dev.quiblo.core.database.entity.PopularTitleEntity
import dev.quiblo.core.database.entity.ProfileEntity
import dev.quiblo.core.database.entity.ProgrammeEntity
import dev.quiblo.core.database.entity.ResumePositionEntity
import dev.quiblo.core.database.entity.SeriesPreferenceEntity
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.database.entity.TitleMetadataEntity
import dev.quiblo.core.database.entity.TitleOpinionEntity
import dev.quiblo.core.database.entity.WatchEventEntity
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
 * Makes a viewer's search text mean itself inside a `LIKE` pattern.
 *
 * `%` and `_` are wildcards in SQL, and the search box passes whatever was typed straight into
 * one. Untreated, a viewer typing `%` matched their entire catalogue and `_` matched any single
 * character — so searching for `HD_1080` returned `HD 1080`, `HDX1080` and everything else of
 * that shape. Never an injection risk, since the value has always been bound; simply the wrong
 * answer, in a way that looks like the search being bad at its job.
 *
 * The backslash is escaped first, because doing it last would escape the escapes.
 *
 * Every query using this must declare `ESCAPE '\'`, and every caller must pass its text
 * through here.
 */
fun escapeForLike(query: String): String = query
    .replace("\\", "\\\\")
    .replace("%", "\\%")
    .replace("_", "\\_")

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

/** What the suggestions scorer reads about one title. See [TitleMetadataDao.allFactRows]. */
data class TitleFactRow(
    val searchTitle: String,
    val kind: String,
    val year: Int,
    val genres: String?,
    val overview: String?,
    val originalLanguage: String?,
    val popularity: Double?,
    val rating: Double?,
    val releaseYear: Int?,
    val runtimeMinutes: Int?,
)

/** A stored channel's identity, for deciding what a re-sync should keep. */
data class ChannelIdentity(
    val id: Long,
    val stableKey: String,
    val addedAtEpochMillis: Long?,
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

// Fifteen queries against one table, which is one over the threshold. A DAO's size is the size
// of the questions its table is asked, and every one of these is a different question with a
// different index behind it — browse, search, favourites, recently added, the fallback for a
// playlist that carries no dates. Splitting the interface to satisfy a count would put queries
// over the same table behind two names and leave the count unchanged.
@Suppress("TooManyFunctions")
@Dao
interface ChannelDao {

    /**
     * The single browse query.
     *
     * Category, search and favourites-only are all optional predicates rather than
     * separate queries, so the four combinations cannot drift apart. Filtering in SQL is
     * what keeps search inside the 200ms budget across 20,000 rows (AC-FAV-05).
     *
     * **`mergeDuplicates` is the same shape and the same reasoning, and this is where it is
     * explained for the five other queries that carry it.** A panel listing one film in SD, HD,
     * FHD and 4K sends four rows; with merging on, only the provider's own first listing of each
     * `(searchTitle, identityYear, kind)` survives, and `TitleVersionsDao` hands the detail
     * screen the rest. It is a predicate rather than a `GROUP BY` because the paged version of
     * this query has to return whole rows and a page that shrinks after SQLite has counted it is
     * a page the pager cannot reason about; and it is in SQL rather than in Kotlin because a
     * twenty-row page that becomes five on the way to the screen scrolls wrong.
     *
     * A row with no computed identity — `searchTitle = ''`, the value a title that cleans away to
     * nothing gets — is never merged. A hundred of those share one key and merging on it would
     * collapse a hundred unrelated channels into one.
     *
     * @param groupTitle null for "all categories".
     * @param query empty for "no search". Must already be escaped with [escapeForLike].
     * @param favoritesOnly 1 to restrict to favourites, 0 for everything.
     */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
              AND f.profileId = :profileId
        WHERE c.sourceId = :sourceId
          AND c.kind = :kind
          AND (:groupTitle IS NULL OR c.groupTitle = :groupTitle)
          AND (:query = '' OR c.name LIKE '%' || :query || '%' ESCAPE '\')
          AND (:favoritesOnly = 0 OR f.stableKey IS NOT NULL)
          AND (c.scriptMask = :unknownMask OR (:hiddenMask & c.scriptMask) = 0)
          AND (:mergeDuplicates = 0 OR c.searchTitle = '' OR c.id = (
                SELECT v.id FROM channels v
                WHERE v.sourceId = c.sourceId AND v.kind = c.kind
                  AND v.searchTitle = c.searchTitle AND v.identityYear = c.identityYear
                ORDER BY v.sortIndex ASC, v.id ASC LIMIT 1))
        ORDER BY c.sortIndex ASC
        """,
    )
    @Suppress("LongParameterList")
    fun observeBrowse(
        profileId: Long,
        sourceId: Long,
        kind: String,
        groupTitle: String?,
        query: String,
        favoritesOnly: Int,
        /** 1 to show one row per title, 0 to show every listing the provider sent. */
        mergeDuplicates: Int,
        /**
         * The writing systems the viewer has hidden, as a bitmask. `0` hides nothing.
         *
         * A bitwise test in SQL, because the Kotlin filter it replaces ran a regex strip, a full
         * codepoint walk and a `Set` allocation **per title per emission** of a query that
         * returns every row of a kind — tens of thousands of them on a real account, recomputed
         * whenever anything in the feed changed.
         */
        hiddenMask: Int,
        /**
         * The mask value meaning "nobody has computed this row's scripts yet".
         *
         * Rows carrying it are passed through here and filtered in Kotlin, exactly as every row
         * was before schema 19. That is what lets a catalogue written by an older version keep
         * hiding what it always hid while `CatalogueIdentityBackfill` works through it, instead
         * of hiding nothing — or, since unknown has every bit set, hiding everything.
         */
        unknownMask: Int,
    ): Flow<List<ChannelWithFavorite>>

    /**
     * The browse query again, a page at a time.
     *
     * **The same predicates, deliberately — and it is one query written twice for one reason.**
     * Room cannot return both a `Flow<List<…>>` and a `PagingSource` from one declaration, and
     * the two consumers genuinely want different things: the television's poster grid groups its
     * whole answer into category rows, and a flat grid does not. Anything that changes about what
     * a browse screen shows has to change in both, which is what
     * `PagedBrowseMatchesUnpagedTest` exists to catch.
     *
     * The `LIMIT`/`OFFSET` is Room's, not written here: a `PagingSource` return type is what
     * makes it generate one.
     */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
              AND f.profileId = :profileId
        WHERE c.sourceId = :sourceId
          AND c.kind = :kind
          AND (:groupTitle IS NULL OR c.groupTitle = :groupTitle)
          AND (:query = '' OR c.name LIKE '%' || :query || '%' ESCAPE '\')
          AND (:favoritesOnly = 0 OR f.stableKey IS NOT NULL)
          AND (c.scriptMask = :unknownMask OR (:hiddenMask & c.scriptMask) = 0)
          AND (:mergeDuplicates = 0 OR c.searchTitle = '' OR c.id = (
                SELECT v.id FROM channels v
                WHERE v.sourceId = c.sourceId AND v.kind = c.kind
                  AND v.searchTitle = c.searchTitle AND v.identityYear = c.identityYear
                ORDER BY v.sortIndex ASC, v.id ASC LIMIT 1))
        ORDER BY c.sortIndex ASC
        """,
    )
    @Suppress("LongParameterList")
    fun pagedBrowse(
        profileId: Long,
        sourceId: Long,
        kind: String,
        groupTitle: String?,
        query: String,
        favoritesOnly: Int,
        mergeDuplicates: Int,
        hiddenMask: Int,
        unknownMask: Int,
    ): PagingSource<Int, ChannelWithFavorite>

    /**
     * The first [perCategory] items of every category of one kind, in one query.
     *
     * **For the television's poster grid, which cannot be paged and should not be.** That screen
     * groups its answer into a row per category; a paged list has no whole to group, so Paging is
     * the wrong tool there. What it was actually doing wrong is simpler: it read *every* row of a
     * kind — thirty thousand of them on a real account — to draw about forty tiles per row, and
     * grouped the lot in Kotlin on every emission.
     *
     * A window function caps each category before any of it leaves SQLite, so the answer is the
     * size of what is drawn rather than the size of the catalogue. Nobody walks three thousand
     * tiles along one row with a D-pad; `BrowseFeed.recentLimit` is the same reasoning already
     * applied to Recently Added.
     *
     * The ordering is the outer query's, not the window's. `ROW_NUMBER` needs its own
     * `PARTITION BY`, and leaving the result in that order would hand the screen its categories
     * grouped by name — where the category *order* is the provider's and is read from elsewhere
     * (`observeCategoriesByKind`). Sorting by `sortIndex` returns it to the order every other
     * feed uses and lets the grouping in Kotlin stay exactly as it was.
     *
     * Window functions need SQLite 3.25; `minSdk` here is 30, which ships 3.28.
     */
    @Query(
        """
        SELECT * FROM (
            SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite,
                   ROW_NUMBER() OVER (PARTITION BY c.groupTitle ORDER BY c.sortIndex ASC) AS rowInCategory
            FROM channels c
            LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
                  AND f.profileId = :profileId
            WHERE c.sourceId = :sourceId
              AND c.kind = :kind
              AND (:query = '' OR c.name LIKE '%' || :query || '%' ESCAPE '\')
              AND (c.scriptMask = :unknownMask OR (:hiddenMask & c.scriptMask) = 0)
              AND (:mergeDuplicates = 0 OR c.searchTitle = '' OR c.id = (
                    SELECT v.id FROM channels v
                    WHERE v.sourceId = c.sourceId AND v.kind = c.kind
                      AND v.searchTitle = c.searchTitle AND v.identityYear = c.identityYear
                    ORDER BY v.sortIndex ASC, v.id ASC LIMIT 1))
        )
        WHERE rowInCategory <= :perCategory
        ORDER BY sortIndex ASC
        """,
    )
    // `rowInCategory` is the window function's own counter and exists only to be compared
    // against `perCategory` in the outer `WHERE`. Room is right that nothing reads it; it is
    // not a column that should be mapped, and `@RewriteQueriesToDropUnusedColumns` is not the
    // answer either — dropping it would take the predicate that uses it with it.
    @Suppress("LongParameterList")
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    fun observeCategoryRows(
        profileId: Long,
        sourceId: Long,
        kind: String,
        query: String,
        perCategory: Int,
        /**
         * 1 to draw one tile per title, exactly as in [observeBrowse].
         *
         * Inside the window, not outside it: `ROW_NUMBER` has to count the rows that will be
         * drawn. Applied after it, a category whose first four listings were four copies of one
         * film would come back as one tile where the count beside it promised twenty.
         */
        mergeDuplicates: Int,
        hiddenMask: Int,
        unknownMask: Int,
    ): Flow<List<ChannelWithFavorite>>

    /** Favourites across every content type, for the dedicated section (AC-FAV-01). */
    @Query(
        """
        SELECT c.*, 1 AS isFavorite
        FROM channels c
        INNER JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
          AND f.profileId = :profileId
        WHERE c.sourceId = :sourceId
          AND (:query = '' OR c.name LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY c.kind ASC, c.sortIndex ASC
        """,
    )
    fun observeFavorites(profileId: Long, sourceId: Long, query: String): Flow<List<ChannelWithFavorite>>

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
        SELECT groupTitle, COUNT(*) AS itemCount FROM channels c
        WHERE c.sourceId = :sourceId AND c.kind = :kind
          AND (:mergeDuplicates = 0 OR c.searchTitle = '' OR c.id = (
                SELECT v.id FROM channels v
                WHERE v.sourceId = c.sourceId AND v.kind = c.kind
                  AND v.searchTitle = c.searchTitle AND v.identityYear = c.identityYear
                ORDER BY v.sortIndex ASC, v.id ASC LIMIT 1))
        GROUP BY c.groupTitle
        ORDER BY MIN(COALESCE(c.categoryIndex, 2147483647)) ASC, MIN(c.sortIndex) ASC
        """,
    )
    fun observeCategoriesByKind(sourceId: Long, kind: String, mergeDuplicates: Int): Flow<List<CategoryCount>>

    /**
     * One kind's worth of matches for a search term, capped.
     *
     * A one-shot read rather than a [Flow], because search is a question asked once per
     * keystroke and answered once — a subscription per kind that stays open would re-run
     * three queries on every write to the table while the viewer is still typing.
     *
     * The cap is what makes searching a 67,000-channel account safe: a two-letter term
     * matches thousands of rows, and nobody scrolls past the first screenful of them.
     *
     * **The cap is now honest, because the script filter is inside the query.** It used to run in
     * Kotlin after `LIMIT` had already cut the list, so the query overscanned by a fixed multiple
     * to make up for what the filter would throw away — a guess that was too small on a catalogue
     * where a hidden script is most of it, and wasted work everywhere else. A row that will be
     * hidden is no longer read.
     */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
              AND f.profileId = :profileId
        WHERE c.sourceId = :sourceId
          AND c.kind = :kind
          AND c.name LIKE '%' || :query || '%' ESCAPE '\'
          AND (:includeHidden OR c.groupTitle NOT IN (
                SELECT o.originalTitle FROM category_overrides o
                WHERE o.profileId = :profileId AND o.kind = c.kind AND o.isHidden = 1))
          AND (c.scriptMask = :unknownMask OR (:hiddenMask & c.scriptMask) = 0)
          AND (:mergeDuplicates = 0 OR c.searchTitle = '' OR c.id = (
                SELECT v.id FROM channels v
                WHERE v.sourceId = c.sourceId AND v.kind = c.kind
                  AND v.searchTitle = c.searchTitle AND v.identityYear = c.identityYear
                ORDER BY v.sortIndex ASC, v.id ASC LIMIT 1))
        ORDER BY c.sortIndex ASC
        LIMIT :limit
        """,
    )
    // Room binds each `:name` to a parameter of its own; a query's arguments cannot be bundled
    // into a value the way the repository above bundles them. Same reason as `observeBrowse`.
    @Suppress("LongParameterList")
    suspend fun search(
        profileId: Long,
        sourceId: Long,
        kind: String,
        query: String,
        limit: Int,
        /**
         * Whether categories the viewer has hidden are searched too.
         *
         * Hidden used to mean "not in the category list" and nothing else, so a category switched
         * off in Settings kept answering every search — the one place a viewer is least able to
         * tell where a result came from. It now means hidden, and this flag is the
         * advanced-search toggle asking for it back.
         */
        includeHidden: Boolean,
        /** 1 to answer with one row per title, exactly as in [observeBrowse]. */
        mergeDuplicates: Int,
        /** The hidden writing systems as a bitmask, exactly as in [observeBrowse]. */
        hiddenMask: Int,
        /** The "not computed yet" mask, exactly as in [observeBrowse]. */
        unknownMask: Int,
    ): List<ChannelWithFavorite>

    /**
     * The films and series in one genre, straight out of the metadata cache.
     *
     * **This query is `021`.** The genre filter used to read every film and series a source
     * carries — fifty thousand rows on this project's own provider — clean each of their titles
     * in Kotlin to a cache key, and intersect the result with the cached genres. Cleaning a title
     * is eight regex passes, so a single press of a genre chip was four hundred thousand regex
     * applications on a television processor, repeated in full for the next press. It was
     * reported as advanced search hanging. It was not hanging.
     *
     * The cleaned key now lives on the row, so this is a join: `title_metadata` is already keyed
     * by `(searchTitle, kind, year)` and `channels` now carries all three.
     *
     * `genres` is a newline-separated list, so a genre is matched by wrapping both sides in
     * newlines — that is what stops `Drama` matching `Documentary`'s neighbours or a genre whose
     * name is another's prefix. `LIKE` is case-insensitive for ASCII in SQLite, which is what the
     * Kotlin `equals(ignoreCase = true)` it replaces did.
     *
     * A blank `searchTitle` is excluded. It is the existing "there was nothing here worth looking
     * up" value, and a hundred junk rows share it — joining on it would file them all under
     * whatever genre the one cached blank key happens to carry.
     *
     * **A year narrows the same join.** `:year = 0` means "any", which is how one query serves a
     * genre alone, a year alone, and both together. The year compared is the service's
     * `releaseYear` and the provider's `year` only where the service gave none: the provider's is
     * read out of a string somebody typed into a playlist, and a filter that trusted it first
     * would file a 2019 remake under whatever the panel's title happened to say.
     */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        INNER JOIN title_metadata m ON m.searchTitle = c.searchTitle
              AND m.kind = c.kind AND m.year = c.identityYear
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
              AND f.profileId = :profileId
        WHERE c.sourceId = :sourceId
          AND c.kind = :kind
          AND c.searchTitle != ''
          AND m.isMiss = 0
          AND (:query = '' OR c.name LIKE '%' || :query || '%' ESCAPE '\')
          AND (:genre = '' OR (char(10) || COALESCE(m.genres, '') || char(10))
              LIKE '%' || char(10) || :genre || char(10) || '%')
          AND (:year = 0 OR COALESCE(m.releaseYear, m.year) = :year)
          AND (:includeHidden OR c.groupTitle NOT IN (
                SELECT o.originalTitle FROM category_overrides o
                WHERE o.profileId = :profileId AND o.kind = c.kind AND o.isHidden = 1))
          AND (c.scriptMask = :unknownMask OR (:hiddenMask & c.scriptMask) = 0)
          AND (:mergeDuplicates = 0 OR c.searchTitle = '' OR c.id = (
                SELECT v.id FROM channels v
                WHERE v.sourceId = c.sourceId AND v.kind = c.kind
                  AND v.searchTitle = c.searchTitle AND v.identityYear = c.identityYear
                ORDER BY v.sortIndex ASC, v.id ASC LIMIT 1))
        ORDER BY c.sortIndex ASC
        LIMIT :limit
        """,
    )
    @Suppress("LongParameterList")
    suspend fun searchByMetadata(
        profileId: Long,
        sourceId: Long,
        kind: String,
        genre: String,
        year: Int,
        query: String,
        limit: Int,
        includeHidden: Boolean,
        mergeDuplicates: Int,
        hiddenMask: Int,
        unknownMask: Int,
    ): List<ChannelWithFavorite>

    /**
     * The films and series a provider added most recently, newest first.
     *
     * Films and series in one list rather than a query each: "what is new on this service"
     * is one question, and answering it twice and interleaving the answers in Kotlin would
     * mean fetching twice the cap to be sure of the top of it.
     *
     * `addedAtEpochMillis IS NOT NULL` is the load-bearing clause. An M3U playlist carries no
     * dates at all and every one of its rows is null here, so this returns nothing for one —
     * which is what lets [observeHasAddedDates] pick the other query rather than this one
     * returning an ordering it does not have.
     *
     * [sinceEpochMillis] is the window: a title added eight months ago is not news, and a
     * service that has added nothing this month should say so rather than fill a row with last
     * spring. The caller owns the clock, as everything with a `now` in this project does.
     */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
              AND f.profileId = :profileId
        WHERE c.sourceId = :sourceId
          AND c.kind IN ('VOD', 'SERIES')
          AND c.addedAtEpochMillis IS NOT NULL
          AND c.addedAtEpochMillis >= :sinceEpochMillis
          AND (:mergeDuplicates = 0 OR c.searchTitle = '' OR c.id = (
                SELECT v.id FROM channels v
                WHERE v.sourceId = c.sourceId AND v.kind = c.kind
                  AND v.searchTitle = c.searchTitle AND v.identityYear = c.identityYear
                ORDER BY v.sortIndex ASC, v.id ASC LIMIT 1))
        ORDER BY c.addedAtEpochMillis DESC, c.sortIndex ASC
        LIMIT :limit
        """,
    )
    fun observeRecentlyAdded(
        profileId: Long,
        sourceId: Long,
        sinceEpochMillis: Long,
        limit: Int,
        mergeDuplicates: Int,
    ): Flow<List<ChannelWithFavorite>>

    /**
     * Whether this source dates anything at all.
     *
     * The question is about the *source*, not about the window: a panel that has added nothing
     * for six weeks still dates its catalogue, and answering "no dates" for it would swap a row
     * that is empty and honest for one that is full and invented. Observed rather than read
     * once, so the first import of a fresh playlist flips it without anybody reopening the tab.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM channels
            WHERE sourceId = :sourceId AND kind IN ('VOD', 'SERIES') AND addedAtEpochMillis IS NOT NULL
        )
        """,
    )
    fun observeHasAddedDates(sourceId: Long): Flow<Boolean>

    /**
     * The end of the provider's own list for one kind, last entry first.
     *
     * The fallback for a playlist that carries no dates, and it claims less than the query
     * above: this is where a title sits in the list, which is *usually* the order things were
     * appended and is never promised to be. `017` refused to invent an ordering at all and the
     * screen said so instead; the request since is that the tab show something, so the ordering
     * is the provider's own rather than one this app made up, and the row is titled differently
     * where it is used.
     *
     * One kind per call so the caller can take the tail of each and interleave them. A single
     * query across both would return forty films and no series on any catalogue that lists its
     * films last.
     */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
              AND f.profileId = :profileId
        WHERE c.sourceId = :sourceId
          AND c.kind = :kind
        ORDER BY c.sortIndex DESC
        LIMIT :limit
        """,
    )
    fun observeLastInListOrder(
        profileId: Long,
        sourceId: Long,
        kind: String,
        limit: Int,
    ): Flow<List<ChannelWithFavorite>>

    /**
     * Every film and series title a source carries, as strings.
     *
     * For the genre filter, which matches a channel against the metadata cache by cleaned
     * title — an operation SQLite cannot express, so the comparison happens in Kotlin and
     * this is the cheapest thing that can be handed to it. Live channels are excluded because
     * nothing looks a television channel up in a film database.
     */
    @Query(
        """
        SELECT c.id, c.name, c.kind FROM channels c
        WHERE c.sourceId = :sourceId
          AND c.kind IN ('VOD', 'SERIES')
          AND (:includeHidden OR c.groupTitle NOT IN (
                SELECT o.originalTitle FROM category_overrides o
                WHERE o.profileId = :profileId AND o.kind = c.kind AND o.isHidden = 1))
        """,
    )
    suspend fun titlesForMetadata(sourceId: Long, includeHidden: Boolean, profileId: Long): List<ChannelTitle>

    /**
     * A batch of rows that predate schema 19 and still carry no computed identity.
     *
     * Ordered by id and taken in fixed-size batches rather than read whole, because this runs
     * against every row of a catalogue that can be sixty-seven thousand deep and the point of
     * doing it in the background is that it never holds all of them at once.
     *
     * No cursor and no offset, and it needs neither: filling a row takes it out of this query's
     * own predicate, so the next call returns the next unfilled rows on its own. An offset over
     * a predicate that shrinks underneath it would skip rows, which is the bug this shape cannot
     * have.
     */
    @Query(
        """
        SELECT c.id, c.name, c.kind FROM channels c
        WHERE c.scriptMask = :unknownMask
        ORDER BY c.id ASC
        LIMIT :limit
        """,
    )
    suspend fun titlesWithoutIdentity(unknownMask: Int, limit: Int): List<ChannelTitle>

    /** Writes one row's computed identity. See `CatalogueIdentityBackfill`. */
    @Query(
        """
        UPDATE channels
        SET searchTitle = :searchTitle, identityYear = :identityYear, scriptMask = :scriptMask
        WHERE id = :id
        """,
    )
    suspend fun setIdentity(id: Long, searchTitle: String, identityYear: Int, scriptMask: Int)

    /** How many rows are still waiting, so the backfill can say when it is finished. */
    @Query("SELECT COUNT(*) FROM channels WHERE scriptMask = :unknownMask")
    suspend fun countWithoutIdentity(unknownMask: Int): Int

    /**
     * How many distinct titles this source has that are worth looking up.
     *
     * **Distinct titles, not rows.** A provider that lists one film four times in four qualities
     * has one title to ask about, and counting rows would report a quarter of the coverage
     * actually held. Rows whose title cleans away to nothing are excluded from both halves of the
     * figure: they will never be looked up, so leaving them in would cap it below 100% forever
     * and make a complete cache look broken.
     */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT c.searchTitle, c.identityYear, c.kind FROM channels c
            WHERE c.sourceId = :sourceId AND c.kind IN ('VOD', 'SERIES') AND c.searchTitle != ''
        )
        """,
    )
    suspend fun countDistinctTitles(sourceId: Long): Int

    /** How many of those the metadata cache has an answer for, hit or miss. */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT c.searchTitle, c.identityYear, c.kind FROM channels c
            INNER JOIN title_metadata m ON m.searchTitle = c.searchTitle
                  AND m.kind = c.kind AND m.year = c.identityYear
            WHERE c.sourceId = :sourceId AND c.kind IN ('VOD', 'SERIES') AND c.searchTitle != ''
        )
        """,
    )
    suspend fun countDescribedTitles(sourceId: Long): Int

    /** The full rows behind ids the genre filter has already chosen. */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
              AND f.profileId = :profileId
        WHERE c.id IN (:ids)
        ORDER BY c.sortIndex ASC
        """,
    )
    suspend fun findAllByIds(profileId: Long, ids: List<Long>): List<ChannelWithFavorite>

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

    /**
     * The same, for a whole remembered row at once.
     *
     * One query rather than one per tile: a cached For You row is up to forty titles, and forty
     * round trips to answer one question is how a cache stops being faster than the thing it
     * replaced.
     */
    @Query(
        """
        SELECT c.*, (f.stableKey IS NOT NULL) AS isFavorite
        FROM channels c
        LEFT JOIN favorites f ON f.sourceId = c.sourceId AND f.stableKey = c.stableKey
              AND f.profileId = :profileId
        WHERE c.sourceId = :sourceId AND c.stableKey IN (:stableKeys)
        """,
    )
    suspend fun findAllByStableKeys(
        profileId: Long,
        sourceId: Long,
        stableKeys: List<String>,
    ): List<ChannelWithFavorite>

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

    /** Every stable key this source currently holds, with the row id and arrival date behind it. */
    @Query("SELECT id, stableKey, addedAtEpochMillis FROM channels WHERE sourceId = :sourceId")
    suspend fun identitiesForSource(sourceId: Long): List<ChannelIdentity>

    @Query("DELETE FROM channels WHERE sourceId = :sourceId AND stableKey IN (:stableKeys)")
    suspend fun deleteByStableKeys(sourceId: Long, stableKeys: List<String>)

    /**
     * Folds a freshly parsed playlist into the one already stored, rather than swapping it.
     *
     * **[replaceForSource] deletes every row and reinserts, which gives each channel a new
     * autogenerated id.** That is tolerable when a person asked for a refresh and is watching it
     * happen; it is not tolerable four days later while nobody is looking, because two facts are
     * destroyed with the ids. `addedAtEpochMillis` is the first: on an M3U playlist the provider
     * supplies no date at all, so the only honest answer to "what is new" is when *we* first saw
     * it — and a rebuild makes everything new every four days, which turns Recently Added into a
     * list of the whole catalogue. Row ids are the second: anything holding one, from a screen
     * that is open to a remembered feed row, is pointing at a row that no longer exists.
     *
     * So a title the provider still carries keeps its id and its arrival date, and only its
     * mutable fields are rewritten. A title that has arrived is inserted with the date it arrived.
     * A title the provider has dropped is deleted, because a catalogue that only grows is not a
     * catalogue.
     *
     * @param now when this sync happened, for the titles the provider does not date.
     */
    @Transaction
    @Suppress("SpreadOperator")
    suspend fun mergeForSource(sourceId: Long, channels: List<ChannelEntity>, now: Long) {
        val held = identitiesForSource(sourceId).associateBy { it.stableKey }
        val arriving = channels.map { it.stableKey }.toSet()

        held.keys.filterNot { it in arriving }
            .chunked(INSERT_CHUNK_SIZE)
            .forEach { deleteByStableKeys(sourceId, it) }

        channels.map { channel ->
            val existing = held[channel.stableKey]
            channel.copy(
                // Keeping the id is what lets anything already pointing at this row keep pointing
                // at it. A new row takes whatever the database gives it.
                id = existing?.id ?: 0L,
                // The provider's date wins when it supplies one. Otherwise: the date we first saw
                // it, kept if we have one and stamped now if this is the first time.
                addedAtEpochMillis = channel.addedAtEpochMillis
                    ?: existing?.addedAtEpochMillis
                    ?: now,
            )
        }.chunked(INSERT_CHUNK_SIZE).forEach { insertAll(it) }
    }

    companion object {
        private const val INSERT_CHUNK_SIZE = 500
    }
}

@Dao
interface ResumePositionDao {

    @Query("SELECT positionMillis FROM resume_positions WHERE profileId = :profileId AND stableKey = :stableKey")
    suspend fun positionFor(profileId: Long, stableKey: String): Long?

    /**
     * The same, as a flow.
     *
     * **What this replaces is a race.** A detail screen read the position once, from a lifecycle
     * effect that fires the instant it returns to the foreground — which on the television is the
     * same instant the player above it is being disposed and is writing the position it just
     * finished with. Nothing ordered the two, so a viewer who pressed back was regularly offered
     * "Play" for something they were four minutes into, with no way to ask again short of leaving
     * the screen and coming back.
     *
     * A flow removes the ordering question rather than answering it: a write that lands late is
     * still a write, and the button changes when it does.
     */
    @Query("SELECT positionMillis FROM resume_positions WHERE profileId = :profileId AND stableKey = :stableKey")
    fun observePositionFor(profileId: Long, stableKey: String): Flow<Long?>

    /**
     * The most recently watched of [stableKeys], for resuming a series where it was left.
     *
     * Ordered by when it was watched rather than by position: the furthest-through episode
     * is not the one a viewer was last on.
     */
    @Query(
        "SELECT * FROM resume_positions WHERE profileId = :profileId AND stableKey IN (:stableKeys) " +
            "ORDER BY updatedAtEpochMillis DESC LIMIT 1",
    )
    suspend fun mostRecentOf(profileId: Long, stableKeys: List<String>): ResumePositionEntity?

    /** The same, as a flow, for the same reason [observePositionFor] is one. */
    @Query(
        "SELECT * FROM resume_positions WHERE profileId = :profileId AND stableKey IN (:stableKeys) " +
            "ORDER BY updatedAtEpochMillis DESC LIMIT 1",
    )
    fun observeMostRecentOf(profileId: Long, stableKeys: List<String>): Flow<ResumePositionEntity?>

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
        WHERE profileId = :profileId AND sourceId = :sourceId AND kind = :kind AND title != ''
        ORDER BY updatedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    fun observeHistory(
        profileId: Long,
        sourceId: Long,
        kind: String,
        limit: Int,
    ): Flow<List<ResumePositionEntity>>

    @Query(
        """
        SELECT * FROM resume_positions
        WHERE profileId = :profileId AND sourceId = :sourceId AND kind IN (:kinds) AND title != ''
        ORDER BY updatedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    fun observeHistoryOfKinds(
        profileId: Long,
        sourceId: Long,
        kinds: List<String>,
        limit: Int,
    ): Flow<List<ResumePositionEntity>>

    @Query("DELETE FROM resume_positions WHERE profileId = :profileId AND stableKey = :stableKey")
    suspend fun delete(profileId: Long, stableKey: String)

    /** Forgets every episode of one series, which is what "remove from history" means there. */
    @Query("DELETE FROM resume_positions WHERE profileId = :profileId AND seriesStableKey = :seriesStableKey")
    suspend fun deleteForSeries(profileId: Long, seriesStableKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ResumePositionEntity)
}

@Dao
interface FavoriteDao {

    @Query(
        "SELECT EXISTS(SELECT 1 FROM favorites " +
            "WHERE profileId = :profileId AND sourceId = :sourceId AND stableKey = :stableKey)",
    )
    suspend fun isFavorite(profileId: Long, sourceId: Long, stableKey: String): Boolean

    /**
     * The same fact as a stream, for a screen that both shows and changes it.
     *
     * A detail screen cannot read it once: it owns the toggle, so a one-shot read would
     * leave the heart it just filled in showing the state from before the tap.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM favorites " +
            "WHERE profileId = :profileId AND sourceId = :sourceId AND stableKey = :stableKey)",
    )
    fun observeIsFavorite(profileId: Long, sourceId: Long, stableKey: String): Flow<Boolean>

    /** Every favourited identity for one viewer and one source, for the scorer. */
    @Query("SELECT stableKey FROM favorites WHERE profileId = :profileId AND sourceId = :sourceId")
    suspend fun keysFor(profileId: Long, sourceId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND sourceId = :sourceId AND stableKey = :stableKey")
    suspend fun remove(profileId: Long, sourceId: Long, stableKey: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE profileId = :profileId AND sourceId = :sourceId")
    suspend fun countFor(profileId: Long, sourceId: Long): Int

    /** Every favourite for one source, for export (AC-DATA-01). */
    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND sourceId = :sourceId")
    suspend fun allFor(profileId: Long, sourceId: Long): List<FavoriteEntity>
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

    /**
     * Every programme on one channel that overlaps a window (INC-F4).
     *
     * Overlaps rather than starts inside: the programme a viewer is watching began before the
     * window did, and a timeline that omitted it would open with a gap where "now" is.
     */
    @Query(
        """
        SELECT * FROM programmes
        WHERE sourceId = :sourceId AND channelKey = :channelKey
          AND endEpochMillis > :fromEpochMillis
          AND startEpochMillis < :toEpochMillis
        ORDER BY startEpochMillis ASC
        """,
    )
    fun observeBetween(
        sourceId: Long,
        channelKey: String,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): Flow<List<ProgrammeEntity>>

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
 * answer "which of these is a crime film" or "which of these is from 2019". Read as a
 * projection so the filter index stays a few kilobytes rather than the whole cache.
 */
data class TitleFilterRow(
    val searchTitle: String,
    val kind: String,
    /** Part of the key since #024, and read because the join is on the whole key. */
    val year: Int,
    /** What the service says the year is, which is what the year filter offers and matches on. */
    val releaseYear: Int?,
    val genres: String?,
    val isMiss: Boolean,
)

/**
 * Which titles the cache holds an answer for, and how old each answer is.
 *
 * Two columns and a timestamp, for the catalogue scan to subtract from its work list. It
 * asks about tens of thousands of titles and most of them are already known by the second
 * run; reading whole rows to find that out would be reading the entire cache to decide not
 * to use it.
 */
data class CachedTitleKey(
    val searchTitle: String,
    val kind: String,
    /** Part of the key since #024, and read because the subtraction is on the whole key. */
    val year: Int,
    val fetchedAtEpochMillis: Long,
)

@Dao
interface TitleMetadataDao {

    @Query(
        "SELECT * FROM title_metadata " +
            "WHERE searchTitle = :searchTitle AND kind = :kind AND year = :year",
    )
    suspend fun find(searchTitle: String, kind: String, year: Int): TitleMetadataEntity?

    /**
     * Every answer the cache holds, misses included.
     *
     * Misses are part of the answer to "how much of your catalogue has been looked up",
     * which is the number the search screen quotes. A title that matched nothing has been
     * asked about; it is known, and pretending otherwise would make the figure creep
     * upward forever without ever arriving.
     */
    @Query("SELECT searchTitle, kind, year, releaseYear, genres, isMiss FROM title_metadata")
    suspend fun allFilterRows(): List<TitleFilterRow>

    /**
     * Everything the scorer reasons over, for the whole cache, in one read.
     *
     * A projection rather than the rows themselves: the suggestions pass considers every described
     * title a provider carries, and the two heaviest columns — the cast list and the two artwork
     * URLs — are exactly the ones it never looks at. The overview *is* read, because the words in
     * it are one of the signals.
     */
    @Query(
        """
        SELECT searchTitle, kind, year, genres, overview, originalLanguage, popularity,
               rating, releaseYear, runtimeMinutes
        FROM title_metadata
        WHERE isMiss = 0
        """,
    )
    suspend fun allFactRows(): List<TitleFactRow>

    /** Every key the cache holds, with its age, for the scan to skip what it already knows. */
    @Query("SELECT searchTitle, kind, year, fetchedAtEpochMillis FROM title_metadata")
    suspend fun allKeys(): List<CachedTitleKey>

    /**
     * How many answers the cache holds.
     *
     * On screen in settings, and there for one reason: an hour of scanning went missing across a
     * restart and nothing on the device could say whether the rows had been lost or had merely
     * stopped counting. One number, read before and after, tells those two apart.
     */
    @Query("SELECT COUNT(*) FROM title_metadata")
    suspend fun count(): Int

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
interface TitleVersionDao {

    /**
     * Every listing of one title, in the provider's own order.
     *
     * The identity is the same three columns the merge predicate groups on and the metadata cache
     * is keyed by — cleaned title, year and kind — so what the detail screen offers is exactly
     * what browse folded away, and neither can drift from the other by being computed differently.
     *
     * An empty `searchTitle` returns nothing rather than everything: it is the "cleaned away to
     * nothing" value shared by every unnameable row in the catalogue.
     */
    @Query(
        """
        SELECT * FROM channels
        WHERE sourceId = :sourceId AND kind = :kind
          AND searchTitle = :searchTitle AND identityYear = :identityYear
          AND searchTitle != ''
        ORDER BY sortIndex ASC, id ASC
        """,
    )
    fun observeVersions(
        sourceId: Long,
        kind: String,
        searchTitle: String,
        identityYear: Int,
    ): Flow<List<ChannelEntity>>
}

@Dao
interface CategoryOverrideDao {

    @Query("SELECT * FROM category_overrides WHERE profileId = :profileId AND kind = :kind")
    fun observeForKind(profileId: Long, kind: String): Flow<List<CategoryOverrideEntity>>

    @Query(
        "SELECT originalTitle FROM category_overrides " +
            "WHERE profileId = :profileId AND kind = :kind AND isHidden = 1",
    )
    fun observeHiddenTitles(profileId: Long, kind: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryOverrideEntity)

    /**
     * Every row of one kind at once, for a reorder.
     *
     * One transaction rather than a write per category: moving a shelf rewrites the position of
     * all of them, and a partial write would leave a list ordered by two different rules.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CategoryOverrideEntity>)

    @Query(
        "DELETE FROM category_overrides " +
            "WHERE profileId = :profileId AND kind = :kind AND originalTitle = :originalTitle",
    )
    suspend fun clear(profileId: Long, kind: String, originalTitle: String)
}

@Dao
interface ProfileDao {

    /** Everyone, guest included — the chooser shows a guest session in progress as itself. */
    @Query("SELECT * FROM profiles ORDER BY isGuest ASC, createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun find(id: Long): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles WHERE isGuest = 0")
    suspend fun countNamed(): Int

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profiles SET avatar = :avatar WHERE id = :id")
    suspend fun setAvatar(id: Long, avatar: String?)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Ends every guest session there has ever been.
     *
     * The favourites and resume points go with the row, by foreign key rather than by a
     * second statement here — which is the point of guest being a row at all. Called at
     * startup as well as on leaving, because a process killed by the system never gets to
     * run a tidy-up and a promise kept only on the happy path is not kept.
     */
    @Query("DELETE FROM profiles WHERE isGuest = 1")
    suspend fun deleteGuests()
}

/** How one viewer reads one series. See [SeriesPreferenceEntity]. */
@Dao
interface SeriesPreferenceDao {

    @Query("SELECT * FROM series_preferences WHERE profileId = :profileId AND seriesKey = :seriesKey")
    fun observe(profileId: Long, seriesKey: String): Flow<SeriesPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: SeriesPreferenceEntity)
}

/** Subtitle files a viewer picked. See [PickedSubtitleEntity]. */
@Dao
interface PickedSubtitleDao {

    @Query("SELECT * FROM picked_subtitles WHERE stableKey = :stableKey")
    suspend fun forTitle(stableKey: String): PickedSubtitleEntity?

    @Query("SELECT * FROM picked_subtitles")
    suspend fun all(): List<PickedSubtitleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subtitle: PickedSubtitleEntity)

    @Query("DELETE FROM picked_subtitles WHERE stableKey = :stableKey")
    suspend fun delete(stableKey: String)
}

@Dao
interface PopularTitleDao {

    /**
     * The whole held list, both catalogues, in rank order.
     *
     * A one-shot read rather than a `Flow`. The row that draws this is composed on demand and the
     * table changes at most once a week — a subscription would re-run a sixty-thousand-title
     * catalogue match every time anything else wrote to the database.
     */
    @Query("SELECT * FROM popular_titles ORDER BY kind ASC, rank ASC")
    suspend fun all(): List<PopularTitleEntity>

    /**
     * When the held list was fetched, or null when nothing has been.
     *
     * The *oldest* stamp rather than the newest, so a refresh that wrote films and was then
     * interrupted before the series does not read as a complete week's answer.
     */
    @Query("SELECT MIN(fetchedAtEpochMillis) FROM popular_titles")
    suspend fun oldestFetchedAt(): Long?

    /**
     * Replaces one catalogue's list outright.
     *
     * A replace rather than an upsert: last week's rank 9 is not this week's anything, and a
     * merge would leave the tail of an older list standing behind a shorter new one.
     */
    @Transaction
    suspend fun replaceKind(kind: String, entries: List<PopularTitleEntity>) {
        clearKind(kind)
        insertAll(entries)
    }

    @Query("DELETE FROM popular_titles WHERE kind = :kind")
    suspend fun clearKind(kind: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PopularTitleEntity>)

    @Query("DELETE FROM popular_titles")
    suspend fun clear()
}

/**
 * The remembered For You rows.
 *
 * Whole rows at a time. A row is one answer — a ranking, or a set of suggestions — and half of one
 * is not a smaller answer, it is a wrong one.
 */
@Dao
interface FeedRowDao {

    @Query(
        """
        SELECT * FROM feed_rows
        WHERE profileId = :profileId AND sourceId = :sourceId
        ORDER BY rowId ASC, position ASC
        """,
    )
    suspend fun rowsFor(profileId: Long, sourceId: Long): List<FeedRowEntity>

    @Query("DELETE FROM feed_rows WHERE profileId = :profileId AND sourceId = :sourceId AND rowId = :rowId")
    suspend fun clearRow(profileId: Long, sourceId: Long, rowId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FeedRowEntity>)

    /**
     * Swaps one row's remembered contents for a new set, in one transaction.
     *
     * A row half-written is a row drawn with a gap in the middle of a ranking, and the whole point
     * of keeping it is that it can be drawn before anything has been recomputed.
     */
    @Transaction
    suspend fun replaceRow(profileId: Long, sourceId: Long, rowId: String, entries: List<FeedRowEntity>) {
        clearRow(profileId, sourceId, rowId)
        insertAll(entries)
    }

    /** Everything for one source, for when the catalogue underneath it has been replaced. */
    @Query("DELETE FROM feed_rows WHERE sourceId = :sourceId")
    suspend fun clearForSource(sourceId: Long)
}

/** How often one title has been watched, and when it was last started. */
data class WatchCount(
    val stableKey: String,
    val plays: Int,
    val lastStartedAtEpochMillis: Long,
)

/**
 * The log of what was watched, when, and from where.
 *
 * Every query here is bounded. A log is the one shape of table that grows without anything on
 * screen getting bigger, so nothing reads all of it and the repository trims it.
 */
@Dao
interface WatchEventDao {

    @Insert
    suspend fun insert(event: WatchEventEntity)

    /** The most recent events, newest first. The window a scorer reasons over. */
    @Query(
        """
        SELECT * FROM watch_events
        WHERE profileId = :profileId AND sourceId = :sourceId
        ORDER BY startedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun recent(profileId: Long, sourceId: Long, limit: Int): List<WatchEventEntity>

    /**
     * How many times each title has been watched, and when it was last started.
     *
     * A repeat viewing is the strongest thing this app observes and it is invisible in a table
     * that keeps one row per title. Counted in SQL rather than by reading the log into Kotlin,
     * because the answer is one row per title and the log is not.
     */
    @Query(
        """
        SELECT stableKey, COUNT(*) AS plays, MAX(startedAtEpochMillis) AS lastStartedAtEpochMillis
        FROM watch_events
        WHERE profileId = :profileId AND sourceId = :sourceId AND fraction >= :minimumFraction
        GROUP BY stableKey
        """,
    )
    suspend fun playCounts(profileId: Long, sourceId: Long, minimumFraction: Double): List<WatchCount>

    /** How many distinct titles this profile has watched, for the cold-start rule. */
    @Query(
        """
        SELECT COUNT(DISTINCT stableKey) FROM watch_events
        WHERE profileId = :profileId AND sourceId = :sourceId AND fraction >= :minimumFraction
        """,
    )
    suspend fun distinctTitles(profileId: Long, sourceId: Long, minimumFraction: Double): Int

    /** Drops everything older than [before], so a log cannot grow without limit. */
    @Query("DELETE FROM watch_events WHERE startedAtEpochMillis < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM watch_events WHERE profileId = :profileId AND stableKey = :stableKey")
    suspend fun deleteFor(profileId: Long, stableKey: String)
}

/** Thumbs up and thumbs down, one per title per viewer. */
@Dao
interface TitleOpinionDao {

    @Query("SELECT * FROM title_opinions WHERE profileId = :profileId")
    suspend fun allFor(profileId: Long): List<TitleOpinionEntity>

    @Query("SELECT * FROM title_opinions WHERE profileId = :profileId AND titleKey = :titleKey")
    fun observeFor(profileId: Long, titleKey: String): Flow<TitleOpinionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(opinion: TitleOpinionEntity)

    /** Clearing an opinion removes the row: absence is what "no opinion" is stored as. */
    @Query("DELETE FROM title_opinions WHERE profileId = :profileId AND titleKey = :titleKey")
    suspend fun clear(profileId: Long, titleKey: String)
}
