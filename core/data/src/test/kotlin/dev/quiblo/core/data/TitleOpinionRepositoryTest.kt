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

import dev.quiblo.core.database.dao.TitleOpinionDao
import dev.quiblo.core.database.entity.TitleOpinionEntity
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Opinion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Thumbs up and thumbs down, and the three things about them that are easy to get wrong.
 *
 * The opinion is keyed by the *cleaned* title rather than by a channel id, which is the whole
 * point of the feature — a viewer who disliked something must not be asked again when their
 * provider re-lists it. Nothing about that is visible from a screen, so it is tested here.
 *
 * Written in `026` for work that shipped in `025` without them. That is an Amendment 10 gap
 * being closed, not new code being covered.
 */
class TitleOpinionRepositoryTest {

    private val dao = FakeTitleOpinionDao()
    private val repository = TitleOpinionRepository(dao, fakeProfiles(PROFILE), now = { DECIDED_AT })

    @Test
    fun `an opinion is stored against this viewer and this title`() = runTest {
        repository.set("The Thing", MediaKind.VOD, Opinion.UP)

        val stored = dao.rows.value.single()
        assertEquals(PROFILE, stored.profileId)
        assertEquals(Opinion.UP.name, stored.opinion)
        assertEquals(MediaKind.VOD.name, stored.kind)
        assertEquals(DECIDED_AT, stored.decidedAtEpochMillis)
    }

    /**
     * The same film under two provider spellings is one opinion.
     *
     * This is the reason the key is a cleaned title. A provider that re-lists "The Thing" as
     * "The Thing (1982) [1080p]" would otherwise be a title the viewer has never rated, and the
     * suggestions row would offer back the thing they had just thumbed down.
     */
    @Test
    fun `two spellings of one film are one opinion`() = runTest {
        repository.set("The Thing", MediaKind.VOD, Opinion.DOWN)
        repository.set("The Thing (1982) [1080p]", MediaKind.VOD, Opinion.DOWN)

        assertEquals(1, dao.rows.value.size, "Two spellings must not become two rows.")
    }

    /**
     * Clearing removes the row; it does not store `NONE`.
     *
     * A stored `NONE` and a missing row would be two ways of saying the same thing, and
     * something downstream would eventually read one of them as a middle rating.
     */
    @Test
    fun `pressing the lit button again removes the row rather than storing NONE`() = runTest {
        repository.set("Alien", MediaKind.VOD, Opinion.UP)
        repository.set("Alien", MediaKind.VOD, Opinion.NONE)

        assertTrue(dao.rows.value.isEmpty(), "No opinion is an absent row.")
    }

    @Test
    fun `a title nobody has rated reads as NONE rather than as nothing`() = runTest {
        assertEquals(Opinion.NONE, repository.observe("Unwatched").first())
    }

    @Test
    fun `an opinion is read back for the title it was set on`() = runTest {
        repository.set("Aliens", MediaKind.VOD, Opinion.DOWN)

        assertEquals(Opinion.DOWN, repository.observe("Aliens").first())
    }

    /**
     * A value the app no longer understands is not an opinion.
     *
     * Rows outlive the code that wrote them — a downgrade, or a value removed from the enum —
     * and `Opinion.valueOf` on an unknown string throws. Reading it as "no opinion" is the only
     * answer that cannot crash a detail screen.
     */
    @Test
    fun `a stored value the enum no longer has reads as NONE`() = runTest {
        dao.rows.value = listOf(
            TitleOpinionEntity(
                profileId = PROFILE,
                titleKey = "alien",
                kind = MediaKind.VOD.name,
                opinion = "MIDDLING",
                decidedAtEpochMillis = DECIDED_AT,
            ),
        )

        assertEquals(Opinion.NONE, repository.observe("Alien").first())
        assertTrue(repository.all().isEmpty(), "And it is not handed to the scorer either.")
    }

    @Test
    fun `the scorer is handed every opinion this viewer holds, by cleaned title`() = runTest {
        repository.set("The Thing", MediaKind.VOD, Opinion.UP)
        repository.set("Alien", MediaKind.VOD, Opinion.DOWN)

        assertEquals(mapOf("the thing" to Opinion.UP, "alien" to Opinion.DOWN), repository.all())
    }

    /** Another viewer's thumbs are not this viewer's. */
    @Test
    fun `opinions do not cross profiles`() = runTest {
        dao.rows.value = listOf(
            TitleOpinionEntity(
                profileId = PROFILE + 1,
                titleKey = "alien",
                kind = MediaKind.VOD.name,
                opinion = Opinion.UP.name,
                decidedAtEpochMillis = DECIDED_AT,
            ),
        )

        assertTrue(repository.all().isEmpty())
        assertEquals(Opinion.NONE, repository.observe("Alien").first())
    }

    /**
     * A DAO that behaves like the table rather than like a mock.
     *
     * The interesting assertions here are about *rows* — one row or two, present or absent — and
     * a relaxed mock would let every one of them pass by returning nothing.
     */
    private class FakeTitleOpinionDao : TitleOpinionDao {
        val rows = MutableStateFlow<List<TitleOpinionEntity>>(emptyList())

        override suspend fun allFor(profileId: Long): List<TitleOpinionEntity> =
            rows.value.filter { it.profileId == profileId }

        override fun observeFor(profileId: Long, titleKey: String): Flow<TitleOpinionEntity?> =
            rows.map { all -> all.firstOrNull { it.profileId == profileId && it.titleKey == titleKey } }

        override suspend fun upsert(opinion: TitleOpinionEntity) {
            rows.value = rows.value
                .filterNot { it.profileId == opinion.profileId && it.titleKey == opinion.titleKey }
                .plus(opinion)
        }

        override suspend fun clear(profileId: Long, titleKey: String) {
            rows.value = rows.value.filterNot { it.profileId == profileId && it.titleKey == titleKey }
        }
    }

    private companion object {
        const val PROFILE = 1L
        const val DECIDED_AT = 1_760_000_000_000L
    }
}
