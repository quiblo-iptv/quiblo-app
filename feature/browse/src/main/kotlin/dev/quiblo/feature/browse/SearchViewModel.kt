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
import dev.quiblo.core.data.SearchRepository
import dev.quiblo.core.data.SearchResults
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** What the search screen renders. */
data class SearchUiState(
    val query: String = "",
    val selectedGenre: String? = null,
    /** Genres the cached metadata can actually filter by, alphabetically. */
    val genres: List<String> = emptyList(),
    /** How much of the catalogue has been described, for the hint under the filter. */
    val coveragePercent: Int = 0,
    /** Whether the genre index is still being built. See `GenreState.isLoading` (#018). */
    val areGenresLoading: Boolean = false,
    /** True when no metadata key is configured, which is why there are no genres. */
    val isMetadataDisabled: Boolean = false,
    val live: List<Channel> = emptyList(),
    val movies: List<Channel> = emptyList(),
    val series: List<Channel> = emptyList(),
    val isSearching: Boolean = false,
    val hasSource: Boolean = true,
    /** Scores and artwork for what is on screen, exactly as the browse grid fills them in. */
    val ratings: Map<String, Double> = emptyMap(),
    val posters: Map<String, String> = emptyMap(),
) {
    /** True once a question has been asked, which is what moves the bar off the middle. */
    val isActive: Boolean get() = query.isNotBlank() || selectedGenre != null

    val hasResults: Boolean get() = live.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()
}

/**
 * Search across everything a source carries.
 *
 * Deliberately one screen rather than a search box on each of Live, Movies and Series. A
 * viewer looking for a title does not know which of the three their provider filed it under —
 * panels routinely list the same film as a film and as a one-episode "series" — and a search
 * that answers for one kind is a search that appears to have found nothing.
 *
 * The genre filter is built from the metadata already cached for titles the viewer has
 * browsed past, so it costs no requests and works with the key switched off, minus the
 * genres. What it cannot do is describe a catalogue nobody has looked at yet, which is why
 * the coverage figure is on screen rather than in a comment.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    sourceRepository: SourceRepository,
    private val searchRepository: SearchRepository,
    private val metadataRepository: TitleMetadataRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedGenre = MutableStateFlow<String?>(null)
    private val genreIndex = MutableStateFlow(GenreState())
    private val ratings = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val posters = MutableStateFlow<Map<String, String>>(emptyMap())
    private val isSearching = MutableStateFlow(false)

    /** The same guards the browse grid uses: a re-composed row must not re-ask TMDB. */
    private val previewRequested = ConcurrentHashMap.newKeySet<String>()
    private val previewLimiter = Semaphore(MAX_CONCURRENT_PREVIEW_FETCHES)

    /** The source being searched. Multi-source selection is post-v1, as everywhere else. */
    private val activeSourceId: StateFlow<Long?> = sourceRepository.observeSources()
        .map { sources -> sources.firstOrNull()?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            metadataRepository.load()
            val sourceId = activeSourceId.first { it != null }
            if (sourceId == null) {
                // No source, so there is nothing to index and nothing to wait for.
                genreIndex.value = GenreState(isLoading = false)
                return@launch
            }
            val index = searchRepository.genreIndex(sourceId)
            genreIndex.value = GenreState(
                genres = index.genres,
                coveragePercent = index.coveragePercent,
                isDisabled = index.isMetadataDisabled,
                isLoading = false,
            )
        }
    }

    /**
     * The answer to whatever is currently being asked.
     *
     * Debounced rather than run per keystroke, and `mapLatest` so a term typed over a slower
     * one cancels it: on a television the on-screen keyboard produces characters in bursts,
     * and each burst would otherwise queue a query nobody is waiting for any more.
     */
    private val results: StateFlow<SearchResults> = combine(
        query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS },
        selectedGenre,
        activeSourceId,
    ) { text, genre, sourceId -> Triple(text, genre, sourceId) }
        .mapLatest { (text, genre, sourceId) ->
            if (sourceId == null || (text.isBlank() && genre == null)) {
                isSearching.value = false
                return@mapLatest SearchResults()
            }
            isSearching.value = true
            searchRepository.search(sourceId = sourceId, query = text, genre = genre)
                .also { isSearching.value = false }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchResults())

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        selectedGenre,
        genreIndex,
        results,
        combine(ratings, posters, isSearching, activeSourceId) { scores, art, searching, sourceId ->
            Artwork(scores, art, searching, sourceId != null)
        },
    ) { text, genre, index, found, extras ->
        SearchUiState(
            query = text,
            selectedGenre = genre,
            genres = index.genres,
            coveragePercent = index.coveragePercent,
            isMetadataDisabled = index.isDisabled,
            areGenresLoading = index.isLoading,
            live = found.live,
            movies = found.movies,
            series = found.series,
            isSearching = extras.isSearching,
            hasSource = extras.hasSource,
            ratings = extras.ratings,
            posters = extras.posters,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState())

    fun search(text: String) {
        query.value = text
    }

    /** Pressing the selected genre again clears it, so the filter needs no separate "off". */
    fun selectGenre(genre: String?) {
        selectedGenre.value = genre.takeIf { it != selectedGenre.value }
    }

    fun clear() {
        query.value = ""
        selectedGenre.value = null
    }

    /**
     * Called as a result tile comes into view.
     *
     * The same fetch the browse grid makes, and usually already answered from the cache —
     * a title reached by searching is very often one that has been scrolled past before.
     * Live channels are never looked up: a television channel is not a title, and asking a
     * film database about one returns whatever film shares its name.
     */
    fun onResultVisible(channel: Channel) {
        if (channel.kind == MediaKind.LIVE) return
        if (!previewRequested.add(channel.stableKey)) return

        viewModelScope.launch {
            previewLimiter.withPermit {
                if (!metadataRepository.isEnabled) {
                    previewRequested.remove(channel.stableKey)
                    return@withPermit
                }
                val preview = metadataRepository.previewFor(channel.name, channel.kind) ?: return@withPermit
                preview.rating?.let { ratings.value = ratings.value + (channel.stableKey to it) }
                preview.posterUrl?.takeIf { it.isNotBlank() }?.let {
                    posters.value = posters.value + (channel.stableKey to it)
                }
            }
        }
    }

    /** The genre filter's own state, read once and then unchanging for the session. */
    private data class GenreState(
        val genres: List<String> = emptyList(),
        val coveragePercent: Int = 0,
        val isDisabled: Boolean = false,
        /**
         * True until the index has been built once.
         *
         * The default, because it is built in `init` and the screen can be drawn before it
         * finishes. Without this the filters opened onto nothing at all for as long as the
         * read took — no chips, no coverage figure, and nothing saying why (#018). An
         * unexplained wait and a broken screen look identical.
         */
        val isLoading: Boolean = true,
    )

    /** Everything that is neither the question nor the answer, bundled to fit `combine`. */
    private data class Artwork(
        val ratings: Map<String, Double>,
        val posters: Map<String, String>,
        val isSearching: Boolean,
        val hasSource: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * How long a term rests before it is asked.
         *
         * Longer than the browse screen's 120ms, because this runs three queries rather than
         * one and because a remote's on-screen keyboard delivers characters slowly enough
         * that a shorter window only produces work for terms nobody finished typing.
         */
        const val SEARCH_DEBOUNCE_MILLIS = 250L

        const val MAX_CONCURRENT_PREVIEW_FETCHES = 4
    }
}
