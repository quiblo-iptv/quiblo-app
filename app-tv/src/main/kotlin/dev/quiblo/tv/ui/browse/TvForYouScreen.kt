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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.FeedRowId
import dev.quiblo.feature.browse.BrowseViewModel
import dev.quiblo.feature.browse.FeedRow
import dev.quiblo.feature.browse.FeedRowItem
import dev.quiblo.feature.browse.di.forYouParams
import dev.quiblo.tv.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Home (formerly For You): featured slider and several rows of content.
 *
 * **Hero Slider** shows randomized featured titles with TMDB metadata.
 *
 * **Recently Added** is what the provider put on the service this month, films and series merged
 * — split in two it would answer a question nobody asked, since a viewer wondering what is new is
 * not also deciding between two formats.
 *
 * **Now popular** is what the world is watching, of the things this provider actually carries. A
 * popular list on its own is not interesting; the intersection with a catalogue is.
 *
 * **You may like** is `013` INC-F2: content-based scoring over what this profile has watched,
 * with each tile naming the title that caused it. Not a model and not a daemon — the reasoning is
 * written out in that document and the arithmetic is in `Recommender`.
 *
 * **A row with nothing in it is not drawn.** No metadata key, an unscanned catalogue, a fresh
 * profile: each of those simply removes a row rather than leaving an empty shelf or a spinner
 * that never resolves. The screen is finished when its failure looks right.
 *
 * The rows are built from [TvCategoryList] rather than from `LazyRow`s of their own. That is
 * worth a line: the poster, the focus growth, the reserved space around it and the scroll
 * behaviour were all settled by measurement once (`TvBrowseScrollStabilityTest`, defect #008,
 * four wrong analyses), and a second copy of them here would be free to drift away from what was
 * proved. The two new shapes — a rank across the artwork, a caption under the title — are drawn
 * inside that same poster for the same reason.
 *
 * There is no continue-watching row and no category rail, both decided in the ViewModel.
 */
