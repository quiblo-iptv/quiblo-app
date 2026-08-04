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

package dev.quiblo.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.quiblo.core.database.dao.CategoryOverrideDao
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.ChannelLogoDao
import dev.quiblo.core.database.dao.FavoriteDao
import dev.quiblo.core.database.dao.ProgrammeDao
import dev.quiblo.core.database.dao.ResumePositionDao
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.entity.CategoryOverrideEntity
import dev.quiblo.core.database.entity.ChannelEntity
import dev.quiblo.core.database.entity.ChannelLogoEntity
import dev.quiblo.core.database.entity.FavoriteEntity
import dev.quiblo.core.database.entity.ProgrammeEntity
import dev.quiblo.core.database.entity.ResumePositionEntity
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.database.entity.TitleMetadataEntity

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
        ProgrammeEntity::class,
        TitleMetadataEntity::class,
        CategoryOverrideEntity::class,
        ChannelLogoEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class QuibloDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao

    abstract fun channelDao(): ChannelDao

    abstract fun resumePositionDao(): ResumePositionDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun titleMetadataDao(): TitleMetadataDao

    abstract fun channelLogoDao(): ChannelLogoDao

    abstract fun categoryOverrideDao(): CategoryOverrideDao

    abstract fun programmeDao(): ProgrammeDao

    companion object {
        const val NAME = "quiblo.db"

        /**
         * Builds the database.
         *
         * Destructive migration is deliberately *not* enabled: silently dropping a user's
         * configured sources on a schema change would be a data-loss bug, and AC-DATA-04
         * requires version mismatches to be handled explicitly rather than by discarding
         * state. Every schema change from here needs a real migration.
         */
        fun create(context: Context): QuibloDatabase =
            Room.databaseBuilder(context, QuibloDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                )
                .build()
    }
}
