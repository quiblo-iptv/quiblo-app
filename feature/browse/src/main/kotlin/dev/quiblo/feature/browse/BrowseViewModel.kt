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
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.quiblo.core.data.CategoryRepository
import dev.quiblo.core.data.ChannelLogoRepository
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.FeedRowCacheRepository
import dev.quiblo.core.data.GuideOutcome
import dev.quiblo.core.data.GuideRepository
import dev.quiblo.core.data.PopularEntry
import dev.quiblo.core.data.PopularTitlesRepository
import dev.quiblo.core.data.RecommendationRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.Suggestion
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.FeedRowEntry
import dev.quiblo.core.model.FeedRowId
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    /**
     * Rows beside the main one, for the feed that has more than one.
     *
     * Only [BrowseScope.FOR_YOU] fills it. A list of lists rather than two named fields because
     * the screen draws them in order and a row that has nothing in it is simply absent — which is
     * the whole of how "no key" and "no history" are rendered.
     */
    val extraRows: List<FeedRow> = emptyList(),
)

/**
 * One extra row, and enough about each tile to draw it.
 *
 * [FeedRowItem.rank] is TMDB's position within its own kind and [FeedRowItem.caption] is the
 * title that caused a suggestion. Both are nullable and both are usually null: a poster row is
 * the ordinary case and these two are the exceptions that earn their extra field.
 */
data class FeedRow(val id: FeedRowId, val items: List<FeedRowItem>)

/**
 * A tile on a For You row, which may or may not have a catalogue row behind it.
 *
 * A sealed pair rather than a nullable [Channel], because the difference is the whole of what the
 * screen does with it: one can be opened and the other can only explain itself. A nullable field
 * would let a caller forget, and forgetting here means handing the player a title with no stream.
 */
sealed interface FeedRowItem {

    /** Where this title sits in the list it came from. Only the popular rows have one. */
    val rank: Int?

    /** A line under the title, saying why the tile is here. Only the suggestions row has one. */
    val caption: String?

    /** A title this provider carries. */
    data class Playable(
        val channel: Channel,
        override val rank: Int? = null,
        override val caption: String? = null,
    ) : FeedRowItem

