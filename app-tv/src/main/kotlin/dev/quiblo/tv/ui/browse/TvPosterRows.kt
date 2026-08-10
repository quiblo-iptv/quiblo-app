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
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.quiblo.feature.browse.labelRes
import dev.quiblo.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        parameters = { browseParams(kind, favoritesOnly = favouritesOnly) },
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
    continueWatching: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Each row reserves FOCUS_GROWTH above and below itself, so the spacing between
        // rows is reduced by the same amount to keep the rhythm on screen unchanged.
        // The gap a viewer sees is still about 28dp.
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        if (continueWatching != null) {
            item(key = CONTINUE_ROW_KEY) { continueWatching() }
        }

        items(items = rows, key = { it.title }) { row ->
            CategoryRow(
                category = row.title,
                items = row.items,
                ratings = ratings,
                posters = posters,
                onVisible = onVisible,
                onItemClick = onItemClick,
            )
        }
    }
}

/** One category's worth of posters, ready to render. */
internal data class TvCategoryRow(val title: String, val items: List<TvRowItem>)

/**
 * A poster and where it sits in the flat list.
 *
 * The index is carried rather than recovered. Finding it with `indexOf` meant a linear scan
 * of every item in the catalogue on each press — unnoticeable on a short list, and on a
 * large one a pause between pressing a film and anything happening.
 */
internal data class TvRowItem(val channel: Channel, val flatIndex: Int)

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
private fun CategoryRow(
    category: String,
    items: List<TvRowItem>,
    ratings: Map<String, Double>,
    posters: Map<String, String>,
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
        // No content padding, and no spacing beyond what the items carry themselves. Both
        // used to live here; both now live inside the poster. See `Poster`.
        LazyRow {
            items(items = items, key = { it.channel.id }) { item ->
                // Per poster on screen, not per category: a category row can hold hundreds
                // of films, and only the handful the remote has actually reached are
                // displaying a score to fetch.
                LaunchedEffect(item.channel.id) { onVisible(item.channel) }
                TvPoster(
                    channel = item.channel,
                    rating = ratings[item.channel.stableKey],
                    fallbackArtworkUrl = posters[item.channel.stableKey],
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
 *
 * Internal rather than private because the search screen draws the same tile. Shared rather
 * than copied for one specific reason: the modifier order inside it is the whole of #008, and
 * a second copy is a second place for that to be undone by somebody tidying up.
 */
@Composable
internal fun TvPoster(
    channel: Channel,
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
) {
    // A live channel's artwork is a small wide logo, not a poster. Cropping one to 2:3
    // shows a corner of a logo — the exact mistake PLAN-TV.md §3.3 exists to avoid — so a
    // live item keeps its whole logo inside the same tile instead.
    val isLogo = channel.kind == MediaKind.LIVE
    val artworkUrl = channel.logoUrl?.takeIf { it.isNotBlank() }
        ?: fallbackArtworkUrl?.takeIf { it.isNotBlank() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

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
     * shake is the modifier order two lines below, and the note there says why.
     */
    Box(
        modifier = Modifier.padding(
            horizontal = FOCUS_GROWTH_HORIZONTAL,
            vertical = FOCUS_GROWTH,
        ),
    ) {
        Column(
            modifier = Modifier
                .width(POSTER_WIDTH)
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
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
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

            // No marquee. This was once thought to be the fix for the shake and it was not —
            // the shake was the modifier order above, and the recording that seemed to
            // implicate a marquee had truncated early, so its "idle" tail still had key
            // presses in it.
            //
            // It stays gone on its own merits: on a ten-foot display a title that never stops
            // moving is harder to read than one politely truncated.
            Text(
                text = channel.name,
                color = Color.White.copy(alpha = if (isFocused) 1f else 0.7f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
            )
        }
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
private val ROW_SPACING = 4.dp

/** Stable across catalogues, so the row is not rebuilt when the titles beneath it change. */
private const val CONTINUE_ROW_KEY = "__continue__"
