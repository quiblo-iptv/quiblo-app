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
 * One title in a popular row: where it sits, and whether this provider carries it.
 *
 * [rank] is TMDB's position within its own kind, and each kind is now a row of its own — so the
 * numbers run 1 to 10 down each row rather than 1 to 5 twice across one. Two lists were fetched
 * and neither says anything about the other; two rows is the shape that says so.
 *
 * **[channelId] is null for a title the provider does not carry, and the entry survives anyway.**
 * The row used to drop those silently, which meant a viewer saw a top ten with four places in it
 * and no way to tell whether the missing six were unpopular or unavailable. The row now says
 * which, and a tile with no channel behind it cannot be played — see `FeedRowItem`.
 *
 * [title] and [posterUrl] are TMDB's, and are what an unavailable tile is drawn from. A tile that
 * *is* carried draws the provider's own name and artwork instead, because that is what the
 * viewer's other devices show.
 */
data class PopularEntry(
    val rank: Int,
    val kind: MediaKind,
    val channelId: Long?,
    val title: String,
    val posterUrl: String?,
) {
    val isAvailable: Boolean get() = channelId != null
}

/**
 * What the world is watching, of the things this viewer can actually play.
 *
 * **Two requests a week, and no more.** TMDB's popular lists are fetched at most once every seven
 * days and held in the database between times, so a household that opens the app every evening
 * still costs two requests across the whole week. That restraint is not politeness: this
 * project's provider account has been blocked twice over requests it did not need, and a
 * metadata service refusing is the same failure with a different host.
 *
 * **The intersection is annotation, not filtering — and that is `023`'s change.** The row used to
 * be TMDB's list narrowed to what the provider carries, which read as a top ten with holes in it:
 * a viewer saw four films where they expected ten and could not tell whether the other six were
 * unpopular or simply absent from their account. Every title now appears in its place, and the
 * ones the provider does not carry say so. The catalogue match survives as the thing that decides
 * whether a tile can be opened at all.
 *
 * **This is still not a content directory.** The list is fetched with the viewer's own Movie
 * Database key, from a service they signed up to; with no key there is no request, no cache and
 * no row at all. Quiblo indexes nothing, hosts nothing and offers no way to obtain anything it
 * cannot already play (`FREEZE.md` §2, §6).
 *
 * **With no key and no cache there is no row.** Not an empty row with a spinner: no row. That
 * rule is `013` INC-F2's and it applies here for the same reason — a screen is finished when its
 * failure looks right.
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
     * Both popular rows for this source, refreshed first if the held lists are a week old.
     *
     * Films then series, each in rank order, [perKind] of each. Returns an empty list only where
     * there is nothing honest to draw at all: no key, or no answer ever received.
     */
    suspend fun popular(sourceId: Long, perKind: Int = DEFAULT_PER_KIND): List<PopularEntry> {
        refreshIfStale()

        val held = dao.all()
        if (held.isEmpty()) return emptyList()

        // Hidden categories are included. This row is about what a provider carries, and a
        // viewer who has tidied a category out of their browse list has not said the films in
        // it stopped existing — the script filter is the setting that speaks to that.
        //
        // That filter is applied where these ids become rows, in `ChannelRepository.channelsByIds`,
        // and not here: this stage has cleaned titles rather than catalogue rows, and the mask
        // lives on the row. It runs *after* [perKind] is taken, so hiding a writing system can
        // leave the row shorter than the cap. That is the honest order — the alternative is
        // reaching further down TMDB's list to backfill places a hidden title vacated, which
        // would make the filter change which titles are popular.
        val titles = channelDao.titlesForMetadata(sourceId, includeHidden = true)

        return withContext(matchDispatcher) {
            val index = CatalogueIndex(titles)
            KIND_ORDER.flatMap { kind ->
                held.asSequence()
                    .filter { it.kind == kind.name }
                    .sortedBy { it.rank }
                    // Taken by rank, before the match rather than after it. TMDB's top ten is the
                    // top ten whatever one provider happens to stock, and reaching down to
                    // eleventh place to fill a gap left by a title the viewer cannot play would
                    // publish a ranking nobody measured.
                    .take(perKind)
                    .map { row ->
                        PopularEntry(
                            rank = row.rank,
                            kind = kind,
                            channelId = index.find(row.title, row.year, kind),
                            title = row.title,
                            posterUrl = row.posterUrl,
                        )
                    }
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
         * Ten of each, in a row of each — a top ten of films and a top ten of series.
         *
         * TMDB returns twenty per page and the whole page is already stored, so ten costs
         * nothing that five did not. A viewer walks a row with a D-pad rather than a thumb, and
         * ten is about two screenfuls at the size these tiles draw.
         */
        const val DEFAULT_PER_KIND = 10

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
