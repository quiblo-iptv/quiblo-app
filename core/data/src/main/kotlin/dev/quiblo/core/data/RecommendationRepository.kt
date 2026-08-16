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

import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.TitleMetadataDao
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.tmdb.titleIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The "You May Like" row: what to suggest, and why.
 *
 * Everything difficult about this feature is in [Recommender], which is pure arithmetic and
 * tested as such. This class is the plumbing around it — read the history, read the genres, match
 * the two against the catalogue by cleaned title, hand the result over — and it is written so
 * that the plumbing has no opinions of its own to get wrong.
 *
 * **Per profile.** Watch history already is, and a suggestion row that was not would leak one
 * person's viewing to another, which is the failure profiles exist to prevent (`AC-PROF-02`).
 *
 * **Silent when it knows nothing.** No history, no metadata key, or an unscanned catalogue all
 * produce an empty list, and the row above draws nothing rather than an empty shelf.
 */
class RecommendationRepository(
    private val history: WatchHistoryRepository,
    private val titleMetadataDao: TitleMetadataDao,
    private val channelDao: ChannelDao,
    /** Sixty thousand titles cleaned and matched. Not the caller's thread. */
    private val matchDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun suggestions(sourceId: Long, limit: Int = Recommender.DEFAULT_LIMIT): List<Suggestion> {
        val watched = watchedTitles(sourceId)
        val genreRows = titleMetadataDao.allGenreRows().filterNot { it.isMiss }

        // Two ways there is nothing honest to say: nobody has watched anything on this profile,
        // or nothing in the catalogue has been described yet. Both draw no row at all.
        if (watched.isEmpty() || genreRows.isEmpty()) return emptyList()

        // Hidden categories are left out here, unlike the popular row. A suggestion is the app
        // proposing something unprompted, and proposing out of a shelf the viewer has put away
        // is the app arguing with them. A hidden writing system is the same argument, and it is
        // answered where these ids become rows — `ChannelRepository.channelsByIds`, BUG-031.
        val titles = channelDao.titlesForMetadata(sourceId, includeHidden = false)

        return withContext(matchDispatcher) {
            val genresByTitle = genreRows.associateBy(
                { it.searchTitle to it.kind },
                { it.genres.orEmpty().splitToGenres() },
            )

            val candidates = titles.mapNotNull { row ->
                val kind = row.kind.toMediaKindOrNull() ?: return@mapNotNull null
                val identity = row.name.titleIdentity().takeIf { it.searchTitle.isNotBlank() }
                    ?: return@mapNotNull null
                val genres = genresByTitle[identity.searchTitle to row.kind].orEmpty()
                if (genres.isEmpty()) return@mapNotNull null
                CandidateTitle(
                    channelId = row.id,
                    kind = kind,
                    title = identity.searchTitle,
                    genres = genres,
                )
            }

            Recommender.suggest(
                watched = watched.mapNotNull { entry ->
                    val identity = entry.title.titleIdentity().takeIf { it.searchTitle.isNotBlank() }
                        ?: return@mapNotNull null
                    val genres = genresByTitle[identity.searchTitle to entry.kind.name].orEmpty()
                    WatchedTitle(
                        title = identity.searchTitle,
                        genres = genres,
                        fraction = entry.watchedFraction(),
                        watchedAtEpochMillis = entry.watchedAtEpochMillis,
                    )
                },
                candidates = candidates,
                now = now(),
                limit = limit,
            )
        }
    }

    /**
     * What this profile has watched, films and series together.
     *
     * Two reads because the history is stored per kind, and both are wanted: somebody who watches
     * nothing but series should still be offered a film that shares their genres. Live channels
     * are never in the history at all — the player does not record them.
     */
    private suspend fun watchedTitles(sourceId: Long): List<HistoryEntry> =
        HISTORY_KINDS.flatMap { kind -> history.observeHistory(sourceId, kind).first() }

    /**
     * When each watched title was last played, by title.
     *
     * The key is the same title a [Suggestion] names as its cause, which is what lets the cached
     * suggestions row tell that a cause has been watched again since the suggestion was made.
     * Watching something a second time is the strongest signal this app collects, and a row that
     * kept a fortnight-old answer in front of what that signal produced would be ignoring it.
     */
    suspend fun lastWatchedByTitle(sourceId: Long): Map<String, Long> =
        watchedTitles(sourceId)
            .groupBy { it.title }
            .mapValues { (_, entries) -> entries.maxOf { it.watchedAtEpochMillis } }

    /**
     * How much of a title was watched, from 0 to 1.
     *
     * A duration of zero means the player never learned one — a stream that reports no length, or
     * a resume point written before the first buffer. Treated as a full watch rather than as
     * nothing: the viewer opened it and it is the only fact available, and reading it as 0 would
     * throw away the whole of a household's live-adjacent viewing.
     */
    private fun HistoryEntry.watchedFraction(): Double =
        if (durationMillis <= 0L) 1.0 else (positionMillis.toDouble() / durationMillis).coerceIn(0.0, 1.0)

    private companion object {
        val HISTORY_KINDS = listOf(MediaKind.VOD, MediaKind.SERIES)
    }
}

/**
 * The cache's newline-separated genre string, as a list.
 *
 * Its own function rather than [String.split] at each call site, because "what separates two
 * genres" is a fact about the cache's format and belongs in one place.
 */
private fun String.splitToGenres(): List<String> =
    split('\n').map { it.trim() }.filter { it.isNotEmpty() }
