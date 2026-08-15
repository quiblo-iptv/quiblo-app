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
import dev.quiblo.core.data.GuideOutcome
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    /**
     * Whether [items] are in the order the provider dated them, or in the order it listed them.
     *
     * Only [BrowseScope.RECENTLY_ADDED] ever sets this false, and it is not decoration: a row
     * built from the end of a playlist is "the latest entries in your playlist" and titling it
     * "Recently Added" would be a claim about dates that nobody made.
     */
    val orderedByDate: Boolean = true,
    val query: String = "",
    /** What is airing now, keyed by channel identity. Empty for sources with no guide. */
    val nowPlaying: Map<String, Programme> = emptyMap(),
    /**
     * How the last guide request went, or null when none has been made yet.
     *
     * Here so a live list can say *why* it is showing no programmes. [nowPlaying] being empty
     * is the same picture whether the panel is refusing this app, has no listings for these
     * channels, or has simply not been asked yet — and only one of those is worth a viewer
     * telephoning their provider about.
     */
    val guideOutcome: GuideOutcome? = null,
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
 * "Have we already asked about this one?", for a catalogue too big to remember all of.
 *
 * The four prefetch guards were unbounded sets that only ever grew: scroll a 67,000-item
 * catalogue and every one of those stable keys is retained for the life of the ViewModel, to
 * answer a question about rows that left the screen an hour ago.
 *
 * A bounded, insertion-ordered set instead. Forgetting the oldest entry costs at worst one
 * repeated request for a row scrolled back to after thousands of others — and every one of these
 * call paths is already idempotent and cached a layer below, which is why forgetting is safe here
 * and would not be somewhere else. The capacity is several screenfuls in every direction.
 *
 * Synchronised rather than concurrent: `LinkedHashMap` in access order has no lock-free
 * equivalent, and the critical section is a single map operation on the main thread.
 */
internal class RecentKeys(private val capacity: Int = DEFAULT_CAPACITY) {

    private val seen = object : LinkedHashMap<String, Unit>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>): Boolean =
            size > capacity
    }

    /** @return true when [key] had not been seen, which is the caller's cue to go and ask. */
    fun add(key: String): Boolean = synchronized(seen) { seen.put(key, Unit) == null }

    /** Forgets [key], so the next sighting asks again. */
    fun remove(key: String) {
        synchronized(seen) { seen.remove(key) }
    }

    private companion object {
        /**
         * Several screenfuls in each direction on the largest layout, which is a television grid.
         */
        const val DEFAULT_CAPACITY = 512
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
    }
}

/**
 * What a browse screen is a list *of*, as opposed to what kind of thing is in it.
 *
 * **This was a boolean until Recently Added arrived**, and a second boolean beside it would
 * have made "favourites and recently added at once" a state the type allowed and every reader
 * downstream had to be trusted never to produce. An enum cannot express it, so nothing has to
 * check. `TvBarSpot` in the television app is the same decision for the same reason.
 */
enum class BrowseScope {
    /** The provider's catalogue for one [MediaKind], grouped by its categories. */
    CATALOGUE,

    /** Titles this viewer marked, across every kind. Ignores the script filter, by design. */
    FAVOURITES,

    /**
     * The newest films and series on the service, merged and newest first.
     *
     * The one scope [BrowseFeed.kind] says nothing about: it spans films and series together,
     * which is what "what is new" means to somebody looking at a service rather than at a
     * catalogue.
     */
    RECENTLY_ADDED,
}

/**
 * Which feed a browse screen is showing.
 *
 * The two travel together everywhere — a screen is Movies, or it is Favourites — so they
 * are one argument rather than two that can be passed in the wrong combination.
 */
data class BrowseFeed(
    val kind: MediaKind,
    val scope: BrowseScope = BrowseScope.CATALOGUE,
) {
    /**
     * How many titles [BrowseScope.RECENTLY_ADDED] keeps.
     *
     * A cap rather than a page: the row is walked with a D-pad, and nobody presses right forty
     * times. Reading more would cost a query nobody scrolls to the end of.
     */
    val recentLimit: Int get() = RECENT_LIMIT

    private companion object {
        const val RECENT_LIMIT = 40
    }
}

