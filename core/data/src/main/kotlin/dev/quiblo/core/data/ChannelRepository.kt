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

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import dev.quiblo.core.common.SCRIPT_MASK_UNKNOWN
import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.common.isInHiddenScript
import dev.quiblo.core.common.toMask
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.database.dao.FavoriteDao
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.database.dao.escapeForLike
import dev.quiblo.core.database.entity.FavoriteEntity
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Profile
import dev.quiblo.core.model.SeriesDetails
import dev.quiblo.core.model.SourceKind
import dev.quiblo.core.model.VodDetails
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.SeriesDetailsResult
import dev.quiblo.source.api.SeriesSource
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.VodDetailsResult
import dev.quiblo.source.api.VodSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

/**
 * Read access to a source's content, for the browse screens.
 *
 * Every query is scoped by [MediaKind]. Live, Movies and Series are separate
 * destinations, and an Xtream account returns all three from a single load — the case
 * that makes an unfiltered query look correct right up until it is not.
 *
 * Filtering, searching and the favourite join all happen in SQL rather than in
 * composition, so a 20,000-entry playlist neither blocks a frame nor holds a second
 * filtered copy in memory (AC-PL-05, AC-FAV-05).
 *
 * **The mapping from rows to domain objects runs on [browseDispatcher], and that is not an
 * optimisation.** A Flow operator runs in its *collector's* context, and every collector
 * here is a `stateIn(viewModelScope, …)` — which is `Dispatchers.Main.immediate`. Without
 * the `flowOn`, allocating one [Channel] per row happened on the main thread every time
 * Room re-emitted, which is on every write to the table. At 67,000 channels that is an
 * ANR, and it was reported as one. Keeping the SQL cheap was never enough on its own,
 * because the work after the SQL was the larger half.
 */
