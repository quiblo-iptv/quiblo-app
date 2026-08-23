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

package dev.quiblo.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.quiblo.core.common.SCRIPT_MASK_UNKNOWN

/**
 * A user-configured source.
 *
 * Note what is absent: no username, no password, no token. Xtream credentials are held
 * encrypted in `:core:datastore` and never reach the database, so a database export or a
 * debug dump cannot leak them (AC-XT-04, AC-DATA-03).
 */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val kind: String,
    val url: String,
    val createdAtEpochMillis: Long,
    val lastRefreshedEpochMillis: Long? = null,
)

/**
 * One playable item belonging to a source.
 *
 * Rows are replaced wholesale on every refresh, which is why favourites are *not* a
 * column here: a favourite would be destroyed by the next refresh. They live in
 * [FavoriteEntity], keyed by the provider's stable identity instead (AC-FAV-03).
 */
@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceId"),
        Index("sourceId", "groupTitle"),
        Index("sourceId", "stableKey"),
        /**
         * The browse query, in the order it asks its questions.
         *
         * `observeBrowse` filters on `sourceId` and `kind` and then sorts by `sortIndex`,
         * and until this existed nothing covered that: SQLite matched every row belonging
         * to the source, tested `kind` one row at a time, then built a temporary B-tree to
         * sort the survivors — on every emission. At 67,000 channels that is the difference
         * between a browse screen appearing and a browse screen arriving.
         *
         * `sortIndex` is part of the index rather than left to the sort step because a
         * covering order is what removes the temporary B-tree; filtering alone would still
         * sort tens of thousands of rows by hand.
         */
        Index("sourceId", "kind", "sortIndex"),
        /**
         * The newest-first query, for the same reason and in the same shape.
         *
         * `observeRecentlyAdded` filters on `sourceId` and `kind` and sorts by
         * `addedAtEpochMillis` descending. The index above stops at `sortIndex`, so that sort
         * would have been a temporary B-tree over every film and series in the account — the
         * exact cost `Index("sourceId", "kind", "sortIndex")` exists to have removed once.
         */
        Index("sourceId", "kind", "addedAtEpochMillis"),
        /**
         * The browse query again, with the writing-system filter in front of the sort.
         *
         * `observeBrowse` gained `scriptMask` as a predicate, and a predicate SQLite cannot
         * reach through the index is a predicate it tests row by row — which is the whole cost
         * this column was added to remove. Ordered filter-then-sort, exactly as
         * `Index("sourceId", "kind", "sortIndex")` is.
         */
        Index("sourceId", "kind", "scriptMask", "sortIndex"),
        /**
         * The genre filter's join key.
         *
         * `title_metadata` is keyed by cleaned title, kind and year, so the filter is now a join
         * on those three rather than fifty thousand regex passes in Kotlin. Without this index
         * that join is a full scan of the channel table per genre press, which is the same cost
         * wearing different clothes.
         */
        Index("searchTitle", "identityYear", "kind"),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceId: Long,
    val name: String,
    val streamUrl: String,
    val kind: String,
    val tvgId: String? = null,
    val logoUrl: String? = null,
    val groupTitle: String,
    /** Denormalised copy of the identity used to survive refreshes; indexed for the join. */
    val stableKey: String,
    /** Preserves playlist order so the list renders as the provider intended. */
    val sortIndex: Int,
    /** The provider's stream id, used to request this channel's guide. */
    val providerStreamId: String? = null,
    /** Where this item's category sat in the provider's category list, when it supplies one. */
    val categoryIndex: Int? = null,
    /**
     * When the provider added this title, when it says. Null for every M3U entry.
     *
     * Indexed with `kind`, because the one query that reads it — newest films and series
     * first — filters on the kind and orders on this, and a catalogue is tens of thousands of
     * rows deep.
     */
    val addedAtEpochMillis: Long? = null,
    /**
     * This title's cleaned identity, and the year read off it — the metadata cache's key.
     *
     * **Stored rather than computed on read, and that is the whole of defect `021`.** Cleaning a
     * title is eight regex passes; a genre search cleaned every film and series on the account to
     * find the ones in a genre, which on this project's own provider is fifty thousand rows and
     * four hundred thousand regex applications *per press of a genre chip*. The answer never
     * changes for a given title, so it is worked out once when the row is written and joined on
     * afterwards.
     *
     * Blank when the title cleans away to nothing — a bare language tag, a name written entirely
     * outside Latin. That is the existing "do not ask about this one" value and it keeps meaning
     * exactly that; it is not a key a hundred junk rows may share.
     *
     * [identityYear] is `NO_YEAR` rather than null for the reason the metadata cache's own column
     * is: this is half of a key, and SQLite does not consider two nulls equal.
     */
    val searchTitle: String = "",
    val identityYear: Int = 0,
    /**
     * Which writing systems this title has a letter in, as a bitmask.
     *
     * The other half of the same decision. Hiding a script was a regex strip, a full codepoint
     * walk and a `Set` allocation **per title per emission** of a browse feed that loads every
     * row of a kind — so it is now one integer, computed at import and tested with `&` in SQL.
     *
     * `SCRIPT_MASK_UNKNOWN` until something has computed it, which is every row written before
     * schema 19. A row still carrying it is filtered in Kotlin exactly as it was before this
     * column existed, so a catalogue mid-backfill hides what it always hid rather than briefly
     * hiding nothing — or, worse, briefly hiding everything.
     */
    val scriptMask: Int = SCRIPT_MASK_UNKNOWN,
)

