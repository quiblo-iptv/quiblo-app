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

import dev.quiblo.core.database.dao.FeedRowDao
import dev.quiblo.core.database.dao.TitleOpinionDao
import dev.quiblo.core.datastore.ProfileScopedStore

/**
 * What a profile leaves behind that no foreign key reaches (`BUG-040`).
 *
 * Favourites, resume points and the watch log cascade from the `profiles` row. These do not:
 * `feed_rows` and `title_opinions` have no foreign key to it, and per-profile preferences are not in
 * the database at all. Deleting a profile — or ending a guest session, which is the same thing on a
 * timer — left all three behind for good. Not a leak between people, since profile ids are never
 * reused, but orphaned data, and for a guest a broken promise (AC-PROF-04): its "not for me"
 * outlived the session.
 *
 * Done the way `SourceRepository.deleteSource` already clears `feed_rows`: explicitly, by id.
 */
class ProfileLeftovers(
    private val feedRowDao: FeedRowDao,
    private val titleOpinionDao: TitleOpinionDao,
    private val scopedStores: List<ProfileScopedStore>,
) {

    /** The rows. Called inside the transaction that deletes the profile, so both go or neither. */
    suspend fun clearRows(profileId: Long) {
        feedRowDao.clearForProfile(profileId)
        titleOpinionDao.clearForProfile(profileId)
    }

    /**
     * The preferences. DataStore cannot join a database transaction, so this runs after the row
     * delete commits — a crash between the two leaves preferences with no owner, which the next
     * startup's [clearPreferencesOtherThan] clears.
     */
    suspend fun clearPreferences(profileId: Long) {
        scopedStores.forEach { it.clearProfile(profileId) }
    }

    /** Preferences of every profile not in [living], including those deleted before this existed. */
    suspend fun clearPreferencesOtherThan(living: Set<Long>) {
        scopedStores.forEach { it.clearProfilesOtherThan(living) }
    }
}
