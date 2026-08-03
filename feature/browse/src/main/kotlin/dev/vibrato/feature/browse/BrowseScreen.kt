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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
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
import coil3.compose.SubcomposeAsyncImage
import dev.vibrato.core.model.Category
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.Programme

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
    var guideFor: Channel? by remember { mutableStateOf(null) }
    var showCategorySheet by remember { mutableStateOf(false) }
    // Grid by default: artwork is the point of a catalogue, and the list view remains one
    // tap away for anyone who wants density instead.
    var isGridView by remember { mutableStateOf(true) }

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
            selectedCategory = state.selectedCategory,
            isGridView = isGridView,
            onToggleGridView = { isGridView = !isGridView },
            onCategoryClick = { showCategorySheet = true },
        )

        when {
            state.items.isEmpty() && query.isNotBlank() ->
                CentredMessage(stringResource(R.string.browse_no_results))

            state.items.isEmpty() -> CentredMessage(emptyMessage)

            isGridView -> LazyVerticalGrid(
                // Posters are portrait and read fine narrower, so they get more per row.
                columns = GridCells.Adaptive(minSize = if (showArtworkCards) 120.dp else 150.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // No guide prefetch here on purpose: a grid card shows no programme, and
                // a grid fits several times more items on screen than a list. Fetching
                // for each one is a burst of requests whose results nothing renders.
                items(items = state.items, key = { it.id }) { item ->
                    if (showArtworkCards) {
                        ChannelArtCard(
                            channel = item,
                            onClick = { onItemClick(item) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                        )
                    } else {
                        ChannelGridCard(
                            channel = item,
                            onClick = { onItemClick(item) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                        )
                    }
                }
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.items, key = { it.id }) { item ->
                    LaunchedEffect(item.id) { viewModel.onRowVisible(item) }
                    ChannelRow(
                        channel = item,
                        nowPlaying = state.nowPlaying[item.stableKey],
                        onClick = { onItemClick(item) },
                        onLongClick = { guideFor = item },
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    guideFor?.let { channel ->
        GuideSheet(
            channel = channel,
            nowNext = viewModel.nowNextFor(channel),
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
@Composable
private fun BrowseHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    showCategory: Boolean,
    selectedCategory: String?,
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
                CategoryChip(selectedCategory = selectedCategory, onClick = onCategoryClick)
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
private fun CategoryChip(selectedCategory: String?, onClick: () -> Unit) {
    val displayLabel = when (selectedCategory) {
        null -> stringResource(R.string.browse_category_all)
        Category.UNGROUPED_TITLE -> stringResource(R.string.browse_category_ungrouped)
        else -> selectedCategory
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
                ChannelLogo(channel.logoUrl)
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
                text = channel.name,
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
        ChannelLogo(channel.logoUrl)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = channel.name,
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
