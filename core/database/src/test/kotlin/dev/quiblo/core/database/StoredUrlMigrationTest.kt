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

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `BUG-041`: an upgrade takes the Xtream password out of every stored URL, and keeps every row.
 *
 * **Built from the exported schema directly, not through `MigrationTestHelper`.** The tables are
 * created from `schemas/…/24.json` — the file the helper itself reads — in an in-memory database,
 * and the migration is run on that. The helper opens through Room's driver, which rejects a Windows
 * path, so a migration test written with it cannot run on the machine this project is mostly
 * developed on. Schema 25 is schema 24, and `MigrationTest`'s end-to-end run validates that; this
 * file is about the rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class StoredUrlMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openVersion24() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val schema = context.assets.open("dev.quiblo.core.database.QuibloDatabase/24.json")
            .bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(schema.getInt("version")) {
                        override fun onCreate(db: SupportSQLiteDatabase) = createAll(db, schema)
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        db = helper.writableDatabase
    }

    @After
    fun close() {
        helper.close()
    }

    private fun createAll(db: SupportSQLiteDatabase, schema: JSONObject) {
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace(TABLE_PLACEHOLDER, table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_PLACEHOLDER, table))
            }
        }
    }

    private fun addSources(xtream: Boolean = true) {
        if (xtream) {
            db.execSQL(
                "INSERT INTO `sources` (`id`, `name`, `kind`, `url`, `createdAtEpochMillis`) " +
                    "VALUES (1, 'Panel', 'XTREAM', 'panel.example.invalid:8080', 0)",
            )
        }
        db.execSQL(
            "INSERT INTO `sources` (`id`, `name`, `kind`, `url`, `createdAtEpochMillis`) " +
                "VALUES (2, 'Export', 'M3U', 'http://panel.example.invalid/get.php', 0)",
        )
    }

    private fun addChannel(id: Long, sourceId: Long, url: String) {
        db.execSQL(
            "INSERT INTO `channels` (`id`, `sourceId`, `name`, `streamUrl`, `kind`, `groupTitle`, `stableKey`, " +
                "`sortIndex`, `searchTitle`, `identityYear`, `scriptMask`) " +
                "VALUES ($id, $sourceId, 'Title $id', '$url', 'LIVE', 'Group', 'key-$id', $id, 'title', 0, 0)",
        )
    }

    private fun addSubtitle(key: String, path: String) {
        db.execSQL(
            "INSERT INTO `picked_subtitles` (`stableKey`, `storedPath`, `label`, `mimeType`, `pickedAt`) " +
                "VALUES ('$key', '$path', 'x.srt', 'application/x-subrip', 0)",
        )
    }

    private fun seed() {
        addSources()
        db.execSQL("INSERT INTO `profiles` (`id`, `name`, `createdAtEpochMillis`, `isGuest`) VALUES (1, 'Sam', 0, 0)")
        addChannel(1, 1, "$PANEL/live/someone/s3cret/101.ts")
        addChannel(2, 1, "$PANEL/movie/someone/s3cret/7.mkv")
        // The same shape of URL in a playlist row, which must keep a URL it can play.
        addChannel(3, 2, "$PANEL/live/someone/s3cret/101.ts")
        db.execSQL(
            "INSERT INTO `resume_positions` (`stableKey`, `profileId`, `positionMillis`, `updatedAtEpochMillis`, " +
                "`sourceId`, `kind`, `title`, `durationMillis`) " +
                "VALUES ('$EPISODE_URL', 1, 240000, 5, 1, 'SERIES', 'A Series', 0)",
        )
        db.execSQL(
            "INSERT INTO `watch_events` (`profileId`, `sourceId`, `stableKey`, `kind`, `title`, " +
                "`startedAtEpochMillis`, `fraction`, `origin`) " +
                "VALUES (1, 1, '$EPISODE_URL', 'SERIES', 'A Series', 5, 0.5, 'ROW')",
        )
        addSubtitle(EPISODE_URL, "/data/x.srt")
    }

    private fun strings(sql: String): List<String> = db.query(sql).use { cursor ->
        generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
    }

    @Test
    fun `an xtream channel stores a locator, and a playlist row keeps its url`() {
        seed()

        MIGRATION_24_25.migrate(db)

        assertEquals(
            listOf("xtream:live/101", "xtream:movie/7.mkv", "$PANEL/live/someone/s3cret/101.ts"),
            strings("SELECT `streamUrl` FROM `channels` ORDER BY `id`"),
        )
    }

    @Test
    fun `history follows an episode to its locator, so its resume point survives`() {
        seed()

        MIGRATION_24_25.migrate(db)

        assertEquals(listOf(EPISODE_LOCATOR), strings("SELECT `stableKey` FROM `resume_positions`"))
        assertEquals(listOf("240000"), strings("SELECT `positionMillis` FROM `resume_positions`"))
        assertEquals(listOf(EPISODE_LOCATOR), strings("SELECT `stableKey` FROM `watch_events`"))
        assertEquals(listOf(EPISODE_LOCATOR), strings("SELECT `stableKey` FROM `picked_subtitles`"))
    }

    @Test
    fun `no history table keeps the password`() {
        seed()

        MIGRATION_24_25.migrate(db)

        STORED_URL_COLUMNS.filterNot { it.table == "channels" }.forEach { target ->
            val leaked = strings("SELECT `${target.column}` FROM `${target.table}`").filter { "s3cret" in it }
            assertTrue("${target.table}.${target.column} still holds $leaked", leaked.isEmpty())
        }
        // The playlist row is the only one left with it: it is the playlist's own URL.
        assertEquals(listOf("3"), strings("SELECT `id` FROM `channels` WHERE `streamUrl` LIKE '%s3cret%'"))
    }

    @Test
    fun `an old url and a newer one for one episode become one row, not a failed upgrade`() {
        seed()
        // The password changed at some point, so the same episode has history under two URLs.
        addSubtitle("$PANEL/series/someone/n3w/501.mp4", "/data/y.srt")

        MIGRATION_24_25.migrate(db)

        assertEquals(listOf(EPISODE_LOCATOR), strings("SELECT `stableKey` FROM `picked_subtitles`"))
    }

    @Test
    fun `an install with no xtream source is not touched`() {
        addSources(xtream = false)
        addChannel(3, 2, "$PANEL/live/someone/s3cret/101.ts")

        MIGRATION_24_25.migrate(db)

        assertEquals(listOf("$PANEL/live/someone/s3cret/101.ts"), strings("SELECT `streamUrl` FROM `channels`"))
    }

    private companion object {
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
        const val PANEL = "http://panel.example.invalid:8080"
        const val EPISODE_URL = "$PANEL/series/someone/s3cret/501.mp4"
        const val EPISODE_LOCATOR = "xtream:series/501.mp4"
    }
}
