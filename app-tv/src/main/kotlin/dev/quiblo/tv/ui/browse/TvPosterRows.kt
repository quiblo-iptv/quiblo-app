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

package dev.quiblo.tv.ui.browse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.common.cleanedForDisplay
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.feature.browse.BrowseScope
import dev.quiblo.feature.browse.BrowseViewModel
import dev.quiblo.feature.browse.RatingBadge
import dev.quiblo.feature.browse.di.browseParams
import dev.quiblo.feature.browse.di.categoryRowsParams
import dev.quiblo.feature.browse.labelRes
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.AmbientRequest
import dev.quiblo.tv.ui.common.LocalAmbientSink
import dev.quiblo.tv.ui.common.LocalTvReturn
import dev.quiblo.tv.ui.common.insistOnFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Movies and Series: one horizontally scrolling row per category, stacked vertically.
 *
 * This is the Google TV shape and it replaces the phone's category *filter* outright. On a
 * phone you pick one category and get a grid; on a television every category is on screen
 * and the remote walks through them, so there is nothing to pick and no picker.
 */
@Composable
fun TvPosterRows(
    kind: MediaKind,
    /** Hands over the whole list, not just the item: the player needs it to zap. */
    onPlay: (List<Channel>, Int) -> Unit,
    onResume: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    favouritesOnly: Boolean = false,
) {
    /*
     * One grid or a shelf per category (`029` #3).
     *
     * **Two screens rather than one that draws both**, and the reason is the query behind them.
     * Shelves read the first tiles of every category — capped in SQL, because a row shows about
     * forty of what may be three thousand — and a single grid has no categories to cap by, so it
     * has to be paged instead. Those are different scopes, chosen when the ViewModel is built, and
     * a screen that switched between them would be a screen holding two.
     *
     * Favourites is never merged. It is a list somebody built by hand and it is grouped by kind
     * rather than by the provider's categories, so there is nothing there to collapse.
     */
    val settings: PlayerSettingsRepository = koinInject()
    val merged by settings.mergeCategories.collectAsStateWithLifecycle(false)

    if (merged && !favouritesOnly) {
        TvMergedGrid(kind = kind, onPlay = onPlay, modifier = modifier)
    } else {
        TvCategoryShelves(
            kind = kind,
            onPlay = onPlay,
            onResume = onResume,
            modifier = modifier,
            favouritesOnly = favouritesOnly,
        )
    }
}

/**
 * The catalogue as one grid, with the provider's shelves collapsed (`029` #3).
 *
 * Paged, unlike the shelves beside it, and that is not an implementation detail: this is the whole
 * of a kind — tens of thousands of rows on a real account — where a row of shelves only ever asks
 * for the first tiles of each. Reading the lot to draw a screenful is the cost `BrowseScope`
 * exists to have removed, and it is exactly what a grid would pay without paging.
 *
 * No continue-watching row and no headings. Both belong to a screen with shelves: the row sits
 * above them, and a heading over a grid of everything would say "everything".
 */
@Composable
private fun TvMergedGrid(
    kind: MediaKind,
    onPlay: (List<Channel>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BrowseViewModel = koinViewModel(
        // Its own key, because it is its own feed: the shelves' ViewModel is built with a scope
        // that caps per category, and sharing the key would hand this screen that answer.
        // not display text: a Koin scope key, never rendered.
        key = "tv-merged-${kind.name}",
        parameters = { browseParams(kind) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.pagedItems.collectAsLazyPagingItems()

    /*
     * The grid's own loading state, read from Paging rather than from the screen state (`030` #1).
     *
     * **`state.isLoading` is not this screen's answer, and that is the whole defect.** The state
     * flow carries the categories, the ratings and the history; for this scope it carries no items
     * at all, because the items are paged. So it went false the moment those arrived — a fraction
     * of a second — while the first page was still being read, and the branch below found an empty
     * list and said the catalogue was empty. Nothing was spinning and nothing was wrong; the
     * screen was simply lying about a query still in flight. Leaving the tab and coming back read
     * the pages Paging had cached by then, which is why moving between tabs "fixed" it.
     *
     * `loadState.refresh` is the fact being asked for: whether the first page has come back.
     */
    val refresh = items.loadState.refresh

    when {
        state.isLoading || refresh is LoadState.Loading ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

        // A read that failed is not an empty catalogue. It says so, and the D-pad can ask again —
        // the alternative is a viewer looking at "nothing here" over a catalogue of thirty
        // thousand films with no way to find out otherwise.
        refresh is LoadState.Error -> TvGridRetry(onRetry = items::retry, modifier = modifier)

        items.itemCount == 0 -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.tv_no_content),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        else -> LazyVerticalGrid(
            // Adaptive rather than a fixed count. A television panel is 960dp wide and a monitor
            // running this build is not, and a column count chosen for one crops the other.
            //
            // The tile is narrower here than on the shelves (`030` #3). A shelf is walked
            // sideways, so a big poster costs nothing but presses; a grid is the whole catalogue
            // at once and the question it answers is "what is in here" — which is a question about
            // how many titles fit on the screen. At 120dp a 960dp panel holds seven across where
            // the shelf width held five.
            columns = GridCells.Adaptive(minSize = GRID_POSTER_WIDTH + FOCUS_GROWTH_HORIZONTAL * 2),
            modifier = modifier.fillMaxSize(),
            // Room for a focused poster to grow into at the edges of the grid, on the same
            // reasoning as the rows: a `LazyVerticalGrid` clips to its own bounds.
            contentPadding = PaddingValues(vertical = FOCUS_GROWTH, horizontal = FOCUS_GROWTH_HORIZONTAL),
            horizontalArrangement = Arrangement.Center,
        ) {
            items(count = items.itemCount) { index ->
                val channel = items[index] ?: return@items
                LaunchedEffect(channel.id) { viewModel.onPosterVisible(channel) }
                TvPoster(
                    tile = TvTile(channel),
                    rating = state.ratings[channel.stableKey],
                    fallbackArtworkUrl = state.posters[channel.stableKey],
                    onClick = { onPlay(items.itemSnapshotList.items, index) },
                    // Matches the cell the grid measured. A tile wider than its column is a tile
                    // clipped down one side, and a focused one is clipped further.
                    posterWidth = GRID_POSTER_WIDTH,
                )
            }
        }
    }
}

/**
 * What the merged grid draws when the catalogue could not be read.
 *
 * A message and one focusable control, because a television has no pull-to-refresh and no error
 * bar: the remote is the only way to say "try again", so there has to be something for it to land
 * on.
 */
@Composable
private fun TvGridRetry(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Insisted on rather than requested once: the node does not exist on the frame this runs in.
    LaunchedEffect(Unit) { focusRequester.insistOnFocus() }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.tv_catalogue_unreadable),
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.tv_retry),
            color = if (isFocused) Color.Black else Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .focusRequester(focusRequester)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.15f))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

