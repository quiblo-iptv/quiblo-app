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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import dev.vibrato.core.model.Category
import dev.vibrato.core.model.Channel

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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.currentQuery.collectAsStateWithLifecycle()

    if (!state.hasSource) {
        CentredMessage(stringResource(R.string.browse_no_source), modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showSearch) {
            SearchField(query = query, onQueryChange = viewModel::search)
        }

        if (state.categories.isNotEmpty()) {
            CategoryFilter(
                categories = state.categories,
                selected = state.selectedCategory,
                onSelect = viewModel::selectCategory,
            )
        }

        when {
            state.items.isEmpty() && query.isNotBlank() ->
                CentredMessage(stringResource(R.string.browse_no_results))

            state.items.isEmpty() -> CentredMessage(emptyMessage)

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.items, key = { it.id }) { item ->
                    ChannelRow(
                        channel = item,
                        onClick = { onItemClick(item) },
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = { Text(stringResource(R.string.browse_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CategoryFilter(
    categories: List<Category>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.browse_category_all)) },
            )
        }
        items(items = categories, key = { it.title }) { category ->
            FilterChip(
                selected = selected == category.title,
                onClick = { onSelect(category.title) },
                label = { Text(category.displayTitle()) },
            )
        }
    }
}

/** Substitutes a translated label for the storage-only ungrouped key (AC-PL-06). */
@Composable
private fun Category.displayTitle(): String =
    if (title == Category.UNGROUPED_TITLE) stringResource(R.string.browse_category_ungrouped) else title

@Composable
private fun ChannelRow(
    channel: Channel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel.logoUrl)
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
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
