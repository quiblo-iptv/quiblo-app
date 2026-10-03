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

/**
 * An in-memory database with exactly the tables an exported schema describes.
 *
 * Built from `schemas/…/<version>.json` — the file `MigrationTestHelper` itself reads — but opened
 * with the framework helper rather than through Room's driver, which rejects a Windows path. A
 * migration's effect on rows can be tested with this on any machine; `MigrationTest`'s end-to-end
 * run is still what validates the resulting schema, in CI.
 */
internal fun openExportedSchema(version: Int): SupportSQLiteOpenHelper {
    val context = InstrumentationRegistry.getInstrumentation().context
    val schema = context.assets.open("dev.quiblo.core.database.QuibloDatabase/$version.json")
        .bufferedReader()
        .use { JSONObject(it.readText()).getJSONObject("database") }
    return FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createAll(db, schema)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build(),
    )
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

/** Every value in the first column of [sql], as text. */
internal fun SupportSQLiteDatabase.strings(sql: String): List<String?> = query(sql).use { cursor ->
    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
}

private const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