/**
 * Movies and Series as a shelf per category: the television's ordinary catalogue screen.
 *
 * Split from [TvPosterRows] when the merged grid arrived, so that neither shape carries a branch
 * about the other. See [TvMergedGrid] for why they cannot be one composable.
 */
@Composable
private fun TvCategoryShelves(
    kind: MediaKind,
    onPlay: (List<Channel>, Int) -> Unit,
    /**
     * Opens a title the viewer had already started.
     *
     * Takes a resolved [Channel] rather than the history entry, because the resolution
     * belongs here: history is keyed by provider identity so it survives a refresh, a
     * detail screen is reached by row id which does not, and the join between them is a
     * suspending lookup this screen's ViewModel already offers.
     */
    onResume: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    favouritesOnly: Boolean = false,
) {
    val viewModel: BrowseViewModel = koinViewModel(
        // Favourites is a different feed of the same kind, so it needs its own ViewModel
        // rather than sharing one and fighting over the filter.
        // not display text: a Koin scope key, never rendered.
        key = if (favouritesOnly) "tv-favourites" else "tv-${kind.name}",
        // A boolean here rather than the scope enum: this composable draws a catalogue or a
        // favourites list and has no third rendering, so taking the wider type would let a
        // caller ask it for a feed it cannot draw.
        //
        // Favourites is a list somebody built by hand and is as long as they made it. A
        // catalogue is not: `CATEGORY_ROWS` caps each category in SQL at what a row can show,
        // so this screen now reads about as many rows as it draws rather than every row of a
        // kind — thirty thousand of them on a real account, to fill rows forty tiles wide.
        parameters = {
            if (favouritesOnly) browseParams(kind, BrowseScope.FAVOURITES) else categoryRowsParams(kind)
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Grouped away from the main thread, not merely away from the query.
    //
    // One query already returns everything for this kind in the provider's order, so
    // grouping here rather than asking per category is still right. What was wrong was
    // where: this ran inside composition, so opening Movies against a catalogue of tens of
    // thousands built every category list during the first frame, on the UI thread. That is
    // the other half of the reported load time (#001).
    //
    // Favourites holds every kind at once, so it groups by kind instead — "Live TV" and
    // "Movies" are the distinction that matters there, and the provider's categories are
    // not even loaded for it.
    // Resolved here and passed down, because grouping runs off the main thread and a string
    // resource needs a composition to be read from. It is also the only reason the grouping
    // function has to know anything about wording at all.
    val kindLabels = MediaKind.entries.associateWith { stringResource(it.labelRes) }

    val rows by produceState(
        initialValue = emptyList<TvCategoryRow>(),
        state.items,
        state.categories,
        favouritesOnly,
        kindLabels,
    ) {
        value = withContext(Dispatchers.Default) {
            groupIntoRows(state.items, state.categories.map { it.title }, favouritesOnly, kindLabels)
        }
    }

    when {
        // Still grouping counts as still loading. Without the second clause the screen
        // shows "nothing here" for a frame between the items arriving and the rows being
        // built, which reads as an empty catalogue rather than as work in progress.
        state.isLoading || (rows.isEmpty() && state.items.isNotEmpty()) ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

        rows.isEmpty() -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.tv_no_content),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        else -> TvCategoryList(
            rows = rows,
            ratings = state.ratings,
            onVisible = viewModel::onPosterVisible,
            // The position travels with the item rather than being searched for. It is
            // indexed against the flat list so zapping walks every item on screen, not just
            // the row the viewer happened to start in.
            onItemClick = { item -> onPlay(state.items, item.flatIndex) },
            modifier = modifier,
            // First, above the catalogue, because it is what a returning viewer came for.
            // Empty on Favourites and on Live, which the ViewModel decides — a channel is
            // not something anyone continues, and Favourites is a list built by hand.
            continueWatching = if (state.history.isEmpty()) {
                null
            } else {
                {
                    TvContinueWatchingRow(
                        entries = state.history,
                        posters = state.posters,
                        // Null when the provider has since dropped the title, which is an
                        // honest outcome rather than an error: the row is stale, and doing
                        // nothing is better than opening a screen about nothing.
                        onClick = { entry ->
                            scope.launch {
                                viewModel.channelForHistory(entry)?.let(onResume)
                            }
                        },
                        // The same repository call the phone's long-press makes, and the same
                        // series branch inside it — one action, one implementation (INC-F3).
                        onRemove = viewModel::removeFromHistory,
                    )
                }
            },
        )
    }
}

/**
 * The catalogue itself: the rows, and the continue-watching row above them.
 *
 * Split out from [TvPosterRows] so it can be driven from a test with rows handed to it
 * directly. That matters more here than the usual argument for testable seams: #008 was a
 * screen that would not hold still, four analyses of it were wrong, and the only way to
 * settle such a thing is to measure the real composables rather than a copy of them that
 * can quietly drift. `TvBrowseScrollStabilityTest` composes this function.
 */
@Composable
internal fun TvCategoryList(
    rows: List<TvCategoryRow>,
    ratings: Map<String, Double>,
    onVisible: (Channel) -> Unit,
    onItemClick: (TvRowItem) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Artwork for titles the provider gave none for, keyed as the metadata cache keys it.
     *
     * Empty for the catalogue screens, which show what the playlist carries. The search
     * results fill it in, because a search is where a viewer meets titles they have never
     * scrolled to, and a grid of grey rectangles answers nothing.
     */
    posters: Map<String, String> = emptyMap(),
    /**
     * Whether each poster says what kind of thing it is.
     *
     * Off everywhere but Recently Added. A Movies row is already headed "Movies" and a label on
     * every tile would say what the screen has said once; a row that mixes films and series is
     * the one place the answer is not already on the page.
     */
    showKindBadge: Boolean = false,
    /**
     * How wide a tile is drawn, for a screen that has less height than a catalogue does.
     *
     * **The default is the catalogue's own size and every catalogue screen takes it.** Search is
     * the one caller that passes anything else: it keeps a field and a strip of genre chips above
     * the results, so the row gets what is left of the panel — and what is left is not the same
     * number on every television. See `posterWidthFor`.
     */
    posterWidth: Dp = POSTER_WIDTH,
    continueWatching: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    /*
     * Which tile the remote was on, so that coming back lands on it (`027` #1).
     *
     * `rememberSaveable`, and that is the load-bearing word. This list is *destroyed* whenever
     * anything opens over the shell — a film, a series, the player — so a plain `remember` would
     * be forgotten by exactly the journey this exists to survive. Saved state is kept for the
     * shell by the holder in `TvApp`, which is what makes the pair work: the list restores its
     * own scroll position, and this restores the remote's place in it.
     *
     * The tile's key rather than its index, because the two are not the same fact. A catalogue
     * that has refreshed underneath the viewer has moved every index and kept every key, and
     * "the fifth thing in the row" is not what anybody was looking at.
     */
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val cursorFocus = remember { FocusRequester() }

    /*
     * Asks for that tile back, once, on the way in from an overlay.
     *
     * Keyed on the rows because they are what has to exist first: a screen returning to a
     * catalogue is composed long before the query behind it answers, and a focus request made
     * against a tile that has not been built yet is a request that quietly returns false — the
     * failure `insistOnFocus` was written for, on the one journey where it matters most.
     *
     * The signal is consumed rather than read, so this cannot fire on a tab switch. See
     * [TvReturnSignal]: the content stealing focus off the tab bar is a worse fault than the one
     * being fixed here.
     */
    val returning = LocalTvReturn.current
    LaunchedEffect(rows) {
        if (rows.isEmpty() || focusedKey == null) return@LaunchedEffect
        if (!returning.consume()) return@LaunchedEffect
        cursorFocus.insistOnFocus()
    }

    val listState = rememberLazyListState()
    var isHeaderFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isHeaderFocused) {
        if (isHeaderFocused) {
            listState.animateScrollToItem(0, 0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Padding at the ends so the first heading and last row are not flush against the
        // edges. contentPadding is used rather than Modifier.padding so that the focused
        // poster's growth is not clipped by the container's bounds.
        //
        // Full-bleed header: top padding is removed when a header is present, so a hero
        // slider can reach the screen edges and blend into the ambient light.
        contentPadding = PaddingValues(top = if (header == null) LIST_TOP_PADDING else 0.dp, bottom = 32.dp),
        // Each row reserves FOCUS_GROWTH above and below itself, so the spacing between
        // rows is reduced by the same amount to keep the rhythm on screen unchanged.
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        if (header != null) {
            item(key = "header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isHeaderFocused = it.hasFocus }
                        .ignoreChildBringIntoView(),
                ) {
                    header()
                }
            }
        }

        if (continueWatching != null) {
            item(key = CONTINUE_ROW_KEY) {
                val rowModifier = if (header != null) {
                    Modifier.padding(horizontal = ROW_HORIZONTAL_PADDING)
                } else {
                    Modifier
                }
                Box(modifier = rowModifier) {
                    continueWatching()
                }
            }
        }

        items(items = rows, key = { it.title }) { row ->
            val rowModifier = if (header != null) {
                Modifier.padding(horizontal = ROW_HORIZONTAL_PADDING)
            } else {
                Modifier
            }
            CategoryRow(
                category = row.title,
                items = row.items,
                ratings = ratings,
                posters = posters,
                onVisible = onVisible,
                onItemClick = onItemClick,
                showKindBadge = showKindBadge,
                style = row.style,
                focusedKey = focusedKey,
                cursorFocus = cursorFocus,
                onFocusedKeyChange = { focusedKey = it },
                posterWidth = posterWidth,
                modifier = rowModifier,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
private fun Modifier.ignoreChildBringIntoView(): Modifier = this.then(
    Modifier.bringIntoViewResponder(
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect {
                return Rect.Zero
            }

            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                // Ignore child bringIntoView requests to prevent unwanted scrolling while focus is inside header
            }
        },
    ),
)

/** One category's worth of posters, ready to render. */
internal data class TvCategoryRow(
    val title: String,
    val items: List<TvRowItem>,
    /**
     * How this row's tiles are drawn.
     *
     * A property of the row rather than of each tile, which is what keeps every tile in a row
     * the same size as its neighbours. That is not tidiness: a focused poster whose measured
     * bounds differ from the one beside it is defect #008, and the note inside [TvPoster] says
     * what finding that cost.
     */
    val style: TvRowStyle = TvRowStyle.POSTER,
)

/** The two shapes a row of tiles comes in. */
internal enum class TvRowStyle {
    /** A poster and its title. Every row in the app but one. */
    POSTER,

    /**
     * A poster with its position drawn large beside it, in a gutter of its own.
     *
     * The two popular rows, and the reason they look like the numbered rows on a commercial
     * service: the number *is* the content there, and a row of ten identical posters says
     * nothing a viewer can read from a sofa.
     *
     * **The gutter is a fixed width and that is not a style choice.** The numeral used to be
     * drawn inside the artwork precisely so that a tile's width could not depend on how many
     * digits its number had — a tile that measures differently from its neighbour is defect
     * #008, and the note inside [TvPoster] records what finding that cost. Standing the figure
     * outside the poster is what was asked for, and a constant-width gutter is the one shape
     * that gives it without giving #008 back: `1` and `10` occupy the same rectangle.
     *
     * A ranked row also always reserves its caption line, whether or not any tile is using it,
     * for the same reason — see [TvPoster]'s `showCaption`.
     */
    RANKED,
}

/**
 * What one tile draws, whether or not there is anything behind it to play.
 *
 * Almost every tile in the app is a catalogue row and says so — [channel] is set and the four
 * fields above it are read off it. The exception is a popular title this provider does not carry:
 * it keeps its place in the ranking, draws from what the metadata service said, and has nothing
 * to open.
 *
 * The distinction is a null [channel] rather than a boolean beside one, so there is no way to
 * write a tile that claims to be playable and has no stream.
 */
internal data class TvTile(
    /** Unique within its row. A catalogue row's stable key, or the ranking's own position. */
    val key: String,
    val name: String,
    val kind: MediaKind,
    /** What the provider or the metadata service supplied. Null falls through to the placeholder. */
    val artworkUrl: String?,
    val channel: Channel?,
) {
    constructor(channel: Channel) : this(
        key = channel.stableKey,
        name = channel.name,
        kind = channel.kind,
        artworkUrl = channel.logoUrl,
        channel = channel,
    )
}

/**
 * A poster and where it sits in the flat list.
 *
 * The index is carried rather than recovered. Finding it with `indexOf` meant a linear scan
 * of every item in the catalogue on each press — unnoticeable on a short list, and on a
 * large one a pause between pressing a film and anything happening.
 *
 * [flatIndex] means nothing for a tile with no channel: there is no position in a list of things
 * to play for something that cannot be played.
 */
internal data class TvRowItem(
    val tile: TvTile,
    val flatIndex: Int,
    /** Where this title sits in the list it came from. Only [TvRowStyle.RANKED] draws it. */
    val rank: Int? = null,
    /**
     * A line under the poster, for the one row that has something to explain.
     *
     * The suggestions row names the title that caused each tile, which is what a scoring
     * function can say and a model cannot. Its height is reserved for every tile in the row
     * whether or not that tile has one, so the row measures the same all the way along.
     */
    val caption: String? = null,
) {
    /** The ordinary tile: a catalogue row, drawn from itself. */
    constructor(channel: Channel, flatIndex: Int, rank: Int? = null, caption: String? = null) :
        this(TvTile(channel), flatIndex, rank, caption)
}

/**
 * Groups the catalogue into rows, recording each item's flat position as it goes.
 *
 * One pass rather than a `groupBy` followed by a lookup per item: the position is known
 * while iterating and is thrown away by any approach that groups first.
 */
private fun groupIntoRows(
    items: List<Channel>,
    categoryOrder: List<String>,
    favouritesOnly: Boolean,
    kindLabels: Map<MediaKind, String>,
): List<TvCategoryRow> {
    val grouped = LinkedHashMap<String, MutableList<TvRowItem>>()
    items.forEachIndexed { index, channel ->
        // Favourites groups by kind, and the heading is the app's word for that kind — not the
        // enum constant, which is how these rows came to be headed "VOD" and "SERIES" (#017).
        val title = if (favouritesOnly) {
            kindLabels.getValue(channel.kind)
        } else {
            channel.groupTitle
        }
        grouped.getOrPut(title) { mutableListOf() }.add(TvRowItem(channel, index))
    }

    // Rows follow the provider's *category* order, not the order the first item of each
    // happens to appear in.
    //
    // Those are different, and only for films and series: a panel returns its categories in
    // one order and its streams in another. Grouping alone therefore produced a row order
    // that disagreed with the one the settings screen shows for the very same source —
    // reported, and correct to call a bug.
    //
    // Anything the order does not mention keeps its grouped position at the end, so a
    // category that exists only in the stream list is still reachable.
    if (favouritesOnly || categoryOrder.isEmpty()) {
        return grouped.map { (title, rowItems) -> TvCategoryRow(title, rowItems) }
    }

    val ranked = categoryOrder.withIndex().associate { (index, title) -> title to index }
    return grouped.entries
        .sortedBy { ranked[it.key] ?: Int.MAX_VALUE }
        .map { (title, rowItems) -> TvCategoryRow(title, rowItems) }
}

@Composable
@Suppress("LongParameterList")
private fun CategoryRow(
    category: String,
    items: List<TvRowItem>,
    ratings: Map<String, Double>,
    posters: Map<String, String>,
    onVisible: (Channel) -> Unit,
    onItemClick: (TvRowItem) -> Unit,
    showKindBadge: Boolean = false,
    style: TvRowStyle = TvRowStyle.POSTER,
    /** The tile the remote was on when the shell was last left. See [TvCategoryList]. */
    focusedKey: String? = null,
    /** Attached to that one tile, wherever in the catalogue it turns out to be. */
    cursorFocus: FocusRequester? = null,
    onFocusedKeyChange: (String) -> Unit = {},
    posterWidth: Dp = POSTER_WIDTH,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = category,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = HEADING_TOP_PADDING, bottom = TITLE_GAP),
        )

        // Room above and below for the focused poster to grow into. A `LazyRow` clips to its
        // own bounds, so without this the top of a scaled card is cut off flat — and the gap
        // under the title has to clear the same growth or the two touch (#003).
        // No content padding, and no spacing beyond what the items carry themselves. Both
        // used to live here; both now live inside the poster. See `Poster`.
        LazyRow {
            items(items = items, key = { it.tile.key }) { item ->
                // Per poster on screen, not per category: a category row can hold hundreds
                // of films, and only the handful the remote has actually reached are
                // displaying a score to fetch. A tile with no catalogue row behind it has
                // nothing to ask about.
                item.tile.channel?.let { channel ->
                    LaunchedEffect(channel.id) { onVisible(channel) }
                }
                TvPoster(
                    tile = item.tile,
                    rating = ratings[item.tile.key],
                    fallbackArtworkUrl = posters[item.tile.key],
                    showKindBadge = showKindBadge,
                    onClick = { onItemClick(item) },
                    // Only the one tile carries the requester. Two nodes sharing a
                    // `FocusRequester` is the state that throws, and a catalogue holds
                    // thousands of these.
                    focusRequester = cursorFocus.takeIf { item.tile.key == focusedKey },
                    onFocused = { onFocusedKeyChange(item.tile.key) },
                    rank = item.rank.takeIf { style == TvRowStyle.RANKED },
                    // Reserved for the whole row, not only for the tiles that have one. A tile
                    // one line taller than its neighbour is a tile whose bounds differ from its
                    // neighbour's, which is where #008 came from.
                    //
                    // A ranked row reserves it unconditionally, because that is where the answer
                    // to pressing an unavailable tile is written: a line that appears on one tile
                    // when it is pressed would resize that tile mid-row.
                    caption = item.caption,
                    showCaption = style == TvRowStyle.RANKED || items.any { it.caption != null },
                    posterWidth = posterWidth,
                )
            }
        }
    }
}

