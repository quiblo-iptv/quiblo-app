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
 * A user's local edits to one provider category.
 *
 * Keyed by kind and the provider's own title rather than by any id, because a category has
 * no id — it is derived by grouping channels — and because the provider's title is what
 * survives a refresh. Renaming is local only: nothing is sent anywhere, and the original
 * title is retained as the key so the edit reattaches after every reload.
 */
@Entity(tableName = "category_overrides", primaryKeys = ["kind", "originalTitle"])
data class CategoryOverrideEntity(
    val kind: String,
    val originalTitle: String,
    /** Null means "use the provider's name". Absence and a blank rename are the same thing. */
    val customName: String? = null,
    val isHidden: Boolean = false,
)
