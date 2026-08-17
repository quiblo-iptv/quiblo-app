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
import dev.quiblo.core.model.Profile
import dev.quiblo.source.tmdb.titleIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Thumbs up and thumbs down, one per title per viewer.
 *
 * **Two buttons rather than five stars.** A star rating asks somebody to be precise about a feeling
 * they are not precise about, and the precision is then treated as data. Up and down is the
 * question people actually answer, and it is the only rating this app will ever have — there is no
 * server to compare ratings against, so the value of a number here is bounded by what one
 * household's own scorer can do with it.
 *
 * **Keyed by the cleaned title rather than by a channel or a stable key.** An opinion is about the
 * film, not about the row a provider happens to be serving it from: somebody who disliked something
 * should not be asked again when their provider re-lists it under a new id, and should not be shown
 * it again on a second account.
 *
 * **Absence is the third value.** "No opinion" is not stored — clearing removes the row — because a
 * stored `NONE` and a missing row would be two ways of saying the same thing and something would
 * eventually treat one of them as a middle rating.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TitleOpinionRepository(
    private val dao: TitleOpinionDao,
    private val profiles: ProfileRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** What this viewer thinks of [title], and what they think of it after they change their mind. */
    fun observe(title: String): Flow<Opinion> =
        profiles.activeProfile.flatMapLatest { profile ->
            dao.observeFor(profile?.id ?: Profile.NONE_ID, title.key())
        }.map { row -> row?.opinion?.let { runCatching { Opinion.valueOf(it) }.getOrNull() } ?: Opinion.NONE }

    /** Records an opinion, or removes it when the viewer presses the same button again. */
    suspend fun set(title: String, kind: MediaKind, opinion: Opinion) {
        val profileId = profiles.activeProfileId
        val key = title.key()
        if (opinion == Opinion.NONE) {
            dao.clear(profileId, key)
            return
        }
        dao.upsert(
            TitleOpinionEntity(
                profileId = profileId,
                titleKey = key,
                kind = kind.name,
                opinion = opinion.name,
                decidedAtEpochMillis = now(),
            ),
        )
    }

    /** Every opinion this viewer holds, by cleaned title, for the scorer. */
    suspend fun all(): Map<String, Opinion> =
        dao.allFor(profiles.activeProfileId)
            .mapNotNull { row ->
                val opinion = runCatching { Opinion.valueOf(row.opinion) }.getOrNull() ?: return@mapNotNull null
                row.titleKey to opinion
            }
            .toMap()

    /** The same cleaning the metadata cache and the suggestions row join on. */
    private fun String.key(): String = titleIdentity().searchTitle
}
