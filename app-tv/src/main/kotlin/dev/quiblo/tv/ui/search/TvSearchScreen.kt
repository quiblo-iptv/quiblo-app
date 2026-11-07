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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.feature.browse.SearchUiState
import dev.quiblo.feature.browse.SearchViewModel
import dev.quiblo.feature.browse.labelRes
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.browse.TvCategoryList
import dev.quiblo.tv.ui.browse.TvCategoryRow
import dev.quiblo.tv.ui.browse.TvRowItem
import dev.quiblo.tv.ui.browse.posterWidthFor
import dev.quiblo.tv.ui.common.AmbientRequest
import dev.quiblo.tv.ui.common.FIELD_CORNER
import dev.quiblo.tv.ui.common.LocalAmbientSink
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.ui.common.TvTextField
import dev.quiblo.tv.ui.common.TvToggle
import dev.quiblo.tv.ui.common.travellingGlow
import org.koin.androidx.compose.koinViewModel

/**
 * Search, across live channels, films and series at once.
 *
 * One destination that does not belong to a kind: a viewer who knows the title they want should
 * not have to know which of the three shelves their provider filed it on.
 *
 * **The screen has one shape.** The field is at the top left and the rest of the panel belongs to
 * the answer, whether or not anything has been asked yet. Advanced adds the genre filter and its
 * coverage figure, because they are a second question about a catalogue; whether to look at live
 * channels at all is on the field's row in both.
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

    /*
     * Puts out whatever the catalogue left lit, and asks for this screen's own light instead.
     *
     * The shell's backdrop is fed by focused posters, and nothing on this screen is one — so
     * before `022` arriving here left the colours of a film looked at two tabs ago sitting behind
     * an empty field. The backdrop's own crossfade turns that into a fade rather than a snap.
     *
     * `023` moved the drifting glow itself to the shell as well. It used to be a modifier on this
     * screen's own `Column`, which sits inside the shell's 48dp inset and below the tab bar — and
     * `drawBehind` clips to the node it is drawn on, so the light stopped dead at three padded
     * edges and under the bar while every other tab's ran to the corners.
     */
    val ambientSink = LocalAmbientSink.current
    LaunchedEffect(Unit) { ambientSink(AmbientRequest.Drift) }

    TvSearchPanel(
        state = state,
        onOpen = onOpen,
        onQueryChange = viewModel::search,
        onSelectGenre = viewModel::selectGenre,
        onSelectYear = viewModel::selectYear,
        onToggleIncludeHidden = viewModel::setIncludeHidden,
        onToggleIncludeLive = viewModel::setIncludeLive,
        onToggleAdvanced = viewModel::setAdvanced,
        onClear = viewModel::clear,
        onResultVisible = viewModel::onResultVisible,
        modifier = modifier,
    )
}

/**
 * The screen itself, with no ViewModel behind it.
 *
 * Split off so the screen's shape can be **measured** rather than looked at. See
 * `TvSearchTopLeftTest`.
 *
 * The state above this line is the ViewModel's; everything below is the screen's own.
 */