// Seven collaborators, and each is a different question this repository answers: two DAOs,
// whose data it is, where sources come from, how to fetch them, the clock, and the thread to
// map on. Bundling them behind a holder would rename the list rather than shorten it.
//
// Fifteen functions, which is one over the threshold, and the fifteenth is `pagedBrowse` — the
// same feed as `observeBrowse` in the shape a flat grid needs. Both have to exist: the
// television's poster grid groups its whole answer into rows and cannot take a paged list, and
// a phone grid must not read a catalogue to draw a screenful. Splitting them across two classes
// would put one table's reads behind two names and leave the count where it is.
@Suppress("LongParameterList", "TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelRepository(
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    /**
     * Who is watching.
     *
     * Read here rather than passed in by every caller. A favourite belongs to a person, but
     * no screen in this app needs to know that to ask for its own catalogue — and threading
     * an id through every ViewModel would be an invitation for one of them to forget, which
     * shows up as one person's favourites appearing in another's list.
     */
    private val profiles: ProfileRepository,
    private val sourceDao: SourceDao? = null,
    private val mediaSources: Map<SourceKind, MediaSource> = emptyMap(),
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * Where rows are turned into domain objects.
     *
     * Injected rather than hardcoded so a test can prove the work leaves the caller's
     * thread, which is the whole point of it — see [browseDispatcher]'s use below.
     */
    private val browseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * The writing systems this viewer has hidden (INC-F14).
     *
     * A flow rather than the repository that owns it, so a test can hand this one value in
     * without standing up a DataStore, and so the default is the honest one: hide nothing.
     */
    private val hiddenScripts: Flow<Set<TitleScript>> = flowOf(emptySet()),
) {

    /**
     * The browse feed.
     *
     * @param groupTitle null for every category.
     * @param query blank for no search.
     */
    fun observeBrowse(
        sourceId: Long,
        kind: MediaKind,
        groupTitle: String? = null,
        query: String = "",
        favoritesOnly: Boolean = false,
    ): Flow<List<Channel>> =
        // Re-queried when the profile changes, so switching redraws the hearts rather than
        // leaving the previous viewer's on screen until something else invalidates them.
        //
        // Re-queried on the hidden scripts too, and that is the change `021` made: the filter
        // used to run in Kotlin over every row of every emission, and it is now a bitmask
        // predicate the query itself applies. A row that will be hidden is no longer read, no
        // longer mapped, and no longer walked character by character to decide.
        combine(profiles.activeProfile, hiddenScripts) { profile, hidden -> profile to hidden }
            .flatMapLatest { (profile, hidden) ->
                channelDao.observeBrowse(
                    profileId = profile?.id ?: Profile.NONE_ID,
                    sourceId = sourceId,
                    kind = kind.name,
                    groupTitle = groupTitle,
                    // Escaped so a viewer typing % or _ searches for those characters rather than
                    // handing SQL a wildcard. See escapeForLike.
                    query = escapeForLike(query.trim()),
                    favoritesOnly = if (favoritesOnly) 1 else 0,
                    hiddenMask = hidden.toMask(),
                    unknownMask = SCRIPT_MASK_UNKNOWN,
                ).map { rows ->
                    rows.hidingUncomputedScripts(hidden, { it.channel.scriptMask }, { it.channel.name })
                }
            }
            .map { rows -> rows.map { it.channel.toDomain(isFavorite = it.isFavorite) } }
            .flowOn(browseDispatcher)

    /**
     * The same feed, a page at a time.
     *
     * **What this replaces is a screen that read its whole catalogue to draw one screenful.**
     * `observeBrowse` has no `LIMIT` and nothing paged it, so opening Movies on a large account
     * materialised thirty thousand rows, allocated a domain object for each, and handed the lot to
     * a lazy grid that would draw about twelve of them. The rows after the first page were paid
     * for on every emission and looked at by nobody.
     *
     * The predicates are the paged query's, not this layer's — including the writing-system mask,
     * which is why a hidden title costs nothing here rather than costing a page's worth of
     * overscan. Rows written before schema 19 carry no mask and are still decided in Kotlin, on
     * the page they arrive in.
     *
     * `PagingData` is not a list and deliberately does not become one. The screens that need the
     * whole answer at once — the television's poster grid, which groups into category rows — use
     * [observeCategoryRows] instead, which caps each category in SQL.
     */
    fun pagedBrowse(
        sourceId: Long,
        kind: MediaKind,
        groupTitle: String? = null,
        query: String = "",
        favoritesOnly: Boolean = false,
    ): Flow<PagingData<Channel>> =
        combine(profiles.activeProfile, hiddenScripts) { profile, hidden -> profile to hidden }
            .flatMapLatest { (profile, hidden) ->
                Pager(
                    config = PagingConfig(
                        pageSize = PAGE_SIZE,
                        // Two screenfuls ahead of the last visible row on the largest layout, so
                        // a page is fetched before it is looked at rather than after.
                        prefetchDistance = PAGE_SIZE,
                        // Off. The whole point is not to hold the catalogue in memory; a
                        // placeholder per unloaded row is a smaller version of the same mistake,
                        // and a television grid has no scrollbar for them to make meaningful.
                        enablePlaceholders = false,
                        initialLoadSize = PAGE_SIZE * INITIAL_PAGES,
                    ),
                ) {
                    channelDao.pagedBrowse(
                        profileId = profile?.id ?: Profile.NONE_ID,
                        sourceId = sourceId,
                        kind = kind.name,
                        groupTitle = groupTitle,
                        query = escapeForLike(query.trim()),
                        favoritesOnly = if (favoritesOnly) 1 else 0,
                        hiddenMask = hidden.toMask(),
                        unknownMask = SCRIPT_MASK_UNKNOWN,
                    )
                }.flow.map { page ->
                    page.filter { row ->
                        row.channel.scriptMask != SCRIPT_MASK_UNKNOWN ||
                            !row.channel.name.isInHiddenScript(hidden)
                    }.map { it.channel.toDomain(isFavorite = it.isFavorite) }
                }
            }
            .flowOn(browseDispatcher)

    /**
     * The first [perCategory] items of every category, for a screen that draws a row each.
     *
     * The television's poster grid, and it is the one browse screen Paging is wrong for: it
     * groups its whole answer into rows, and a paged list has no whole to group. What it was
     * doing wrong is simpler than paging — it read every row of a kind to draw about forty tiles
     * per row. The cap is applied in SQL, so the answer is the size of what is drawn.
     */
    fun observeCategoryRows(
        sourceId: Long,
        kind: MediaKind,
        query: String = "",
        perCategory: Int = DEFAULT_PER_CATEGORY,
    ): Flow<List<Channel>> =
        combine(profiles.activeProfile, hiddenScripts) { profile, hidden -> profile to hidden }
            .flatMapLatest { (profile, hidden) ->
                channelDao.observeCategoryRows(
                    profileId = profile?.id ?: Profile.NONE_ID,
                    sourceId = sourceId,
                    kind = kind.name,
                    query = escapeForLike(query.trim()),
                    perCategory = perCategory,
                    hiddenMask = hidden.toMask(),
                    unknownMask = SCRIPT_MASK_UNKNOWN,
                ).map { rows ->
                    rows.hidingUncomputedScripts(hidden, { it.channel.scriptMask }, { it.channel.name })
                }
            }
            .map { rows -> rows.map { it.channel.toDomain(isFavorite = it.isFavorite) } }
            .flowOn(browseDispatcher)

    /**
     * The playable rows behind a set of ids, for the feeds that choose by id rather than by query.
     *
     * The popular row and the suggestions row both work out *which* titles they want somewhere
     * else — one against a service's list, one against a scoring function — and then need the
     * catalogue rows for them. One read for both, because the two lists overlap more often than
     * not and two queries for one question is two chances to disagree about a favourite.
     *
     * **The script filter applies here, and its absence was BUG-031.** Every other catalogue feed
     * hands the hidden mask to its query; this one has no query to hand it to, because the ids
     * were already chosen. So a viewer who had hidden Arabic was still offered Arabic titles by
     * both rows that come through here — the two rows on For You that propose something the
     * viewer did not ask for, which is the worst place in the app for the setting to be ignored.
     */
    suspend fun channelsByIds(ids: List<Long>): List<Channel> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            channelDao.findAllByIds(profiles.activeProfileId, ids)
                .hidingUnreadableScripts(hiddenScripts.first(), { it.channel.scriptMask }, { it.channel.name })
                .map { it.channel.toDomain(isFavorite = it.isFavorite) }
        }

    /**
     * The newest films and series a provider has added, in one list — and how that "newest" was
     * arrived at, because the two answers are not equally strong.
     *
     * A source that dates its catalogue is asked for everything added since [sinceEpochMillis],
     * newest first. A source that dates nothing gets the end of its own list instead, half films
     * and half series, interleaved so neither is forty presses away. That second answer is a
     * weaker claim and [RecentlyAddedFeed.orderedByDate] is how the screen knows to make a
     * weaker one.
     *
     * The script filter applies, exactly as it does to browse: this is a catalogue feed, and a
     * viewer who has hidden a writing system did so to stop meeting titles in it. Favourites
     * are the one feed that ignores the filter, because those are titles chosen by hand.
     *
     * @param limit how many to keep. The caller's cap, not a page size — nothing pages this.
     * @param sinceEpochMillis the oldest date that still counts as new. The caller owns the clock.
     */
    fun observeRecentlyAdded(sourceId: Long, limit: Int, sinceEpochMillis: Long): Flow<RecentlyAddedFeed> =
        combine(
            profiles.activeProfile,
            channelDao.observeHasAddedDates(sourceId).distinctUntilChanged(),
        ) { profile, hasDates -> (profile?.id ?: Profile.NONE_ID) to hasDates }
            .flatMapLatest { (profileId, hasDates) ->
                if (hasDates) {
                    datedRecents(profileId, sourceId, limit, sinceEpochMillis)
                } else {
                    listOrderRecents(profileId, sourceId, limit)
                }
            }
            .flowOn(browseDispatcher)

    private fun datedRecents(
        profileId: Long,
        sourceId: Long,
        limit: Int,
        sinceEpochMillis: Long,
    ): Flow<RecentlyAddedFeed> = channelDao.observeRecentlyAdded(
        profileId = profileId,
        sourceId = sourceId,
        sinceEpochMillis = sinceEpochMillis,
        limit = limit,
    ).map { rows -> rows.map { it.channel.toDomain(isFavorite = it.isFavorite) } }
        .hidingUnreadableScripts(hiddenScripts) { it.name }
        .map { RecentlyAddedFeed(items = it, orderedByDate = true) }

    private fun listOrderRecents(profileId: Long, sourceId: Long, limit: Int): Flow<RecentlyAddedFeed> {
        // Half the cap each, so a playlist with both ends up with both. An odd cap gives the
        // extra place to films, which is the larger catalogue on every panel seen so far.
        val perKind = limit - limit / 2
        return combine(
            tail(profileId, sourceId, MediaKind.VOD, perKind),
            tail(profileId, sourceId, MediaKind.SERIES, limit / 2),
        ) { films, series -> RecentlyAddedFeed(items = interleave(films, series), orderedByDate = false) }
    }

    private fun tail(profileId: Long, sourceId: Long, kind: MediaKind, limit: Int): Flow<List<Channel>> =
        channelDao.observeLastInListOrder(
            profileId = profileId,
            sourceId = sourceId,
            kind = kind.name,
            limit = limit,
        ).map { rows -> rows.map { it.channel.toDomain(isFavorite = it.isFavorite) } }
            .hidingUnreadableScripts(hiddenScripts) { it.name }

    // Favourites are not filtered, and that is the point — see [hidingUnreadableScripts].
    /** Favourites across every content type (AC-FAV-01). */
    fun observeFavorites(sourceId: Long, query: String = ""): Flow<List<Channel>> =
        profiles.activeProfile.flatMapLatest { profile ->
            channelDao.observeFavorites(profile?.id ?: Profile.NONE_ID, sourceId, escapeForLike(query.trim()))
        }.map { rows -> rows.map { it.channel.toDomain(isFavorite = true) } }
            .flowOn(browseDispatcher)

    /**
     * Marks or unmarks a favourite.
     *
     * Keyed by the provider's stable identity, never by row id. That is what lets a
     * favourite survive a refresh in which the stream URL changed and every row was
     * reinserted with a new id (AC-FAV-03).
     */
    suspend fun toggleFavorite(channel: Channel) {
        val profileId = profiles.activeProfileId
        if (favoriteDao.isFavorite(profileId, channel.sourceId, channel.stableKey)) {
            favoriteDao.remove(profileId, channel.sourceId, channel.stableKey)
        } else {
            favoriteDao.add(
                FavoriteEntity(
                    sourceId = channel.sourceId,
                    stableKey = channel.stableKey,
                    favoritedAtEpochMillis = now(),
                    profileId = profileId,
                ),
            )
        }
    }

    /**
     * Whether one item is favourited, as a stream.
     *
     * For the detail screens, which both show the state and change it. Keyed on the same
     * stable identity [toggleFavorite] writes, so the two cannot disagree.
     */
    fun observeIsFavorite(channel: Channel): Flow<Boolean> =
        profiles.activeProfile.flatMapLatest { profile ->
            favoriteDao.observeIsFavorite(profile?.id ?: Profile.NONE_ID, channel.sourceId, channel.stableKey)
        }

    suspend fun favoriteCount(sourceId: Long): Int =
        favoriteDao.countFor(profiles.activeProfileId, sourceId)

    suspend fun channelCount(sourceId: Long): Int = channelDao.countForSource(sourceId)

    suspend fun findById(channelId: Long): Channel? = channelDao.findById(channelId)?.toDomain()

    /**
     * The playable row for a provider identity, or null if the source no longer carries it.
     *
     * How a history entry gets back to a detail screen: history is keyed by identity so it
     * survives a refresh, and a detail screen is reached by row id, which does not.
     */
    suspend fun findByStableKey(sourceId: Long, stableKey: String): Channel? =
        channelDao.findByStableKey(sourceId, stableKey)?.toDomain()

    suspend fun getSeriesDetails(channelId: Long): SeriesDetailsResult {
        val channel = findById(channelId) ?: return SeriesDetailsResult.Failure(SourceError.NotFound)
        return getSeriesDetails(channel)
    }

    suspend fun getSeriesDetails(channel: Channel): SeriesDetailsResult =
        seriesCache[channel.stableKey]?.let { SeriesDetailsResult.Success(it) } ?: fetchSeriesDetails(channel)

    private suspend fun fetchSeriesDetails(channel: Channel): SeriesDetailsResult {
        val seriesId = channel.providerStreamId ?: return SeriesDetailsResult.Failure(SourceError.NotFound)
        val sourceDao = sourceDao ?: return SeriesDetailsResult.Failure(SourceError.NotFound)
        val source = sourceDao.findById(channel.sourceId)?.toDomain()
            ?: return SeriesDetailsResult.Failure(SourceError.UnreachableHost)
        val seriesSource = mediaSources[source.kind] as? SeriesSource
            ?: return SeriesDetailsResult.Failure(SourceError.NotFound)

        return seriesSource.seriesDetails(
            request = SourceRequest(channel.sourceId, source.url),
            seriesId = seriesId,
        ).also { if (it is SeriesDetailsResult.Success) seriesCache[channel.stableKey] = it.details }
    }

    /**
     * Details for one film.
     *
     * Fails with [SourceError.NotFound] when the source cannot describe films at all —
     * an M3U playlist carries no plot, so there is nothing to fetch rather than something
     * that went wrong. Callers show the artwork and title they already have.
     */
    suspend fun getVodDetails(channelId: Long): VodDetailsResult {
        val channel = findById(channelId) ?: return VodDetailsResult.Failure(SourceError.NotFound)
        return vodCache[channel.stableKey]?.let { VodDetailsResult.Success(it) } ?: fetchVodDetails(channel)
    }

    private suspend fun fetchVodDetails(channel: Channel): VodDetailsResult {
        val vodId = channel.providerStreamId ?: return VodDetailsResult.Failure(SourceError.NotFound)
        val sourceDao = sourceDao ?: return VodDetailsResult.Failure(SourceError.NotFound)
        val source = sourceDao.findById(channel.sourceId)?.toDomain()
            ?: return VodDetailsResult.Failure(SourceError.UnreachableHost)
        val vodSource = mediaSources[source.kind] as? VodSource
            ?: return VodDetailsResult.Failure(SourceError.NotFound)

        return vodSource.vodDetails(
            request = SourceRequest(channel.sourceId, source.url),
            vodId = vodId,
        ).also { if (it is VodDetailsResult.Success) vodCache[channel.stableKey] = it.details }
    }

    /**
     * Details already fetched this session, so re-opening an item costs nothing.
     *
     * Held in memory rather than in the database on purpose. Unlike film metadata, these
     * come from the user's own panel and describe what it is serving *now* — an episode
     * list can gain an episode, and a stale one persisted across weeks would be wrong in a
     * way nobody could clear. A session is the honest lifetime: long enough that browsing
     * back and forth is free, short enough that a relaunch re-reads the truth.
     *
     * Failures are deliberately not cached. A blocked panel recovers, and remembering a
     * refusal would keep a working account looking broken until the app was restarted.
     *
     * **Keyed by the provider's stable identity, not by row id**, on exactly the rule
     * [findByStableKey] exists to serve: a refresh reinserts every row with a new id. Keyed by id,
     * the whole cache went stale the moment anything was refreshed and then sat there for the rest
     * of the session — every entry unreachable, and none of them freed.
     */
    private val seriesCache = ConcurrentHashMap<String, SeriesDetails>()
    private val vodCache = ConcurrentHashMap<String, VodDetails>()

    private companion object {
        /**
         * How many rows a page holds.
         *
         * Several screenfuls of the largest layout, which is a television grid. Small enough that
         * the first page arrives immediately whatever the catalogue's size — the whole point —
         * and large enough that scrolling does not issue a query every few tiles.
         */
        const val PAGE_SIZE = 60

        /**
         * How many pages the first load asks for.
         *
         * Paging's own advice, and it is about scroll restoration rather than speed: a screen
         * returned to has to be able to redraw the position it was left at, and a single first
         * page cannot if the viewer had scrolled past it.
         */
        const val INITIAL_PAGES = 3

        /**
         * How many tiles a television category row holds.
         *
         * The same forty [BrowseFeed.recentLimit] uses, for the same reason: a row is walked with
         * a D-pad, and nobody presses right forty times. Reading more is reading rows nobody
         * reaches.
         */
        const val DEFAULT_PER_CATEGORY = 40
    }
}

/**
 * The Recently Added row, and what its order actually means.
 *
 * Two facts rather than a list, because the screen's wording depends on the second one. A row
 * built from provider dates is genuinely "recently added"; a row built from the end of a playlist
 * is "the latest entries in your playlist" and must not claim more than that. Passing only the
 * list would leave the screen guessing, and the guess it would make — "non-empty, so these must be
 * new" — is the one that puts a wrong sentence over a right list.
 */
data class RecentlyAddedFeed(
    val items: List<Channel> = emptyList(),
    /** True when the provider dated these itself. False when the order is the playlist's own. */
    val orderedByDate: Boolean = true,
)

/**
 * One from each, in turn, until both run out.
 *
 * So that a playlist listing every film before every series does not produce a row of forty films
 * with the series past the end of the cap. Order within each list is preserved.
 */
private fun interleave(first: List<Channel>, second: List<Channel>): List<Channel> = buildList {
    val rounds = maxOf(first.size, second.size)
    for (index in 0 until rounds) {
        first.getOrNull(index)?.let(::add)
        second.getOrNull(index)?.let(::add)
    }
}
