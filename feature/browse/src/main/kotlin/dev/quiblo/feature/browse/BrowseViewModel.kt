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

package dev.quiblo.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.quiblo.core.data.CategoryRepository
import dev.quiblo.core.data.ChannelLogoRepository
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.GuideRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Programme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** What a browse screen renders. */
data class BrowseUiState(
    /**
     * True until the first real answer arrives.
     *
     * Distinct from "no source" on purpose. The two were conflated, so every browse screen
     * opened by telling the user to add a playlist — including when they had one and it was
     * still loading. Advice that is wrong for the first second is worse than a spinner.
     */
    val isLoading: Boolean = true,
    val hasSource: Boolean = false,
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val items: List<Channel> = emptyList(),
    val query: String = "",
    /** What is airing now, keyed by channel identity. Empty for sources with no guide. */
    val nowPlaying: Map<String, Programme> = emptyMap(),
    /**
     * Scores from the metadata service, keyed by channel identity.
     *
     * Empty unless the user has configured a key, and filled in only for rows that have
     * actually been on screen. A tile with no entry shows no badge rather than a blank one:
     * "not fetched" and "no score" are the same thing to a poster.
     */
    val ratings: Map<String, Double> = emptyMap(),
    /**
     * Artwork found elsewhere, keyed by channel identity.
     *
     * Two sources feed it and neither is ever preferred over the provider's own: TMDB
     * posters for films and series, and the iptv-org reference list for live channels. A
     * panel's own artwork is the artwork for the thing being served, and second-guessing it
     * is how a grid ends up showing the wrong film's poster.
     *
     * One map rather than two because the consumer's question is one question — "is there
     * anything to show in this empty frame?" — and a tile has no use for the distinction.
     */
    val posters: Map<String, String> = emptyMap(),
    /**
     * What the user has already started, most recent first.
     *
     * Empty for Live and for Favourites: a channel is not something anyone continues, and
     * Favourites is a list the user built by hand rather than one built by watching.
     */
    val history: List<HistoryEntry> = emptyList(),
)

/**
 * Which feed a browse screen is showing.
 *
 * The two travel together everywhere — a screen is Movies, or it is Favourites — so they
 * are one argument rather than two that can be passed in the wrong combination.
 */
data class BrowseFeed(
    val kind: MediaKind,
    val favoritesOnly: Boolean = false,
)