    /**
     * A title this provider does not carry.
     *
     * It keeps its place in the ranking and is drawn from what the metadata service said, because
     * a top ten with its unavailable places quietly removed is a top ten a viewer cannot read.
     * There is no channel and no stream behind it, and the screen is what says so when it is
     * opened.
     */
    data class Unavailable(
        val key: String,
        val title: String,
        val kind: MediaKind,
        val posterUrl: String?,
        override val rank: Int?,
    ) : FeedRowItem {
        override val caption: String? get() = null
    }
}

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
    /**
     * The provider's catalogue for one [MediaKind], as a flat list.
     *
     * Paged. The list is the whole of a kind — tens of thousands of rows on a real account — and
     * every screen using this scope draws it as one flat grid or one flat list, which is the
     * shape Paging is for. [BrowseUiState.items] is empty here on purpose; the rows arrive
     * through [BrowseViewModel.pagedItems].
     */
    CATALOGUE,

    /**
     * The same catalogue, but only the first tiles of each category.
     *
     * The television's poster grid, and the one browse screen Paging is wrong for: it groups its
     * whole answer into a row per category, and a paged list has no whole to group. Capping each
     * category in SQL gives it an answer the size of what it draws instead of the size of the
     * catalogue — which is what it was reading before, thirty thousand rows at a time, to show
     * about forty per row.
     */
    CATEGORY_ROWS,

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

    /**
     * The television's For You tab: three rows about three different questions.
     *
     * What the provider added lately, what the world is watching of the things this provider
     * carries, and what this viewer is likely to want. The first is [RECENTLY_ADDED]'s own feed
     * and arrives in `items`; the other two are [BrowseUiState.extraRows], because they are
     * lists of a different shape — one carries a rank and the other carries a reason.
     */
    FOR_YOU,
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
     *
     * Fifteen since `023`, down from forty. Forty was chosen as "more than anyone will walk",
     * which is an argument for the number being harmless rather than for it being right — and it
     * is not harmless on the row above two others, where the answer to "what is new" wants to be
     * short enough to be an answer.
     */
    val recentLimit: Int get() = RECENT_LIMIT

    private companion object {
        const val RECENT_LIMIT = 15
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
    private val popularTitles: PopularTitlesRepository,
    private val recommendations: RecommendationRepository,
    private val feedRowCache: FeedRowCacheRepository,
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
        // there is anything to ask. [requestPreview] samples `isEnabled` synchronously and has
        // no way to wait; [extraRowsFor] does wait, and calls this itself rather than trusting
        // this launch to have finished — the two are not the same guarantee.
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

    /**
     * The catalogue, a page at a time, for the screens that draw it flat.
     *
     * **Separate from [uiState] rather than a field inside it, and that is Paging's own shape
     * rather than a preference.** `PagingData` is a stream of loads, not a value: putting one in
     * a `StateFlow` of screen state would mean every unrelated change to that state — a rating
     * arriving, a poster resolving, a category chip moving — handing the grid a "new" pager and
     * throwing away its scroll position along with every page already loaded.
     *
     * `cachedIn` is what makes the stream survive a configuration change, and it belongs to the
     * ViewModel because that is the only thing here that outlives one.
     *
     * Empty for every scope but [BrowseScope.CATALOGUE]. Favourites is a list somebody built by
     * hand, Recently Added is already capped at forty, and the television's poster grid groups
     * its whole answer — none of the three is a list long enough to page, and two of them cannot
     * be paged at all.
     */
    val pagedItems: Flow<PagingData<Channel>> =
        if (feed.scope != BrowseScope.CATALOGUE) {
            flowOf(PagingData.empty())
        } else {
            combine(
                activeSourceId,
                selectedCategory,
                query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS },
            ) { active, selected, searchText -> Triple(active, selected, searchText) }
                .flatMapLatest { (active, selected, searchText) ->
                    val sourceId = (active as? ActiveSource.Resolved)?.id
                        ?: return@flatMapLatest flowOf(PagingData.empty())
                    channelRepository.pagedBrowse(
                        sourceId = sourceId,
                        kind = feed.kind,
                        groupTitle = selected,
                        query = searchText,
                    )
                }
                .cachedIn(viewModelScope)
        }

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

            // The first of the three For You rows is the newest-first feed exactly as the tab
            // used to be. The other two are fetched once and held in `extraRows`.
            BrowseScope.FOR_YOU ->
                channelRepository.observeRecentlyAdded(
                    sourceId = sourceId,
                    limit = feed.recentLimit,
                    sinceEpochMillis = now() - RECENT_WINDOW_MILLIS,
                ).map { BrowseRows(it.items, orderedByDate = it.orderedByDate) }

            BrowseScope.CATEGORY_ROWS ->
                channelRepository.observeCategoryRows(sourceId, feed.kind, searchText).map(::BrowseRows)

            // Paged, and therefore not here. `pagedItems` carries the rows; this scope's `items`
            // stays empty rather than holding a second unpaged copy of the same query, which
            // would be the whole cost this scope exists to have removed.
            BrowseScope.CATALOGUE -> flowOf(BrowseRows(emptyList()))
        }
        combine(
            rows,
            guideFor(sourceId),
            ratings,
            posters,
            combine(historyFor(sourceId), extraRowsFor(sourceId)) { entries, extras -> entries to extras },
        ) { list, guide, scores, art, (history, extras) ->
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
                extraRows = extras,
            )
        }
    }

    /**
     * The two rows that are not a query.
     *
     * Not subscribed to the catalogue. Neither row is a live view of anything: the popular list
     * changes weekly, and the suggestions are arithmetic over a history that only moves when
     * something is watched — which is not while this screen is open. A `Flow` that re-ran on
     * every write to the database would recompute a sixty-thousand-title match for nothing.
     *
     * **Keyed on the metadata key, and nothing is built until that key is known.** This is the
     * defect the shape exists for. The popular row cannot be fetched without the key, the key
     * lives in an encrypted store, and reading it is a `MasterKey` build plus a keystore round
     * trip — so a build that merely *sampled* `apiKey.value` lost a race it was never told it
     * was in, read null, skipped the fetch, and left the row empty for the life of the screen
     * with nothing to ask again. Awaiting [TitleMetadataRepository.load] removes the race;
     * keying on the value afterwards is what makes a key pasted into Settings mid-session fill
     * the row rather than wait for a relaunch.
     *
     * One build per distinct key, not two: waiting is what keeps the null pass — a full
     * suggestions match over the catalogue — from being paid for and thrown away.
     *
     * Nothing here fails loudly. A refusal, a missing key or an unscanned catalogue all produce
     * an empty list, and an empty row is not drawn at all.
     */
    private fun extraRowsFor(sourceId: Long): Flow<List<FeedRow>> =
        if (feed.scope != BrowseScope.FOR_YOU) {
            flowOf(emptyList())
        } else {
            flow {
                // Last night's rows, drawn straight away. Everything below this line is minutes
                // of arithmetic on a large account, and the whole reason the rows are kept is
                // that a viewer should not watch an empty shelf while it happens.
                emit(cachedExtraRows(sourceId))
                metadataRepository.load()
                // No `distinctUntilChanged`: `apiKey` is a StateFlow and already conflates.
                emitAll(metadataRepository.apiKey.map { buildExtraRows(sourceId) })
            }
        }

    /**
     * The rows as they were last worked out, with their titles resolved against the catalogue now.
     *
     * Titles are looked up by the provider's stable key rather than by row id, because a refresh
     * reassigns every id in the catalogue. Anything that no longer resolves is simply absent,
     * which on the popular rows means the tile is drawn as unavailable — because to this viewer,
     * now, it is.
     */
    private suspend fun cachedExtraRows(sourceId: Long): List<FeedRow> {
        val cached = feedRowCache.cached(sourceId)
        if (cached.isEmpty()) return emptyList()

        val wanted = cached.values.flatten().mapNotNull { it.stableKey }.distinct()
        val byKey = channelRepository.channelsByStableKeys(sourceId, wanted).associateBy { it.stableKey }

        return FeedRowId.entries.mapNotNull { rowId ->
            val items = cached[rowId].orEmpty().mapNotNull { it.toItem(byKey) }
            FeedRow(rowId, items).takeIf { items.isNotEmpty() }
        }
    }

    /**
     * One remembered entry as a tile.
     *
     * A ranking keeps its place whether or not the title resolves — that is the whole of what an
     * unavailable tile is. A suggestion does not: suggesting something that cannot be opened is
     * the app proposing a title it has no way to play.
     */
    private fun FeedRowEntry.toItem(byKey: Map<String, Channel>): FeedRowItem? {
        val channel = stableKey?.let { byKey[it] }
        return when {
            channel != null -> FeedRowItem.Playable(channel = channel, rank = rank, caption = becauseOf)
            rowId.isRanked -> FeedRowItem.Unavailable(
                key = "${rowId.name}-$rank",
                title = title,
                kind = kind,
                posterUrl = posterUrl,
                rank = rank,
            )
            else -> null
        }
    }

    private suspend fun buildExtraRows(sourceId: Long): List<FeedRow> {
        val popular = popularTitles.popular(sourceId)
        val suggestions = recommendations.suggestions(sourceId)

        // One read for all three rows. Resolving each list separately would be several queries
        // for one question, and the ids overlap more often than not.
        val wanted = (popular.mapNotNull { it.channelId } + suggestions.map { it.channelId }).distinct()
        val byId = if (wanted.isEmpty()) emptyMap() else channelRepository.channelsByIds(wanted).associateBy { it.id }

        // A title the provider carries but the script filter has removed comes back from
        // `channelsByIds` as nothing at all, and is drawn as unavailable — which it is, to this
        // viewer, for as long as they have hidden its writing system.
        val popularRows = popular.groupBy { it.kind }.mapValues { (_, entries) ->
            entries.map { entry ->
                val channel = entry.channelId?.let { byId[it] }
                if (channel != null) {
                    FeedRowItem.Playable(channel = channel, rank = entry.rank)
                } else {
                    FeedRowItem.Unavailable(
                        key = "${entry.kind.name}-${entry.rank}",
                        title = entry.title,
                        kind = entry.kind,
                        posterUrl = entry.posterUrl,
                        rank = entry.rank,
                    )
                }
            }
        }
        val suggestionRow = suggestions.mapNotNull { suggestion ->
            byId[suggestion.channelId]?.let { FeedRowItem.Playable(channel = it, caption = suggestion.becauseOf) }
        }

        // Artwork for anything the provider gave none for, exactly as the poster grid asks for
        // it. Without this a suggestion row on a catalogue with no covers is grey rectangles.
        // An unavailable tile is not asked about: it already carries the artwork the list it came
        // from supplied, and there is no catalogue row to look one up for.
        (popularRows.values.flatten() + suggestionRow)
            .filterIsInstance<FeedRowItem.Playable>()
            .forEach { requestPreview(it.channel.stableKey, it.channel.name, it.channel.kind) }

        rememberRows(sourceId, popular, suggestions, byId)

        return buildList {
            popularRows[MediaKind.VOD]?.takeIf { it.isNotEmpty() }
                ?.let { add(FeedRow(FeedRowId.POPULAR_MOVIES, it)) }
            popularRows[MediaKind.SERIES]?.takeIf { it.isNotEmpty() }
                ?.let { add(FeedRow(FeedRowId.POPULAR_SERIES, it)) }
            if (suggestionRow.isNotEmpty()) add(FeedRow(FeedRowId.YOU_MAY_LIKE, suggestionRow))
        }
    }

    /**
     * Writes down what was just worked out, so the next open draws it immediately.
     *
     * The two kinds of row are remembered differently on purpose. **A ranking is replaced**: it is
     * one answer arrived at all at once, and appending to last week's top ten would give a list of
     * twenty in which the number means two different things. **Suggestions are appended to**: the
     * same history scored twice a fortnight apart returns mostly the same titles in a different
     * order, and a shelf that reshuffles itself for no visible reason is one nobody learns the
     * shape of. What is there stays where it is; anything new goes on the end.
     *
     * Nothing here is awaited by the row above it. A cache that failed to write would cost the
     * next open the time this open already spent, which is not a reason to keep a viewer waiting.
     */
    private suspend fun rememberRows(
        sourceId: Long,
        popular: List<PopularEntry>,
        suggestions: List<Suggestion>,
        byId: Map<Long, Channel>,
    ) {
        FeedRowId.entries.filter { it.isRanked }.forEach { rowId ->
            val kind = if (rowId == FeedRowId.POPULAR_MOVIES) MediaKind.VOD else MediaKind.SERIES
            val entries = popular.filter { it.kind == kind }.mapIndexed { index, entry ->
                FeedRowEntry(
                    rowId = rowId,
                    position = index,
                    stableKey = entry.channelId?.let { byId[it]?.stableKey },
                    title = entry.title,
                    posterUrl = entry.posterUrl,
                    kind = entry.kind,
                    rank = entry.rank,
                )
            }
            if (entries.isNotEmpty()) feedRowCache.replace(sourceId, rowId, entries)
        }

        if (suggestions.isEmpty()) return
        feedRowCache.append(
            sourceId = sourceId,
            rowId = FeedRowId.YOU_MAY_LIKE,
            fresh = suggestions.mapIndexed { index, suggestion ->
                FeedRowEntry(
                    rowId = FeedRowId.YOU_MAY_LIKE,
                    position = index,
                    stableKey = byId[suggestion.channelId]?.stableKey,
                    // The provider's own name, which is what the tile draws. A suggestion whose
                    // channel did not resolve is dropped rather than remembered under a blank.
                    title = byId[suggestion.channelId]?.name.orEmpty(),
                    kind = suggestion.kind,
                    becauseOf = suggestion.becauseOf,
                )
            },
            watchedSince = recommendations.lastWatchedByTitle(sourceId),
            stillInCatalogue = byId.values.map { it.stableKey }.toSet(),
            limit = SUGGESTION_ROW_LIMIT,
        )
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
     * The provider's categories, for the two feeds that show them.
     *
     * The phone's catalogue draws them as a filter rail; the television's poster grid draws one
     * row per category and reads this for their *order*, which is the provider's and cannot be
     * recovered from the items. Favourites and Recently Added are single lists with no rail, and
     * both were subscribing to a query whose every answer they threw away at the state boundary.
     * The same reasoning as [guideFor] and [historyFor]: a feed subscribes to what it can
     * display, and to nothing else.
     */
    private fun categoriesFor(sourceId: Long): Flow<List<Category>> =
        if (feed.scope in CATALOGUE_SCOPES) {
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
        if (feed.scope !in CATALOGUE_SCOPES || feed.kind == MediaKind.LIVE) {
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
     *
     * **Every guide request goes through here, including the top-of-list prefetch.** That
     * prefetch — `017`'s fix for a television list that drew blank until somebody focused a row
     * — used to live in this class, which could see the whole list. The list is paged now, so
     * the screen is what knows which rows exist and `TvLiveScreen` calls this for the first ten
     * of them. Nothing about the guard moved: the kind check, the stream-id check, the
     * once-per-session dedupe and the concurrency limit are all still here, and there is still
     * no path around them.
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
        /**
         * The two scopes that are a provider's catalogue rather than a list about the viewer.
         *
         * Both show the provider's categories and both show what was left half-watched; they
         * differ only in the shape they read it in — a page of the whole kind, or the top of
         * each category. Naming the pair once is what stops the next scope-conditional feed
         * being added to one of them and quietly missing from the other, which is how the
         * television lost its category order the first time this split existed.
         */
        val CATALOGUE_SCOPES = setOf(BrowseScope.CATALOGUE, BrowseScope.CATEGORY_ROWS)

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
         * How long the remembered suggestions row may grow to.
         *
         * It is appended to rather than replaced, so it needs a ceiling or a household that opens
         * the app every evening for a year has a row a thousand tiles long. Twenty is what the
         * scorer returns in one pass, which makes the cap "about one pass' worth of the best of
         * them" rather than a number chosen for its own sake.
         */
        const val SUGGESTION_ROW_LIMIT = 20

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
