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

package dev.vibrato.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.vibrato.core.database.dao.ChannelDao
import dev.vibrato.core.database.dao.ResumePositionDao
import dev.vibrato.core.database.dao.SourceDao
import dev.vibrato.core.database.entity.ChannelEntity
import dev.vibrato.core.database.entity.FavoriteEntity
import dev.vibrato.core.database.entity.ResumePositionEntity
import dev.vibrato.core.database.entity.SourceEntity

/**
 * The single local database. There is no remote counterpart and never will be
 * (docs/FREEZE.md §2).
 */
@Database(
    entities = [
        SourceEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        ResumePositionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class VibratoDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao

    abstract fun channelDao(): ChannelDao

    abstract fun resumePositionDao(): ResumePositionDao

    companion object {
        const val NAME = "vibrato.db"

        /**
         * Builds the database.
         *
         * Destructive migration is deliberately *not* enabled: silently dropping a user's
         * configured sources on a schema change would be a data-loss bug, and AC-DATA-04
         * requires version mismatches to be handled explicitly rather than by discarding
         * state. Every schema change from here needs a real migration.
         */
        fun create(context: Context): VibratoDatabase =
            Room.databaseBuilder(context, VibratoDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
