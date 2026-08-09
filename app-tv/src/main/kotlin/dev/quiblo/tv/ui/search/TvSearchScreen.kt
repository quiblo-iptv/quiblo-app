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

package dev.quiblo.tv.ui.search

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Channel
import dev.quiblo.feature.browse.SearchUiState
import dev.quiblo.feature.browse.SearchViewModel
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.browse.TvCategoryList
import dev.quiblo.tv.ui.browse.TvCategoryRow
import dev.quiblo.tv.ui.browse.TvRowItem
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.ui.common.TvTextField
import org.koin.androidx.compose.koinViewModel

/**
 * Search, across live channels, films and series at once.
 *
 * The first tab and the only one drawn as an icon, because it is the one destination that
 * does not belong to a kind: a viewer who knows the title they want should not have to know
 * which of the three shelves their provider filed it on.
 *
 * The results reuse [TvCategoryList] whole rather than laying out rows of their own. That is
 * not only economy — that list is the composable measured by `TvBrowseScrollStabilityTest`,
 * and the modifier order inside its poster is the fix for #008. A second row implementation
 * here would be a second place for the shake to come back unmeasured.
 */
@Composable
fun TvSearchScreen(
    /** Hands over the whole result list, so the shell can decide per item what opening means. */
    onOpen: (List<Channel>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(key = "tv-search"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val liveTitle = stringResource(R.string.tv_search_live)
    val filmsTitle = stringResource(R.string.tv_search_movies)
    val seriesTitle = stringResource(R.string.tv_search_series)

    val found = remember(state.live, state.movies, state.series, liveTitle, filmsTitle, seriesTitle) {
        searchRows(state, liveTitle, filmsTitle, seriesTitle)
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            query = state.query,
            onQueryChange = viewModel::search,
            genres = state.genres,
            selectedGenre = state.selectedGenre,
            isActive = state.isActive,
            hint = genreHint(state),
            onSelectGenre = viewModel::selectGenre,
            onClear = viewModel::clear,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !state.hasSource -> Message(stringResource(R.string.tv_search_no_source))

                !state.isActive -> Message(stringResource(R.string.tv_search_prompt))

                // Only while there is nothing to look at. A term typed over an answer keeps
                // that answer on screen until the next one arrives, because replacing results
                // with a spinner on every keystroke is a screen that flashes rather than one
                // that responds.
                state.isSearching && !state.hasResults ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }

                !state.hasResults -> Message(stringResource(R.string.tv_search_empty))

                else -> TvCategoryList(
                    rows = found.rows,
                    ratings = state.ratings,
                    posters = state.posters,
                    onVisible = viewModel::onResultVisible,
                    // Indexed against the flat list, so a live result opened from here still
                    // has every other result to zap through.
                    onItemClick = { item -> onOpen(found.flat, item.flatIndex) },
                )
            }
        }
    }
}

/**
 * The question: a field, whatever the catalogue can be filtered by, and how far that goes.
 *
 * The genres sit on their own line under the field rather than beside it. Beside it they
 * could not be reached at all — a text field keeps left and right for the cursor, so a
 * viewer's only way out is down, and down finds what is *below* the field. A control a
 * remote cannot arrive at is a control that does not exist, which this project has already
 * had to learn once and delete nine features over.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    genres: List<String>,
    selectedGenre: String?,
    isActive: Boolean,
    hint: String?,
    onSelectGenre: (String?) -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvTextField(
                value = query,
                onValueChange = onQueryChange,
                label = stringResource(R.string.tv_search_field),
                // The last field on the screen, so the keyboard's action key dismisses the
                // keyboard instead of hunting for a next one. Nothing is submitted by it:
                // the answer is already on screen behind the keyboard by then.
                isLast = true,
                modifier = Modifier.width(FIELD_WIDTH),
            )

            Spacer(modifier = Modifier.width(COLUMN_GAP))

            // Beside the field rather than under the chips, where it would push the first row
            // of results off a 540dp panel.
            hint?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp)
                .focusGroup(),
        ) {
            // Only once there is something to clear. A remote walking a row of genres should
            // not have to pass a control that would do nothing.
            if (isActive) {
                item(key = CLEAR_KEY) {
                    TvChip(
                        label = stringResource(R.string.tv_search_clear),
                        isSelected = false,
                        onClick = onClear,
                    )
                }
            }

            items(items = genres, key = { it }) { genre ->
                TvChip(
                    label = genre,
                    isSelected = genre == selectedGenre,
                    // Pressing the selected genre again clears it, which is why there is no
                    // separate "all genres" chip to walk past.
                    onClick = { onSelectGenre(genre) },
                )
            }
        }
    }
}

/**
 * What to say about the genre filter, if anything.
 *
 * A filter built from cached metadata describes only the part of a catalogue somebody has
 * looked at, and the three ways it can fall short are three different things to tell a
 * viewer: no key, no cache yet, or a cache that covers part of what they own.
 */
@Composable
private fun genreHint(state: SearchUiState): String? = when {
    state.isMetadataDisabled -> stringResource(R.string.tv_search_genres_disabled)
    state.genres.isEmpty() -> stringResource(R.string.tv_search_genres_empty)
    else -> stringResource(R.string.tv_search_genres_coverage, state.coveragePercent)
}

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/** The results as rows, and the flat list those rows are indexed against. */
internal data class TvSearchRows(
    val flat: List<Channel>,
    val rows: List<TvCategoryRow>,
)

/**
 * Lays the three kinds out as three rows, in the order a viewer scans them.
 *
 * Live first because a channel is the one result that plays the moment it is pressed, then
 * films, then series. An empty kind produces no row at all rather than an empty one — a
 * heading with nothing under it reads as something that failed to load.
 *
 * The flat index is recorded while building, as the browse screen does it: the player is
 * handed a list and a position, and searching for the position afterwards is a scan of every
 * result per press.
 */
internal fun searchRows(
    state: SearchUiState,
    liveTitle: String,
    filmsTitle: String,
    seriesTitle: String,
): TvSearchRows {
    val kinds = listOf(liveTitle to state.live, filmsTitle to state.movies, seriesTitle to state.series)
    val flat = kinds.flatMap { (_, items) -> items }

    var flatIndex = 0
    val rows = kinds.mapNotNull { (title, items) ->
        if (items.isEmpty()) {
            null
        } else {
            TvCategoryRow(title, items.map { TvRowItem(it, flatIndex++) })
        }
    }

    return TvSearchRows(flat = flat, rows = rows)
}

/** Wide enough for a film title typed in full, and no wider — the genres share the line. */
private val FIELD_WIDTH = 420.dp

/** Air between the field and the sentence beside it, matching the settings screen. */
private val COLUMN_GAP = 40.dp

/** Stable, so the chip row is not rebuilt when the genres behind it change. */
private const val CLEAR_KEY = "__clear__"
