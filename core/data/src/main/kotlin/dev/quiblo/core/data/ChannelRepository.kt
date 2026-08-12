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

import dev.quiblo.core.common.TitleScript
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
@Suppress("LongParameterList")
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
        profiles.activeProfile.flatMapLatest { profile ->
            channelDao.observeBrowse(
                profileId = profile?.id ?: Profile.NONE_ID,
                sourceId = sourceId,
                kind = kind.name,
                groupTitle = groupTitle,
                // Escaped so a viewer typing % or _ searches for those characters rather than
                // handing SQL a wildcard. See escapeForLike.
                query = escapeForLike(query.trim()),
                favoritesOnly = if (favoritesOnly) 1 else 0,
            )
        }.map { rows -> rows.map { it.channel.toDomain(isFavorite = it.isFavorite) } }
            .hidingUnreadableScripts(hiddenScripts) { it.name }
            .flowOn(browseDispatcher)

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
}
