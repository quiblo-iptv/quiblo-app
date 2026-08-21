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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.common.cleanedForDisplay
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.Programme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * The list UI shared by Live, Movies, Series and Favourites.
 *
 * Rows are keyed by database id so Compose reuses them across refreshes and favourite
 * toggles instead of rebuilding the list, which is what keeps a 20,000-entry list
 * scrolling smoothly (AC-PL-05).
 *
 * @param emptyMessage shown when the source has no content of this type.
 */
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onItemClick: (Channel) -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    showSearch: Boolean = true,
    /**
     * Renders grid items as poster cards — artwork edge to edge with the title over a
     * gradient — instead of a logo and a label.
     *
     * Opt-in rather than the default because it only suits content whose artwork *is* a
     * poster. Live channel logos are small wide badges, and cropping one into a portrait
     * card shows you a corner of a logo.
     */
    showArtworkCards: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.currentQuery.collectAsStateWithLifecycle()

    /**
     * The catalogue, a page at a time.
     *
     * The grid used to be handed every row of a kind — tens of thousands on a real account,
     * mapped to domain objects and re-emitted on every write to the table — to draw about a
     * dozen cards. `PagingData` is a stream of loads rather than a value, which is why it is
     * collected here and not read off [BrowseUiState].
     */
    val items = viewModel.pagedItems.collectAsLazyPagingItems()
    val loaded = items.itemSnapshotList.items
    var guideFor: Channel? by remember { mutableStateOf(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    // Grid by default: artwork is the point of a catalogue, and the list view remains one
    // tap away for anyone who wants density instead.
    var isGridView by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Hidden while searching. A search is a question about the catalogue, and answering it
    // with a strip of unrelated things the user happened to watch last week is noise.
    val history = if (query.isBlank()) state.history else emptyList()

    // History is keyed by provider identity so it survives a refresh; the screens it opens
    // are keyed by row id, which does not. A tile whose item the provider has since dropped
    // resolves to nothing and does nothing, which is the honest outcome.
    val openHistory: (HistoryEntry) -> Unit = { entry ->
        scope.launch { viewModel.channelForHistory(entry)?.let(onItemClick) }
    }

    // The "continue watching" strip is one lazy item ahead of the catalogue, so a visible
    // index is one further along than the item it shows. Getting this wrong prefetches the
    // wrong rows, which is invisible until someone wonders why the last poster never scores.
    val headerCount = if (history.isEmpty()) 0 else 1

    // Both prefetches wait for the list to stop moving. See [PrefetchWhenSettled].
    PrefetchWhenSettled(
        isScrolling = { gridState.isScrollInProgress },
        visibleIndices = { gridState.layoutInfo.visibleItemsInfo.map { it.index } },
        items = loaded,
        indexOffset = headerCount,
        // On for every grid, not only the poster ones. A live grid shows no score, but it
        // does show a logo, and a channel whose playlist supplied none has nothing in the
        // frame until the reference list is asked.
        enabled = true,
        onVisible = viewModel::onPosterVisible,
    )
    PrefetchWhenSettled(
        isScrolling = { listState.isScrollInProgress },
        visibleIndices = { listState.layoutInfo.visibleItemsInfo.map { it.index } },
        items = loaded,
        indexOffset = headerCount,
        enabled = true,
        onVisible = viewModel::onRowVisible,
    )

    // Loading is checked before "no source": they used to be the same branch, so opening a
    // screen with a perfectly good playlist told the user to go and add one.
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!state.hasSource) {
        CentredMessage(stringResource(R.string.browse_no_source), modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        BrowseHeader(
            query = query,
            onQueryChange = viewModel::search,
            showSearch = showSearch,
            showCategory = state.categories.isNotEmpty(),
            // The chip shows the local name, while the state keeps the provider's title —
            // renaming must not change what the filter matches on.
            selectedCategory = state.selectedCategory,
            selectedCategoryLabel = state.categories
                .firstOrNull { it.title == state.selectedCategory }
                ?.displayTitle,
            isGridView = isGridView,
            onToggleGridView = { isGridView = !isGridView },
            onCategoryClick = { showCategorySheet = true },
        )

        BrowseCatalogue(
            state = state,
            items = items,
            history = history,
            query = query,
            emptyMessage = emptyMessage,
            isGridView = isGridView,
            showArtworkCards = showArtworkCards,
            gridState = gridState,
            listState = listState,
            onItemClick = onItemClick,
            onHistoryClick = openHistory,
            onHistoryRemove = viewModel::removeFromHistory,
            onToggleFavorite = viewModel::toggleFavorite,
            onShowGuide = { guideFor = it },
        )
    }

    guideFor?.let { channel ->
        // Remembered against the channel, not rebuilt per recomposition: a fresh Flow instance
        // restarts collection, and a restarted collection empties the sheet for a frame.
        GuideSheet(
            channel = channel,
            nowNext = remember(channel.stableKey) { viewModel.nowNextFor(channel) },
            schedule = remember(channel.stableKey) { viewModel.scheduleFor(channel) },
            onOpen = { viewModel.requestFullGuide(channel) },
            onDismiss = { guideFor = null },
        )
    }

    if (showCategorySheet) {
        CategoryPickerSheet(
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            onSelectCategory = viewModel::selectCategory,
            onDismiss = { showCategorySheet = false },
        )
    }
}

/**
 * The catalogue itself: the empty states, the poster grid and the dense list.
 *
 * Split out of [BrowseScreen] because that function had grown to hold the header, the two
 * prefetch effects, two sheets and all three of these — one composable making six unrelated
 * decisions, where each of them is simple on its own.
 */
@Composable
@Suppress("LongParameterList")
private fun BrowseCatalogue(
    state: BrowseUiState,
    /** The catalogue itself. Not on [state]: `PagingData` is a stream of loads, not a value. */
    items: LazyPagingItems<Channel>,
    history: List<HistoryEntry>,
    query: String,
    emptyMessage: String,
    isGridView: Boolean,
    showArtworkCards: Boolean,
    gridState: LazyGridState,
    listState: LazyListState,
    onItemClick: (Channel) -> Unit,
    onHistoryClick: (HistoryEntry) -> Unit,
    onHistoryRemove: (HistoryEntry) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onShowGuide: (Channel) -> Unit,
) {
    // A page still arriving is not an empty catalogue, and saying so is the difference between
    // "nothing matched" and a screen that has not been answered yet.
    val isEmpty = items.loadState.refresh !is LoadState.Loading && items.itemCount == 0

    when {
        isEmpty && query.isNotBlank() ->
            CentredMessage(stringResource(R.string.browse_no_results))

        // The strip is still shown above an empty catalogue: a category filter that matches
        // nothing does not mean the things the user was watching stopped existing.
        isEmpty -> Column {
            ContinueWatchingRow(
                entries = history,
                posters = state.posters,
                onClick = onHistoryClick,
                onRemove = onHistoryRemove,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            CentredMessage(emptyMessage)
        }

        isGridView -> LazyVerticalGrid(
            state = gridState,
            // Posters are portrait and read fine narrower, so they get more per row.
            columns = GridCells.Adaptive(minSize = if (showArtworkCards) 120.dp else 150.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Scrolls away with the catalogue rather than pinned above it: the strip is a
            // shortcut, and a viewer who has scrolled past it is browsing instead.
            if (history.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = HISTORY_ITEM_KEY) {
                    ContinueWatchingRow(
                        entries = history,
                        posters = state.posters,
                        onClick = onHistoryClick,
                        onRemove = onHistoryRemove,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            // No guide prefetch here on purpose: a grid card shows no programme, and a grid
            // fits several times more items on screen than a list. Fetching for each one is
            // a burst of requests whose results nothing renders. The score a poster *does*
            // show is prefetched on scroll settling — see [PrefetchWhenSettled].
            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                // Null while the page holding this card is still arriving. Placeholders are off,
                // so this is only ever transient and drawing nothing for a frame is honest.
                val item = items[index] ?: return@items
                if (showArtworkCards) {
                    ChannelArtCard(
                        channel = item,
                        rating = state.ratings[item.stableKey],
                        fallbackPosterUrl = state.posters[item.stableKey],
                        onClick = { onItemClick(item) },
                        onToggleFavorite = { onToggleFavorite(item) },
                    )
                } else {
                    ChannelGridCard(
                        channel = item,
                        fallbackLogoUrl = state.posters[item.stableKey],
                        onClick = { onItemClick(item) },
                        onToggleFavorite = { onToggleFavorite(item) },
                    )
                }
            }
        }

        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (history.isNotEmpty()) {
                item(key = HISTORY_ITEM_KEY) {
                    ContinueWatchingRow(
                        entries = history,
                        posters = state.posters,
                        onClick = onHistoryClick,
                        onRemove = onHistoryRemove,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                }
            }

            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                val item = items[index] ?: return@items
                ChannelRow(
                    channel = item,
                    fallbackLogoUrl = state.posters[item.stableKey],
                    nowPlaying = state.nowPlaying[item.stableKey],
                    onClick = { onItemClick(item) },
                    onLongClick = { onShowGuide(item) },
                    onToggleFavorite = { onToggleFavorite(item) },
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * One row: the category filter, the search affordance, and the layout toggle.
 *
 * Previously two stacked rows. The collapsed search kept a full row of its own to hold a
 * single icon, so every browse screen opened with a band of empty space between the app bar
 * and the first result — on a tablet in landscape that is a meaningful fraction of the
 * visible catalogue given away to nothing.
 *
 * Expanding search takes the row over rather than adding another, so the height never
 * changes and the content below does not jump.
 */

/**
 * Prefetches for the rows on screen, once the list has stopped moving.
 *
 * Not per row entering composition, which is what this used to be. A fling through a
 * channel list composes hundreds of rows in a couple of seconds, and asking the provider
 * for a guide for each one is a burst of hundreds of requests at a panel that counts them
 * — the behaviour that got this project's account blocked, and then blocked again. The
 * television app was given a focus-settle delay for exactly this reason and the phone was
 * not; this is the phone's equivalent.
 *
 * Rows flown past are not rows anyone read, so nothing is lost by skipping them. The same
 * rule covers poster scores: TMDB answers a flood with a rate limit rather than a ban, but
 * it is still the user's key and their quota being spent on titles nobody looked at.
 */
@Composable
private fun PrefetchWhenSettled(
    isScrolling: () -> Boolean,
    visibleIndices: () -> List<Int>,
    items: List<Channel>,
    /** How many lazy items sit before the catalogue — the header strip, when there is one. */
    indexOffset: Int,
    enabled: Boolean,
    onVisible: (Channel) -> Unit,
) {
    LaunchedEffect(items, enabled, indexOffset) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow(isScrolling)
            .filter { scrolling -> !scrolling }
            .collect {
                visibleIndices().forEach { index -> items.getOrNull(index - indexOffset)?.let(onVisible) }
            }
    }
}

@Composable
private fun BrowseHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    showCategory: Boolean,
    selectedCategory: String?,
    selectedCategoryLabel: String?,
    isGridView: Boolean,
    onToggleGridView: () -> Unit,
    onCategoryClick: () -> Unit,
) {
    var isExpanded by remember(query) { mutableStateOf(query.isNotEmpty()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isExpanded && showSearch) {
            // No label, only a placeholder: a floating label makes the field tall enough to
            // change the row height, which is the thing this layout exists to avoid.
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.browse_search)) },
                leadingIcon = {
                    IconButton(onClick = {
                        if (query.isEmpty()) isExpanded = false else onQueryChange("")
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.browse_close_search),
                        )
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.browse_search_clear),
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            if (showCategory) {
                CategoryChip(
                    selectedCategory = selectedCategory,
                    label = selectedCategoryLabel,
                    onClick = onCategoryClick,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showSearch) {
                IconButton(onClick = { isExpanded = true }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.browse_search))
                }
            }

            IconButton(onClick = onToggleGridView) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = stringResource(R.string.browse_toggle_view),
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(selectedCategory: String?, label: String?, onClick: () -> Unit) {
    val displayLabel = when (selectedCategory) {
        null -> stringResource(R.string.browse_category_all)
        Category.UNGROUPED_TITLE -> stringResource(R.string.browse_category_ungrouped)
        else -> label ?: selectedCategory
    }

    FilterChip(
        selected = selectedCategory != null,
        onClick = onClick,
        label = {
            Text(
                text = stringResource(R.string.browse_category_label, displayLabel),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
    )
}

@Composable
private fun ChannelGridCard(
    channel: Channel,
    /** From the channel reference list, used only when the playlist supplied none. */
    fallbackLogoUrl: String?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                ChannelLogo(channel.logoUrl?.takeIf { it.isNotBlank() } ?: fallbackLogoUrl)
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.browse_favorite),
                        tint = if (channel.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Text(
                text = channel.name.cleanedForDisplay(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: Channel,
    /** From the channel reference list, used only when the playlist supplied none. */
    fallbackLogoUrl: String?,
    nowPlaying: Programme?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Tap plays; long press opens the guide. Playing is what a user wants
            // almost every time, so it keeps the cheaper gesture.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel.logoUrl?.takeIf { it.isNotBlank() } ?: fallbackLogoUrl)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = channel.name.cleanedForDisplay(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Nothing at all when there is no guide, rather than an empty placeholder.
            // An M3U source has no guide by construction (AC-EPG-04).
            if (nowPlaying != null) {
                NowPlayingLine(nowPlaying)
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(
                    if (channel.isFavorite) R.string.browse_favorite_remove else R.string.browse_favorite_add,
                ),
                tint = if (channel.isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * The current programme and how far through it we are (AC-EPG-01).
 *
 * The progress value is recomputed from the programme's own times rather than ticking a
 * timer per row, so a long list costs nothing extra to keep roughly accurate.
 */
@Composable
private fun NowPlayingLine(programme: Programme) {
    val now = System.currentTimeMillis()
    Text(
        text = programme.title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    LinearProgressIndicator(
        progress = { programme.progressAt(now) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, end = 8.dp),
    )
}

/**
 * A fixed-size logo slot.
 *
 * The size is fixed regardless of load state so a slow or broken logo cannot reflow the
 * list while the user is scrolling (AC-PL-03).
 */
@Composable
private fun ChannelLogo(logoUrl: String?) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNullOrBlank()) {
            LogoPlaceholder()
        } else {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = { LogoPlaceholder() },
                error = { LogoPlaceholder() },
            )
        }
    }
}

@Composable
private fun LogoPlaceholder() {
    Icon(
        imageVector = Icons.Filled.LiveTv,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun CentredMessage(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The strip's own lazy key.
 *
 * Constant and distinct from any channel id, so adding or removing the strip never makes
 * Compose reuse a poster's slot for it — the kind of collision that shows one item's
 * artwork under another's title.
 */
private const val HISTORY_ITEM_KEY = "continue-watching"
