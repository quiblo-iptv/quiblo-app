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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.feature.browse.BrowseViewModel
import dev.quiblo.feature.browse.di.browseParams
import dev.quiblo.tv.R
import org.koin.androidx.compose.koinViewModel

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
    onItemClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BrowseViewModel = koinViewModel(
        key = "tv-${kind.name}",
        parameters = { browseParams(kind, favoritesOnly = false) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Grouped here rather than queried per category: one query already returns everything
    // for this kind in the provider's order, and issuing one query per category would turn
    // a single read into dozens.
    val rows = remember(state.items) {
        state.items.groupBy { it.groupTitle }.entries.toList()
    }

    when {
        state.isLoading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }

        rows.isEmpty() -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.tv_no_content),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            items(items = rows, key = { it.key }) { (category, items) ->
                CategoryRow(category = category, items = items, onItemClick = onItemClick)
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: String,
    items: List<Channel>,
    onItemClick: (Channel) -> Unit,
) {
    Column {
        Text(
            text = category,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items = items, key = { it.id }) { item ->
                Poster(channel = item, onClick = { onItemClick(item) })
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
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Poster(channel: Channel, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) FOCUSED_SCALE else 1f,
        label = "posterScale",
    )

    Column(
        modifier = Modifier
            .width(POSTER_WIDTH)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
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
            if (channel.logoUrl.isNullOrBlank()) {
                ArtworkPlaceholder()
            } else {
                SubcomposeAsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { ArtworkPlaceholder() },
                    error = { ArtworkPlaceholder() },
                )
            }
        }

        Text(
            text = channel.name,
            color = Color.White.copy(alpha = if (isFocused) 1f else 0.7f),
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
            // Only the focused poster scrolls its title. Every title scrolling at once would
            // be unreadable, and marquee is a no-op when the text already fits.
            overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .then(if (isFocused) Modifier.basicMarquee() else Modifier),
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

private val POSTER_WIDTH = 150.dp
private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val FOCUSED_SCALE = 1.1f
