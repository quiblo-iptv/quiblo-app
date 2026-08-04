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

package dev.quiblo.core.data

import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.database.entity.TitleMetadataEntity
import dev.quiblo.core.datastore.TmdbKeyStore
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.source.tmdb.TmdbClient
import dev.quiblo.source.tmdb.TmdbKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The cache is what stands between a browse screen and the user's rate limit, so these are
 * about what is *not* requested at least as much as what is.
 */
class TitleMetadataRepositoryTest {

    private val dao = FakeTitleMetadataDao()
    private val client: TmdbClient = mockk()
    private val keyStore: TmdbKeyStore = mockk<TmdbKeyStore>().apply {
        every { apiKey } returns MutableStateFlow("a-key")
    }

    private val repository = TitleMetadataRepository(
        client = client,
        keyStore = keyStore,
        dao = dao,
        now = { FIXED_NOW },
    )

    @Test
    @DisplayName("a poster's score costs one request, and a second poster costs none")
    fun `a rating is fetched once and then served from the cache`() = runTest {
        coEvery { client.summary(any(), any(), any(), any()) } returns
            TitleMetadata(rating = 8.2, isPartial = true)

        assertEquals(8.2, repository.ratingFor("The Matrix (1999)", MediaKind.VOD))
        assertEquals(8.2, repository.ratingFor("The Matrix (1999)", MediaKind.VOD))

        coVerify(exactly = 1) { client.summary(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("a record fetched for a tile is not good enough for a detail screen")
    fun `a partial record is upgraded rather than served to a detail screen`() = runTest {
        coEvery { client.summary(any(), any(), any(), any()) } returns
            TitleMetadata(rating = 8.2, isPartial = true)
        coEvery { client.lookup(any(), any(), any(), any()) } returns
            TitleMetadata(rating = 8.2, overview = "A hacker learns the truth.", topCast = listOf("Someone"))

        repository.ratingFor("The Matrix", MediaKind.VOD)
        val full = repository.forTitle("The Matrix", MediaKind.VOD)

        assertEquals("A hacker learns the truth.", full?.overview)
        // Upgraded in place: one row for the title, now complete, rather than two.
        assertEquals(1, dao.rows.size)
        assertTrue(dao.rows.values.single().isPartial.not())
    }

    @Test
    @DisplayName("a title that matched nothing is never asked about twice")
    fun `a cached miss is not re-requested by either caller`() = runTest {
        coEvery { client.lookup(any(), any(), any(), any()) } returns null

        assertNull(repository.forTitle("Nonexistent", MediaKind.VOD))
        assertNull(repository.forTitle("Nonexistent", MediaKind.VOD))
        // A miss has no fuller version to go and fetch, so the tile path must not mistake
        // it for a partial record and ask again.
        assertNull(repository.ratingFor("Nonexistent", MediaKind.VOD))

        coVerify(exactly = 1) { client.lookup(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Fargo the film and Fargo the series are two records")
    fun `kind is part of the key`() = runTest {
        coEvery { client.lookup(any(), any(), TmdbKind.MOVIE, any()) } returns TitleMetadata(rating = 8.1)
        coEvery { client.lookup(any(), any(), TmdbKind.SERIES, any()) } returns TitleMetadata(rating = 8.9)

        assertEquals(8.1, repository.forTitle("Fargo", MediaKind.VOD)?.rating)
        assertEquals(8.9, repository.forTitle("Fargo", MediaKind.SERIES)?.rating)
        assertEquals(2, dao.rows.size)
    }

    @Test
    @DisplayName("a live channel is never looked up at all")
    fun `live channels are not titles`() = runTest {
        // A television channel is not a film. Searching for one matches whatever happens to
        // share its name, and it would spend the user's rate limit doing it.
        assertNull(repository.ratingFor("BBC One HD", MediaKind.LIVE))

        coVerify(exactly = 0) { client.summary(any(), any(), any(), any()) }
    }

    private class FakeTitleMetadataDao : TitleMetadataDao {
        val rows = mutableMapOf<Pair<String, String>, TitleMetadataEntity>()

        override suspend fun find(searchTitle: String, kind: String): TitleMetadataEntity? =
            rows[searchTitle to kind]

        override suspend fun upsert(entity: TitleMetadataEntity) {
            rows[entity.searchTitle to entity.kind] = entity
        }

        override suspend fun clear() = rows.clear()
    }

    private companion object {
        const val FIXED_NOW = 1_000_000L
    }
}
