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

import dev.quiblo.core.database.dao.SeriesPreferenceDao
import dev.quiblo.core.database.entity.SeriesPreferenceEntity
import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.Season
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * How one viewer likes one series laid out: merged into a single list, and in which direction.
 *
 * Defaults to by-season and oldest-first, which is what the screen did before this existed. A
 * viewer who never presses either control sees no change at all.
 */
data class SeriesPreference(
    val isMerged: Boolean = false,
    val isDescending: Boolean = false,
)

/**
 * `INC-F6`. Two booleans per series per profile, and the ordering they imply.
 *
 * **Per profile.** One household member reading a thousand-episode series from the end does not
 * decide how anyone else reads it.
 *
 * The reason this is worth a table rather than a setting: a series with a thousand episodes is
 * unusable if the newest is a thousand rows away, and a preference that is not remembered is a
 * preference re-entered every evening.
 */
class SeriesPreferenceRepository(
    private val dao: SeriesPreferenceDao,
    private val profiles: ProfileRepository,
) {

    /**
     * The preference for one series, following the active profile.
     *
     * Re-reads when the profile changes rather than when the screen opens, so switching who is
     * watching while a series screen is open shows that person's arrangement rather than the
     * previous one's.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(seriesKey: String): Flow<SeriesPreference> =
        profiles.activeProfile.flatMapLatest { profile ->
            dao.observe(profile?.id ?: 0L, seriesKey).map { row ->
                SeriesPreference(
                    isMerged = row?.isMerged ?: false,
                    isDescending = row?.isDescending ?: false,
                )
            }
        }

    suspend fun set(seriesKey: String, preference: SeriesPreference) {
        val profileId = profiles.activeProfileId
        // Nobody is watching yet, so there is nobody to remember this for. Writing it against
        // the sentinel id would file it under a profile that matches no row and can never be
        // read back — a preference that silently does not persist.
        if (profileId == 0L) return

        dao.upsert(
            SeriesPreferenceEntity(
                profileId = profileId,
                seriesKey = seriesKey,
                isMerged = preference.isMerged,
                isDescending = preference.isDescending,
            ),
        )
    }
}

/**
 * The seasons a screen should draw, given how this viewer wants to read them.
 *
 * Four shapes out of two booleans, and the merged ones are where the care is:
 *
 * - **Merged numbering stays honest.** Episode 3 of season 2 and episode 3 of season 5 end up in
 *   one list, so the season has to survive on each episode or the list becomes ambiguous at
 *   exactly the point it is most useful. That is why merging produces one [Season] whose
 *   `episodes` keep their own `seasonNumber` — the label is drawn from the episode, never from
 *   the season heading, and this function does not flatten that away.
 * - **Merging sorts across seasons, not within them.** A provider that returns season 10 before
 *   season 2 is common; concatenating in the provider's order would produce a list that is
 *   neither chronological nor the provider's idea of one.
 *
 * **The merged season carries no name**, and that is deliberate: this layer holds no display
 * strings. It is marked by [MERGED_SEASON_NUMBER], which no provider uses — seasons are numbered
 * from one, and a special-numbered season is a marker the screen can read without this function
 * having to know what language anybody speaks.
 */
fun List<Season>.arrangedBy(preference: SeriesPreference): List<Season> {
    if (isEmpty()) return this

    val ordered = if (preference.isMerged) {
        val all = flatMap { it.episodes }.sortedWith(EPISODE_ORDER)
        listOf(Season(seasonNumber = MERGED_SEASON_NUMBER, name = "", episodes = all))
    } else {
        // Seasons themselves are left in the provider's order. Reversing which season comes
        // first is not what "newest first" means to somebody scrolling one season's episodes,
        // and a chip strip that reorders itself under the finger is its own annoyance.
        map { it.copy(episodes = it.episodes.sortedWith(EPISODE_ORDER)) }
    }

    return if (preference.isDescending) {
        ordered.map { it.copy(episodes = it.episodes.reversed()) }
    } else {
        ordered
    }
}

/**
 * The season number of the one season that stands for all of them.
 *
 * Zero, because providers number seasons from one and a "season 0" is occasionally a specials
 * bucket — which is why the screen must check this against a *merged* state rather than treat
 * every zero as merged.
 */
const val MERGED_SEASON_NUMBER = 0

/** Season first, then episode. Both ascending; reversing is the caller's decision. */
private val EPISODE_ORDER = compareBy<Episode>({ it.seasonNumber }, { it.episodeNumber })