/**
 * Who is watching.
 *
 * A profile owns favourites and resume positions and nothing else — player settings, hidden
 * categories and the metadata key stay app-wide, because they describe the television rather
 * than the person in front of it.
 *
 * **Guest is a row like any other, flagged.** The alternative — a reserved id with no row —
 * would mean every delete of guest data was a routine somebody has to remember to call. As a
 * row it is deleted instead, and the cascades below take its favourites and its resume
 * points with it. The database enforces the promise rather than the code repeating it.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAtEpochMillis: Long,
    /** True for the one throwaway profile. At most one exists at a time. */
    val isGuest: Boolean = false,
    /**
     * Which of the illustrated faces this profile chose, or null for none.
     *
     * A key into a fixed set the app ships, never a path and never image data. Nothing of a
     * viewer's ends up in this column, so nothing of a viewer's ends up inside an export file
     * either — `AC-DATA` promises a backup is portable, and a backup carrying somebody's
     * photograph is a different promise than the one this project made.
     *
     * Nullable rather than defaulted, because null means "this profile predates avatars" and
     * the initial-on-a-colour fallback draws it. A default value would claim that every
     * profile already on a device had chosen the first face.
     */
    val avatar: String? = null,
)

/**
 * A favourite, keyed by provider identity rather than by row id.
 *
 * Surviving a refresh in which the stream URL changed is the entire point (AC-FAV-03), so
 * this table is deliberately not joined to [ChannelEntity] by primary key.
 *
 * The profile is part of the key rather than a column beside it: two people may each
 * favourite the same channel of the same source, and those are two facts.
 */
