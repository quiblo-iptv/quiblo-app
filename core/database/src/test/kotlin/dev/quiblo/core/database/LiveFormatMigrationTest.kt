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

import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `BUG-043`: every existing source upgrades to Automatic, and nothing else about it changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class LiveFormatMigrationTest {

    private val helper: SupportSQLiteOpenHelper = openExportedSchema(25)

    @After
    fun close() {
        helper.close()
    }

    @Test
    fun `an existing source becomes automatic, with no formats known yet`() {
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO `sources` (`id`, `name`, `kind`, `url`, `createdAtEpochMillis`, `lastRefreshedEpochMillis`) " +
                "VALUES (1, 'Panel', 'XTREAM', 'panel.example.invalid', 10, 20)",
        )

        MIGRATION_25_26.migrate(db)

        assertEquals(listOf("AUTO"), db.strings("SELECT `liveFormat` FROM `sources`"))
        assertEquals(listOf(null), db.strings("SELECT `allowedLiveFormats` FROM `sources`"))
        assertEquals(
            listOf("Panel|panel.example.invalid|20"),
            db.strings("SELECT `name` || '|' || `url` || '|' || `lastRefreshedEpochMillis` FROM `sources`"),
        )
    }

    @Test
    fun `a source added after the upgrade gets the default without naming it`() {
        val db = helper.writableDatabase
        MIGRATION_25_26.migrate(db)

        db.execSQL(
            "INSERT INTO `sources` (`id`, `name`, `kind`, `url`, `createdAtEpochMillis`) " +
                "VALUES (2, 'Later', 'M3U', 'http://cdn.example.invalid/list.m3u', 0)",
        )

        assertEquals(listOf("AUTO"), db.strings("SELECT `liveFormat` FROM `sources`"))
    }
}