/**
 * Drives Live, Movies, Series, Favourites and Recently Added.
 *
 * One implementation rather than five, parameterised by [feed]. The screens differ in what
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
    /** Injected like every repository's, rather than read off the wall clock inline. */
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /**
     * Channels whose guide has already been requested this session.
     *
     * Rows are re-composed constantly while scrolling; without this the same channel
     * would be fetched dozens of times.
     */
    private val guideRequested = RecentKeys()

    /** Caps concurrent guide requests so scrolling cannot stampede a panel. */
    private val guideLimiter = Semaphore(MAX_CONCURRENT_GUIDE_FETCHES)

    /**
     * Channels whose *full* listing has been asked for this session.
     *
     * Kept apart from [guideRequested]: a channel whose now/next is already cached still has
     * a day of listings nobody has fetched, so one set cannot answer both questions.
     */
    private val fullGuideRequested = RecentKeys()

    /** The same two guards for the metadata service, which rate-limits per key. */
    private val ratingRequested = RecentKeys()
    private val ratingLimiter = Semaphore(MAX_CONCURRENT_RATING_FETCHES)

    /** The same guard again, for the channel logo index. */
    private val logoRequested = RecentKeys()

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
        categoriesFor(sourceId),
        selectedCategory,
        // Debounced so a fast typist does not issue a query per keystroke. Short enough
        // to stay well inside the 200ms budget in AC-FAV-05.
        query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS },
    ) { categories, selected, searchText ->
        Triple(categories, selected, searchText)
    }.flatMapLatest { (categories, selected, searchText) ->
        val rows = when (feed.scope) {
            BrowseScope.FAVOURITES ->
                channelRepository.observeFavorites(sourceId, searchText).map(::BrowseRows)
            // No search term: this feed has no field to type one into, and applying the one
            // left over from another screen would silently filter a list the viewer never
            // asked to filter.
            BrowseScope.RECENTLY_ADDED ->
                channelRepository.observeRecentlyAdded(
                    sourceId = sourceId,
                    limit = feed.recentLimit,
                    sinceEpochMillis = now() - RECENT_WINDOW_MILLIS,
                ).map { BrowseRows(it.items, orderedByDate = it.orderedByDate) }

            BrowseScope.CATALOGUE ->
                channelRepository.observeBrowse(sourceId, feed.kind, selected, searchText).map(::BrowseRows)
        }
        // A live list asks for the top of itself the moment it exists, rather than waiting for
        // the remote to come to rest on a row that may never be reached. See prefetchGuides.
        val items = if (feed.kind == MediaKind.LIVE) rows.onEach { prefetchGuides(it.items) } else rows
        combine(
            items,
            guideFor(sourceId),
            ratings,
            posters,
            historyFor(sourceId),
        ) { list, guide, scores, art, history ->
            BrowseUiState(
                isLoading = false,
                hasSource = true,
                categories = categories,
                selectedCategory = selected,
                items = list.items,
                orderedByDate = list.orderedByDate,
                query = searchText,
                nowPlaying = guide.nowPlaying,
                guideOutcome = guide.outcome,
                ratings = scores,
                posters = art,
                history = history,
            )
        }
    }

    /**
     * One feed's rows, and whether their order is the provider's dates or its list.
     *
     * Only Recently Added can answer the second with "no", but it travels with the rows rather
     * than beside them: the two are read in the same breath at the state boundary, and a
     * separate flow for one boolean would be a second thing to keep in step with the first.
     */
    private data class BrowseRows(val items: List<Channel>, val orderedByDate: Boolean = true)

    /**
     * The provider's categories, for the one feed that groups by them.
     *
     * Favourites and Recently Added are single lists with no rail, and both were subscribing
     * to a query whose every answer they threw away at the state boundary. The same reasoning
     * as [guideFor] and [historyFor]: a feed subscribes to what it can display, and to nothing
     * else.
     */
    private fun categoriesFor(sourceId: Long): Flow<List<Category>> =
        if (feed.scope == BrowseScope.CATALOGUE) {
            categoryRepository.observeCategories(sourceId, feed.kind)
        } else {
            flowOf(emptyList())
        }

    /**
     * What is on now, for the feeds that can actually show it.
     *
     * A film has no programme and a poster has nowhere to put one, so Movies and Series
     * were combining in a query whose every answer they discarded — and it is not a cheap
     * query: it asks for every programme airing now across the whole source.
     *
     * Keyed on [BrowseFeed.kind] alone rather than also excluding favourites, because the
     * favourites feed is built with [MediaKind.LIVE] and *does* render a programme line
     * for the live channels in it. Excluding it would have been a silent regression on a
     * screen that displays this today.
     *
     * The same reasoning as [historyFor] immediately below, in the other direction: each
     * feed subscribes to what it can display and to nothing else.
     */
    private fun guideFor(sourceId: Long): Flow<GuideFeed> =
        if (feed.kind == MediaKind.LIVE) {
            combine(guideRepository.observeNowPlaying(sourceId), guideOutcome, ::GuideFeed)
        } else {
            flowOf(GuideFeed())
        }

    /**
     * The programmes and the reason there are none, together.
     *
     * One value rather than two more arguments to the `combine` below. They are also one fact:
     * a screen reading "nothing is airing" needs the second half to know what to say about it.
     */
    private data class GuideFeed(
        val nowPlaying: Map<String, Programme> = emptyMap(),
        val outcome: GuideOutcome? = null,
    )

    /**
     * How the guide requests have gone so far on this feed.
     *
     * Kept as a running summary rather than per channel. Per channel would be more precise and
     * would say nothing more: the two answers worth putting on a screen are "your provider is
     * refusing us" and "your provider has no listings for these channels", and both are facts
     * about the account rather than about one row.
     */
    private val guideOutcome = MutableStateFlow<GuideOutcome?>(null)

    /**
     * Folds one channel's answer into that summary.
     *
     * The precedence is the point. One channel with listings proves the guide works, so
     * [GuideOutcome.STORED] outranks everything and is never overwritten — otherwise a working
     * guide would report itself broken the moment it met one channel the panel knows nothing
     * about, which on a large account is most of them. Below that, a refusal outranks silence,
     * because it is the one a viewer can do something about.
     */
    private fun recordGuideOutcome(outcome: GuideOutcome) {
        guideOutcome.update { current ->
            when {
                current == GuideOutcome.STORED || outcome == GuideOutcome.STORED -> GuideOutcome.STORED
                current == GuideOutcome.BLOCKED || outcome == GuideOutcome.BLOCKED -> GuideOutcome.BLOCKED
                else -> outcome
            }
        }
    }

    /**
     * Asks for the guide of the first handful of channels as soon as a live list exists.
     *
     * **This is the defect this branch is named for.** The television fetches a guide only for
     * the row focus has rested on for 450ms, and nothing has focus when the screen opens — so
     * the whole list drew with no programme on any row, indefinitely, for anybody who did not
     * happen to stop on one. The phone never had this: it fetches for the rows on screen.
     *
     * Bounded and deduplicated rather than unbounded, because the restraint it replaces was
     * right about the danger. [onRowVisible] carries the kind check, the stream-id check, the
     * once-per-session dedupe and the concurrency limit; this adds a fixed ten calls into all
     * of that and no new path around any of it. Re-emissions of the same list — a write to the
     * table, a category change, a keystroke — cost nothing, because the dedupe has already seen
     * these keys.
     */
    private fun prefetchGuides(channels: List<Channel>) {
        channels.take(GUIDE_PREFETCH_ROWS).forEach(::onRowVisible)
    }

    /**
     * The "continue watching" feed for this screen.
     *
     * Nothing for Live, which records no position, and nothing for Favourites, which is a
     * list the user curated rather than one derived from what they watched — putting
     * history there would mix two different meanings of "things I care about" into one
     * screen. Nothing for Recently Added either, for the third meaning: it answers what the
     * provider has just put on, and what the viewer was halfway through is the opposite of
     * new.
     *
     * Artwork is requested for entries the provider gave none for, which is the same
     * request the poster grid makes and therefore usually already answered from the cache.
     */
    private fun historyFor(sourceId: Long): Flow<List<HistoryEntry>> =
        if (feed.scope != BrowseScope.CATALOGUE || feed.kind == MediaKind.LIVE) {
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
                val outcome = if (guideRepository.hasGuideFor(channel.sourceId, channel.stableKey)) {
                    // Already cached from an earlier session, and it counts: the screen is
                    // about to draw a programme line, so the guide plainly works here.
                    GuideOutcome.STORED
                } else {
                    guideRepository.refreshGuideFor(channel)
                }
                recordGuideOutcome(outcome)
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
            posters.update { it + (channel.stableKey to logo) }
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
                // update {} rather than `value = value + x`, which is a read-modify-write with
                // four of these in flight. It is safe today only because viewModelScope resumes
                // on Main.immediate — not a property this code should be depending on.
                preview.rating?.let { rating -> ratings.update { it + (key to rating) } }
                preview.posterUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    posters.update { it + (key to url) }
                }
            }
        }
    }

    /** Now and next for one channel, for the detail sheet (AC-EPG-02). */
    fun nowNextFor(channel: Channel) = guideRepository.observeNowNext(channel.sourceId, channel.stableKey)

    /**
     * Everything stored for one channel across the window a timeline draws (INC-F4).
     *
     * Read from storage, so the timeline opens on whatever was last cached and fills in when
     * [requestFullGuide] answers. With no connection it draws the cache and nothing waits.
     */
    fun scheduleFor(channel: Channel): Flow<List<Programme>> {
        val window = guideWindow(now())
        return guideRepository.observeSchedule(
            sourceId = channel.sourceId,
            channelKey = channel.stableKey,
            fromEpochMillis = window.first,
            toEpochMillis = window.last,
        )
    }

    /**
     * Asks the panel for the whole listing, on an explicit request from the viewer (INC-F4).
     *
     * Deliberately not folded into [onRowVisible]: this is the heavier `get_simple_data_table`
     * call, and a list makes that request per row it passes. Long-pressing one channel is a
     * viewer asking once, which is the only shape this call is safe in (AC-TV-05).
     *
     * Requested once per channel per session, on the same reasoning as the now/next prefetch —
     * a sheet reopened while scrolling should not ask the panel again for a listing it has.
     */
    fun requestFullGuide(channel: Channel) {
        if (channel.kind != MediaKind.LIVE) return
        if (channel.providerStreamId == null) return
        if (!fullGuideRequested.add(channel.stableKey)) return

        viewModelScope.launch { recordGuideOutcome(guideRepository.refreshFullGuideFor(channel)) }
    }

    fun selectCategory(groupTitle: String?) {
        selectedCategory.value = groupTitle
    }

    fun search(text: String) {
        query.value = text
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { channelRepository.toggleFavorite(channel) }
    }

    fun removeFromHistory(entry: HistoryEntry) {
        viewModelScope.launch {
            val seriesStableKey = entry.seriesStableKey
            if (seriesStableKey != null) {
                // A series tile represents the whole programme collapsed to the episode last watched.
                // Removing only that episode leaves the row showing the episode before it, which reads
                // to a viewer as the action failing. So we remove the whole series.
                historyRepository.removeSeriesFromHistory(seriesStableKey)
            } else {
                historyRepository.removeFromHistory(entry.stableKey)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 120L

        /**
         * How far back "recently added" reaches: thirty days.
         *
         * A cap on age rather than only on count. Without it a service that added forty films
         * last March fills the row with them forever, and a tab that answers "what is new"
         * with last spring is answering a question nobody asked.
         */
        const val RECENT_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val MAX_CONCURRENT_GUIDE_FETCHES = 3

        /**
         * How many rows of a fresh live list are asked about without being focused.
         *
         * About a screenful on the largest layout, which is what makes the list look answered
         * rather than empty. A fixed number and not a fraction of the account: the whole reason
         * the television fetched one row at a time is that "per visible row" against a
         * 20,000-channel account is how this project's account was blocked, twice.
         */
        const val GUIDE_PREFETCH_ROWS = 10

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