/**
 * Drives Live, Movies, Series and Favourites.
 *
 * One implementation rather than four, parameterised by [feed]. The screens differ in what
 * they show, not in how they behave.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
// Seven collaborators, and every one of them answers a different question this screen puts
// on the page: what is in the catalogue, how it is grouped, what is on now, what it scores,
// and what has already been watched. Bundling them into a holder would hide the count
// without reducing it, and the alternative — four screens instead of one parameterised
// ViewModel — is the duplication this class exists to avoid.
@Suppress("LongParameterList")
class BrowseViewModel(
    private val feed: BrowseFeed,
    sourceRepository: SourceRepository,
    private val channelRepository: ChannelRepository,
    private val categoryRepository: CategoryRepository,
    private val guideRepository: GuideRepository,
    private val metadataRepository: TitleMetadataRepository,
    private val historyRepository: WatchHistoryRepository,
    private val channelLogoRepository: ChannelLogoRepository,
) : ViewModel() {

    /**
     * Channels whose guide has already been requested this session.
     *
     * Rows are re-composed constantly while scrolling; without this the same channel
     * would be fetched dozens of times.
     */
    private val guideRequested = ConcurrentHashMap.newKeySet<String>()

    /** Caps concurrent guide requests so scrolling cannot stampede a panel. */
    private val guideLimiter = Semaphore(MAX_CONCURRENT_GUIDE_FETCHES)

    /** The same two guards for the metadata service, which rate-limits per key. */
    private val ratingRequested = ConcurrentHashMap.newKeySet<String>()
    private val ratingLimiter = Semaphore(MAX_CONCURRENT_RATING_FETCHES)

    /** The same guard again, for the channel logo index. */
    private val logoRequested = ConcurrentHashMap.newKeySet<String>()

    private val ratings = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val posters = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        // Reads the stored key once, so the first posters on screen already know whether
        // there is anything to ask.
        viewModelScope.launch { metadataRepository.load() }
    }

    private val selectedCategory = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    val currentQuery: StateFlow<String> = query.asStateFlow()

    /** M3 browses the first configured source. Multi-source selection is post-v1. */
    /**
     * Which source is active, wrapped so "not asked yet" is not indistinguishable from
     * "there are none". A nullable id cannot express the difference, and that is exactly
     * the distinction the empty state depends on.
     */
    private sealed interface ActiveSource {
        data object Unknown : ActiveSource
        data class Resolved(val id: Long?) : ActiveSource
    }

    private val activeSourceId: StateFlow<ActiveSource> = sourceRepository.observeSources()
        .map { sources -> ActiveSource.Resolved(sources.firstOrNull()?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ActiveSource.Unknown)

    val uiState: StateFlow<BrowseUiState> = activeSourceId
        .flatMapLatest { active ->
            val sourceId = (active as? ActiveSource.Resolved)?.id
            when {
                active is ActiveSource.Unknown -> flowOf(BrowseUiState(isLoading = true))
                sourceId == null -> flowOf(BrowseUiState(isLoading = false, hasSource = false))
                // Room answers quickly but not instantly, and a 20k list is not instant to
                // map. Show the spinner until the first page of rows actually exists.
                else -> feedFor(sourceId).onStart {
                    emit(BrowseUiState(isLoading = true, hasSource = true))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BrowseUiState())

    private fun feedFor(sourceId: Long) = combine(
        categoryRepository.observeCategories(sourceId, feed.kind),
        selectedCategory,
        // Debounced so a fast typist does not issue a query per keystroke. Short enough
        // to stay well inside the 200ms budget in AC-FAV-05.
        query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS },
    ) { categories, selected, searchText ->
        Triple(categories, selected, searchText)
    }.flatMapLatest { (categories, selected, searchText) ->
        val items = if (feed.favoritesOnly) {
            channelRepository.observeFavorites(sourceId, searchText)
        } else {
            channelRepository.observeBrowse(sourceId, feed.kind, selected, searchText)
        }
        combine(
            items,
            guideRepository.observeNowPlaying(sourceId),
            ratings,
            posters,
            historyFor(sourceId),
        ) { list, guide, scores, art, history ->
            BrowseUiState(
                isLoading = false,
                hasSource = true,
                categories = if (feed.favoritesOnly) emptyList() else categories,
                selectedCategory = selected,
                items = list,
                query = searchText,
                nowPlaying = guide,
                ratings = scores,
                posters = art,
                history = history,
            )
        }
    }

    /**
     * The "continue watching" feed for this screen.
     *
     * Nothing for Live, which records no position, and nothing for Favourites, which is a
     * list the user curated rather than one derived from what they watched — putting
     * history there would mix two different meanings of "things I care about" into one
     * screen.
     *
     * Artwork is requested for entries the provider gave none for, which is the same
     * request the poster grid makes and therefore usually already answered from the cache.
     */
    private fun historyFor(sourceId: Long): Flow<List<HistoryEntry>> =
        if (feed.favoritesOnly || feed.kind == MediaKind.LIVE) {
            flowOf(emptyList())
        } else {
            historyRepository.observeHistory(sourceId, feed.kind)
                .onEach { entries ->
                    entries.filter { it.artworkUrl.isNullOrBlank() }
                        .forEach { requestPreview(it.titleKey, it.title, it.kind) }
                }
        }

    /**
     * The playable row behind a history entry, or null once the provider has dropped it.
     *
     * History is keyed by provider identity so it survives a refresh; a detail screen is
     * reached by row id, which does not. This is the join between the two, and the null is
     * the honest answer for a film the provider has since removed.
     */
    suspend fun channelForHistory(entry: HistoryEntry): Channel? =
        channelRepository.findByStableKey(entry.sourceId, entry.titleKey)

    /**
     * Called as a row scrolls into view.
     *
     * Guide data is fetched for what the user can actually see rather than for the whole
     * account, because a 20,000-channel account would otherwise mean 20,000 requests.
     *
     * Only live channels have a guide. Series carry a `providerStreamId` too — it is the
     * series id, not a stream id — so without the [MediaKind] check, scrolling the Series
     * tab fires a `get_short_epg` per row that can only ever come back empty. Panels
     * count those against their anti-flood rules and start refusing the account, which
     * takes series details and refresh down with it.
     */
    fun onRowVisible(channel: Channel) {
        if (channel.kind != MediaKind.LIVE) return
        requestChannelLogo(channel)
        if (channel.providerStreamId == null) return
        if (!guideRequested.add(channel.stableKey)) return

        viewModelScope.launch {
            guideLimiter.withPermit {
                if (!guideRepository.hasGuideFor(channel.sourceId, channel.stableKey)) {
                    guideRepository.refreshGuideFor(channel)
                }
            }
        }
    }

    /**
     * Called as a poster comes into view, and only from the poster grid.
     *
     * Separate from [onRowVisible] rather than folded into it, because the two prefetch
     * different things for different reasons: a list row displays a guide entry and a
     * poster displays a score, and a list row showing a film has no use for either. The
     * kind check keeps live channels out — a television channel is not a title, and asking
     * a film database about one returns whatever film shares its name.
     */
    fun onPosterVisible(channel: Channel) {
        // A live channel gets its logo looked up and nothing else. No guide: a card shows
        // no programme, and a grid holds several times more items than a list, so asking
        // the panel per tile is a burst of requests whose answers nothing renders — the
        // behaviour that got this project's account blocked twice.
        if (channel.kind == MediaKind.LIVE) {
            requestChannelLogo(channel)
            return
        }
        requestPreview(channel.stableKey, channel.name, channel.kind)
    }

    /**
     * Looks up a missing logo in the channel reference list.
     *
     * Only for a channel the playlist gave no artwork for, and only when the user has
     * turned the feature on — the repository enforces the second part, and the first is
     * enforced here so a playlist with complete artwork never reaches it at all.
     *
     * Costs no network request per channel: the whole index is one file, downloaded once
     * and then queried locally. Guarded by the same set as the metadata lookups because
     * scrolling re-composes the same rows constantly.
     */
    private fun requestChannelLogo(channel: Channel) {
        if (!channel.logoUrl.isNullOrBlank()) return
        if (!logoRequested.add(channel.stableKey)) return

        viewModelScope.launch {
            val logo = channelLogoRepository.logoFor(channel)
            if (logo == null) {
                // Nothing was found, or nothing was asked because the feature is off.
                // Forgetting the row is what lets logos fill in when the switch is turned
                // on mid-session, rather than staying blank until the app restarts.
                logoRequested.remove(channel.stableKey)
                return@launch
            }
            posters.value = posters.value + (channel.stableKey to logo)
        }
    }

    /**
     * Fetches a film's or series' score and poster.
     *
     * Per visible tile rather than per category, deliberately. A category can hold
     * thousands of films, and asking about all of them the moment it is opened would spend
     * the user's whole rate limit on titles they scrolled past — the same mistake as
     * requesting a guide for 20,000 channels, made against a service that answers with a
     * hard limit rather than a slow one. There is no batch endpoint that would make the
     * whole-category version cheap; TMDB has no way to ask about many titles at once.
     *
     * Costs one request per title once, then nothing: the answer, including "no match",
     * is cached in the database across launches.
     *
     * The poster comes back in the same response as the score, so filling the very common
     * gap where a panel lists a series with no cover at all costs nothing extra. It is
     * offered rather than applied: the tile still prefers the provider's own artwork and
     * only reaches for this when there is none.
     *
     * @param key what the answer is filed under — a channel's identity, or a history
     *   entry's title key, which for a film are the same string.
     */
    private fun requestPreview(key: String, title: String, kind: MediaKind) {
        if (!ratingRequested.add(key)) return

        viewModelScope.launch {
            ratingLimiter.withPermit {
                if (!metadataRepository.isEnabled) {
                    // Nothing was asked, so nothing has been answered. Forgetting the row
                    // again is what lets tiles fill in when a user adds a key mid-session,
                    // instead of staying blank until the app is restarted.
                    ratingRequested.remove(key)
                    return@withPermit
                }
                val preview = metadataRepository.previewFor(title, kind) ?: return@withPermit
                preview.rating?.let { ratings.value = ratings.value + (key to it) }
                preview.posterUrl?.takeIf { it.isNotBlank() }?.let {
                    posters.value = posters.value + (key to it)
                }
            }
        }
    }

    /** Now and next for one channel, for the detail sheet (AC-EPG-02). */
    fun nowNextFor(channel: Channel) = guideRepository.observeNowNext(channel.sourceId, channel.stableKey)

    fun selectCategory(groupTitle: String?) {
        selectedCategory.value = groupTitle
    }

    fun search(text: String) {
        query.value = text
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { channelRepository.toggleFavorite(channel) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 120L
        const val MAX_CONCURRENT_GUIDE_FETCHES = 3

        /**
         * Four at a time against the metadata service.
         *
         * Higher than the guide limit because TMDB is a public API sized for this, where
         * the panel on the other end of a guide request is one user's subscription and the
         * thing this project keeps getting blocked by.
         */
        const val MAX_CONCURRENT_RATING_FETCHES = 4
    }
}