@Composable
internal fun TvSearchPanel(
    state: SearchUiState,
    onOpen: (List<Channel>, Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onSelectYear: (Int?) -> Unit,
    onToggleIncludeHidden: (Boolean) -> Unit,
    onToggleIncludeLive: (Boolean) -> Unit,
    onToggleAdvanced: (Boolean) -> Unit,
    onClear: () -> Unit,
    onResultVisible: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The ViewModel's, not this screen's. Opening the filters changes what is *queried* — live
    // channels are left out of an advanced search unless Settings keeps them — so the flag has
    // to be somewhere the query can see it, and two copies of it would be two answers.
    val isAdvanced = state.isAdvanced

    // The app's one vocabulary, from the module both apps share, rather than three strings
    // this screen owns. It owned them, and it called films "Films" while every other screen
    // called them "Movies" (#019) — which is the failure a shared list makes impossible.
    val liveTitle = stringResource(MediaKind.LIVE.labelRes)
    val filmsTitle = stringResource(MediaKind.VOD.labelRes)
    val seriesTitle = stringResource(MediaKind.SERIES.labelRes)

    val found = remember(state.live, state.movies, state.series, liveTitle, filmsTitle, seriesTitle) {
        searchRows(state, liveTitle, filmsTitle, seriesTitle)
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchHeader(
            state = state,
            isAdvanced = isAdvanced,
            onQueryChange = onQueryChange,
            onSelectGenre = onSelectGenre,
            onSelectYear = onSelectYear,
            onToggleIncludeHidden = onToggleIncludeHidden,
            onToggleIncludeLive = onToggleIncludeLive,
            onClear = onClear,
            onToggleAdvanced = { onToggleAdvanced(!isAdvanced) },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                !state.hasSource -> Message(stringResource(R.string.tv_no_source))

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

                /*
                 * Sized from the room actually left, rather than from the panel this was written
                 * against (`029` #4).
                 *
                 * **The heading is what a fixed size costs, and losing it is the report.** This
                 * screen keeps a field, an Advanced chip and a strip of genre chips above its
                 * results, so the row gets the remainder — which is 420dp on the geometry the
                 * tests use and less on a television that reports a shorter panel. When the row is
                 * taller than what it is given, the list scrolls the focused tile into view and
                 * takes the heading above it off the top: on a phone and on the emulator the
                 * headings are there, on the television they are not, which is exactly how it was
                 * reported.
                 *
                 * `BoxWithConstraints` because the answer is a measurement and there is no other
                 * honest way to get one. See `posterWidthFor` for the arithmetic.
                 */
                else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    TvCategoryList(
                        rows = found.rows,
                        ratings = state.ratings,
                        posters = state.posters,
                        onVisible = onResultVisible,
                        // Indexed against the flat list, so a live result opened from here still
                        // has every other result to zap through.
                        onItemClick = { item -> onOpen(found.flat, item.flatIndex) },
                        posterWidth = posterWidthFor(maxHeight),
                    )
                }
            }
        }
    }
}

