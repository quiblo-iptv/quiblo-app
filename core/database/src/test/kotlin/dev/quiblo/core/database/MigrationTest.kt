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

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The newest Android Robolectric 4.16 ships an image for.
 *
 * Deliberately not `compileSdk`: nothing under test here is sensitive to the platform version.
 * It is SQLite and Room's own migration machinery.
 */
internal const val ROBOLECTRIC_SDK = 34

private const val DB_NAME = "migration-test.db"

/**
 * Does an upgrade keep what the viewer had?
 *
 * **This file exists because until now nothing ran a migration anywhere.** Eleven of them are
 * declared in `Migrations.kt` and every one had been signed off by reading it. A migration that
 * is wrong does not fail loudly — it presents as an **empty catalogue**, on a device, belonging
 * to somebody who already had the app, and `AC-DATA-04` is the criterion it breaks. That is the
 * most expensive place in this project to find a bug, and the cheapest place to find this class
 * of bug is here.
 *
 * `MigrationTestHelper` compares the schema Room generates against the JSON exported under
 * `schemas/`, so a migration that produces *nearly* the right table fails on the difference
 * rather than on the next query that touches it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        QuibloDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `every declared migration runs end to end`() {
        // From the first schema there is, through all of them, validating the result against
        // the exported JSON for the current version. A migration that compiles, runs, and
        // leaves a column of the wrong type behind is caught here and nowhere else.
        helper.createDatabase(DB_NAME, 1).close()

        helper.runMigrationsAndValidate(DB_NAME, SCHEMA_VERSION, true, *ALL_MIGRATIONS)
    }

    @Test
    fun `11 to 12 rebuilds the metadata cache with the year in its key`() {
        helper.createDatabase(DB_NAME, 11).use { old ->
            // A row filed the way the cache filed rows before #024: no year column at all,
            // and therefore no way to tell this from any other Dune.
            old.execSQL(
                "INSERT INTO `title_metadata` " +
                    "(`searchTitle`, `kind`, `overview`, `fetchedAtEpochMillis`, `isMiss`, `isPartial`) " +
                    "VALUES ('dune', 'VOD', 'A duke''s son leads desert warriors.', 1, 0, 0)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 12, true, MIGRATION_11_12)

        // The upgrade drops the cache rather than adopting it, and that is the deliberate
        // opposite of what 10 to 11 does for favourites. These rows are somebody else's
        // answers behind a fortnight's expiry, and they are precisely the rows we have just
        // established may hold the wrong film's details. Carrying one across would launder a
        // known-bad row into one that now looks precisely keyed.
        db.query("SELECT COUNT(*) FROM title_metadata").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        // And the new key holds two Dunes at once, which is the whole point of the item.
        db.execSQL(
            "INSERT INTO `title_metadata` (`searchTitle`, `kind`, `year`, `fetchedAtEpochMillis`, " +
                "`isMiss`, `isPartial`) VALUES ('dune', 'VOD', 1984, 1, 0, 0)",
        )
        db.execSQL(
            "INSERT INTO `title_metadata` (`searchTitle`, `kind`, `year`, `fetchedAtEpochMillis`, " +
                "`isMiss`, `isPartial`) VALUES ('dune', 'VOD', 2021, 1, 0, 0)",
        )
        db.query("SELECT COUNT(*) FROM title_metadata").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }

    @Test
    fun `12 to 13 gives every existing profile an avatar of null`() {
        helper.createDatabase(DB_NAME, 12).use { old ->
            old.execSQL(
                "INSERT INTO `profiles` (`id`, `name`, `createdAtEpochMillis`, `isGuest`) " +
                    "VALUES (1, 'Mahmoud', 0, 0)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 13, true, MIGRATION_12_13)

        // Null rather than the first face. Null means "chose no picture" and the chooser draws
        // the initial for it; a default would claim this profile had picked face one, which
        // nobody did. AC-PROF-05 is the criterion watching this, and it has never been run on
        // a device — see docs/STOPPERS.md.
        db.query("SELECT `name`, `avatar` FROM `profiles`").use { cursor ->
            assertTrue("the profile did not survive the upgrade", cursor.moveToFirst())
            assertEquals("Mahmoud", cursor.getString(0))
            assertTrue("an avatar was invented for an existing profile", cursor.isNull(1))
            assertFalse("an extra profile appeared", cursor.moveToNext())
        }
    }

    @Test
    fun `13 to 14 adds series preferences without touching anything else`() {
        helper.createDatabase(DB_NAME, 13).use { old ->
            old.execSQL(
                "INSERT INTO `profiles` (`id`, `name`, `createdAtEpochMillis`, `isGuest`) " +
                    "VALUES (1, 'Mahmoud', 0, 0)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 14, true, MIGRATION_13_14)

        // A new table and nothing else. The profile is untouched, and no preference row is
        // invented — absence means the defaults, so an upgrade changes nothing anybody sees
        // until one of the two controls is pressed.
        db.query("SELECT COUNT(*) FROM series_preferences").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT `name` FROM `profiles`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Mahmoud", cursor.getString(0))
        }

        // The profile owns them: deleting it takes its preferences with it, by foreign key.
        db.execSQL(
            "INSERT INTO `series_preferences` (`profileId`, `seriesKey`, `isMerged`, `isDescending`) " +
                "VALUES (1, 'one-piece', 1, 1)",
        )
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM `profiles` WHERE `id` = 1")
        db.query("SELECT COUNT(*) FROM series_preferences").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun `15 to 16 keeps every channel and dates none of them`() {
        helper.createDatabase(DB_NAME, 15).use { old ->
            old.execSQL(
                "INSERT INTO `sources` (`id`, `name`, `kind`, `url`, `createdAtEpochMillis`) " +
                    "VALUES (1, 'A panel', 'XTREAM', 'https://example.invalid', 0)",
            )
            old.execSQL(
                "INSERT INTO `channels` (`id`, `sourceId`, `name`, `streamUrl`, `kind`, " +
                    "`groupTitle`, `stableKey`, `sortIndex`) " +
                    "VALUES (1, 1, 'A film', 'https://example.invalid/1.mkv', 'VOD', 'Films', 'k-1', 0)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 16, true, MIGRATION_15_16)

        // The row survives and its date is null, not zero. Nothing backfills it and nothing
        // should: a zero would be 1 January 1970 and would sort into a row that claims to be
        // ordered by when the provider added things. The real dates arrive on the next
        // refresh, which rewrites every channel row for the source anyway.
        db.query("SELECT `name`, `addedAtEpochMillis` FROM `channels`").use { cursor ->
            assertTrue("the channel did not survive the upgrade", cursor.moveToFirst())
            assertEquals("A film", cursor.getString(0))
            assertTrue("a date was invented for an existing channel", cursor.isNull(1))
            assertFalse("an extra channel appeared", cursor.moveToNext())
        }
    }

    @Test
    fun `16 to 17 keeps every cached title and its answers`() {
        helper.createDatabase(DB_NAME, 16).use { old ->
            old.execSQL(
                "INSERT INTO `title_metadata` (`searchTitle`, `kind`, `year`, `overview`, `rating`, " +
                    "`fetchedAtEpochMillis`, `isMiss`, `isPartial`) " +
                    "VALUES ('the matrix', 'VOD', 1999, 'A hacker learns the truth.', 8.7, 100, 0, 0)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 17, true, MIGRATION_16_17)

        // The row survives whole. `MIGRATION_11_12` dropped this table and had reasons; the
        // reason not to do it again is that an hour of scanning stands behind these rows and a
        // user's own rate limit is what buys them back.
        db.query(
            "SELECT `searchTitle`, `overview`, `rating`, `releaseYear`, `runtimeMinutes` FROM `title_metadata`",
        ).use { cursor ->
            assertTrue("the cached title did not survive the upgrade", cursor.moveToFirst())
            assertEquals("the matrix", cursor.getString(0))
            assertEquals("A hacker learns the truth.", cursor.getString(1))
            assertEquals(8.7, cursor.getDouble(2), 0.001)
            // Null rather than zero, and for the same reason a channel's date is: a year of 0
            // is a claim, and this migration invents nothing. Both fill in on the next fetch.
            assertTrue("a year was invented for an existing row", cursor.isNull(3))
            assertTrue("a running time was invented for an existing row", cursor.isNull(4))
            assertFalse("an extra row appeared", cursor.moveToNext())
        }
    }

    @Test
    fun `10 to 11 adopts existing favourites rather than dropping them`() {
        // The opposite promise to the one above, and the one that matters more: this is the
        // viewer's own data and nobody can hand it back. AC-DATA-04.
        helper.createDatabase(DB_NAME, 10).use { old ->
            old.execSQL(
                "INSERT INTO `sources` (`id`, `name`, `kind`, `url`, `createdAtEpochMillis`) " +
                    "VALUES (1, 'A playlist', 'M3U', 'https://example.invalid/list.m3u', 0)",
            )
            old.execSQL(
                "INSERT INTO `favorites` (`sourceId`, `stableKey`, `favoritedAtEpochMillis`) " +
                    "VALUES (1, 'a-channel', 99)",
            )
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 11, true, MIGRATION_10_11)

        db.query("SELECT `stableKey`, `profileId` FROM `favorites`").use { cursor ->
            assertTrue("the favourite did not survive the upgrade", cursor.moveToFirst())
            assertEquals("a-channel", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertFalse("an extra favourite appeared", cursor.moveToNext())
        }

        // The profile that adopted it is a real one, and it is what the chooser shows.
        db.query("SELECT `name` FROM `profiles`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Default", cursor.getString(0))
        }
    }
}