@Entity(
    tableName = "favorites",
    primaryKeys = ["profileId", "sourceId", "stableKey"],
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class FavoriteEntity(
    val sourceId: Long,
    val stableKey: String,
    val favoritedAtEpochMillis: Long,
    val profileId: Long,
)

/**
 * Where the user got to in a VOD item, and enough about it to list it as history.
 *
 * Keyed by the provider's stable identity rather than a row id, so a playlist refresh
 * that renumbers everything does not lose the resume point (AC-PLAY-03).
 *
 * The descriptive columns are denormalised rather than joined to [ChannelEntity], because
 * for a series episode there is nothing to join to: an episode's identity is its stream
 * URL, and episodes are never rows in the channel table — they are fetched per series and
 * held for a session. A history list that joined would show films and silently drop every
 * episode, which is most of what anyone actually resumes.
 *
 * Rows written before this table carried any of that keep resuming correctly and are
 * excluded from history by their empty [title]: a tile with no name and no artwork is
 * worse than one row of history missing.
 */
@Entity(
    tableName = "resume_positions",
    primaryKeys = ["profileId", "stableKey"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId", "sourceId", "kind", "updatedAtEpochMillis")],
)
data class ResumePositionEntity(
    val stableKey: String,
    val profileId: Long,
    val positionMillis: Long,
    val updatedAtEpochMillis: Long,
    val sourceId: Long = 0L,
    /** A [dev.quiblo.core.model.MediaKind] name. Empty on rows written before v8. */
    val kind: String = "",
    /** The film's or series' name — never the episode's. Empty on rows written before v8. */
    val title: String = "",
    val artworkUrl: String? = null,
    /** The item's full length, or 0 when the player never reported one. */
    val durationMillis: Long = 0L,
    /** The parent series' stable key when this row is an episode; null for a film. */
    val seriesStableKey: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

/**
 * One programme in the guide.
 *
 * Source-agnostic by design. Only Xtream supplies programme data in v1
 * (docs/FREEZE.md 3), but nothing here is Xtream-specific, so adding XMLTV later
 * needs no schema migration (FREEZE 4.3).
 *
 * Times are UTC milliseconds. Panels report a local formatted string alongside a Unix
 * timestamp and only the timestamp is trustworthy; conversion to the device's zone
 * happens at render time (AC-EPG-03).
 */
@Entity(
    tableName = "programmes",
    indices = [
        Index("sourceId", "channelKey", "startEpochMillis"),
        Index("sourceId", "channelKey", "endEpochMillis"),
        /**
         * What is on now across a whole source, which asks about no channel in particular.
         *
         * The two indices above lead with `channelKey`, so neither serves
         * `observeNowPlaying` — it filters on a source and a time window and nothing else,
         * and was scanning the whole table to do it.
         */
        Index("sourceId", "startEpochMillis", "endEpochMillis"),
    ],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceId: Long,
    /** Matches ChannelEntity.stableKey, so the guide survives a playlist refresh. */
    val channelKey: String,
    val title: String,
    val description: String? = null,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
)

/**
 * Cached film and series metadata, keyed by the cleaned search title and the kind asked for.
 *
 * Persisted rather than held in memory so a relaunch does not re-ask the service for
 * everything the user browses. A metadata provider rate-limits per key, and the key is the
 * user's own — being wasteful with it is being wasteful with something that belongs to them.
 *
 * Keyed by title rather than by channel id because a refresh reassigns every channel id,
 * and the cache would be thrown away for no reason each time a playlist reloaded.
 *
 * [kind] is half the key rather than a column, because "Fargo" is a film and "Fargo" is a
 * series and they are not the same record. With the title alone, whichever tab the user
 * opened first would answer for the other.
 */
@Entity(tableName = "title_metadata", primaryKeys = ["searchTitle", "kind", "year"])
data class TitleMetadataEntity(
    val searchTitle: String,
    /** A [dev.quiblo.core.model.MediaKind] name — `VOD` or `SERIES`. */
    val kind: String,
    /**
     * The release year the provider's title carried, or `0` when it carried none.
     *
     * The third of the key, and it was missing until #024. The cleaner that produces
     * [searchTitle] strips bracketed groups whole because that is what makes a good search
     * query — and a provider year is almost always bracketed. So `Dune (1984)` and
     * `Dune (2021)` were one row, the first fetched won, and the other film showed the
     * winner's poster and plot until the entry expired.
     *
     * `0` rather than null because SQLite does not consider two nulls equal, so a nullable
     * column in a primary key would let every undated title insert a fresh row on each visit
     * — the cache failing open, which is worse than the fault it replaced.
     */
    val year: Int = 0,
    /**
     * The service's own id for this title, when the answer carried one.
     *
     * Stored but not yet keyed on. It is the only identity in this problem that is not an
     * inference — [searchTitle] and [year] are both read out of a string a provider typed —
     * and `014`'s grouping needs an identity it can trust when two rows disagree. Filled now
     * so grouping does not cost this table a second migration for the same reason.
     */
    val tmdbId: Int? = null,
    val overview: String? = null,
    /** Newline-separated. A list table for a handful of short strings is not worth a join. */
    val genres: String? = null,
    val ageRating: String? = null,
    val rating: Double? = null,
    /** A film's director or a series' creator; which one follows from [kind]. */
    val author: String? = null,
    val topCast: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    /**
     * The year the service gives, which is not the same fact as [year].
     *
     * [year] is part of the key and comes out of the *provider's* title; this is what the
     * metadata service says, and it is what a screen shows when the panel supplied no date of
     * its own. Keeping them apart matters: the key must never move because a service disagreed
     * with a playlist about a remake.
     */
    val releaseYear: Int? = null,
    /**
     * A film's length in whole minutes, from the service.
     *
     * Null for a series: the average episode length a service offers is not the length of
     * anything a viewer is about to watch, and putting it on screen would state it as one.
     */
    val runtimeMinutes: Int? = null,
    /**
     * The language the title was made in, as TMDB's two-letter code.
     *
     * Already in every payload this app parses and discarded until `025`. It is what tells a
     * K-drama from an American series and — with Animation — anime from a cartoon, which is the
     * distinction the suggestions row was most obviously missing.
     *
     * Null means "not fetched since this column existed" as well as "the service does not know",
     * and the two are deliberately not distinguished: the ordinary fourteen-day refresh refills
     * the first, and nothing will ever refill the second.
     */
    val originalLanguage: String? = null,
    /**
     * How much of the world is watching this, as TMDB reports it.
     *
     * Not a rating. It is closer to attention than to quality, and it is here to tell a rare
     * title from a common one — somebody who watches things nobody has heard of is saying
     * something, and a row that only ever offers the top of the charts cannot hear it.
     */
    val popularity: Double? = null,
    /** When this was fetched, so a stale entry can be refreshed rather than kept forever. */
    val fetchedAtEpochMillis: Long,
    /**
     * True when the service was asked and had nothing.
     *
     * Recorded so a title with no match is not re-asked on every visit. A negative answer
     * is an answer, and re-requesting it is the most wasteful thing this cache could do.
     */
    val isMiss: Boolean = false,
    /**
     * True when only the search step ran, giving a score and artwork but no cast or plot.
     *
     * Poster tiles ask for a score for everything on screen; detail screens ask for
     * everything about one title. Recording which was fetched lets a tile be satisfied by
     * one request and a detail screen upgrade the same row rather than duplicate it.
     */
    val isPartial: Boolean = false,
)

/**
 * A logo for a channel the user's playlist gave none for, from the reference list.
 *
 * Keyed by a normalised match key rather than by anything belonging to the user's source:
 * this is a copy of a public catalogue, not a fact about their playlist. The same row
 * therefore serves every source they configure, and a playlist refresh has no bearing on it.
 *
 * A whole table for what could be a column on `channels` is deliberate. The column would be
 * destroyed on every refresh — the same reasoning that keeps favourites out of that table —
 * and it would be re-downloaded to rebuild, which is the one thing this cache exists to
 * prevent.
 */
@Entity(tableName = "channel_logos")
data class ChannelLogoEntity(
    @PrimaryKey val matchKey: String,
    val logoUrl: String,
)

/**
 * One viewer's local edits to one provider category.
 *
 * Keyed by kind and the provider's own title rather than by any id, because a category has
 * no id — it is derived by grouping channels — and because the provider's title is what
 * survives a refresh. Renaming is local only: nothing is sent anywhere, and the original
 * title is retained as the key so the edit reattaches after every reload.
 *
 * **Per profile, and it was not until schema 23.** Hiding, renaming and reordering are the same
 * kind of statement favourites are — this is what *I* want my list to look like — and one
 * household member hiding the adult categories, or renaming a shelf into their own language,
 * decided it for everybody. The upgrade copies what existed to every profile, so nobody's list
 * changes on the way over; they diverge from there.
 */
@Entity(
    tableName = "category_overrides",
    primaryKeys = ["profileId", "kind", "originalTitle"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CategoryOverrideEntity(
    val profileId: Long,
    val kind: String,
    val originalTitle: String,
    /** Null means "use the provider's name". Absence and a blank rename are the same thing. */
    val customName: String? = null,
    val isHidden: Boolean = false,
    /**
     * Where the viewer moved this category to, or null if they never did.
     *
     * A third kind of local edit, alongside hiding and renaming, and keyed the same way for the
     * same reason: a provider's own category order changes between refreshes, and a position
     * stored against anything but the provider's title would reattach to the wrong shelf.
     *
     * Nullable rather than defaulted, so a viewer who has ordered three categories out of ninety
     * gets three moved categories rather than a list in which the other eighty-seven have all
     * silently agreed to be equal.
     */
    val userOrder: Int? = null,
)

/**
 * How one viewer prefers to read one series: merged or by season, newest first or oldest.
 *
 * `INC-F6`. A series with a thousand episodes is unusable if the newest is a thousand rows away,
 * and a preference that is not remembered is a preference re-entered every evening.
 *
 * **Per profile, not global.** One household member reading a long-running series from the end
 * does not decide how anyone else reads it — the same reasoning that put favourites behind a
 * profile.
 *
 * Keyed by `seriesKey`, which is the series' [ChannelEntity.stableKey], for the reason that key
 * exists at all: a refresh reassigns every row id, and a preference keyed to one would be lost
 * the first time a playlist reloaded (`AC-FAV-03`'s lesson, applied before it could bite).
 *
 * A row exists only once somebody has changed something. Absence means the defaults, which is
 * why both columns are non-null with defaults rather than nullable.
 */
@Entity(
    tableName = "series_preferences",
    primaryKeys = ["profileId", "seriesKey"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SeriesPreferenceEntity(
    val profileId: Long,
    val seriesKey: String,
    /** True when every season is shown as one continuous list. */
    val isMerged: Boolean = false,
    /** True when the newest episode is first. */
    val isDescending: Boolean = false,
)

/**
 * A subtitle file the viewer picked for one title (INC-F10).
 *
 * **The cached copy, not the file they chose.** The picker hands back a `content://` URI whose
 * read permission belongs to this process and dies with it, so a row holding one would be a row
 * that works this evening and fails tomorrow. The file is copied into the app's cache — decoded
 * out of whatever it was written in and rewritten as UTF-8 on the way — and it is that copy this
 * row points at.
 *
 * Keyed by the title's `stableKey`, for the reason that key exists: a refresh reassigns every row
 * id, and a pick keyed to one would be lost the next time a playlist reloaded. An episode's key
 * is its stream URL, so a subtitle picked for one episode stays with that episode rather than
 * following the series.
 *
 * One row per title. Picking a second file for the same title replaces the first, because two
 * files a viewer cannot tell apart in a menu is not a feature.
 */
@Entity(tableName = "picked_subtitles")
data class PickedSubtitleEntity(
    @PrimaryKey val stableKey: String,
    /** An absolute path inside this app's own storage. */
    val storedPath: String,
    /** What the menu calls it — the picked file's own name. */
    val label: String,
    val mimeType: String,
    /** An ISO 639 code where the filename declared one the platform recognises. */
    val language: String? = null,
    val pickedAt: Long,
)

/**
 * One entry of a TMDB popular list, held for a week.
 *
 * **A cache of an answer, not a catalogue of its own.** Nothing here is a title the viewer owns;
 * it is a name, a year and a position, kept only long enough to look for that name in the
 * catalogue every time the row is composed. The record a detail screen reads still comes from
 * `title_metadata`, by the same path as every other title.
 *
 * Keyed by kind and rank rather than by `tmdbId`, because the row's whole content is *TMDB's
 * order* — the same film at rank 2 this week and rank 9 next week is a different fact, and a
 * primary key on the id would make the two indistinguishable without a second column anyway.
 *
 * `fetchedAtEpochMillis` is stamped on every row of a fetch rather than held once elsewhere, so
 * a half-written refresh cannot leave a table that claims to be newer than its contents.
 */
@Entity(tableName = "popular_titles", primaryKeys = ["kind", "rank"])
data class PopularTitleEntity(
    /** `VOD` or `SERIES`, the app's own vocabulary rather than TMDB's `movie` and `tv`. */
    val kind: String,
    val rank: Int,
    val tmdbId: Int,
    val title: String,
    /** Null where TMDB has no release date. Not zero: nothing joins on this column. */
    val year: Int? = null,
    val posterUrl: String? = null,
    val fetchedAtEpochMillis: Long,
)

/**
 * A For You row, as it was last worked out.
 *
 * **This is a cache of arithmetic, not of anything the viewer owns.** Both rows it holds are
 * expensive in a way the tab could not hide: the popular rows are a service's list matched against
 * a sixty-thousand-title catalogue, and the suggestions row was a full scoring pass over the same
 * catalogue *on every emission of the metadata key*. Neither answer changes between one evening
 * and the next, and both were being recomputed while a viewer watched an empty shelf.
 *
 * Keyed by profile as well as source, because the suggestions row is one person's viewing and a
 * cache shared across profiles would be the leak `AC-PROF-02` exists to prevent.
 *
 * **Titles are remembered by the provider's stable key, never by row id.** A refresh deletes every
 * row of a source and reinserts it with a new autogenerated id, so a remembered id would resolve
 * to nothing the first time a viewer refreshed — and on the popular rows "resolves to nothing"
 * is drawn as *unavailable*, which would have turned a refresh into a top ten that claims the
 * provider carries none of it. The stable key is what favourites and resume points already use,
 * and for exactly this reason.
 */
@Entity(tableName = "feed_rows", primaryKeys = ["profileId", "sourceId", "rowId", "position"])
data class FeedRowEntity(
    val profileId: Long,
    val sourceId: Long,
    /** A `FeedRowId` name. Rows are read and written a whole row at a time. */
    val rowId: String,
    val position: Int,
    /** Null for a popular title this provider does not carry. */
    val stableKey: String? = null,
    val title: String,
    val posterUrl: String? = null,
    val kind: String,
    val rank: Int? = null,
    /** The watched title that caused a suggestion. Null on the popular rows. */
    val becauseOf: String? = null,
    /**
     * When this entry was worked out.
     *
     * Per entry rather than per row, because the suggestions row is appended to rather than
     * replaced: two entries in one row can honestly have been decided a fortnight apart, and the
     * date is what says whether a cause has been watched again since.
     */
    val builtAtEpochMillis: Long,
)

/**
 * One occasion on which something was watched.
 *
 * **Kept as events rather than folded into `resume_positions`, which holds one row per title.**
 * That shape answers "where was I" and cannot answer how often something was watched, at what hour,
 * or what the viewer was doing when they chose it. All three are facts about an occasion, and the
 * suggestions row wants all three: a film watched five times is a comfort film, an hour is a
 * pattern, and a title typed into a search box was wanted rather than accepted.
 *
 * **It is a log, so it is bounded.** A household watching every evening writes a few hundred rows
 * a year, which is nothing — but nothing that only grows should be written without saying where it
 * stops, so `WatchEventRepository` trims it.
 *
 * Per profile, like everything about viewing.
 */
@Entity(
    tableName = "watch_events",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId", "sourceId", "startedAtEpochMillis"), Index("profileId", "stableKey")],
)
data class WatchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: Long,
    val sourceId: Long,
    /** The provider's identity, so an event survives a refresh exactly as a favourite does. */
    val stableKey: String,
    val kind: String,
    val title: String,
    val startedAtEpochMillis: Long,
    val fraction: Double,
    /** A `WatchOrigin` name. */
    val origin: String,
)

/**
 * What one viewer thinks of one title.
 *
 * Keyed by the cleaned title rather than by a channel id or a stable key, and that is deliberate:
 * an opinion is about the film, not about the row a provider happens to be serving it from. A
 * viewer who dislikes something should not be asked again when their provider re-lists it under a
 * different key, and should not be shown it again on a second account.
 */
@Entity(tableName = "title_opinions", primaryKeys = ["profileId", "titleKey"])
data class TitleOpinionEntity(
    val profileId: Long,
    /** The cleaned title, from `titleIdentity()`. */
    val titleKey: String,
    val kind: String,
    /** An `Opinion` name. `NONE` is not stored — an absent row is the absence of an opinion. */
    val opinion: String,
    val decidedAtEpochMillis: Long,
)
