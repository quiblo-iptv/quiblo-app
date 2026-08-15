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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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

    TvSearchPanel(
        state = state,
        onOpen = onOpen,
        onQueryChange = viewModel::search,
        onSelectGenre = viewModel::selectGenre,
        onToggleIncludeHidden = viewModel::setIncludeHidden,
        onToggleAdvanced = viewModel::setAdvanced,
        onClear = viewModel::clear,
        onResultVisible = viewModel::onResultVisible,
        modifier = modifier,
    )
}

/**
 * The screen itself, with no ViewModel behind it.
 *
 * Split off so the resting shape can be **measured** rather than looked at. Where the mark, the
 * name and the field come to rest is the whole of what this screen is at rest, it was wrong on a
 * real panel and right on a laptop, and the difference is arithmetic on a window height — which
 * is a fact a test can hold and an eye cannot. See `TvSearchRestingCentreTest`.
 *
 * The state above this line is the ViewModel's; everything below is the screen's own.
 */
@Composable
internal fun TvSearchPanel(
    state: SearchUiState,
    onOpen: (List<Channel>, Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onToggleIncludeHidden: (Boolean) -> Unit,
    onToggleAdvanced: (Boolean) -> Unit,
    onClear: () -> Unit,
    onResultVisible: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The ViewModel's, not this screen's. Opening the filters changes what is *queried* — live
    // channels are left out of an advanced search unless Settings keeps them — so the flag has
    // to be somewhere the query can see it, and two copies of it would be two answers.
    val isAdvanced = state.isAdvanced

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

    /*
     * Where the middle of the *panel* is, measured from the top of this column.
     *
     * **The screen's middle, not this container's.** The column starts under the tab bar and
     * inside the overscan padding, so centring the resting block within it put the mark, the name
     * and the field visibly high — the block was centred on a rectangle whose top edge is some
     * 90dp down the television. Right by the layout and wrong by the eye, which is the only
     * measurement that matters on something watched from three metres away.
     *
     * Both halves are measured, and both are read off the layout itself — the height of what is
     * being drawn into, and how far down it this column begins. So nothing here has to be told the
     * height of the tab bar, and nothing breaks when that changes. The bar has already grown once,
     * when the profile control joined the gear.
     *
     * **The root of the composition, not `LocalWindowInfo`.** The window is a larger and vaguer
     * thing than the surface: it was tried, and it reported 1264px for a panel drawn 1080px tall,
     * which centred the block below the screen's middle by exactly the difference. The root is the
     * rectangle the app is actually painting into, which is the one a viewer is looking at.
     *
     * **It is [OPTICAL_CENTRE] of the panel, not half of it**, and the difference is the point.
     * Geometrically centred, this block reads as sitting low, and it reads that way because it
     * is: the eye takes a bar across the top as the edge of the picture and centres what is
     * below it, while a screen has weight underneath — here a line of copy where results will
     * be. Every craft that sets type on a page moves the block up for the same reason. The
     * arithmetic is still measured; only the line it is measured against has moved.
     */
    val density = LocalDensity.current
    var centreLine by remember { mutableStateOf(0.dp) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val panelHeight = coordinates.findRootCoordinates().size.height
                centreLine = with(density) {
                    (panelHeight * OPTICAL_CENTRE - coordinates.positionInRoot().y).toDp()
                }
            },
    ) {
        SearchHeader(
            centreLine = centreLine,
            state = state,
            isResting = isResting,
            isAdvanced = isAdvanced,
            onQueryChange = onQueryChange,
            onSelectGenre = onSelectGenre,
            onToggleIncludeHidden = onToggleIncludeHidden,
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
                    onVisible = onResultVisible,
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
     * How far below the top of this column the middle of the television's panel lies.
     *
     * The block is centred on it, and it is measured rather than assumed — see the note on
     * `topSpace` for why every version of this that used a constant broke.
     */
    centreLine: Dp,
    state: SearchUiState,
    isResting: Boolean,
    isAdvanced: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onToggleIncludeHidden: (Boolean) -> Unit,
    onClear: () -> Unit,
    onToggleAdvanced: () -> Unit,
) {
    /*
     * The gap that centres the resting block.
     *
     * **Every number in it is measured. None is a constant, and that is the whole point.**
     * This was a fixed gap first, then a calculation off the window height and an assumed
     * padding — and each of those broke the moment something changed: the first every time the
     * mark or the name was resized, the second on any device whose chrome is not the one it was
     * written against. A layout that has to be told the size of the thing it is inside is a
     * layout that is wrong somewhere else.
     *
     * The block is the wordmark's height plus the field row measuring itself; half of it above
     * [centreLine] is where the gap ends. It cannot be wrong on a panel it has never seen.
     *
     * Clamped at zero, which is what stops this from being worse than the version it replaces.
     * On a short window the panel's middle can fall above this column entirely — [centreLine]
     * goes negative — and a negative gap would drag the field up under the tab bar. It rests at
     * the top instead, and the results below it keep the room they need.
     */
    val fieldDensity = LocalDensity.current
    var fieldRowHeight by remember { mutableStateOf(0.dp) }

    val topSpace by animateDpAsState(
        targetValue = if (isResting && fieldRowHeight > 0.dp && centreLine > 0.dp) {
            (centreLine - (WORDMARK_HEIGHT + fieldRowHeight) / 2).coerceAtLeast(0.dp)
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
            .height(wordmarkHeight)
            .testTag(WORDMARK_TAG),
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

    /*
     * The field, and Advanced — beside it while there is an answer on screen, under it at rest.
     *
     * **At rest all four things share one axis**: the mark, the name, the field and Advanced.
     * Advanced used to sit beside the field there too, balanced by an invisible copy of itself
     * on the left. That put the *field* on the middle of the panel and left the composition a
     * viewer actually sees — field plus one visible chip — hanging to the right of it. Balanced
     * by measurement and lopsided by eye, on the screen it was drawn for.
     *
     * Under the field it costs a row on a screen that has nothing else on it, and the way in is
     * still one press: down, out of a text field, which is the only direction a text field
     * leaves free. Beside the field is right once results are up, where a row is expensive and
     * the field has stopped being the whole screen.
     *
     * Both rows are measured together, because the gap above them is half of what they measure.
     */
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FIELD_ROW_TAG)
            .onSizeChanged { fieldRowHeight = with(fieldDensity) { it.height.toDp() } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isResting) Arrangement.Center else Arrangement.Start,
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
                // Right leaves the field for Advanced *when Advanced is beside it*. At rest it
                // is underneath, where Down finds it without the field having to spend a key.
                onExitRight = if (isResting) {
                    null
                } else {
                    { runCatching { advancedFocus.requestFocus() } }
                },
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

            if (!isResting) {
                Spacer(modifier = Modifier.width(COLUMN_GAP))
                TvChip(
                    label = stringResource(R.string.tv_search_advanced),
                    isSelected = isAdvanced,
                    onClick = onToggleAdvanced,
                    modifier = Modifier.focusRequester(advancedFocus),
                )

                // Only alongside the genre chips it explains, and only once there is room for
                // it — at rest it would be a paragraph beside a box nobody has used yet.
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
        }

        if (isResting) {
            Spacer(modifier = Modifier.height(RESTING_ADVANCED_GAP))
            TvChip(
                label = stringResource(R.string.tv_search_advanced),
                isSelected = isAdvanced,
                onClick = onToggleAdvanced,
                modifier = Modifier.focusRequester(advancedFocus),
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
            // First among the advanced controls, and before the genres, because it changes what
            // every one of them can return rather than narrowing what they already do.
            item(key = HIDDEN_KEY) {
                TvChip(
                    label = stringResource(R.string.tv_search_include_hidden),
                    isSelected = state.includeHidden,
                    onClick = { onToggleIncludeHidden(!state.includeHidden) },
                )
            }

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
 * The two edges of the resting block, named so a test can measure it.
 *
 * The block is the wordmark column and the field row together, and neither edge can be found
 * any other way: the mark is a `Canvas` and carries no semantics — correctly, it is a
 * decoration — and the row's bottom is the tallest of three controls rather than any one of
 * them. Naming the ends is what lets `TvSearchRestingCentreTest` assert where the block sits
 * without being told the height of the mark, the name or the field, none of which are the
 * thing under test and all of which get adjusted.
 */
internal const val WORDMARK_TAG = "search.wordmark"
internal const val FIELD_ROW_TAG = "search.fieldRow"

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

/** Between the resting field and Advanced under it. Close enough to read as one control's row. */
private val RESTING_ADVANCED_GAP = 20.dp

/**
 * Where the middle of the panel is *to a viewer*, as a fraction of its height.
 *
 * 0.46, not 0.50. A block on the true half-way line looks low on a television, and the two
 * reasons are both real: the bar across the top reads as the top edge of the picture, so the eye
 * centres what is under it; and there is always something below the block — the results, or the
 * line of copy standing in for them — while above it there is nothing.
 *
 * Roughly 43px up on a 1080 panel, and it scales with the screen rather than being a fixed
 * nudge, which is what stops it becoming wrong on the next size of television.
 */
private const val OPTICAL_CENTRE = 0.46f

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
private const val HIDDEN_KEY = "__hidden__"