/**
 * One poster.
 *
 * Focus scales it up and gives it a border, and that is the whole affordance: on a
 * television the only way to know where you are is that one thing looks different from
 * everything else.
 *
 * Internal rather than private because the search screen draws the same tile. Shared rather
 * than copied for one specific reason: the modifier order inside it is the whole of #008, and
 * a second copy is a second place for that to be undone by somebody tidying up.
 */
@Composable
internal fun TvPoster(
    tile: TvTile,
    rating: Double?,
    onClick: () -> Unit,
    /**
     * Artwork to use when the playlist supplied none.
     *
     * Only ever a substitute. What the provider sent is what the viewer's other devices
     * show, and a looked-up poster that disagrees with it is a title that appears to be two
     * different things depending on where it is opened.
     */
    fallbackArtworkUrl: String? = null,
    /** Draws "Movie" or "Series" in the far corner from the score. See [KindBadge]. */
    showKindBadge: Boolean = false,
    /** This title's position in the list it came from, drawn across the artwork when present. */
    rank: Int? = null,
    /**
     * Set on the one tile a returning viewer should land on, and null on every other.
     *
     * The tile does not decide that — the list does, from what it remembered before it was
     * destroyed. See [TvCategoryList].
     */
    focusRequester: FocusRequester? = null,
    /** Says this tile now has the remote, so the list can remember it. */
    onFocused: () -> Unit = {},
    /** One line under the title, saying why this tile is here. */
    caption: String? = null,
    /**
     * Whether the caption's line is reserved at all.
     *
     * Set for every tile of a row that has any captions, so a tile without one is the same
     * height as a tile with one. **A tile whose height depends on its own content is what
     * defect #008 was**, and the long note below is the record of what that cost to find.
     */
    showCaption: Boolean = false,
    /** See the same parameter on [TvCategoryList]. The catalogue never passes it. */
    posterWidth: Dp = POSTER_WIDTH,
) {
    // A live channel's artwork is a small wide logo, not a poster. Cropping one to 2:3
    // shows a corner of a logo — the exact mistake PLAN-TV.md §3.3 exists to avoid — so a
    // live item keeps its whole logo inside the same tile instead.
    val isLogo = tile.kind == MediaKind.LIVE
    val artworkUrl = tile.artworkUrl?.takeIf { it.isNotBlank() }
        ?: fallbackArtworkUrl?.takeIf { it.isNotBlank() }
    val isPlayable = tile.channel != null
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Tells the shell which picture to take its background light from. One line, and it reports
    // rather than reads — nothing about this tile changes because of it, which is the point:
    // the scroll behaviour of these rows was expensive to get right and is measured by
    // `TvBrowseScrollStabilityTest`. See `LocalAmbientSink`.
    val ambientSink = LocalAmbientSink.current
    LaunchedEffect(isFocused, artworkUrl) {
        if (isFocused) ambientSink(AmbientRequest.Artwork(artworkUrl))
    }

    // Reported rather than stored here: which tile the remote is on is a fact about the list,
    // and a tile that remembered it for itself would be forgotten with the tile.
    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) FOCUSED_SCALE else 1f,
        label = "posterScale",
    )

    /*
     * Room for the focused poster to grow into. A `LazyRow` clips to its own bounds, so
     * without this the top of a scaled card is cut off flat and the first card in a row is
     * clipped at the left edge (#003).
     *
     * This padding is *not* what stopped the shake, though it was twice believed to be. The
     * shake is the modifier order below, and the note there says why.
     */
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The gutter is drawn for every tile of a ranked row and for no tile of any other, so a
        // row's tiles all measure the same. Its width is fixed rather than wrapped around the
        // figure: `1` and `10` are different widths and a tile whose width depends on its own
        // content is where #008 came from.
        rank?.let { RankGutter(rank = it, dimmed = !isPlayable) }

        Column(
            modifier = Modifier
                // On the tile a returning viewer left, and on no other. Ahead of `clickable`
                // because a requester attaches to the next focus target in the chain, and
                // `clickable`'s is the only one this tile has.
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                /*
                 * `clickable` before `graphicsLayer`, and the order is the whole of #008.
                 *
                 * A modifier chain applies outside-in, so anything to the right of
                 * `graphicsLayer` sits inside that layer — and `clickable` used to, which put
                 * the *focusable* node inside the animating scale. A focus node's bounds are
                 * resolved through every layer between it and the scrollable above it, so for
                 * the length of the scale animation this poster reported a rectangle that
                 * grew a little every frame.
                 *
                 * The vertical list reads exactly that rectangle to decide whether the
                 * focused thing is on screen. From the second row down the answer is "only
                 * just", so it scrolls until the poster is flush with the bottom edge — and
                 * flush is the one position where the next frame's growth immediately puts it
                 * out of view again. Press right, and the whole catalogue twitches upward
                 * while the new poster inflates. Hold the remote down and it does that on
                 * every repeat. That is the wobble, and it is why the **first row never
                 * shook**: a poster there fits with room to spare, so the list never scrolls
                 * and there is no equilibrium to fall off.
                 *
                 * With the focusable outside the layer its bounds are a constant 150dp box
                 * whatever the scale is doing. The poster still grows; nothing is asked to
                 * chase it, and every poster in a row now reports the same rectangle as its
                 * neighbours — so moving along a row gives the vertical list nothing to
                 * react to at all.
                 *
                 * Measured, not reasoned: `TvBrowseScrollStabilityTest` walks the remote
                 * along a row and reads the catalogue's position off every frame. Before this
                 * line it moved 11px on the second row and 12px on the fourth while the first
                 * stayed at zero, which is the asymmetry that was reported from the sofa.
                 * After it, all three are flat.
                 *
                 * Note for anyone changing this tile: the resting position is still *flush*
                 * with the viewport edge, which tolerates nothing. Anything that makes a
                 * focused poster's measured bounds vary — a focus-dependent size, a border
                 * that takes up layout, a label that grows a line — will start the loop
                 * again. The test is what will tell you.
                 */
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                /*
                 * The growth reserved **inside** the focus target rather than around it (`027` #4).
                 *
                 * The room itself is #003's and is unchanged. What moved is which node owns it:
                 * this padding used to sit on a `Box` *outside* the focusable, so the rectangle the
                 * tile reported to the list above was the 253dp column and not the 281dp the tile
                 * actually draws into. A vertical list scrolls a focused thing until *that*
                 * rectangle is on screen and then stops — which on the search results, where the
                 * field and the chips leave a row barely enough height, left the last ten
                 * millimetres of the tile below the viewport. The poster fitted; the title under it
                 * was sliced along its baseline, which is the report.
                 *
                 * Inside the focusable, the rectangle is the whole tile including the room the
                 * scale needs, so the list brings all of it into view and there is nothing left to
                 * clip. **It is still constant**, which is the invariant the note above exists to
                 * protect: this padding is the same 14dp whether the tile is focused or not, and it
                 * is outside `graphicsLayer`, so a tile's measured bounds still never move.
                 */
                .padding(horizontal = FOCUS_GROWTH_HORIZONTAL, vertical = FOCUS_GROWTH)
                .width(posterWidth)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            PosterArtwork(
                artworkUrl = artworkUrl,
                isLogo = isLogo,
                isPlayable = isPlayable,
                isFocused = isFocused,
                kind = tile.kind,
                rating = rating,
                showKindBadge = showKindBadge,
            )

            // No marquee. This was once thought to be the fix for the shake and it was not —
            // the shake was the modifier order above, and the recording that seemed to
            // implicate a marquee had truncated early, so its "idle" tail still had key
            // presses in it.
            //
            // It stays gone on its own merits: on a ten-foot display a title that never stops
            // moving is harder to read than one politely truncated.
            Text(
                text = tile.name.cleanedForDisplay(),
                color = Color.White.copy(alpha = if (isFocused) 1f else 0.7f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
            )

            // A fixed line, present or blank, for every tile in a row that has any. Its height
            // is set rather than left to the text, so an empty one and a full one measure alike.
            if (showCaption) {
                Text(
                    text = caption.orEmpty(),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    lineHeight = CAPTION_LINE_HEIGHT,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .height(CAPTION_HEIGHT)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The poster itself: the artwork, its border, and the overlays that sit on it.
 *
 * Its own composable because it is the whole of what the tile draws that is *not* layout, and
 * keeping it apart leaves [TvPoster] as what it should read like — a column of a picture, a title
 * and a caption. Nothing here takes up layout of its own: the box is a fixed aspect ratio and the
 * border draws at a constant size whether the tile is focused or not, which is the invariant the
 * modifier-order note in [TvPoster] exists to protect.
 */
@Composable
private fun PosterArtwork(
    artworkUrl: String?,
    isLogo: Boolean,
    isPlayable: Boolean,
    isFocused: Boolean,
    kind: MediaKind,
    rating: Double?,
    showKindBadge: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(POSTER_ASPECT_RATIO)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        if (artworkUrl == null) {
            ArtworkPlaceholder()
        } else {
            SubcomposeAsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isLogo) LOGO_PADDING else 0.dp)
                    // Dimmed rather than greyed. A title the provider does not carry
                    // is still the title it is, and a viewer reading a top ten should
                    // recognise the poster — it is the reason the place is filled at
                    // all. The badge is what says it cannot be opened.
                    .alpha(if (isPlayable) 1f else UNAVAILABLE_ARTWORK_ALPHA),
                loading = { ArtworkPlaceholder() },
                error = { ArtworkPlaceholder() },
            )
        }

        PosterOverlays(
            kind = kind,
            rating = rating,
            showKindBadge = showKindBadge,
            isPlayable = isPlayable,
        )
    }
}