@Composable
fun TvForYouScreen(
    /** Hands over the whole list, not just the item: the player needs it to zap. */
    onPlay: (List<Channel>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BrowseViewModel = koinViewModel(
        // Not display text: a Koin scope key, never rendered.
        key = "tv-for-you",
        parameters = { forYouParams() },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Two different headings for the first row, and the difference is the point. A provider that
    // dates its catalogue gives a genuine "recently added"; a playlist that dates nothing gives
    // the end of its own list, which is the latest thing in it and is not a claim about when
    // anything arrived.
    val recentTitle = stringResource(
        if (state.orderedByDate) R.string.tv_row_recently_added else R.string.tv_row_latest_in_playlist,
    )
    val popularMoviesTitle = stringResource(R.string.tv_row_popular_movies)
    val popularSeriesTitle = stringResource(R.string.tv_row_popular_series)
    val suggestionsTitle = stringResource(R.string.tv_row_you_may_like)
    val becauseTemplate = stringResource(R.string.tv_because_you_watched, CAUSE_PLACEHOLDER)
    val notAvailable = stringResource(R.string.tv_not_available_yet)

    /*
     * Which unavailable tile has been pressed, if any.
     *
     * One at a time, and it is the tile's own key rather than a boolean, so pressing a second
     * unavailable tile moves the sentence rather than lighting two of them. It is deliberately
     * not remembered across a tab change: it answers a press, and a press is over when the
     * viewer leaves.
     */
    var pressedUnavailable by rememberSaveable { mutableStateOf<String?>(null) }

    /*
     * The three rows, and one flat list behind them.
     *
     * Flat and shared rather than one list per row, because that is what the player is handed
     * when a tile is opened: it zaps along the list it was given, and the honest list here is
     * everything on the screen in the order it is drawn. Each item carries its own index into it,
     * recorded while building rather than recovered with `indexOf` — the same reason [TvRowItem]
     * carries one at all.
     */
    val screen = remember(state.items, state.extraRows, recentTitle, suggestionsTitle, pressedUnavailable) {
        forYouRows(
            recent = state.items,
            extras = state.extraRows,
            headings = ForYouHeadings(
                recent = recentTitle,
                popularMovies = popularMoviesTitle,
                popularSeries = popularSeriesTitle,
                suggestions = suggestionsTitle,
                because = becauseTemplate,
                notAvailable = notAvailable,
            ),
            pressedUnavailable = pressedUnavailable,
        )
    }
    val rows = screen.first

    when {
        state.isLoading ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

        // Three different facts, and only the middle one is the viewer's to fix. Telling them
        // apart is the whole reason this screen has three branches rather than one "nothing
        // here": a playlist that carries no dates is not a fault, and neither is a panel that
        // has added nothing this month.
        !state.hasSource -> Message(stringResource(R.string.tv_no_source))

        rows.isEmpty() && state.heroItems.isEmpty() && state.history.isEmpty() -> Message(
            stringResource(
                if (state.orderedByDate) {
                    R.string.tv_recently_added_none
                } else {
                    R.string.tv_no_films_or_series
                },
            ),
        )

        else -> TvCategoryList(
            rows = rows,
            ratings = state.ratings,
            onVisible = viewModel::onPosterVisible,
            // A tile with nothing behind it answers instead of opening. No dialog: this app has
            // none, and a modal to say "not yet" would be the heaviest possible way to say it.
            onItemClick = { item ->
                if (item.tile.channel == null) {
                    pressedUnavailable = item.tile.key
                } else {
                    pressedUnavailable = null
                    onPlay(screen.second, item.flatIndex)
                }
            },
            modifier = modifier,
            posters = state.posters,
            // Recently Added mixes films and series, so its tiles have to say which they are.
            // The two popular rows are headed by their kind and say it once instead.
            showKindBadge = true,
            header = {
                TvHeroSlider(
                    items = state.heroItems,
                    onPlay = { channel -> onPlay(listOf(channel), 0) },
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            },
            continueWatching = {
                TvContinueWatchingRow(
                    entries = state.history,
                    posters = state.posters,
                    onClick = { entry ->
                        scope.launch {
                            viewModel.channelForHistory(entry)?.let { channel ->
                                onPlay(listOf(channel), 0)
                            }
                        }
                    },
                    onRemove = viewModel::removeFromHistory,
                )
            }
        )
    }
}

/**
 * The three rows, and one flat list behind them.
 *
 * Flat and shared rather than one list per row, because that is what the player is handed when a
 * tile is opened: it zaps along the list it was given, and the honest list here is everything on
 * the screen in the order it is drawn. Each item carries its own index into it, recorded while
 * building rather than recovered with `indexOf` — the same reason [TvRowItem] carries one at all.
 *
 * Outside the composable rather than inside it. It is the one piece of this screen with real
 * branching in it, and a screen function that also builds its own data is a screen function
 * nobody can read at a glance.
 */
private fun forYouRows(
    recent: List<Channel>,
    extras: List<FeedRow>,
    headings: ForYouHeadings,
    pressedUnavailable: String?,
): Pair<List<TvCategoryRow>, List<Channel>> {
    val flat = mutableListOf<Channel>()
    val extrasById = extras.associateBy { it.id }

    val rows = buildList {
        extrasById[FeedRowId.POPULAR_MOVIES]?.let { row ->
            add(
                TvCategoryRow(
                    title = headings.popularMovies,
                    items = row.items.map { item -> item.toTile(flat, headings, pressedUnavailable) },
                    style = TvRowStyle.RANKED,
                ),
            )
        }
        extrasById[FeedRowId.POPULAR_SERIES]?.let { row ->
            add(
                TvCategoryRow(
                    title = headings.popularSeries,
                    items = row.items.map { item -> item.toTile(flat, headings, pressedUnavailable) },
                    style = TvRowStyle.RANKED,
                ),
            )
        }
        if (recent.isNotEmpty()) {
            add(
                TvCategoryRow(
                    title = headings.recent,
                    items = recent.map { channel -> TvRowItem(channel, flat.size).also { flat += channel } },
                ),
            )
        }
        extrasById[FeedRowId.YOU_MAY_LIKE]?.let { row ->
            add(
                TvCategoryRow(
                    title = headings.suggestions,
                    items = row.items.map { item -> item.toTile(flat, headings, pressedUnavailable) },
                    style = TvRowStyle.POSTER,
                ),
            )
        }
    }
    return rows to flat.toList()
}

/**
 * One row item as a tile, adding its channel to the flat list only when it has one.
 *
 * The flat list is what the player is handed to zap along, so a title that cannot be played has
 * no place in it — and its `flatIndex` is therefore meaningless, which is why the screen checks
 * for a channel before using one.
 */
private fun FeedRowItem.toTile(
    flat: MutableList<Channel>,
    headings: ForYouHeadings,
    pressedUnavailable: String?,
): TvRowItem = when (this) {
    is FeedRowItem.Playable -> TvRowItem(
        channel = channel,
        flatIndex = flat.size,
        rank = rank,
        caption = caption?.let { headings.because.replace(CAUSE_PLACEHOLDER, it) },
    ).also { flat += channel }

    is FeedRowItem.Unavailable -> TvRowItem(
        tile = TvTile(key = key, name = title, kind = kind, artworkUrl = posterUrl, channel = null),
        flatIndex = -1,
        rank = rank,
        // The answer to a press, written into the line every tile of a ranked row already
        // reserves. Nothing appears or disappears when it is set, so nothing resizes.
        caption = headings.notAvailable.takeIf { key == pressedUnavailable },
    )
}

/**
 * The four strings this screen resolves, kept together.
 *
 * Four parameters that always travel as a set, and resolving them is the one thing the row
 * builder below cannot do for itself: `stringResource` is a composable and that function is
 * deliberately not one.
 */
private data class ForYouHeadings(
    val recent: String,
    val popularMovies: String,
    val popularSeries: String,
    val suggestions: String,
    /** The "Because you watched X" sentence, with [CAUSE_PLACEHOLDER] where the title goes. */
    val because: String,
    /** What a tile says when it is pressed and there is nothing behind it. */
    val notAvailable: String,
) {
    fun forRow(id: FeedRowId): String = when (id) {
        FeedRowId.POPULAR_MOVIES -> popularMovies
        FeedRowId.POPULAR_SERIES -> popularSeries
        FeedRowId.YOU_MAY_LIKE -> suggestions
    }
}

/** Stands in for the caused title inside the formatted string, so the row can fill it per tile. */
private const val CAUSE_PLACEHOLDER = "\u0000cause\u0000"

/** Whatever this screen has to say when it has no row to draw. */
@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
    }
}
