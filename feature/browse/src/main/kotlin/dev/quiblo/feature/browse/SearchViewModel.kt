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
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.SearchOptions
import dev.quiblo.core.data.SearchRepository
import dev.quiblo.core.data.SearchResults
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.suggestionKey
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
    /**
     * Titles to offer under the field as the term is typed.
     *
     * `INC-F1`. **Derived from the results already fetched, so autocomplete costs no query at
     * all** — `AC-TV-14` forbids a database read per keystroke, and the surest way to obey that
     * is not to add a read. The results flow is already debounced and already `mapLatest`, and
     * these come out of its answer rather than out of a second path beside it.
     */
    val suggestions: List<String> = emptyList(),
    /**
     * Whether this search is also looking in what the viewer has hidden.
     *
     * Off by default and reset with the screen. Hidden categories and hidden writing systems
     * are one switch here because they are one question from the viewer's side: "look in the
     * parts I usually do not want to see".
     */
    val includeHidden: Boolean = false,
    /**
     * Whether this search is also looking at live channels.
     *
     * `027` #5. Advanced search leaves television channels out — a viewer narrowing by genre is
     * looking for a title, and a channel has no genre to be narrowed by — and until now the only
     * way to change that was a setting two screens away, in Settings, that decided it for every
     * search ever made. It is a question about *this* search, so it is asked where the search is.
     *
     * The setting is still what it starts as. A viewer who has said "always look in live" in
     * Settings has said it; this switch is how they say otherwise for one question.
     */
    val includeLive: Boolean = false,
    /**
     * Whether the filters are open.
     *
     * The view model's rather than the screen's, because it changes the *query* and not only the
     * layout: advanced search leaves live channels out unless a setting says otherwise.
     */
    val isAdvanced: Boolean = false,
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
    playerSettingsRepository: PlayerSettingsRepository,
) : ViewModel() {

    /**
     * Whether advanced search offers live channels, from Settings.
     *
     * Read here rather than at the screen because it decides whether a query is *made*. A row
     * that is fetched and then not drawn is a request paid for and thrown away, and this project
     * has had its provider account blocked twice over requests it did not need.
     */
    private val showLiveInSearch: StateFlow<Boolean> = playerSettingsRepository.showLiveInSearch
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val query = MutableStateFlow("")
    private val selectedGenre = MutableStateFlow<String?>(null)
    private val includeHidden = MutableStateFlow(false)

    /**
     * What the viewer has said about live channels for *this* search, or null for "whatever the
     * rule says".
     *
     * Nullable rather than a boolean seeded from the setting, and the difference is real: the
     * setting arrives asynchronously and the rule also depends on whether the filters are open, so
     * a copied boolean would be a third answer that has to be kept in step with two others. Null
     * means nobody has overruled anything, which is the truth until they do.
     */
    private val includeLiveOverride = MutableStateFlow<Boolean?>(null)
    private val isAdvanced = MutableStateFlow(false)
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
     * Whether live channels are in this search at all.
     *
     * One flow read twice — by the query that decides whether to ask for them, and by the screen
     * that draws the switch — because the two must never disagree. A switch drawn from one rule
     * and a query made under another is a control that appears to do nothing.
     */
    private val includeLive: StateFlow<Boolean> =
        combine(isAdvanced, showLiveInSearch, includeLiveOverride) { advanced, showLive, chosen ->
            chosen ?: (!advanced || showLive)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
        includeHidden,
        activeSourceId,
        includeLive,
    ) { text, genre, hidden, sourceId, live -> Ask(text, genre, hidden, sourceId, live) }
        .mapLatest { ask ->
            if (ask.sourceId == null || (ask.text.isBlank() && ask.genre == null)) {
                isSearching.value = false
                return@mapLatest SearchResults()
            }
            isSearching.value = true
            searchRepository.search(
                sourceId = ask.sourceId,
                query = ask.text,
                options = SearchOptions(
                    genre = ask.genre,
                    includeHidden = ask.includeHidden,
                    includeLive = ask.includeLive,
                ),
            ).also { isSearching.value = false }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchResults())

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        selectedGenre,
        genreIndex,
        results,
        combine(
            ratings,
            posters,
            isSearching,
            activeSourceId,
            combine(includeHidden, isAdvanced, includeLive) { hidden, advanced, live ->
                Switches(hidden, advanced, live)
            },
        ) { scores, art, searching, sourceId, switches ->
            Extras(scores, art, searching, sourceId != null, switches)
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
            suggestions = found.suggestionsFor(text),
            includeHidden = extras.switches.includeHidden,
            includeLive = extras.switches.includeLive,
            isAdvanced = extras.switches.isAdvanced,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState())

    fun search(text: String) {
        query.value = text
    }

    /**
     * Narrows to one genre, and keeps it there.
     *
     * **Pressing the chosen genre again used to clear it, and that was the fault** (`027` #7). A
     * chip that does one thing on the first press and the opposite on the second is a control a
     * viewer cannot aim: on a remote they arrive at the chip they are already filtering by as they
     * walk the strip, and one stray press emptied the filter with nothing on screen saying so.
     * Choosing is choosing. Clear is what unchooses, it is the first thing in the strip, and it is
     * the only thing that means "no genre".
     *
     * Null is still accepted, because Clear goes through here rather than around it.
     */
    fun selectGenre(genre: String?) {
        selectedGenre.value = genre
    }

    /** Looks in hidden categories and hidden writing systems too, for this search only. */
    fun setIncludeHidden(include: Boolean) {
        includeHidden.value = include
    }

    /**
     * Looks at live channels too, or stops looking at them, for this search only.
     *
     * Never written back to Settings. A switch on a search screen answers the search it is on —
     * changing a stored preference from here would make one evening's question the app's standing
     * answer, which is the shape `FREEZE.md` §4.2 calls a setting that sets itself.
     */
    fun setIncludeLive(include: Boolean) {
        includeLiveOverride.value = include
    }

    fun setAdvanced(advanced: Boolean) {
        isAdvanced.value = advanced
    }

    fun clear() {
        query.value = ""
        selectedGenre.value = null
        includeHidden.value = false
        // Back to whatever the rule says rather than to false, because "off" is not what this
        // switch was before it was touched — see [includeLiveOverride].
        includeLiveOverride.value = null
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

    /** The whole question, bundled to fit `combine`. */
    private data class Ask(
        val text: String,
        val genre: String?,
        val includeHidden: Boolean,
        val sourceId: Long?,
        val includeLive: Boolean,
    )

    /** The three switches above the results, bundled to fit `combine`'s arity. */
    private data class Switches(
        val includeHidden: Boolean,
        val isAdvanced: Boolean,
        val includeLive: Boolean,
    )

    /** Everything that is neither the question nor the answer, bundled to fit `combine`. */
    private data class Extras(
        val ratings: Map<String, Double>,
        val posters: Map<String, String>,
        val isSearching: Boolean,
        val hasSource: Boolean,
        val switches: Switches,
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

/**
 * The distinct titles among these results, for the suggestion list.
 *
 * **One suggestion per title, not per row.** A provider listing one film in four qualities is
 * one thing a viewer is looking for, and four identical lines under the field would be the
 * feature actively getting in the way.
 *
 * Empty until there is something to complete: with a blank or one-character term the results are
 * either absent or so broad that a suggestion list is noise rather than a shortcut.
 *
 * The *provider's* own title is what is shown. The cleaned form is only how two rows are told to
 * be the same title — a viewer searching their catalogue should see the names their catalogue
 * uses.
 */
private fun SearchResults.suggestionsFor(term: String): List<String> {
    if (term.length < MIN_SUGGESTION_TERM) return emptyList()

    val seen = LinkedHashMap<String, String>()
    (movies + series + live).forEach { channel ->
        val key = channel.name.suggestionKey()
        if (key.isNotBlank()) seen.putIfAbsent(key, channel.name)
    }
    return seen.values.take(MAX_SUGGESTIONS)
}

/**
 * Below this the results are too broad for a suggestion to be a shortcut.
 *
 * Two rather than one: a single character matches most of a catalogue, and a list of six
 * arbitrary titles from it is worse than nothing because it looks like an answer.
 */
private const val MIN_SUGGESTION_TERM = 2

/** Six. Enough to be worth reading from three metres, few enough not to bury the results. */
private const val MAX_SUGGESTIONS = 6
