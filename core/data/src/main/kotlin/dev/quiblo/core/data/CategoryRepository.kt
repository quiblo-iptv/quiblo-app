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

import dev.quiblo.core.database.dao.CategoryOverrideDao
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.entity.CategoryOverrideEntity
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Categories, with one viewer's local edits applied.
 *
 * Separate from `ChannelRepository` because it answers a different question. That class
 * reads content; this one reads and writes the small set of preferences a user has
 * expressed *about* content, and folding the two together was what pushed the first past
 * every size threshold the project has.
 *
 * Every edit — hiding, renaming and reordering — is local. Nothing is sent to the provider,
 * hiding deletes no channels, and the provider's own title stays the key, because it is the only
 * thing that survives a refresh reassigning every id in the database.
 *
 * **And every edit belongs to the profile that made it.** Hiding a shelf, renaming one into your
 * own language and dragging one to the top are all the same statement favourites are — this is
 * what *I* want my list to look like — and one of them deciding it for the whole household was
 * the fault. The reads follow the active profile, so switching person redraws the list rather
 * than needing anything to be reloaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryRepository(
    private val channelDao: ChannelDao,
    private val categoryOverrideDao: CategoryOverrideDao,
    private val profiles: ProfileRepository,
) {

    /**
     * Categories for browsing: edits applied, hidden ones removed.
     *
     * Joined in code rather than in SQL because a category has no id to join on — it is
     * derived by grouping channels — so the provider's title is the only key available.
     */
    fun observeCategories(sourceId: Long, kind: MediaKind): Flow<List<Category>> =
        withOverrides(sourceId, kind).map { categories -> categories.filterNot { it.isHidden } }

    /** Every category including hidden ones, for the screen that edits them. */
    fun observeAllCategories(sourceId: Long, kind: MediaKind): Flow<List<Category>> =
        withOverrides(sourceId, kind)

    suspend fun setCategoryHidden(kind: MediaKind, originalTitle: String, hidden: Boolean) {
        val existing = currentOverride(kind, originalTitle)
        categoryOverrideDao.upsert(
            CategoryOverrideEntity(
                profileId = profiles.activeProfileId,
                kind = kind.name,
                originalTitle = originalTitle,
                customName = existing?.customName,
                isHidden = hidden,
                userOrder = existing?.userOrder,
            ),
        )
    }

    /**
     * Moves one category one place, and writes down where every category now sits.
     *
     * **The whole visible order is written, not only the row that moved.** A single moved row
     * would carry a position while its neighbours carried none, and the comparison between "third"
     * and "not moved" has no answer — so the first move would scatter the list rather than shift
     * one shelf by one place. Stamping the order the viewer is looking at makes the result the
     * list they saw with two rows exchanged, which is the only outcome that reads as a move.
     *
     * Hidden categories are stamped too, so that showing one again puts it back where it was
     * rather than at the end.
     *
     * @param ordered every category of this kind, in the order it is currently drawn, hidden
     *   ones included.
     * @param by how far to move it: -1 for up one place, 1 for down one.
     */
    suspend fun moveCategory(kind: MediaKind, originalTitle: String, ordered: List<String>, by: Int) {
        val from = ordered.indexOf(originalTitle).takeIf { it >= 0 } ?: return
        val to = (from + by).coerceIn(0, ordered.lastIndex)
        if (to == from) return

        val profileId = profiles.activeProfileId
        val moved = ordered.toMutableList().apply { add(to, removeAt(from)) }
        val existing = categoryOverrideDao.observeForKind(profileId, kind.name)
            .first()
            .associateBy { it.originalTitle }

        categoryOverrideDao.upsertAll(
            moved.mapIndexed { index, title ->
                val override = existing[title]
                CategoryOverrideEntity(
                    profileId = profileId,
                    kind = kind.name,
                    originalTitle = title,
                    customName = override?.customName,
                    isHidden = override?.isHidden == true,
                    userOrder = index,
                )
            },
        )
    }

    suspend fun renameCategory(kind: MediaKind, originalTitle: String, customName: String?) {
        val cleaned = customName?.trim()?.takeIf { it.isNotBlank() }
        val existing = currentOverride(kind, originalTitle)

        // A row that neither renames, hides nor moves is noise. Removing it keeps the table a
        // record of deliberate edits rather than of every category ever looked at.
        if (cleaned == null && existing?.isHidden != true && existing?.userOrder == null) {
            categoryOverrideDao.clear(profiles.activeProfileId, kind.name, originalTitle)
            return
        }

        categoryOverrideDao.upsert(
            CategoryOverrideEntity(
                profileId = profiles.activeProfileId,
                kind = kind.name,
                originalTitle = originalTitle,
                customName = cleaned,
                isHidden = existing?.isHidden == true,
                userOrder = existing?.userOrder,
            ),
        )
    }

    private suspend fun currentOverride(kind: MediaKind, originalTitle: String) =
        categoryOverrideDao.observeForKind(profiles.activeProfileId, kind.name)
            .first()
            .firstOrNull { it.originalTitle == originalTitle }

    private companion object {
        /** Where a category the viewer has never moved sorts: after every one they have. */
        const val UNMOVED = Int.MAX_VALUE
    }

    /**
     * The categories, redrawn whenever the person watching changes.
     *
     * `flatMapLatest` on the active profile rather than a one-off read of its id: switching
     * profile is a live event on every other screen in this app, and a list of shelves that kept
     * the last person's hiding until something else happened to reload it would be the one place
     * it was not.
     */
    private fun withOverrides(sourceId: Long, kind: MediaKind): Flow<List<Category>> =
        profiles.activeProfile.flatMapLatest { profile ->
            overridden(sourceId, kind, profile?.id ?: Profile.NONE_ID)
        }

    private fun overridden(sourceId: Long, kind: MediaKind, profileId: Long): Flow<List<Category>> = combine(
        channelDao.observeCategoriesByKind(sourceId, kind.name),
        categoryOverrideDao.observeForKind(profileId, kind.name),
    ) { counts, overrides ->
        val byTitle = overrides.associateBy { it.originalTitle }
        counts.map { it.toDomain(sourceId) }
            .map { category ->
                val override = byTitle[category.title]
                category.copy(
                    customName = override?.customName,
                    isHidden = override?.isHidden == true,
                    userOrder = override?.userOrder,
                )
            }
            // Moved categories first, in the order they were moved into; everything else keeps
            // the provider's own order behind them. A stable sort, so the query's order — the
            // provider's category list, then its stream order — survives untouched for every
            // category the viewer has never touched.
            .sortedBy { it.userOrder ?: UNMOVED }
    }
}
