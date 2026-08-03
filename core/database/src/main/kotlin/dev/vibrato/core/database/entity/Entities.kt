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

package dev.vibrato.core.database.entity

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
