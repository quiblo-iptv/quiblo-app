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
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.GuideRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Programme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
class BrowseViewModel(
    private val feed: BrowseFeed,
    sourceRepository: SourceRepository,
    private val channelRepository: ChannelRepository,
    private val categoryRepository: CategoryRepository,
    private val guideRepository: GuideRepository,
    private val metadataRepository: TitleMetadataRepository,
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

    private val ratings = MutableStateFlow<Map<String, Double>>(emptyMap())

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
        combine(items, guideRepository.observeNowPlaying(sourceId), ratings) { list, guide, scores ->
            BrowseUiState(
                isLoading = false,
                hasSource = true,
                categories = if (feed.favoritesOnly) emptyList() else categories,
                selectedCategory = selected,
                items = list,
                query = searchText,
                nowPlaying = guide,
                ratings = scores,
            )
        }
    }

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
        if (channel.kind == MediaKind.LIVE) return
        requestRating(channel)
    }

    /**
     * Fetches a film's or series' score.
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
     */
    private fun requestRating(channel: Channel) {
        if (!ratingRequested.add(channel.stableKey)) return

        viewModelScope.launch {
            ratingLimiter.withPermit {
                if (!metadataRepository.isEnabled) {
                    // Nothing was asked, so nothing has been answered. Forgetting the row
                    // again is what lets tiles fill in when a user adds a key mid-session,
                    // instead of staying blank until the app is restarted.
                    ratingRequested.remove(channel.stableKey)
                    return@withPermit
                }
                val rating = metadataRepository.ratingFor(channel.name, channel.kind)
                    ?: return@withPermit
                ratings.value = ratings.value + (channel.stableKey to rating)
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
