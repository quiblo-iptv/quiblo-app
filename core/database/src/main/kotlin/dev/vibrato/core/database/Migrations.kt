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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * Destructive migration is deliberately never enabled: dropping a user's configured
 * sources on a schema change would be silent data loss. Every version bump gets a real
 * migration, starting here, while the cost of writing one is trivial.
 */

/** Adds resume positions for VOD playback (AC-PLAY-03). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `resume_positions` (
                `stableKey` TEXT NOT NULL,
                `positionMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`stableKey`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Adds the electronic programme guide (AC-EPG-*) and the provider stream id that a
 * panel needs in order to be asked for one.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `providerStreamId` TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `programmes` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `sourceId` INTEGER NOT NULL,
                `channelKey` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `startEpochMillis` INTEGER NOT NULL,
                `endEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_programmes_sourceId_channelKey_startEpochMillis` " +
                "ON `programmes` (`sourceId`, `channelKey`, `startEpochMillis`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_programmes_sourceId_channelKey_endEpochMillis` " +
                "ON `programmes` (`sourceId`, `channelKey`, `endEpochMillis`)",
        )
    }
}

/**
 * Adds the provider's category ordering.
 *
 * Nullable with no default: existing rows genuinely do not know where their category sat,
 * and the query falls back to item order for them. The next refresh fills it in.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `categoryIndex` INTEGER")
    }
}

/** Adds the film metadata cache. Nothing to migrate — the table starts empty and refills. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `movie_metadata` (
                `searchTitle` TEXT NOT NULL,
                `overview` TEXT,
                `genres` TEXT,
                `ageRating` TEXT,
                `rating` REAL,
                `director` TEXT,
                `topCast` TEXT,
                `posterUrl` TEXT,
                `backdropUrl` TEXT,
                `fetchedAtEpochMillis` INTEGER NOT NULL,
                `isMiss` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`searchTitle`)
            )
            """.trimIndent(),
        )
    }
}