/**
 * The field, its switches, and the strip of chips under them, at the top left of the screen.
 *
 * The chips sit under the field rather than beside it because a text field keeps left and right
 * for the cursor, and a control a remote cannot arrive at is a control that does not exist.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
internal fun ColumnScope.SearchHeader(
    state: SearchUiState,
    isAdvanced: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onSelectYear: (Int?) -> Unit,
    onToggleIncludeHidden: (Boolean) -> Unit,
    onToggleIncludeLive: (Boolean) -> Unit,
    onClear: () -> Unit,
    onToggleAdvanced: () -> Unit,
) {
    val fieldWidth by animateDpAsState(
        targetValue = if (isAdvanced) FIELD_WIDTH_ADVANCED else FIELD_WIDTH,
        label = "searchFieldWidth",
    )

    val advancedFocus = remember { FocusRequester() }
    var isFieldFocused by remember { mutableStateOf(false) }

    // The field and everything that decides what it looks at, on one row at the top left.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.tv_search_field),
            // The last field on the screen, so the keyboard's action key dismisses the
            // keyboard instead of hunting for a next one. Nothing is submitted by it: the
            // answer is already on screen behind the keyboard by then.
            isLast = true,
            onExitRight = { runCatching { advancedFocus.requestFocus() } },
            modifier = Modifier
                .width(fieldWidth)
                .onFocusChanged { isFieldFocused = it.isFocused }
                // Only while the remote is somewhere else. Two moving highlights on a
                // television is one too many, and the focus ring is the one that must win.
                .travellingGlow(
                    isActive = !isFieldFocused,
                    cornerRadius = FIELD_CORNER,
                ),
        )

        Spacer(modifier = Modifier.width(COLUMN_GAP_COMPACT))
        TvChip(
            label = stringResource(R.string.tv_search_advanced),
            isSelected = isAdvanced,
            onClick = onToggleAdvanced,
            modifier = Modifier.focusRequester(advancedFocus),
            horizontalPadding = 10.dp,
            verticalPadding = 6.dp,
        )
        Spacer(modifier = Modifier.width(COLUMN_GAP_COMPACT))

        // Hidden categories are a filter, so that switch waits for Advanced. Live channels are a
        // question about any search, so that one is always here.
        if (isAdvanced) {
            TvToggle(
                label = stringResource(R.string.tv_search_include_hidden),
                isOn = state.includeHidden,
                onToggle = onToggleIncludeHidden,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
            )
            Spacer(modifier = Modifier.width(TOGGLE_GAP_COMPACT))
        }
        TvToggle(
            label = stringResource(R.string.tv_search_include_live),
            isOn = state.includeLive,
            onToggle = onToggleIncludeLive,
            horizontalPadding = 8.dp,
            verticalPadding = 6.dp,
        )
    }

    /*
     * What the genre filter can and cannot describe, under the field and above the genres.
     *
     * `027` #6 again, and this half is the reason the switches had nowhere to go. The sentence sat
     * *beside* the field, taking the width the switches now use, to explain chips that were a row
     * below it and a screen's width to the left. It reads where it belongs: after the question,
     * before the answers it qualifies.
     *
     * **Hidden when results are on screen.** It takes vertical room that the results need, and once
     * a viewer has results they have already navigated past the filter.
     */
    if (isAdvanced && !state.isActive) {
        genreHint(state)?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }

    /*
     * The wait, drawn (#018).
     *
     * Building the genre index reads the whole metadata cache, and on a scanned catalogue that
     * is long enough to see. Until now nothing was shown while it happened: the filters opened
     * onto an empty strip with no chips and no coverage figure, which is indistinguishable
     * from a catalogue that has no genres in it.
     *
     * A thin bar directly under the field, exactly where the report asked for it, and gone the
     * moment the chips arrive. It is deliberately not a spinner in the middle of the screen —
     * the results below are still usable while this is happening, and a modal wait would take
     * them away for no reason.
     */
    if (isAdvanced && state.areGenresLoading) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(2.dp),
            color = Color.White.copy(alpha = 0.7f),
            trackColor = Color.White.copy(alpha = 0.15f),
        )
    }

    // Which filter the strip is listing. The screen's, not the view model's: it changes what is
    // drawn and never what is queried, and a chosen genre stays chosen while the years are shown.
    var strip by rememberSaveable { mutableStateOf(SearchStrip.GENRES) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(
            space = 10.dp,
            alignment = Alignment.Start,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CHIP_STRIP_PADDING)
            .focusGroup(),
    ) {
        // Only once there is something to clear. A remote walking a row of chips should not
        // have to pass a control that would do nothing.
        if (state.isActive) {
            item(key = CLEAR_KEY) {
                TvChip(
                    label = stringResource(R.string.tv_search_clear),
                    isSelected = false,
                    onClick = onClear,
                    horizontalPadding = 12.dp,
                    verticalPadding = 6.dp,
                )
            }
        }

        // Suggestions before the genres, because they answer the question being typed rather
        // than narrowing one already asked. They cost no query: they are the distinct titles
        // among results already fetched, which is how AC-TV-14's no-query-per-keystroke rule is
        // obeyed by construction rather than by care (INC-F1).
        items(items = state.suggestions, key = { "suggest-$it" }) { suggestion ->
            TvChip(
                label = suggestion,
                isSelected = false,
                // Completes the field rather than opening the title. A remote user who walked
                // here wants the shorter term, and opening a title from a strip that also holds
                // filters would make the strip mean two things.
                onClick = { onQueryChange(suggestion) },
                horizontalPadding = 12.dp,
                verticalPadding = 6.dp,
            )
        }

        if (isAdvanced) {
            /*
             * Which of the two filters this strip is listing.
             *
             * **Two head chips rather than two strips, because there is no room for a second
             * row.** `TvSearchResultsFitTest` measures what is above the results, and a focused
             * poster already reaches within a few pixels of the bottom edge of a 1080 panel;
             * another row of chips is the crop that test exists to catch. So the strip holds one
             * list at a time and these say which — a selector, not a switch: neither chip changes
             * meaning when it is pressed, and each says what it is filtering by when something is.
             */
            item(key = GENRES_KEY) {
                TvChip(
                    label = filterLabel(stringResource(R.string.tv_search_filter_genres), state.selectedGenre),
                    isSelected = strip == SearchStrip.GENRES,
                    onClick = { strip = SearchStrip.GENRES },
                    horizontalPadding = 12.dp,
                    verticalPadding = 6.dp,
                )
            }

            // Only when the cache knows of any. A control that lists nothing is the hollow shape
            // this project deletes rather than draws.
            if (state.years.isNotEmpty()) {
                item(key = YEARS_KEY) {
                    TvChip(
                        label = filterLabel(
                            stringResource(R.string.tv_search_filter_years),
                            state.selectedYear?.toString(),
                        ),
                        isSelected = strip == SearchStrip.YEARS,
                        onClick = { strip = SearchStrip.YEARS },
                        horizontalPadding = 12.dp,
                        verticalPadding = 6.dp,
                    )
                }
            }

            if (strip == SearchStrip.GENRES) {
                // Genres. The two switches that decide what a search *looks at* are up on the
                // field's row — see the note there for why they cannot live in this strip.
                items(items = state.genres, key = { "genre-$it" }) { genre ->
                    TvChip(
                        label = genre,
                        isSelected = genre == state.selectedGenre,
                        // Choosing, and only choosing (`027` #7). Pressing the chosen genre again
                        // used to clear it, so the one chip a viewer is most likely to walk onto
                        // was the one that undid their filter. Clear is at the head of this strip
                        // and is the only thing that means "no genre".
                        onClick = { onSelectGenre(genre) },
                        horizontalPadding = 12.dp,
                        verticalPadding = 6.dp,
                    )
                }
            } else {
                // Newest first, and only years the cache has a title for. Pressing the chosen
                // year again clears it — unlike the genres, nothing else in this strip means
                // "any year", and the chosen one is the chip the remote arrives on.
                items(items = state.years, key = { "year-$it" }) { year ->
                    TvChip(
                        label = year.toString(),
                        isSelected = year == state.selectedYear,
                        onClick = { onSelectYear(year) },
                        horizontalPadding = 12.dp,
                        verticalPadding = 6.dp,
                    )
                }
            }
        }
    }
}

