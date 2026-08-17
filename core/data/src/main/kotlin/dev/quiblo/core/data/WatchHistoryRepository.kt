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

import dev.quiblo.core.database.dao.ResumePositionDao
import dev.quiblo.core.database.entity.ResumePositionEntity
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What the user has watched, and where they got to in it.
 *
 * Separate from `ChannelRepository`, which answers questions about what a source *offers*.
 * This answers questions about what the user *did*, and the two have nothing in common but
 * the identity strings they both key on: one is refreshed wholesale from a provider every
 * time a playlist reloads, the other must survive exactly that.
 *
 * A resume point and a history entry are deliberately the same record. Two would need a rule
 * for what happens when they disagree, and there is no honest answer to that question — a
 * user who removes something from "continue watching" has said they are not continuing it,
 * so a resume point outliving the removal would be a bug wearing a feature's clothes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryRepository(
    private val resumePositionDao: ResumePositionDao,
    /** Whose history this is. Resolved here so no caller can forget to say. */
    private val profiles: ProfileRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * What the user has been watching, most recent first, one entry per title.
     *
     * A series appears once, at the episode last touched, rather than once per episode:
     * "continue watching" is a list of things, and six rows of the same programme is a list
     * of one thing pretending to be six.
     *
     * The scan is deliberately wider than the result. Collapsing happens after the read, so
     * a series watched through five episodes in a row cannot crowd everything else out of
     * the answer.
     */
    fun observeHistory(sourceId: Long, kind: MediaKind, limit: Int = DEFAULT_HISTORY_LIMIT): Flow<List<HistoryEntry>> =
        profiles.activeProfile.flatMapLatest { profile ->
            resumePositionDao.observeHistory(
                profileId = profile?.id ?: Profile.NONE_ID,
                sourceId = sourceId,
                kind = kind.name,
                limit = HISTORY_SCAN_LIMIT,
            )
        }
            .map { rows ->
                rows.map { it.toDomain() }
                    .distinctBy { it.titleKey }
                    .take(limit)
            }

    /** Where the user got to in this item, or 0 if it has never been played (AC-PLAY-03). */
    suspend fun resumePosition(stableKey: String): Long =
        resumePositionDao.positionFor(profiles.activeProfileId, stableKey) ?: 0L

    /**
     * The same, as a flow, and this is what a detail screen watches.
     *
     * **A single read cannot answer this question correctly.** Coming back from the player, the
     * screen returns to the foreground at the same moment the player is being disposed and is
     * writing the position it finished with. Nothing orders a read against a write in flight, so
     * the button said "Play" for something the viewer was four minutes into — and, having read
     * once, never asked again.
     *
     * A flow does not order them either. It removes the need to: a write that lands a hundred
     * milliseconds late still lands, and the button changes when it does.
     */
    fun observeResumePosition(stableKey: String): Flow<Long> =
        profiles.activeProfile.flatMapLatest { profile ->
            resumePositionDao.observePositionFor(profile?.id ?: Profile.NONE_ID, stableKey)
        }.map { it ?: 0L }

    /**
     * The most recently watched of [stableKeys], with where it stopped.
     *
     * Used to offer "resume" for a container — a series — whose parts are tracked
     * individually. Returns null when none of them has ever been played.
     */
    suspend fun mostRecentlyWatched(stableKeys: List<String>): Pair<String, Long>? {
        if (stableKeys.isEmpty()) return null
        val entity = resumePositionDao.mostRecentOf(profiles.activeProfileId, stableKeys) ?: return null
        return entity.stableKey to entity.positionMillis
    }

    /** The same, as a flow, for the reason [observeResumePosition] is one. */
    fun observeMostRecentlyWatched(stableKeys: List<String>): Flow<Pair<String, Long>?> =
        if (stableKeys.isEmpty()) {
            flowOf(null)
        } else {
            profiles.activeProfile.flatMapLatest { profile ->
                resumePositionDao.observeMostRecentOf(profile?.id ?: Profile.NONE_ID, stableKeys)
            }.map { entity -> entity?.let { it.stableKey to it.positionMillis } }
        }

    /**
     * Records where playback stopped, and enough about the item to list it later.
     *
     * One call rather than a position write and a separate history write, because they are
     * one fact. [HistoryEntry.watchedAtEpochMillis] is ignored and stamped here: when
     * something was watched is not the caller's to assert.
     */
    suspend fun saveProgress(entry: HistoryEntry) {
        resumePositionDao.upsert(
            ResumePositionEntity(
                stableKey = entry.stableKey,
                profileId = profiles.activeProfileId,
                positionMillis = entry.positionMillis,
                updatedAtEpochMillis = now(),
                sourceId = entry.sourceId,
                kind = entry.kind.name,
                title = entry.title,
                artworkUrl = entry.artworkUrl,
                durationMillis = entry.durationMillis,
                seriesStableKey = entry.seriesStableKey,
                seasonNumber = entry.seasonNumber,
                episodeNumber = entry.episodeNumber,
            ),
        )
    }

    /** Forgets one item, resume point and all. */
    suspend fun removeFromHistory(stableKey: String) =
        resumePositionDao.delete(profiles.activeProfileId, stableKey)

    /** Forgets every episode of one series — what "remove from history" means on a series. */
    suspend fun removeSeriesFromHistory(seriesStableKey: String) =
        resumePositionDao.deleteForSeries(profiles.activeProfileId, seriesStableKey)

    private companion object {
        /** How many titles a "continue watching" row offers before it is just a catalogue. */
        const val DEFAULT_HISTORY_LIMIT = 20

        /**
         * How many rows are read before collapsing episodes to their series.
         *
         * Wider than the result on purpose: someone three episodes into two programmes
         * would otherwise see one of them push the other off the end.
         */
        const val HISTORY_SCAN_LIMIT = 200
    }
}
