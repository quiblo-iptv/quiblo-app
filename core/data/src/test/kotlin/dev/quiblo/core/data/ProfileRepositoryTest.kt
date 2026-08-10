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

import dev.quiblo.core.database.dao.ProfileDao
import dev.quiblo.core.database.entity.ProfileEntity
import dev.quiblo.core.datastore.ProfileStore
import dev.quiblo.core.model.Profile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Who is watching, and the one promise this feature makes that a viewer would notice being
 * broken: **a guest leaves nothing behind.**
 *
 * The deletion of guest *data* is not asserted here — it is a foreign key, and testing that
 * SQLite honours a cascade is testing SQLite. What is asserted is that the row goes, at both
 * of the moments it has to: when the guest leaves, and at the next startup for the times
 * nobody left and the television was simply switched off at the wall.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryTest {

    private val rows = MutableStateFlow(emptyList<ProfileEntity>())
    private val storedId = MutableStateFlow<Long?>(null)
    private var nextId = 1L

    private val dao: ProfileDao = mockk<ProfileDao>().apply {
        every { observeAll() } returns rows
        coEvery { insert(any()) } answers {
            val entity = firstArg<ProfileEntity>().copy(id = nextId++)
            rows.value = rows.value + entity
            entity.id
        }
        coEvery { deleteGuests() } answers {
            rows.value = rows.value.filterNot { it.isGuest }
        }
        coEvery { delete(any()) } answers {
            val id = firstArg<Long>()
            rows.value = rows.value.filterNot { it.id == id }
        }
        coEvery { find(any()) } answers { rows.value.firstOrNull { it.id == firstArg<Long>() } }
        coEvery { countNamed() } answers { rows.value.count { !it.isGuest } }
    }

    private val store: ProfileStore = mockk<ProfileStore>().apply {
        every { activeProfileId } returns storedId
        coEvery { setActiveProfileId(any()) } answers { storedId.value = firstArg() }
    }

    @Test
    fun `nobody is watching until somebody is chosen`() = runTest {
        val repository = repository()
        repository.awaitChooser()

        assertNull(repository.activeProfile.value)
        // The id that matches no row, so every profile-scoped read is empty and every write
        // lands nowhere. No screen needs a special case for the moment before choosing.
        assertEquals(Profile.NONE_ID, repository.activeProfileId)
    }

    @Test
    @DisplayName("adding somebody makes them the one watching")
    fun `a new profile can be selected`() = runTest {
        val repository = repository()
        val added = repository.addProfile("Mahmoud")
        repository.select(added!!)

        assertEquals("Mahmoud", repository.awaitWatching().name)
        assertEquals(added.id, repository.activeProfileId)
    }

    @Test
    fun `a blank name is not a profile`() = runTest {
        assertNull(repository().addProfile("   "))
    }

    @Test
    @DisplayName("leaving guest deletes it there and then")
    fun `signing out of guest removes the row`() = runTest {
        val repository = repository()
        repository.startGuestSession("Guest")
        assertTrue(repository.awaitWatching().isGuest)

        repository.signOut()
        repository.awaitChooser()

        // Gone immediately rather than at the next launch: a viewer who hands the remote back
        // should not leave their evening sitting in the chooser for whoever picks it up.
        assertTrue(rows.value.none { it.isGuest })
        assertNull(repository.activeProfile.value)
    }

    @Test
    @DisplayName("a guest that survived a power cut is gone by the time anything is drawn")
    fun `startup ends a guest session nobody left`() = runTest {
        val repository = repository()
        repository.startGuestSession("Guest")
        repository.awaitWatching()

        // A television is switched off at the wall; a process the system kills runs no
        // tidy-up. Startup is the only moment the promise can be kept for certain.
        repository.endGuestSessions()
        repository.awaitChooser()

        assertTrue(rows.value.isEmpty())
        // The stored id now points at nothing, which is what puts the chooser back — no
        // separate "was a guest" flag is needed anywhere.
        assertNull(repository.activeProfile.value)
    }

    @Test
    @DisplayName("every launch asks who is watching, even after a named profile")
    fun `startup clears the chosen profile`() = runTest {
        val repository = repository()
        val profile = repository.addProfile("Mahmoud")!!
        repository.select(profile)
        repository.awaitWatching()

        // #016: the chosen id used to survive process death, so the chooser appeared once per
        // install and a household went back to sharing one continue-watching row — the state
        // profiles exist to end. Who is watching is session state, not a stored preference.
        repository.beginSession()
        repository.awaitChooser()

        assertNull(repository.activeProfile.value)
        // The profile itself is untouched: this ends a session, it does not delete anybody.
        assertEquals(listOf("Mahmoud"), rows.value.map { it.name })
    }

    @Test
    fun `leaving a named profile keeps it`() = runTest {
        val repository = repository()
        val profile = repository.addProfile("Mahmoud")!!
        repository.select(profile)
        repository.awaitWatching()

        repository.signOut()
        repository.awaitChooser()

        assertEquals(listOf("Mahmoud"), rows.value.map { it.name })
        assertNull(repository.activeProfile.value)
    }

    @Test
    fun `there is never more than one guest`() = runTest {
        val repository = repository()
        repository.startGuestSession("Guest")
        repository.startGuestSession("Guest")
        repository.awaitWatching()

        assertEquals(1, rows.value.count { it.isGuest })
    }

    @Test
    fun `deleting the active profile puts the chooser back`() = runTest {
        val repository = repository()
        val profile = repository.addProfile("Mahmoud")!!
        repository.select(profile)
        repository.awaitWatching()

        repository.delete(profile)
        repository.awaitChooser()

        assertNull(repository.activeProfile.value)
        assertEquals(Profile.NONE_ID, repository.activeProfileId)
    }

    @Test
    fun `profiles reach the chooser as they are added`() = runTest {
        val repository = repository()

        repository.addProfile("Mahmoud")
        repository.addProfile("Sara")

        assertEquals(listOf("Mahmoud", "Sara"), repository.profiles.first().map { it.name })
    }

    /**
     * Waits for the shared state to catch up, rather than advancing the scheduler and hoping.
     *
     * `activeProfile` is an eagerly-shared flow, and a scheduler advance alone does not run
     * its sharing coroutine — a real suspension does. Reading `.value` after
     * `advanceUntilIdle` therefore saw the *previous* value and made these tests fail for a
     * reason that had nothing to do with the code under test. Awaiting the answer is both
     * correct and what a caller actually does.
     */
    private suspend fun ProfileRepository.awaitWatching(): Profile =
        activeProfile.filterNotNull().first()

    /** The other direction: nobody watching, which is what puts the chooser back on screen. */
    private suspend fun ProfileRepository.awaitChooser() {
        activeProfile.first { it == null }
    }

    /**
     * The repository's own scope is the test's background one.
     *
     * `activeProfile` is an eagerly-shared `StateFlow`, so the coroutine behind it never
     * completes by design — handed the test's main scope, `runTest` would wait a minute for
     * it and then fail every test here for a reason that has nothing to do with profiles.
     */
    private fun TestScope.repository() = ProfileRepository(
        profileDao = dao,
        profileStore = store,
        now = { FIXED_NOW },
        scope = backgroundScope,
    )

    private companion object {
        const val FIXED_NOW = 1_000L
    }
}
