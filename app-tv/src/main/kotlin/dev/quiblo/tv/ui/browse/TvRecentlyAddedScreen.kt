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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Channel
import dev.quiblo.feature.browse.BrowseViewModel
import dev.quiblo.feature.browse.di.recentlyAddedParams
import dev.quiblo.tv.R
import org.koin.androidx.compose.koinViewModel

/**
 * What the provider added most recently: films and series in one row, newest first.
 *
 * **One row, and merged on purpose.** Split into "new films" and "new series" it would answer a
 * question nobody asked — a viewer wondering what is new on their service is not also deciding
 * between two formats — and on a 444dp panel the second row would sit below the fold anyway.
 *
 * The row is built from [TvCategoryList] rather than from a `LazyRow` of its own. That is worth
 * a line: the poster, the focus growth, the reserved space around it and the scroll behaviour
 * were all settled by measurement once (`TvBrowseScrollStabilityTest`, defect #008, four wrong
 * analyses), and a second copy of them here would be free to drift away from what was proved.
 *
 * There is no continue-watching row and no category rail. Both are deliberate and both are
 * decided in the ViewModel: this screen is about what the *provider* has done lately, not about
 * what the viewer was in the middle of.
 */
@Composable
fun TvRecentlyAddedScreen(
    /** Hands over the whole list, not just the item: the player needs it to zap. */
    onPlay: (List<Channel>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BrowseViewModel = koinViewModel(
        // Not display text: a Koin scope key, never rendered.
        key = "tv-recently-added",
        parameters = { recentlyAddedParams() },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Two different rows, and the heading is the difference. A provider that dates its catalogue
    // gives a genuine "recently added"; a playlist that dates nothing gives the end of its own
    // list, which is the latest thing in it and is not a claim about when anything arrived.
    val rowTitle = stringResource(
        if (state.orderedByDate) R.string.tv_row_recently_added else R.string.tv_row_latest_in_playlist,
    )

    // No grouping pass, unlike the catalogue screens: the query returns the row already
    // ordered and capped, so there is nothing to group and nothing worth moving off the main
    // thread. Building one TvRowItem per item is bounded by BrowseFeed.recentLimit.
    val rows = remember(state.items, rowTitle) {
        if (state.items.isEmpty()) {
            emptyList()
        } else {
            listOf(
                TvCategoryRow(
                    title = rowTitle,
                    items = state.items.mapIndexed { index, channel -> TvRowItem(channel, index) },
                ),
            )
        }
    }

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

        rows.isEmpty() -> Message(
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
            // The position is indexed against the flat list, so zapping from here walks the
            // newest titles rather than the catalogue behind them.
            onItemClick = { item -> onPlay(state.items, item.flatIndex) },
            modifier = modifier,
            posters = state.posters,
            // The one row in the app that mixes films and series, so the one row where a tile
            // has to say which it is.
            showKindBadge = true,
        )
    }
}

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
