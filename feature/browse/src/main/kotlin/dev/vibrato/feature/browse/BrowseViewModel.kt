/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vibrato.core.data.ChannelRepository
import dev.vibrato.core.data.GuideRepository
import dev.vibrato.core.data.SourceRepository
import dev.vibrato.core.model.Category
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.MediaKind
import dev.vibrato.core.model.Programme
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** What a browse screen renders. */
data class BrowseUiState(
    val hasSource: Boolean = false,
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val items: List<Channel> = emptyList(),
    val query: String = "",
    /** What is airing now, keyed by channel identity. Empty for sources with no guide. */
    val nowPlaying: Map<String, Programme> = emptyMap(),
)

/**
 * Drives Live, Movies, Series and Favourites.
 *
 * One implementation rather than four, parameterised by [kind] and [favoritesOnly]. The
 * screens differ in what they show, not in how they behave.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BrowseViewModel(
    private val kind: MediaKind,
    private val favoritesOnly: Boolean,
    sourceRepository: SourceRepository,
    private val channelRepository: ChannelRepository,
    private val guideRepository: GuideRepository,
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

    private val selectedCategory = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    val currentQuery: StateFlow<String> = query.asStateFlow()

    /** M3 browses the first configured source. Multi-source selection is post-v1. */
    private val activeSourceId: StateFlow<Long?> = sourceRepository.observeSources()
        .map { sources -> sources.firstOrNull()?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val uiState: StateFlow<BrowseUiState> = activeSourceId
        .flatMapLatest { sourceId ->
            if (sourceId == null) flowOf(BrowseUiState(hasSource = false)) else feedFor(sourceId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BrowseUiState())

    private fun feedFor(sourceId: Long) = combine(
        channelRepository.observeCategories(sourceId, kind),
        selectedCategory,
        // Debounced so a fast typist does not issue a query per keystroke. Short enough
        // to stay well inside the 200ms budget in AC-FAV-05.
        query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS },
    ) { categories, selected, searchText ->
        Triple(categories, selected, searchText)
    }.flatMapLatest { (categories, selected, searchText) ->
        val items = if (favoritesOnly) {
            channelRepository.observeFavorites(sourceId, searchText)
        } else {
            channelRepository.observeBrowse(sourceId, kind, selected, searchText)
        }
        combine(items, guideRepository.observeNowPlaying(sourceId)) { list, guide ->
            BrowseUiState(
                hasSource = true,
                categories = if (favoritesOnly) emptyList() else categories,
                selectedCategory = selected,
                items = list,
                query = searchText,
                nowPlaying = guide,
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
    }
}
