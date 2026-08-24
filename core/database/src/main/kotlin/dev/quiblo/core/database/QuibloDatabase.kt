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
import dev.quiblo.core.database.dao.FeedRowDao
import dev.quiblo.core.database.dao.PickedSubtitleDao
import dev.quiblo.core.database.dao.PopularTitleDao
import dev.quiblo.core.database.dao.ProfileDao
import dev.quiblo.core.database.dao.ProgrammeDao
import dev.quiblo.core.database.dao.ResumePositionDao
import dev.quiblo.core.database.dao.SeriesPreferenceDao
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.dao.TitleOpinionDao
import dev.quiblo.core.database.dao.TitleVersionDao
import dev.quiblo.core.database.dao.WatchEventDao
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

/**
 * The current schema version.
 *
 * A top-level constant rather than a companion member because the annotation below needs it at
 * compile time and cannot reach into the class it annotates. [MigrationTest] reads the same
 * constant, so the version the app ships and the version the upgrade path is tested against
 * cannot drift apart.
 */
const val SCHEMA_VERSION = 24

/**
 * Every migration, in order, in one place.
 *
 * Named rather than listed inline in [QuibloDatabase.create] so that the builder and the
 * migration test take the same list. A migration registered in one and forgotten in the other
 * is a migration that is either untested or unreachable, and both of those look fine until an
 * upgrade on somebody's television.
 */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
)

/**
 * The single local database. There is no remote counterpart and never will be
 * (docs/FREEZE.md §2).
 */
// Fifteen accessors, one per table, and that is what the count is: a database class is exactly as
// large as the number of tables it holds, and splitting it to satisfy a threshold would put one
// schema behind two names.
@Suppress("TooManyFunctions")
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
        ProfileEntity::class,
        SeriesPreferenceEntity::class,
        PickedSubtitleEntity::class,
        PopularTitleEntity::class,
        FeedRowEntity::class,
        WatchEventEntity::class,
        TitleOpinionEntity::class,
    ],
    version = SCHEMA_VERSION,
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

    abstract fun titleVersionDao(): TitleVersionDao

    abstract fun programmeDao(): ProgrammeDao

    abstract fun profileDao(): ProfileDao

    abstract fun seriesPreferenceDao(): SeriesPreferenceDao

    abstract fun pickedSubtitleDao(): PickedSubtitleDao

    abstract fun popularTitleDao(): PopularTitleDao

    abstract fun feedRowDao(): FeedRowDao

    abstract fun watchEventDao(): WatchEventDao

    abstract fun titleOpinionDao(): TitleOpinionDao

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
        // The spread copies an eleven-element array once, at startup, on the only call that
        // builds the database. The alternative is listing the migrations a second time here
        // and letting the two lists drift, which is the failure this array exists to prevent.
        @Suppress("SpreadOperator")
        fun create(context: Context): QuibloDatabase =
            Room.databaseBuilder(context, QuibloDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
