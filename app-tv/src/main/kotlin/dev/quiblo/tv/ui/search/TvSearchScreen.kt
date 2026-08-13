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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import dev.quiblo.tv.ui.common.FIELD_CORNER
import dev.quiblo.tv.ui.common.QuibloMark
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.ui.common.TvTextField
import dev.quiblo.tv.ui.common.travellingGlow
import org.koin.androidx.compose.koinViewModel

/**
 * Search, across live channels, films and series at once.
 *
 * The first tab and the only one drawn as an icon, because it is the one destination that
 * does not belong to a kind: a viewer who knows the title they want should not have to know
 * which of the three shelves their provider filed it on.
 *
 * **The screen has a resting shape and a working one.** At rest it is the name and a field in
 * the middle of the panel and nothing else — the whole screen says "type something", which is
 * the only thing to do here. Asking a question, or opening Advanced, moves the field to the
 * top and gives the rest of the panel to the answer. Advanced is where the genre filter and
 * its coverage figure live, because they are a second question about a catalogue rather than
 * part of the first.
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

    // Survives a configuration change, so a viewer who opened the filters does not find them
    // shut again for a reason that has nothing to do with them.
    var isAdvanced by rememberSaveable { mutableStateOf(false) }

    // Nothing asked, and no filters opened. The one state where the panel belongs to the
    // field rather than to an answer.
    val isResting = !state.isActive && !isAdvanced

    // The app's one vocabulary, from the module both apps share, rather than three strings
    // this screen owns. It owned them, and it called films "Films" while every other screen
    // called them "Movies" (#019) — which is the failure a shared list makes impossible.
    val liveTitle = stringResource(MediaKind.LIVE.labelRes)
    val filmsTitle = stringResource(MediaKind.VOD.labelRes)
    val seriesTitle = stringResource(MediaKind.SERIES.labelRes)

    val found = remember(state.live, state.movies, state.series, liveTitle, filmsTitle, seriesTitle) {
        searchRows(state, liveTitle, filmsTitle, seriesTitle)
    }

    // How tall the area under the tab bar is, read off the column itself rather than by
    // wrapping it in something that measures it. See `SearchHeader`'s note on `topSpace`.
    val density = LocalDensity.current
    var contentHeight by remember { mutableStateOf(0.dp) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { contentHeight = with(density) { it.height.toDp() } },
    ) {
        SearchHeader(
            contentHeight = contentHeight,
            state = state,
            isResting = isResting,
            isAdvanced = isAdvanced,
            onQueryChange = viewModel::search,
            onSelectGenre = viewModel::selectGenre,
            onClear = viewModel::clear,
            onToggleAdvanced = { isAdvanced = !isAdvanced },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                !state.hasSource -> Message(stringResource(R.string.tv_no_source))

                // At rest the name and the field have already said what this screen is for.
                // A sentence under them repeating it is furniture.
                isResting -> Unit

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
 * The name, the field, and the row of chips under it.
 *
 * **One composition for both shapes, animated between them, rather than two layouts swapped.**
 * Swapping would rebuild the text field the moment a viewer typed their first character —
 * losing focus, and with it the on-screen keyboard, mid-word. Everything that moves here is a
 * size or an arrangement, so the field is the same node throughout and the remote never loses
 * its place.
 *
 * The chips sit under the field rather than beside it because a text field keeps left and
 * right for the cursor: the only way out is down, and down finds what is *below*. A control a
 * remote cannot arrive at is a control that does not exist, which this project has already
 * had to learn once and delete nine features over.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
internal fun ColumnScope.SearchHeader(
    /**
     * How tall the area under the tab bar is.
     *
     * Passed in because centring the resting block on the *screen* needs to know how much of
     * the screen sits above this area, and the difference between the window and this box is
     * exactly that. Centring inside the box alone leaves the block half a tab bar low.
     */
    contentHeight: Dp,
    state: SearchUiState,
    isResting: Boolean,
    isAdvanced: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onClear: () -> Unit,
    onToggleAdvanced: () -> Unit,
) {
    /*
     * The gap that puts the resting block on the middle of the *screen*.
     *
     * It was a fixed gap under the tab bar, which centres nothing — it puts the block wherever
     * that constant happens to land, and every change to the mark or the wordmark moved it
     * again. Twice.
     *
     * Nothing here is guessed. **The block's height** is the wordmark's, which is fixed, plus
     * the field row's, which measures itself — so this stays right through any future change to
     * either. **The chrome above this screen** is the difference between the window and the box
     * this is drawn in, less the padding underneath it.
     */
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val fieldDensity = LocalDensity.current
    var fieldRowHeight by remember { mutableStateOf(0.dp) }

    val topSpace by animateDpAsState(
        targetValue = if (isResting && fieldRowHeight > 0.dp && contentHeight > 0.dp) {
            val block = WORDMARK_HEIGHT + fieldRowHeight
            val above = screenHeight - contentHeight - CONTENT_BOTTOM_PADDING
            ((screenHeight - block) / 2 - above).coerceAtLeast(0.dp)
        } else {
            0.dp
        },
        label = "searchTopSpace",
    )
    val wordmarkHeight by animateDpAsState(
        targetValue = if (isResting) WORDMARK_HEIGHT else 0.dp,
        label = "searchWordmarkHeight",
    )
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (isResting) 1f else 0f,
        label = "searchWordmarkAlpha",
    )
    val fieldWidth by animateDpAsState(
        targetValue = if (isResting) RESTING_FIELD_WIDTH else FIELD_WIDTH,
        label = "searchFieldWidth",
    )

    Spacer(modifier = Modifier.height(topSpace))

    /*
     * The mark, then the name, then the field (`013` INC-E2).
     *
     * The app's own launcher icon rather than a drawing of one, so what a viewer sees at rest
     * is the thing they pressed on the home screen a second earlier. It shares the wordmark's
     * height and alpha animation rather than owning a second one — S10's note that this is an
     * element joining a transition that already exists is the whole reason it is cheap.
     */
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(wordmarkHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        QuibloMark(
            colour = Color.White.copy(alpha = wordmarkAlpha),
            modifier = Modifier.size(LOGO_SIZE),
        )
        Text(
            text = stringResource(R.string.tv_app_name),
            color = Color.White.copy(alpha = wordmarkAlpha),
            fontSize = WORDMARK_TEXT_SIZE,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
    }

    val advancedFocus = remember { FocusRequester() }
    var isFieldFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { fieldRowHeight = with(fieldDensity) { it.height.toDp() } },
        horizontalArrangement = if (isResting) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        /*
         * A mirror of Advanced, on the left, invisible.
         *
         * Centring a row centres the *row*, so putting Advanced beside the field pushed the
         * field left of the screen's middle while the mark and the name above it stayed on it —
         * three things that should share an axis and did not. Measured on a 50-inch panel,
         * which is where it is obvious and on a laptop is not.
         *
         * **The mirror is the same composable, not a copy of its text.** It was a bare `Text`
         * at first and the field still sat 30-odd pixels left of the mark above it: a chip is
         * its label *plus its padding*, so matching only the label matches only part of it.
         * Rendering the real control at zero alpha cannot be wrong about its own width.
         *
         * Invisible and unfocusable, so a remote walking the row never lands on it, and only
         * present while the row is centred at all.
         */
        if (isResting) {
            TvChip(
                label = stringResource(R.string.tv_search_advanced),
                isSelected = false,
                onClick = {},
                modifier = Modifier
                    .alpha(0f)
                    .focusProperties { canFocus = false },
            )
            Spacer(modifier = Modifier.width(COLUMN_GAP))
        }

        TvTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.tv_search_field),
            // The last field on the screen, so the keyboard's action key dismisses the
            // keyboard instead of hunting for a next one. Nothing is submitted by it: the
            // answer is already on screen behind the keyboard by then.
            isLast = true,
            // Right leaves the field for Advanced beside it. See the parameter's own note for
            // why this one field is allowed to spend the cursor keys and no other is.
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

        /*
         * Advanced, beside the field rather than under it (`013` INC-E1).
         *
         * S10 read this as "probably no", on the grounds that a control next to a text box
         * cannot be reached with a remote. That was right about the default behaviour and it is
         * what `onExitRight` above answers — the field hands Right over, so the way in is one
         * press from where the viewer is already typing, which is the same cost the chip row
         * had and one row cheaper on a panel with 444dp to spend.
         *
         * **It is no longer in the chip row.** Keeping both was meant to preserve the old way
         * in, and on the panel it read as exactly what it was: the same control twice, one
         * above the other, on a screen that has almost nothing else on it. One control, one
         * place.
         */
        Spacer(modifier = Modifier.width(COLUMN_GAP))
        TvChip(
            label = stringResource(R.string.tv_search_advanced),
            isSelected = isAdvanced,
            onClick = onToggleAdvanced,
            modifier = Modifier.focusRequester(advancedFocus),
        )

        // Only alongside the genre chips it explains, and only once there is room for it —
        // at rest it would be a paragraph beside a search box nobody has used yet.
        if (isAdvanced) {
            Spacer(modifier = Modifier.width(COLUMN_GAP))
            genreHint(state)?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(
            space = 10.dp,
            alignment = if (isResting) Alignment.CenterHorizontally else Alignment.Start,
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
            )
        }

        if (isAdvanced) {
            items(items = state.genres, key = { it }) { genre ->
                TvChip(
                    label = genre,
                    isSelected = genre == state.selectedGenre,
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

/**
 * How far down the panel the resting field sits.
 *
 * Enough to put the name and the field near the middle of the 540dp panel once overscan and
 * the tab bar are taken off it, without measuring: the three things above the results come to
 * about 150dp, and this is half of what is left.
 */
// Reduced when the mark joined the wordmark: the block above the field is twice the height it
// was, and the old spacing pushed the whole composition into the bottom half of the panel.
/** `TvApp` pads the content area by this much underneath. See the note on `topSpace`. */
private val CONTENT_BOTTOM_PADDING = 48.dp

/*
 * The mark, the name, and the room they need.
 *
 * All three were raised after seeing them on a 50-inch panel: the mark was smaller than the
 * word under it, which inverts the order they are meant to be read in, and the word was set at
 * a size chosen against a laptop. On a television the resting screen is three things in the
 * middle of a very large rectangle, so they can afford to be large.
 */
private val WORDMARK_HEIGHT = 128.dp
private val LOGO_SIZE = 72.dp
private val WORDMARK_TEXT_SIZE = 34.sp

/** Wider at rest, where it is the only thing on the panel and reads as an invitation. */
private val RESTING_FIELD_WIDTH = 560.dp

/** Wide enough for a film title typed in full, and no wider — the hint shares the line. */
private val FIELD_WIDTH = 420.dp

/** Air between the field and the sentence beside it, matching the settings screen. */
private val COLUMN_GAP = 40.dp

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
 */
private val CHIP_STRIP_PADDING = 4.dp

/** Stable, so the chip row is not rebuilt when the genres behind it change. */
private const val CLEAR_KEY = "__clear__"
