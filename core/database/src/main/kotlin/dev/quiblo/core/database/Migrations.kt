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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.quiblo.core.common.SCRIPT_MASK_UNKNOWN

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

/** Adds the user's local category edits. Empty by default: no edit means no row. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `category_overrides` (
                `kind` TEXT NOT NULL,
                `originalTitle` TEXT NOT NULL,
                `customName` TEXT,
                `isHidden` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`kind`, `originalTitle`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Widens the film metadata cache to cover series.
 *
 * The key becomes (title, kind) rather than title alone, because "Fargo" is a film and
 * "Fargo" is a series and they are not the same record. A column cannot be added to a
 * SQLite primary key, so the table is rebuilt.
 *
 * Existing rows are carried over as `VOD` and as complete records, which is what they are:
 * everything cached before this migration was fetched by the film detail screen, in full.
 * Throwing them away would have been easier and would have re-spent the user's rate limit
 * on answers already held.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `title_metadata` (
                `searchTitle` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `overview` TEXT,
                `genres` TEXT,
                `ageRating` TEXT,
                `rating` REAL,
                `author` TEXT,
                `topCast` TEXT,
                `posterUrl` TEXT,
                `backdropUrl` TEXT,
                `fetchedAtEpochMillis` INTEGER NOT NULL,
                `isMiss` INTEGER NOT NULL DEFAULT 0,
                `isPartial` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`searchTitle`, `kind`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO `title_metadata` (
                `searchTitle`, `kind`, `overview`, `genres`, `ageRating`, `rating`,
                `author`, `topCast`, `posterUrl`, `backdropUrl`, `fetchedAtEpochMillis`,
                `isMiss`, `isPartial`
            )
            SELECT
                `searchTitle`, 'VOD', `overview`, `genres`, `ageRating`, `rating`,
                `director`, `topCast`, `posterUrl`, `backdropUrl`, `fetchedAtEpochMillis`,
                `isMiss`, 0
            FROM `movie_metadata`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `movie_metadata`")
    }
}

/**
 * Widens resume positions into watch history.
 *
 * Added as columns on the existing table rather than as a second one, because a history
 * entry and a resume point are the same fact: "this was started and not finished". Two
 * tables would need a rule for what happens when they disagree, and there is no honest
 * answer to that question.
 *
 * Existing rows keep their position and resume exactly as before. They gain an empty title,
 * which is what excludes them from the history list — a tile with no name and no artwork is
 * worse than one absent row, and the next time anything is played the row is rewritten in
 * full.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `resume_positions` ADD COLUMN `sourceId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `resume_positions` ADD COLUMN `kind` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `resume_positions` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `resume_positions` ADD COLUMN `artworkUrl` TEXT")
        db.execSQL("ALTER TABLE `resume_positions` ADD COLUMN `durationMillis` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `resume_positions` ADD COLUMN `seriesStableKey` TEXT")
        db.execSQL("ALTER TABLE `resume_positions` ADD COLUMN `seasonNumber` INTEGER")
        db.execSQL("ALTER TABLE `resume_positions` ADD COLUMN `episodeNumber` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_resume_positions_sourceId_kind_updatedAtEpochMillis` " +
                "ON `resume_positions` (`sourceId`, `kind`, `updatedAtEpochMillis`)",
        )
    }
}

/**
 * Adds the channel logo index.
 *
 * Nothing to migrate: the table starts empty and stays empty unless the user turns the
 * feature on, at which point it is filled from a download. It is a cache of a public
 * catalogue, so losing it costs one refetch and nothing else.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `channel_logos` (
                `matchKey` TEXT NOT NULL,
                `logoUrl` TEXT NOT NULL,
                PRIMARY KEY(`matchKey`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Indexes the two queries that were reading whole tables.
 *
 * No data changes and no columns move — both statements are pure index creation, so this
 * migration cannot lose anything, and on an empty or small database it costs nothing.
 *
 * The browse query filters channels by source and kind and sorts by the provider's order;
 * the guide query asks what is on now across a source. Neither had an index that led with
 * the columns it actually filters on, so both scanned. On the account this project is
 * tested against — 67,567 channels in one source — the browse scan is the reason the
 * Movies and Series screens took as long to appear as they did, and the reason the phone
 * could be made to hang while scrolling.
 *
 * Building these over an existing large playlist takes a moment on first launch after
 * upgrading, once. That is the right trade against paying for the scan on every emission
 * from then on.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_channels_sourceId_kind_sortIndex` " +
                "ON `channels` (`sourceId`, `kind`, `sortIndex`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_programmes_sourceId_startEpochMillis_endEpochMillis` " +
                "ON `programmes` (`sourceId`, `startEpochMillis`, `endEpochMillis`)",
        )
    }
}

/**
 * Profiles: favourites and resume positions become somebody's rather than the device's.
 *
 * Both tables are rebuilt rather than altered, because the profile belongs in their primary
 * key — two people may each favourite the same channel, and those are two rows. SQLite cannot
 * add a column to a primary key in place.
 *
 * **Everything already stored is adopted by a profile named "Default" rather than dropped.**
 * A viewer upgrading has favourites and half-watched films on this device already, and a
 * migration that quietly emptied them would be the data-loss bug the database is built to
 * avoid (AC-DATA-04). The Default profile is a real profile: it appears in the chooser, and
 * whoever was using the app before is still using it.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createProfiles(db)
        rebuildFavourites(db)
        rebuildResumePositions(db)
    }

    private fun createProfiles(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profiles` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAtEpochMillis` INTEGER NOT NULL, " +
                "`isGuest` INTEGER NOT NULL)",
        )

        // Everything that exists now belongs to whoever has been using the app.
        db.execSQL(
            "INSERT INTO `profiles` (`id`, `name`, `createdAtEpochMillis`, `isGuest`) " +
                "VALUES (1, 'Default', 0, 0)",
        )
    }

    private fun rebuildFavourites(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorites_new` (" +
                "`sourceId` INTEGER NOT NULL, " +
                "`stableKey` TEXT NOT NULL, " +
                "`favoritedAtEpochMillis` INTEGER NOT NULL, " +
                "`profileId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`profileId`, `sourceId`, `stableKey`), " +
                "FOREIGN KEY(`sourceId`) REFERENCES `sources`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `favorites_new` (`sourceId`, `stableKey`, `favoritedAtEpochMillis`, `profileId`) " +
                "SELECT `sourceId`, `stableKey`, `favoritedAtEpochMillis`, 1 FROM `favorites`",
        )
        db.execSQL("DROP TABLE `favorites`")
        db.execSQL("ALTER TABLE `favorites_new` RENAME TO `favorites`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_profileId` ON `favorites` (`profileId`)")
    }

    private fun rebuildResumePositions(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `resume_positions_new` (" +
                "`stableKey` TEXT NOT NULL, " +
                "`profileId` INTEGER NOT NULL, " +
                "`positionMillis` INTEGER NOT NULL, " +
                "`updatedAtEpochMillis` INTEGER NOT NULL, " +
                "`sourceId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`artworkUrl` TEXT, " +
                "`durationMillis` INTEGER NOT NULL, " +
                "`seriesStableKey` TEXT, " +
                "`seasonNumber` INTEGER, " +
                "`episodeNumber` INTEGER, " +
                "PRIMARY KEY(`profileId`, `stableKey`), " +
                "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `resume_positions_new` (" +
                "`stableKey`, `profileId`, `positionMillis`, `updatedAtEpochMillis`, `sourceId`, " +
                "`kind`, `title`, `artworkUrl`, `durationMillis`, `seriesStableKey`, " +
                "`seasonNumber`, `episodeNumber`) " +
                "SELECT `stableKey`, 1, `positionMillis`, `updatedAtEpochMillis`, `sourceId`, " +
                "`kind`, `title`, `artworkUrl`, `durationMillis`, `seriesStableKey`, " +
                "`seasonNumber`, `episodeNumber` FROM `resume_positions`",
        )
        db.execSQL("DROP TABLE `resume_positions`")
        db.execSQL("ALTER TABLE `resume_positions_new` RENAME TO `resume_positions`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_resume_positions_profileId_sourceId_kind_updatedAtEpochMillis` " +
                "ON `resume_positions` (`profileId`, `sourceId`, `kind`, `updatedAtEpochMillis`)",
        )
    }
}

/**
 * #024: the metadata cache key gains the year, and the table gains the service's own id.
 *
 * The cache keyed rows on a cleaned search title, and the cleaner strips bracketed groups
 * whole — including the year, because a provider year is almost always in brackets. So
 * `Dune (1984)` and `Dune (2021)` were one row. The first fetched won, permanently, and the
 * other film showed the winner's poster, plot, rating and cast. Nothing was empty and nothing
 * errored; a viewer saw a catalogue that did not have their film.
 *
 * `year` belongs in the primary key, and SQLite cannot add a column to a primary key in place,
 * so the table is rebuilt — `MIGRATION_10_11`'s shape, which is the model for this.
 *
 * **Every existing row is dropped rather than adopted, and that is the opposite of what
 * `MIGRATION_10_11` does.** The difference is whose data it is. Favourites and resume points
 * are the viewer's and cannot be recovered by asking anyone; this table is a fortnight-long
 * cache of somebody else's answers, and `setApiKey` already empties it wholesale when the key
 * changes. More to the point, **these are the rows we have just established may hold the wrong
 * film's details** — carrying them across under a key that now claims to be precise would
 * launder a known-bad row into a trustworthy-looking one. Re-fetching costs a request per
 * title on next visit, which is what the cache is for.
 *
 * The cost is stated rather than hidden: the first browse after this upgrade is as slow as a
 * first install, and on a scanned catalogue the search screen's coverage figure reads 0% until
 * a scan is run again.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `title_metadata`")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `title_metadata` (" +
                "`searchTitle` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`year` INTEGER NOT NULL, " +
                "`tmdbId` INTEGER, " +
                "`overview` TEXT, " +
                "`genres` TEXT, " +
                "`ageRating` TEXT, " +
                "`rating` REAL, " +
                "`author` TEXT, " +
                "`topCast` TEXT, " +
                "`posterUrl` TEXT, " +
                "`backdropUrl` TEXT, " +
                "`fetchedAtEpochMillis` INTEGER NOT NULL, " +
                "`isMiss` INTEGER NOT NULL, " +
                "`isPartial` INTEGER NOT NULL, " +
                "PRIMARY KEY(`searchTitle`, `kind`, `year`))",
        )
    }
}

/**
 * Profiles gain an avatar.
 *
 * One nullable column, and nothing is rebuilt: `avatar` is not part of any key, so SQLite can
 * add it in place. That makes this the cheapest migration in the file and the one least likely
 * to lose anything — which matters more than usual here, because `AC-PROF-05` is the criterion
 * that upgrades a build with profiles in it and it has never been run on a device.
 *
 * Null is the value every existing profile gets, and it means "chose no face" rather than
 * "chose the first one". The chooser draws the viewer's initial on a colour derived from their
 * name for those, so a profile that predates this column still has a picture — it simply has
 * one nobody picked.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `avatar` TEXT")
    }
}

/**
 * How a viewer likes one series laid out.
 *
 * A new table and nothing else — no existing row is read, rewritten or dropped, which makes this
 * the safest kind of migration there is. Absence of a row means the defaults, so an upgrade
 * changes nothing anybody sees until they press one of the two new controls.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `series_preferences` (" +
                "`profileId` INTEGER NOT NULL, " +
                "`seriesKey` TEXT NOT NULL, " +
                "`isMerged` INTEGER NOT NULL, " +
                "`isDescending` INTEGER NOT NULL, " +
                "PRIMARY KEY(`profileId`, `seriesKey`), " +
                "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

/**
 * Where a picked subtitle file lives, and which title it belongs to.
 *
 * A new table and nothing else, the same safe shape as `MIGRATION_13_14`. Nobody has picked a
 * subtitle before this version, so the table starts empty on every device and the upgrade
 * changes nothing anyone sees.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `picked_subtitles` (" +
                "`stableKey` TEXT NOT NULL, " +
                "`storedPath` TEXT NOT NULL, " +
                "`label` TEXT NOT NULL, " +
                "`mimeType` TEXT NOT NULL, " +
                "`language` TEXT, " +
                "`pickedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`stableKey`) )",
        )
    }
}

/**
 * When the provider added each film and series, and the index the newest-first row reads.
 *
 * **Nothing backfills the column, and nothing needs to.** `ChannelDao.replaceForSource` deletes
 * every row for a source and rewrites it on each refresh, so the nulls this migration leaves
 * behind survive exactly until the viewer next refreshes their playlist — at which point the
 * dates arrive from the same `get_vod_streams` and `get_series` responses the app was already
 * making. A migration that tried to invent dates for existing rows would be inventing the one
 * thing the screen above it promises is true.
 *
 * The column is added rather than the table rebuilt, which `MIGRATION_10_11` had to do: adding
 * a nullable column with no default is the one schema change SQLite performs in place, so no
 * channel row is copied and nobody's catalogue is at risk during the upgrade.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `addedAtEpochMillis` INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_channels_sourceId_kind_addedAtEpochMillis` " +
                "ON `channels` (`sourceId`, `kind`, `addedAtEpochMillis`)",
        )
    }
}

/**
 * The metadata cache learns a year and a running time.
 *
 * **Two columns added in place, and not one row rewritten.** `MIGRATION_11_12` rebuilt this table
 * and dropped everything in it, which was defensible then and is the last thing this table needs
 * now: an upgrade that empties it costs an hour of somebody's scanning and their rate limit to
 * earn back, and that is exactly the complaint this round of work started from. A nullable column
 * with no default is the one schema change SQLite makes without copying anything.
 *
 * Existing rows keep every fact they hold and gain these two as null, which reads on screen as a
 * film that does not say how long it is — the same as before this migration. They fill in when the
 * row is next fetched, which the panel's own fields make unnecessary for most catalogues anyway:
 * an Xtream account supplies both a release date and a length of its own, and those are preferred
 * over the service's wherever they exist.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `title_metadata` ADD COLUMN `releaseYear` INTEGER")
        db.execSQL("ALTER TABLE `title_metadata` ADD COLUMN `runtimeMinutes` INTEGER")
    }
}

/**
 * A table for the week's popular lists.
 *
 * **A new table, so nothing existing is touched at all.** It holds a cache of an answer from a
 * service rather than anything the viewer owns, and it starts empty: the first composition of the
 * For You tab fetches it, or does not and draws no row. An upgrade therefore cannot lose anything
 * here, and cannot cost anything either — unlike the metadata cache, which the same week's work
 * went to some trouble to stop rebuilding.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `popular_titles` (
                `kind` TEXT NOT NULL,
                `rank` INTEGER NOT NULL,
                `tmdbId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `year` INTEGER,
                `posterUrl` TEXT,
                `fetchedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`kind`, `rank`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * The catalogue learns its own cleaned title and its own writing systems.
 *
 * **Three columns and two indexes, and not one row read.** Cleaning a title is eight regex passes
 * and reading its scripts is a full codepoint walk, so filling fifty thousand rows here would be
 * ten to twenty seconds of a television frozen on its own splash — a migration runs on the first
 * access to the database, and every screen is behind it. The value that says "nobody has computed
 * this yet" is what buys the alternative: rows arrive carrying [SCRIPT_MASK_UNKNOWN] and a blank
 * identity, `CatalogueIdentityBackfill` fills them in the background afterwards, and every query
 * that reads them treats an unfilled row exactly as schema 18 did.
 *
 * That last part is the property worth stating plainly. A migration that left the columns merely
 * *empty* would make a hidden writing system stop hiding for as long as the backfill ran — and
 * "hidden means hidden" is the defect the release before this one was spent fixing. Unknown is a
 * third value precisely so it can be told apart from "no scripts", and the Kotlin filter is kept
 * alive for exactly the rows that still carry it.
 *
 * The indexes are created here rather than left to the backfill because an index on a column of
 * identical values costs nothing to build and is wanted the moment the first row is filled.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `searchTitle` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `identityYear` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "ALTER TABLE `channels` ADD COLUMN `scriptMask` INTEGER NOT NULL DEFAULT $SCRIPT_MASK_UNKNOWN",
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_channels_sourceId_kind_scriptMask_sortIndex`
            ON `channels` (`sourceId`, `kind`, `scriptMask`, `sortIndex`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_channels_searchTitle_identityYear_kind`
            ON `channels` (`searchTitle`, `identityYear`, `kind`)
            """.trimIndent(),
        )
    }
}

/**
 * 19 to 20: a category can be moved.
 *
 * One nullable column on the table that already holds the other two local edits. Nullable is the
 * whole design: `NULL` means "this viewer never moved this one", which is a different fact from
 * "they moved it to position zero" and has to stay different, because the browse order falls back
 * to the provider's own for everything unmoved.
 *
 * Nothing is read and nothing is rewritten. An installation upgrading through this has exactly
 * the category order it had before, because every row arrives with no position.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `category_overrides` ADD COLUMN `userOrder` INTEGER DEFAULT NULL")
    }
}