/**
 * Everything drawn *on* the artwork: the score, and the kind or the fact that it is unavailable.
 *
 * All of them are overlays and none takes up layout. **That is the rule this composable exists to
 * keep in one place**: a label in the column below would change the tile's measured height, and a
 * focused tile whose bounds move is defect #008 all over again — the long note on the modifier
 * order in [TvPoster] is the record of what finding that cost.
 *
 * The rank is the one thing that used to be here and is no longer: it now stands outside the
 * poster, in a gutter of fixed width. See [RankGutter].
 */
@Composable
private fun BoxScope.PosterOverlays(
    kind: MediaKind,
    rating: Double?,
    showKindBadge: Boolean,
    isPlayable: Boolean,
) {
    // Top left, where the phone's card puts it, so the two apps agree about what a poster
    // looks like.
    rating?.let {
        RatingBadge(
            rating = it,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
    }

    // Opposite corner from the score, and one badge or the other rather than both. "Unavailable"
    // is the more useful of the two facts by a distance, and stacking a second plate in the same
    // corner is how a poster becomes unreadable from a sofa.
    val badge = if (isPlayable) kindLabel(kind).takeIf { showKindBadge } else stringResource(R.string.tv_unavailable)
    badge?.let { label ->
        KindBadge(
            label = label,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
    }
}

@Composable
private fun ArtworkPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * "Movie" or "Series", drawn on the artwork.
 *
 * Its own plate for the reason [RatingBadge] has one: artwork is arbitrary, and words drawn
 * straight onto it are legible or not depending on the film.
 */
@Composable
private fun KindBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = KIND_BADGE_ALPHA),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * The word for a kind, or null where there is no word worth drawing.
 *
 * Live returns null: a Recently Added row holds films and series only, and a channel that
 * somehow reached this tile is better with no label than with one that explains nothing.
 */
@Composable
private fun kindLabel(kind: MediaKind): String? = when (kind) {
    MediaKind.VOD -> stringResource(R.string.tv_kind_movie)
    MediaKind.SERIES -> stringResource(R.string.tv_kind_series)
    MediaKind.LIVE -> null
}

/**
 * The position, drawn large beside the poster in a gutter of its own.
 *
 * **The width is [RANK_GUTTER_WIDTH] whatever the figure is**, and that constant is the whole
 * safety of this shape. The numeral used to be drawn inside the artwork for exactly this reason:
 * a tile as wide as its own number is one that measures differently from its neighbours, and a
 * focused tile whose bounds differ from its neighbour's is defect #008 — see the note inside
 * [TvPoster]. The figure is centred in the gutter and is allowed to be clipped by it rather than
 * allowed to widen it, which is why nothing here wraps its content.
 *
 * The gutter takes up layout, unlike everything in [PosterOverlays]. That is safe because it is
 * outside the animating scale and identical on every tile of the row: it moves the poster along
 * and changes nothing about what the poster measures.
 */
@Composable
private fun RankGutter(rank: Int, dimmed: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(RANK_GUTTER_WIDTH)
            .height(RANK_GUTTER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            color = Color.White.copy(alpha = if (dimmed) RANK_DIMMED_ALPHA else 1f),
            fontSize = RANK_SIZE,
            lineHeight = RANK_SIZE,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private const val KIND_BADGE_ALPHA = 0.6f

/** Big enough to be the thing a viewer reads first, which is the whole point of the row. */
private val RANK_SIZE = 56.sp

/**
 * Wide enough for two digits at [RANK_SIZE], and the same for one.
 *
 * Fixed rather than measured. A gutter that wrapped its figure would make tile 1 narrower than
 * tile 10, and unequal tiles in a row is #008 — see [RankGutter].
 */
private val RANK_GUTTER_WIDTH = 68.dp

/** A number standing for something that cannot be opened is still a number, but a quieter one. */
private const val RANK_DIMMED_ALPHA = 0.45f

/** Recognisable, plainly not available. See the note at the call site. */
private const val UNAVAILABLE_ARTWORK_ALPHA = 0.4f

/** One line, at a fixed height so a tile without a caption measures like one with. */
private val CAPTION_HEIGHT = 15.dp
private val CAPTION_LINE_HEIGHT = 13.sp

/**
 * How wide a tile can be and still leave its row's heading on screen (`029` #4).
 *
 * **The heading is what gets lost, and losing it is the report.** Search stacks a field, an
 * Advanced chip and a strip of genre chips above its results, so the poster row is handed whatever
 * height is left — and how much that is depends on the panel. On the 960×540dp geometry this
 * project tests at, everything fits; on a television that reports less, the row is taller than its
 * viewport, the list scrolls the focused tile into view, and the heading above it goes off the
 * top. A viewer then cannot tell whether they are looking at films or at series, which is the
 * whole job the heading has.
 *
 * A fixed size cannot answer that for every panel, so the tile is sized from the room it is given:
 * take the height, subtract everything in the row that is not artwork, and the rest is the poster.
 * Clamped at both ends — never larger than the catalogue's own tile, because search is not the
 * screen that should have the biggest posters in the app, and never smaller than [MIN_POSTER_WIDTH],
 * below which artwork stops being recognisable from a sofa and shrinking further buys nothing.
 *
 * @param available the height the row and its heading have between them.
 */
internal fun posterWidthFor(available: Dp): Dp {
    val forArtwork = available - ROW_CHROME_HEIGHT
    return (forArtwork * POSTER_ASPECT_RATIO).coerceIn(MIN_POSTER_WIDTH, POSTER_WIDTH)
}

/**
 * Everything in a row that is not the artwork itself, measured rather than guessed at.
 *
 * The heading and the gap under it, the room a focused tile grows into above and below, the tile's
 * own title line, and the list's top padding. Every one of them is a constant in this file, so this
 * is a sum rather than an estimate — and it will follow any of them being retuned.
 */
// A getter rather than a stored value, because half the constants it sums are declared below it
// and a top-level `val` is initialised in file order — it would have read them as zero.
private val ROW_CHROME_HEIGHT: Dp
    get() = HEADING_TOP_PADDING + HEADING_LINE_HEIGHT + TITLE_GAP + FOCUS_GROWTH * 2 +
        TILE_TITLE_HEIGHT + LIST_TOP_PADDING

/** The heading's own line at 18sp, as the dp a layout has to find for it. */
private val HEADING_LINE_HEIGHT = 24.dp

/** The tile's title line at 14sp, plus the 8dp above it. */
private val TILE_TITLE_HEIGHT = 26.dp

/** Air above the heading, and the one number in [ROW_CHROME_HEIGHT] that is spent on nothing. */
private val HEADING_TOP_PADDING = 8.dp

/** Air above the first heading, so it is not flush against whatever is above the list. */
private val LIST_TOP_PADDING = 16.dp

/**
 * Below this a poster is a coloured rectangle rather than artwork.
 *
 * Two thirds of the catalogue's tile. A row that cannot fit even this is a row on a panel too
 * short for a poster row at all, and cropping is then the honest outcome — shrinking further would
 * hide the crop by making the content unreadable instead.
 */
private val MIN_POSTER_WIDTH = 100.dp

private val POSTER_WIDTH = 150.dp

/**
 * The tile width of the merged grid, which is narrower than a shelf's (`030` #3).
 *
 * 120dp rather than 150dp, which is seven columns on a 960dp panel where the shelf width gave
 * five. It stays above [MIN_POSTER_WIDTH], the figure the search screen already established as
 * the smallest a poster stays readable at from a sofa.
 */
private val GRID_POSTER_WIDTH = 120.dp
private const val POSTER_ASPECT_RATIO = 2f / 3f

/** The poster's own height, so the figure sits beside the artwork rather than beside the title. */
private val RANK_GUTTER_HEIGHT = POSTER_WIDTH / POSTER_ASPECT_RATIO
private const val FOCUSED_SCALE = 1.1f
private val LOGO_PADDING = 12.dp

/**
 * How far a focused poster grows past its own edge, rounded up.
 *
 * A poster column is the artwork — 150dp wide at 2:3, so 225dp tall — plus its label, near
 * enough 253dp altogether. [FOCUSED_SCALE] scales it about its centre, so it gains about
 * 25dp of height and half of that goes upwards. The old 10dp gap under a category title was
 * less than that, which is exactly why a focused card touched the title above it (#003).
 *
 * Derived here rather than measured because the poster's size is fixed and known: if any of
 * the three constants above change, this is the line to revisit.
 */
private val FOCUS_GROWTH = 14.dp

/**
 * How far a focused poster grows past its own left and right edges.
 *
 * Half of what it gains in width: 150dp scaled by 1.1 is 15dp wider, so 7.5dp each side.
 * Rounded up, and the row reserves it so the first and last cards are not clipped.
 */
private val FOCUS_GROWTH_HORIZONTAL = 10.dp

/** Clear space under a category title, over and above the growth reserved in the row. */
private val TITLE_GAP = 16.dp

/** Between rows, net of the [FOCUS_GROWTH] every poster reserves above and below itself. */
private val ROW_SPACING = 8.dp

/** Horizontal overscan padding for rows when a full-bleed header is present. */
private val ROW_HORIZONTAL_PADDING = 48.dp

/** Stable across catalogues, so the row is not rebuilt when the titles beneath it change. */
private const val CONTINUE_ROW_KEY = "__continue__"
