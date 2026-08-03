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

package dev.quiblo.core.data.backup

import dev.quiblo.core.database.dao.FavoriteDao
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.database.entity.FavoriteEntity
import dev.quiblo.core.database.entity.SourceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BackupRepositoryTest {

    /** An in-memory stand-in, so these tests exercise the format rather than Room. */
    private class FakeSourceDao(initial: List<SourceEntity> = emptyList()) : SourceDao {
        val rows = initial.toMutableList()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

        override fun observeAll(): Flow<List<SourceEntity>> = flowOf(rows.toList())
        override suspend fun findById(id: Long): SourceEntity? = rows.firstOrNull { it.id == id }
        override suspend fun allOnce(): List<SourceEntity> = rows.toList()
        override suspend fun insert(source: SourceEntity): Long {
            val id = nextId++
            rows += source.copy(id = id)
            return id
        }

        override suspend fun update(source: SourceEntity) = Unit
        override suspend fun delete(source: SourceEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun markRefreshed(id: Long, timestamp: Long) = Unit
    }

    private class FakeFavoriteDao(initial: List<FavoriteEntity> = emptyList()) : FavoriteDao {
        val rows = initial.toMutableList()

        override suspend fun isFavorite(sourceId: Long, stableKey: String): Boolean =
            rows.any { it.sourceId == sourceId && it.stableKey == stableKey }

        override suspend fun add(favorite: FavoriteEntity) {
            rows += favorite
        }

        override suspend fun remove(sourceId: Long, stableKey: String) = Unit
        override suspend fun countFor(sourceId: Long): Int = rows.count { it.sourceId == sourceId }
        override suspend fun allFor(sourceId: Long): List<FavoriteEntity> =
            rows.filter { it.sourceId == sourceId }
    }

    private val xtreamSource = SourceEntity(
        id = 1L,
        name = "My provider",
        kind = "XTREAM",
        url = "http://panel.example.invalid:8080",
        createdAtEpochMillis = 1_000L,
        lastRefreshedEpochMillis = 5_000L,
    )

    @Test
    @DisplayName("AC-DATA-03 — an export never contains a credential")
    fun `export omits credentials entirely`() = runTest {
        val repository = BackupRepository(
            sourceDao = FakeSourceDao(listOf(xtreamSource)),
            favoriteDao = FakeFavoriteDao(),
            now = { 42L },
        )

        val json = repository.export()

        // The store holds the password, and nothing here reads from it. Asserting on the
        // serialized text is what would catch a field being added back later.
        assertFalse(json.contains("password", ignoreCase = true))
        assertFalse(json.contains("credential", ignoreCase = true) && json.contains("secret", ignoreCase = true))
        assertTrue(json.contains("requires_credentials"))
        assertTrue(json.contains("\"schema_version\": 1"))
    }

    @Test
    @DisplayName("AC-DATA-02 — sources and favourites survive a round trip onto a fresh install")
    fun `export then import restores everything on an empty device`() = runTest {
        val source = FakeSourceDao(listOf(xtreamSource))
        val favorites = FakeFavoriteDao(
            listOf(FavoriteEntity(sourceId = 1L, stableKey = "xtream-live-77", favoritedAtEpochMillis = 9L)),
        )
        val json = BackupRepository(source, favorites).export()

        val freshSources = FakeSourceDao()
        val freshFavorites = FakeFavoriteDao()
        val result = BackupRepository(freshSources, freshFavorites).import(json)

        val success = assertInstanceOf(ImportResult.Success::class.java, result)
        assertEquals(1, success.sourcesRestored)
        assertEquals(1, success.favoritesRestored)
        assertEquals(listOf("My provider"), success.credentialsNeeded)

        assertEquals("http://panel.example.invalid:8080", freshSources.rows.single().url)
        // The new device has not loaded this source, and the export must not pretend it has.
        assertEquals(null, freshSources.rows.single().lastRefreshedEpochMillis)
        // The favourite reattached to the id this device assigned, not the exported one.
        assertEquals(freshSources.rows.single().id, freshFavorites.rows.single().sourceId)
    }

    @Test
    @DisplayName("AC-DATA-04 — a newer schema is refused rather than partially applied")
    fun `import rejects a future schema version and writes nothing`() = runTest {
        val sources = FakeSourceDao()
        val favorites = FakeFavoriteDao()
        val future = """
            {
              "schema_version": ${BackupFile.CURRENT_SCHEMA_VERSION + 1},
              "exported_at_epoch_millis": 1,
              "sources": [
                {"name":"X","kind":"M3U","url":"http://a.invalid/x.m3u","created_at_epoch_millis":1}
              ],
              "favorites": []
            }
        """.trimIndent()

        val result = BackupRepository(sources, favorites).import(future)

        val rejected = assertInstanceOf(ImportResult.VersionTooNew::class.java, result)
        assertEquals(BackupFile.CURRENT_SCHEMA_VERSION + 1, rejected.fileVersion)
        assertEquals(BackupFile.CURRENT_SCHEMA_VERSION, rejected.supportedVersion)
        assertTrue(sources.rows.isEmpty(), "a rejected import must not write anything")
    }

    @Test
    fun `import reports unreadable input rather than throwing`() = runTest {
        val result = BackupRepository(FakeSourceDao(), FakeFavoriteDao()).import("not json at all")
        assertEquals(ImportResult.Unreadable, result)
    }

    @Test
    @DisplayName("Importing the same file twice does not duplicate a source")
    fun `import is additive and skips sources already configured`() = runTest {
        val sources = FakeSourceDao(listOf(xtreamSource))
        val favorites = FakeFavoriteDao()
        val json = BackupRepository(FakeSourceDao(listOf(xtreamSource)), FakeFavoriteDao()).export()

        val result = BackupRepository(sources, favorites).import(json)

        val success = assertInstanceOf(ImportResult.Success::class.java, result)
        assertEquals(0, success.sourcesRestored)
        assertEquals(1, sources.rows.size)
    }
}
