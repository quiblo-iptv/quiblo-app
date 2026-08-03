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
 * A favourite, keyed by provider identity rather than by row id.
 *
 * Surviving a refresh in which the stream URL changed is the entire point (AC-FAV-03), so
 * this table is deliberately not joined to [ChannelEntity] by primary key.
 */
@Entity(
    tableName = "favorites",
    primaryKeys = ["sourceId", "stableKey"],
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    val sourceId: Long,
    val stableKey: String,
    val favoritedAtEpochMillis: Long,
)

/**
 * Where the user got to in a VOD item.
 *
 * Keyed by the provider's stable identity rather than a row id, so a playlist refresh
 * that renumbers everything does not lose the resume point (AC-PLAY-03).
 */
@Entity(tableName = "resume_positions")
data class ResumePositionEntity(
    @PrimaryKey val stableKey: String,
    val positionMillis: Long,
    val updatedAtEpochMillis: Long,
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
 * Cached film metadata, keyed by the cleaned search title.
 *
 * Persisted rather than held in memory so a relaunch does not re-ask the service for
 * everything the user browses. A metadata provider rate-limits per key, and the key is the
 * user's own — being wasteful with it is being wasteful with something that belongs to them.
 *
 * Keyed by title rather than by channel id because a refresh reassigns every channel id,
 * and the cache would be thrown away for no reason each time a playlist reloaded.
 */
@Entity(tableName = "movie_metadata")
data class MovieMetadataEntity(
    @PrimaryKey val searchTitle: String,
    val overview: String? = null,
    /** Newline-separated. A list table for a handful of short strings is not worth a join. */
    val genres: String? = null,
    val ageRating: String? = null,
    val rating: Double? = null,
    val director: String? = null,
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
