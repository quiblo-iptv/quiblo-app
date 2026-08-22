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

package dev.quiblo.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.data.MetadataRefresh
import dev.quiblo.core.data.ScanRefusal
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Opinion
import dev.quiblo.core.model.releaseYearIn
import dev.quiblo.feature.browse.runtimeLabel
import dev.quiblo.feature.browse.runtimeLabelFromMinutes
import dev.quiblo.feature.vod.MovieDetailUiState
import dev.quiblo.feature.vod.MovieDetailViewModel
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.ambientBackdrop
import dev.quiblo.tv.ui.common.rememberAmbient
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A film, before playing it.
 *
 * #005: the television used to send a film straight to the player the moment it was
 * pressed, so there was nowhere to read what it was, no score, and — the part that actually
 * costs a viewer something — no way to resume. A stored position existed the whole time and
 * nothing on this frontend offered it.
 *
 * Reuses `MovieDetailViewModel` unchanged, so a film means the same thing here as on the
 * phone and the resume point is the same record.
 */
@Composable
fun TvMovieScreen(
    channel: Channel,
    onPlay: (Long?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MovieDetailViewModel = koinViewModel(
        key = "tv-movie-${channel.id}",
        parameters = { parametersOf(channel.id) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val artworkUrl = (state as? MovieDetailUiState.Ready)?.let { ready ->
        ready.channel.logoUrl?.takeIf { it.isNotBlank() }
            ?: ready.details?.coverUrl
            ?: ready.metadata?.posterUrl
    } ?: channel.logoUrl
    val ambient = rememberAmbient(artworkUrl)

    // The resume point is watched rather than re-read on returning to the foreground. A read on
    // resume raced the player's own write of the position it had just finished with, and lost it
    // often enough that backing out of a film offered "Play" for something four minutes in. See
    // `observeResumePosition`.

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .ambientBackdrop(ambient),
    ) {
        when (val current = state) {
            MovieDetailUiState.Loading -> DetailMessage(stringResource(R.string.tv_detail_loading))

            MovieDetailUiState.NotFound ->
                DetailMessage(stringResource(R.string.tv_detail_unavailable))

            is MovieDetailUiState.Ready -> Loaded(
                state = current,
                onPlay = onPlay,
                onToggleFavorite = viewModel::toggleFavorite,
                onRemoveFromHistory = viewModel::removeFromHistory,
                onRefreshMetadata = viewModel::refreshMetadata,
                onRate = viewModel::rate,
                modifier = modifier,
            )
        }
    }
}

// Sixteen branches, and every one of them is a control that is present or absent for its own
// reason: resume, start again, favourite, two opinions, forget, refresh, and the two messages.
// Splitting the row into helpers to satisfy a count would scatter the ordering decision that
// AC-TV-01 is about — what a remote walks past to reach what it wants — across four functions.
@Suppress("CyclomaticComplexMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Loaded(
    state: MovieDetailUiState.Ready,
    onPlay: (Long?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onRate: (Opinion) -> Unit,
    modifier: Modifier,
) {
    val firstAction = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    /*
     * Focus the first action, and leave the screen at its top while doing it.
     *
     * The second half is #015. Requesting focus is not only a focus event: a focus target
     * inside a scrolling container asks to be brought into view, and the container obliges by
     * scrolling — before the viewer has pressed anything. The actions sit under a 390dp cover
     * on a panel with 444dp of usable height, so the screen opened with its artwork cropped
     * from the top and its title off screen altogether.
     *
     * Focus position and scroll position are separate facts and only the second one was
     * wrong, so the scroll is put back rather than the focus moved elsewhere. `scrollTo` and
     * the bring-into-view are both scroll operations on the same state at the same priority,
     * so the later one wins — which is why this runs after the request rather than before it.
     */
    LaunchedEffect(state.channel.id) {
        openDetailScreen(scrollState = scrollState, firstAction = firstAction)
    }

    /*
     * Scrolls, and centres what does not need to.
     *
     * Scrolling is for the same reason the series screen does it: 444dp of usable height is not
     * enough to guarantee a cover, a plot and a row of actions all fit, and a long description
     * has to stay reachable. A film has no episode list, so this is a single column rather than a
     * lazy one.
     *
     * **Centring is `023`'s complaint and it only applies to a film.** A series fills the screen
     * with its episodes; a film has nothing below its actions row, so top-aligning it left the
     * whole lower half of the panel empty with the content pressed against the ceiling. The
     * minimum height is the viewport's, so a short film's details sit in the middle of the screen
     * and a long one's start at the top and scroll exactly as before — one rule, not a branch on
     * how much text a provider happened to send.
     */
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .defaultMinSize(minHeight = viewportHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DETAIL_COLUMN_GAP)) {
                // The provider own artwork first, and the metadata service only where there is
                // none. A panel cover is the cover for the thing it is serving.
                DetailArtwork(
                    url = state.channel.logoUrl?.takeIf { it.isNotBlank() }
                        ?: state.details?.coverUrl
                        ?: state.metadata?.posterUrl,
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    DetailTitle(state.details?.title ?: state.channel.name)

                    DetailFacts(
                        rating = state.metadata?.rating,
                        ageRating = state.metadata?.ageRating,
                        genres = state.metadata.genresOrEmpty()
                            .ifEmpty { listOfNotNull(state.details?.genre?.takeIf { it.isNotBlank() }) },
                        // The year rather than the whole date the panel sends. "2021-10-22" is a
                        // record from a database; a viewer choosing a film wants the year, and the
                        // day of the month costs the line width that the length now uses.
                        year = releaseYearIn(state.details?.releaseDate) ?: state.metadata?.releaseYear,
                        // The panel's own length first, on the same rule as its artwork and its
                        // plot: it is timing the file it is about to serve, where the service is
                        // timing whichever cut it holds.
                        runtime = runtimeLabel(state.details?.durationSeconds)
                            ?: runtimeLabelFromMinutes(state.metadata?.runtimeMinutes),
                    )

                    DetailOverview(
                        // The panel own description wins: it describes the thing being served,
                        // where the metadata service describes whatever title matched the name.
                        overview = state.details?.overview?.takeIf { it.isNotBlank() }
                            ?: state.metadata?.overview,
                        isEnriching = state.isEnriching,
                        author = state.metadata?.author,
                        authorLabel = state.metadata?.authorLabel?.name
                            ?.lowercase()?.replaceFirstChar(Char::uppercase),
                        cast = state.metadata?.topCast.orEmpty(),
                    )

                    // Wraps rather than overflows — see the note on the series screen. With four
                    // controls the last one ran off the edge and was cut mid-word (#014), and a
                    // shorter label only postpones that until somebody translates it.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .focusGroup(),
                    ) {
                        val onScrollToTop = {
                            if (scrollState.value > 0) {
                                scope.launch { scrollState.animateScrollTo(0) }
                            }
                        }

                        // Resume first when there is one, because it is what a returning viewer
                        // came for. Start from the beginning stays beside it rather than being
                        // buried: rewatching should not mean resuming and then seeking back.
                        if (state.canResume) {
                            DetailButton(
                                icon = Icons.Filled.PlayArrow,
                                label = stringResource(R.string.tv_detail_resume),
                                onClick = { onPlay(state.resumePositionMillis) },
                                isPrimary = true,
                                modifier = Modifier.focusRequester(firstAction),
                                onFocus = onScrollToTop,
                            )
                            DetailButton(
                                icon = Icons.Filled.SkipPrevious,
                                label = stringResource(R.string.tv_detail_from_start),
                                contentDescription = stringResource(R.string.tv_detail_from_start),
                                onClick = { onPlay(0L) },
                                onFocus = onScrollToTop,
                            )
                        } else {
                            DetailButton(
                                icon = Icons.Filled.PlayArrow,
                                label = stringResource(R.string.tv_detail_play),
                                onClick = { onPlay(null) },
                                isPrimary = true,
                                modifier = Modifier.focusRequester(firstAction),
                                onFocus = onScrollToTop,
                            )
                        }

                        DetailButton(
                            icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(
                                if (state.isFavorite) R.string.tv_detail_unfavourite else R.string.tv_detail_favourite
                            ),
                            onClick = onToggleFavorite,
                            onFocus = onScrollToTop,
                        )

                        // What the viewer thought, which is the one signal the app cannot observe
                        // and has to be told. Both buttons are always here rather than appearing
                        // after playback: a viewer who has seen something elsewhere has an opinion
                        // about it too, and a control that comes and goes is one nobody finds.
                        MovieRatingButtons(
                            opinion = state.opinion,
                            onRate = onRate,
                            onScrollToTop = onScrollToTop,
                        )

                        // Only when there is a position to forget. The phone has offered this
                        // since its detail screens were built and the television never did — the
                        // parity gap `agile/004` generalised, found again (#014).
                        if (state.canResume) {
                            DetailButton(
                                icon = Icons.Filled.DeleteOutline,
                                label = stringResource(R.string.tv_detail_remove_history),
                                contentDescription = stringResource(R.string.tv_detail_remove_history),
                                onClick = onRemoveFromHistory,
                                onFocus = onScrollToTop,
                            )
                        }

                        // Last in the row on purpose. It is the least used control here and the
                        // one a viewer reaches for only when the screen already looks wrong, so
                        // it must not sit between Play and the favourite (AC-TV-01's ordering is
                        // about what a remote walks past to reach what it wants).
                        if (state.canRefreshMetadata) {
                            DetailButton(
                                icon = Icons.Filled.Refresh,
                                contentDescription = stringResource(
                                    if (state.isEnriching) {
                                        R.string.tv_detail_refresh_working
                                    } else {
                                        R.string.tv_detail_refresh
                                    },
                                ),
                                onClick = onRefreshMetadata,
                                onFocus = onScrollToTop,
                            )
                        }
                    }

                    state.refreshResult?.let { result ->
                        Text(
                            text = stringResource(result.messageRes()),
                            color = if (result is MetadataRefresh.Refused) {
                                Color(0xFFFF8A80)
                            } else {
                                Color.White.copy(alpha = 0.75f)
                            },
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieRatingButtons(
    opinion: Opinion,
    onRate: (Opinion) -> Unit,
    onScrollToTop: () -> Unit,
) {
    DetailButton(
        icon = if (opinion == Opinion.UP) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
        contentDescription = stringResource(
            if (opinion == Opinion.UP) {
                R.string.tv_detail_liked
            } else {
                R.string.tv_detail_like
            },
        ),
        onClick = { onRate(Opinion.UP) },
        onFocus = onScrollToTop,
    )

    DetailButton(
        icon = if (opinion == Opinion.DOWN) {
            Icons.Filled.ThumbDown
        } else {
            Icons.Outlined.ThumbDown
        },
        contentDescription = stringResource(
            if (opinion == Opinion.DOWN) {
                R.string.tv_detail_disliked
            } else {
                R.string.tv_detail_dislike
            },
        ),
        onClick = { onRate(Opinion.DOWN) },
        onFocus = onScrollToTop,
    )
}

/**
 * Which sentence a refresh outcome gets on the television.
 *
 * A function rather than a `when` inside the composable because the series screen needs the
 * same mapping, and two copies of a six-branch map is how one of them quietly stops matching.
 */
internal fun MetadataRefresh.messageRes(): Int = when (this) {
    is MetadataRefresh.Updated -> R.string.tv_detail_refresh_updated
    MetadataRefresh.NoMatch -> R.string.tv_detail_refresh_no_match
    is MetadataRefresh.Refused -> when (reason) {
        ScanRefusal.RATE_LIMITED -> R.string.tv_detail_refresh_rate_limited
        ScanRefusal.KEY_REJECTED -> R.string.tv_detail_refresh_key_rejected
        ScanRefusal.UNAVAILABLE -> R.string.tv_detail_refresh_unavailable
    }
}
