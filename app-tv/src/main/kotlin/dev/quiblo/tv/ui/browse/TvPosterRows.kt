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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import dev.quiblo.feature.browse.RatingBadge
import dev.quiblo.feature.browse.di.browseParams
import dev.quiblo.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /** Hands over the whole list, not just the item: the player needs it to zap. */
    onPlay: (List<Channel>, Int) -> Unit,
    modifier: Modifier = Modifier,
    favouritesOnly: Boolean = false,
) {
    val viewModel: BrowseViewModel = koinViewModel(
        // Favourites is a different feed of the same kind, so it needs its own ViewModel
        // rather than sharing one and fighting over the filter.
        key = if (favouritesOnly) "tv-favourites" else "tv-${kind.name}",
        parameters = { browseParams(kind, favoritesOnly = favouritesOnly) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
    val rows by produceState(initialValue = emptyList<TvCategoryRow>(), state.items, favouritesOnly) {
        value = withContext(Dispatchers.Default) { groupIntoRows(state.items, favouritesOnly) }
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

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            // Each row now reserves FOCUS_GROWTH above and below itself, so the spacing
            // between rows is reduced by the same amount to keep the rhythm on screen
            // unchanged. The gap a viewer sees is still about 28dp.
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        ) {
            items(items = rows, key = { it.title }) { row ->
                CategoryRow(
                    category = row.title,
                    items = row.items,
                    ratings = state.ratings,
                    onVisible = viewModel::onPosterVisible,
                    // The position travels with the item rather than being searched for.
                    // It is indexed against the flat list so zapping walks every item on
                    // screen, not just the row the viewer happened to start in.
                    onItemClick = { item -> onPlay(state.items, item.flatIndex) },
                )
            }
        }
    }
}

/** One category's worth of posters, ready to render. */
private data class TvCategoryRow(val title: String, val items: List<TvRowItem>)

/**
 * A poster and where it sits in the flat list.
 *
 * The index is carried rather than recovered. Finding it with `indexOf` meant a linear scan
 * of every item in the catalogue on each press — unnoticeable on a short list, and on a
 * large one a pause between pressing a film and anything happening.
 */
private data class TvRowItem(val channel: Channel, val flatIndex: Int)

/**
 * Groups the catalogue into rows, recording each item's flat position as it goes.
 *
 * One pass rather than a `groupBy` followed by a lookup per item: the position is known
 * while iterating and is thrown away by any approach that groups first.
 */
private fun groupIntoRows(items: List<Channel>, favouritesOnly: Boolean): List<TvCategoryRow> {
    val grouped = LinkedHashMap<String, MutableList<TvRowItem>>()
    items.forEachIndexed { index, channel ->
        val title = if (favouritesOnly) channel.kind.name else channel.groupTitle
        grouped.getOrPut(title) { mutableListOf() }.add(TvRowItem(channel, index))
    }
    return grouped.map { (title, rowItems) -> TvCategoryRow(title, rowItems) }
}

@Composable
private fun CategoryRow(
    category: String,
    items: List<TvRowItem>,
    ratings: Map<String, Double>,
    onVisible: (Channel) -> Unit,
    onItemClick: (TvRowItem) -> Unit,
) {
    Column {
        Text(
            text = category,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = TITLE_GAP),
        )

        // Room above and below for the focused poster to grow into. A `LazyRow` clips to its
        // own bounds, so without this the top of a scaled card is cut off flat — and the gap
        // under the title has to clear the same growth or the two touch (#003).
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = FOCUS_GROWTH),
        ) {
            items(items = items, key = { it.channel.id }) { item ->
                // Per poster on screen, not per category: a category row can hold hundreds
                // of films, and only the handful the remote has actually reached are
                // displaying a score to fetch.
                LaunchedEffect(item.channel.id) { onVisible(item.channel) }
                Poster(
                    channel = item.channel,
                    rating = ratings[item.channel.stableKey],
                    onClick = { onItemClick(item) },
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
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Poster(channel: Channel, rating: Double?, onClick: () -> Unit) {
    // A live channel's artwork is a small wide logo, not a poster. Cropping one to 2:3
    // shows a corner of a logo — the exact mistake PLAN-TV.md §3.3 exists to avoid — so a
    // live item keeps its whole logo inside the same tile instead.
    val isLogo = channel.kind == MediaKind.LIVE
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
                    contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isLogo) LOGO_PADDING else 0.dp),
                    loading = { ArtworkPlaceholder() },
                    error = { ArtworkPlaceholder() },
                )
            }

            // Top left, where the phone's card puts it, so the two apps agree about what a
            // poster looks like.
            rating?.let {
                RatingBadge(
                    rating = it,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
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

/** Clear space under a category title, over and above the growth reserved in the row. */
private val TITLE_GAP = 16.dp

/** Between rows, net of the [FOCUS_GROWTH] each one now reserves on both sides. */
private val ROW_SPACING = 14.dp