/** Which list the one chip strip is holding. */
private enum class SearchStrip { GENRES, YEARS }

/** "Years", or "Years · 2019" once one is chosen. The strip cannot show both lists at once. */
@Composable
private fun filterLabel(name: String, chosen: String?): String =
    if (chosen == null) name else stringResource(R.string.tv_search_filter_chosen, name, chosen)

/**
 * What to say about the genre filter, if anything.
 *
 * A filter built from cached metadata describes only the part of a catalogue somebody has
 * looked at, and the three ways it can fall short are three different things to tell a
 * viewer: no key, no cache yet, or a cache that covers part of what they own. The scan in
 * Settings is what moves the third one to 100%.
 */
@Composable
private fun genreHint(state: SearchUiState): String? = when {
    // Said before "you have no genres", because they are different facts and only one of them
    // is the viewer's problem. An empty list while still reading looks like an answer (#018).
    state.areGenresLoading -> stringResource(R.string.tv_search_genres_loading)
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

/** Wide enough for a film title typed in full, and no wider — the hint shares the line. */
private val FIELD_WIDTH = 420.dp

/**
 * Narrower again once the switches are on the row beside it.
 *
 * Advanced puts three more controls on the field's line. Something has to give and it is the
 * field: a title is still typed in full at 380dp, and the alternative — the switches wrapping to
 * a line of their own — costs the results the height they need.
 */
private val FIELD_WIDTH_ADVANCED = 380.dp

/**
 * Air between the field and what is beside it.
 *
 * 28 rather than the settings screen's 40, because this row holds Advanced and two switches when
 * it is open and the same gap four times over pushed the last switch off the panel.
 */
private val COLUMN_GAP_COMPACT = 28.dp

/** Between the two switches. Nearer to each other than either is to Advanced, because they pair. */
private val TOGGLE_GAP_COMPACT = 16.dp

/**
 * Air above and below the chip strip, and it is load-bearing rather than taste.
 *
 * This screen is the only one that puts anything above a poster row, and the panel does not
 * have the height to spare: 420dp, less this header, has to hold a row heading and a poster
 * that **grows when it is focused**. It was 8dp above and 10dp below, and at that size a
 * focused result reached 852px on an 840px panel — the label went under the bottom edge, which
 * is #019's "half cropped when focused". Measured, not guessed: `TvSearchResultsFitTest`.
 *
 * The chips also read better nearer the field they belong to, but that is a bonus and not the
 * reason. If anything above the results grows again, that test is what will say so.
 *
 * **12 rather than 4 since the genre hint stopped being drawn over results.** That line was worth
 * a row of its own and it is now hidden the moment there are results to read, which is where the
 * air came from. `TvSearchResultsFitTest` is what says the eight dp were actually spare.
 */
private val CHIP_STRIP_PADDING = 12.dp

/** Stable, so the chip row is not rebuilt when the genres behind it change. */
private const val CLEAR_KEY = "__clear__"
private const val GENRES_KEY = "__genres__"
private const val YEARS_KEY = "__years__"
