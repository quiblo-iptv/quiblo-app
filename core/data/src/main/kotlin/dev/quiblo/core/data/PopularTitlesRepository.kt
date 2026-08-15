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
import dev.quiblo.core.database.dao.PopularTitleDao
import dev.quiblo.core.database.entity.PopularTitleEntity
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.tmdb.PopularTitle
import dev.quiblo.source.tmdb.TmdbClient
import dev.quiblo.source.tmdb.TmdbPopular
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One title in the popular row: where it sits, and which of the viewer's own rows it is.
 *
 * [rank] is TMDB's position **within its own kind**, so a row of five films and five series
 * carries the numbers 1–5 twice. That is the honest reading of what was asked for: two lists were
 * fetched and neither says anything about the other, and renumbering them 1–10 would invent an
 * ordering between a film and a series that nobody measured. The poster's Movie or Series badge
 * is what tells the two fives apart.
 */
data class PopularEntry(val rank: Int, val channelId: Long, val kind: MediaKind)

/**
 * What the world is watching, of the things this viewer can actually play.
 *
 * **Two requests a week, and no more.** TMDB's popular lists are fetched at most once every seven
 * days and held in the database between times, so a household that opens the app every evening
 * still costs two requests across the whole week. That restraint is not politeness: this
 * project's provider account has been blocked twice over requests it did not need, and a
 * metadata service refusing is the same failure with a different host.
 *
 * **The intersection is the feature.** A popular list is not interesting on its own — every
 * streaming service has one and none of them can be played from here. What is interesting is
 * which of those titles the viewer's own provider carries, which is why the answer is always a
 * list of *catalogue rows* and never a list of TMDB records.
 *
 * **With no key, no cache and nothing matched, there is no row.** Not an empty row with a
 * spinner: no row. That rule is `013` INC-F2's and it applies here for the same reason — a
 * screen is finished when its failure looks right.
 */
class PopularTitlesRepository(
    private val dao: PopularTitleDao,
    private val channelDao: ChannelDao,
    private val client: TmdbClient,
    private val metadata: TitleMetadataRepository,
    /** Where the catalogue is indexed. Sixty thousand titles is not a main-thread pass. */
    private val matchDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * The popular row for this source, newest fetch first refreshed if it is a week old.
     *
     * Returns an empty list wherever there is nothing honest to draw: no key, no answer ever
     * received, or an answer holding nothing this provider carries.
     */
    suspend fun popular(sourceId: Long, perKind: Int = DEFAULT_PER_KIND): List<PopularEntry> {
        refreshIfStale()

        val held = dao.all()
        if (held.isEmpty()) return emptyList()

        // Hidden categories are included. This row is about what a provider carries, and a
        // viewer who has tidied a category out of their browse list has not said the films in
        // it stopped existing — the script filter above the browse feed is the setting that
        // speaks to that, and it applies to what this row is rendered into.
        val titles = channelDao.titlesForMetadata(sourceId, includeHidden = true)

        return withContext(matchDispatcher) {
            val index = CatalogueIndex(titles)
            KIND_ORDER.flatMap { kind ->
                held.asSequence()
                    .filter { it.kind == kind.name }
                    .sortedBy { it.rank }
                    .mapNotNull { row ->
                        val channelId = index.find(row.title, row.year, kind) ?: return@mapNotNull null
                        PopularEntry(rank = row.rank, channelId = channelId, kind = kind)
                    }
                    // Taken *after* the match, so a provider that carries none of TMDB's top
                    // five still fills the row from further down the list rather than showing
                    // four gaps.
                    .take(perKind)
                    .toList()
            }
        }
    }

    /**
     * Fetches both lists if the held ones are a week old, and does nothing otherwise.
     *
     * A refusal leaves whatever is held exactly as it was and is never written down — the same
     * rule [TitleMetadataRepository] follows, and for a longer-lived reason here: a cached
     * refusal would stand for a week rather than a fortnight of one title.
     */
    private suspend fun refreshIfStale() {
        val apiKey = metadata.apiKey.value?.takeIf { it.isNotBlank() } ?: return

        val fetchedAt = dao.oldestFetchedAt()
        if (fetchedAt != null && now() - fetchedAt < REFRESH_INTERVAL_MILLIS) return

        KIND_ORDER.forEach { kind ->
            val tmdbKind = kind.toTmdbKind() ?: return@forEach
            when (val answer = client.popular(apiKey, tmdbKind)) {
                is TmdbPopular.Refused -> Unit
                is TmdbPopular.Titles -> if (answer.entries.isNotEmpty()) {
                    dao.replaceKind(kind.name, answer.entries.map { it.toEntity(kind) })
                }
            }
        }
    }

    private fun PopularTitle.toEntity(kind: MediaKind) = PopularTitleEntity(
        kind = kind.name,
        rank = rank,
        tmdbId = tmdbId,
        title = title,
        year = year,
        posterUrl = posterUrl,
        fetchedAtEpochMillis = now(),
    )

    private companion object {
        /**
         * Five of each, which is what was asked for.
         *
         * Ten tiles is about a screenful on a television at the size the ranked row draws them,
         * and a viewer walks a row with a D-pad rather than a thumb.
         */
        const val DEFAULT_PER_KIND = 5

        /** Films first, then series, which is the order the row is drawn in. */
        val KIND_ORDER = listOf(MediaKind.VOD, MediaKind.SERIES)

        /**
         * How long a fetched list stands.
         *
         * A week, because that is roughly how fast the answer changes and because it puts the
         * cost of this feature at two requests every seven days however often the app is opened.
         */
        const val REFRESH_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
