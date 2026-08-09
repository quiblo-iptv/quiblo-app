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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Who is watching, and the switching of it.
 *
 * **The active profile is a [StateFlow] with a synchronous value, and that shape is load
 * bearing.** Every profile-scoped read in the app needs the id — the browse query, a
 * favourite toggle, a resume point written from the player — and a suspending lookup in each
 * of those would be a database round trip on every one. Held here, it is a field read.
 *
 * The value falls back to [Profile.NONE_ID] rather than to a real profile whenever nobody has
 * chosen, or whoever was chosen has gone. That is not a failure case to guard against at each
 * call site: it is an id that matches no row, so reads come back empty and writes land
 * nowhere, which is exactly right for the moment before the chooser has been answered.
 */
class ProfileRepository(
    private val profileDao: ProfileDao,
    private val profileStore: ProfileStore,
    private val now: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    val profiles: Flow<List<Profile>> = profileDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * The chosen profile, or null when the chooser should be shown.
     *
     * Derived from both halves rather than from the stored id alone, because a stored id
     * whose row has gone is the normal way a guest session ends: the row is deleted at
     * startup and this becomes null, which is what puts the chooser back on screen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProfile: StateFlow<Profile?> = combine(
        profileStore.activeProfileId,
        profileDao.observeAll(),
    ) { storedId, rows ->
        rows.firstOrNull { it.id == storedId }?.toDomain()
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The id every profile-scoped query is filtered by. [Profile.NONE_ID] when nobody has chosen. */
    val activeProfileId: Long get() = activeProfile.value?.id ?: Profile.NONE_ID

    /**
     * Removes any guest left over from a previous session.
     *
     * Called at startup, before anything is shown. Deleting the row takes its favourites and
     * its resume points with it by foreign key, which is why guest is a row at all — the
     * promise that guest data does not outlive the session is kept by the database rather
     * than by every screen remembering to help.
     */
    suspend fun endGuestSessions() {
        profileDao.deleteGuests()
    }

    suspend fun addProfile(name: String): Profile? {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return null

        val id = profileDao.insert(
            ProfileEntity(name = cleaned, createdAtEpochMillis = now(), isGuest = false),
        )
        return Profile(id = id, name = cleaned)
    }

    /**
     * Starts a guest session, replacing any that somehow survived.
     *
     * At most one guest exists at a time: a second would be a second set of throwaway
     * favourites nobody could tell apart in the chooser.
     */
    suspend fun startGuestSession(guestName: String): Profile {
        profileDao.deleteGuests()
        val id = profileDao.insert(
            ProfileEntity(name = guestName, createdAtEpochMillis = now(), isGuest = true),
        )
        val guest = Profile(id = id, name = guestName, isGuest = true)
        profileStore.setActiveProfileId(id)
        return guest
    }

    suspend fun select(profile: Profile) {
        profileStore.setActiveProfileId(profile.id)
    }

    /**
     * Puts the chooser back, ending a guest session if that is what was running.
     *
     * Leaving guest deletes it here rather than waiting for the next startup, so a viewer who
     * hands the remote back does not leave their evening's favourites sitting in the chooser
     * for whoever picks it up.
     */
    suspend fun signOut() {
        if (activeProfile.value?.isGuest == true) profileDao.deleteGuests()
        profileStore.setActiveProfileId(null)
    }

    /** Deleting a profile takes its favourites and resume points with it, by foreign key. */
    suspend fun delete(profile: Profile) {
        profileDao.delete(profile.id)
        if (activeProfile.value?.id == profile.id) profileStore.setActiveProfileId(null)
    }
}

private fun ProfileEntity.toDomain() = Profile(id = id, name = name, isGuest = isGuest)
