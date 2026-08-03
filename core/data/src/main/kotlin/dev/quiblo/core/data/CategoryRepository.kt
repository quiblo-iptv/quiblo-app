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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Categories, with the user's local edits applied.
 *
 * Separate from `ChannelRepository` because it answers a different question. That class
 * reads content; this one reads and writes the small set of preferences a user has
 * expressed *about* content, and folding the two together was what pushed the first past
 * every size threshold the project has.
 *
 * Both edits — hiding and renaming — are local. Nothing is sent to the provider, hiding
 * deletes no channels, and the provider's own title stays the key, because it is the only
 * thing that survives a refresh reassigning every id in the database.
 */
class CategoryRepository(
    private val channelDao: ChannelDao,
    private val categoryOverrideDao: CategoryOverrideDao,
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
                kind = kind.name,
                originalTitle = originalTitle,
                customName = existing?.customName,
                isHidden = hidden,
            ),
        )
    }

    suspend fun renameCategory(kind: MediaKind, originalTitle: String, customName: String?) {
        val cleaned = customName?.trim()?.takeIf { it.isNotBlank() }
        val existing = currentOverride(kind, originalTitle)

        // A row that neither renames nor hides is noise. Removing it keeps the table a
        // record of deliberate edits rather than of every category ever looked at.
        if (cleaned == null && existing?.isHidden != true) {
            categoryOverrideDao.clear(kind.name, originalTitle)
            return
        }

        categoryOverrideDao.upsert(
            CategoryOverrideEntity(
                kind = kind.name,
                originalTitle = originalTitle,
                customName = cleaned,
                isHidden = existing?.isHidden == true,
            ),
        )
    }

    private suspend fun currentOverride(kind: MediaKind, originalTitle: String) =
        categoryOverrideDao.observeForKind(kind.name).first().firstOrNull { it.originalTitle == originalTitle }

    private fun withOverrides(sourceId: Long, kind: MediaKind): Flow<List<Category>> = combine(
        channelDao.observeCategoriesByKind(sourceId, kind.name),
        categoryOverrideDao.observeForKind(kind.name),
    ) { counts, overrides ->
        val byTitle = overrides.associateBy { it.originalTitle }
        counts.map { it.toDomain(sourceId) }.map { category ->
            val override = byTitle[category.title]
            category.copy(customName = override?.customName, isHidden = override?.isHidden == true)
        }
    }
}
