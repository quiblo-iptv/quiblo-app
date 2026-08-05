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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Channel
import dev.quiblo.feature.vod.MovieDetailUiState
import dev.quiblo.feature.vod.MovieDetailViewModel
import dev.quiblo.tv.R
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

    // Re-read the resume point every time this screen is shown.
    //
    // Not optional, and not merely an optimisation. The ViewModel is keyed per film and
    // lives as long as the activity, so coming back from the player reuses the instance that
    // loaded *before* anything had been watched — the position it holds is stale by exactly
    // the amount the viewer just watched. That is why a film played, backed out of and
    // reopened still offered Play rather than Resume.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshResumePosition()
        onPauseOrDispose {}
    }

    BackHandler(onBack = onBack)

    when (val current = state) {
        MovieDetailUiState.Loading -> DetailMessage(stringResource(R.string.tv_detail_loading))

        MovieDetailUiState.NotFound ->
            DetailMessage(stringResource(R.string.tv_detail_unavailable))

        is MovieDetailUiState.Ready -> Loaded(
            state = current,
            onPlay = onPlay,
            onToggleFavorite = viewModel::toggleFavorite,
            modifier = modifier,
        )
    }
}

@Composable
private fun Loaded(
    state: MovieDetailUiState.Ready,
    onPlay: (Long?) -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier,
) {
    val firstAction = remember { FocusRequester() }
    LaunchedEffect(state.channel.id) { runCatching { firstAction.requestFocus() } }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(DETAIL_COLUMN_GAP),
    ) {
        // The provider's own artwork first, and the metadata service's only where there is
        // none. A panel's cover is the cover for the thing it is serving.
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
                extra = state.details?.releaseDate?.takeIf { it.isNotBlank() },
            )

            DetailOverview(
                // The panel's own description wins: it describes the thing being served,
                // where the metadata service describes whatever title matched the name.
                overview = state.details?.overview?.takeIf { it.isNotBlank() }
                    ?: state.metadata?.overview,
                isEnriching = state.isEnriching,
                author = state.metadata?.author,
                authorLabel = state.metadata?.authorLabel?.name
                    ?.lowercase()?.replaceFirstChar(Char::uppercase),
                cast = state.metadata?.topCast.orEmpty(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(top = 24.dp)
                    .focusGroup(),
            ) {
                // Resume first when there is one, because it is what a returning viewer
                // came for. "Start from the beginning" stays available beside it rather
                // than being buried — a viewer who wants to rewatch should not have to
                // resume and then seek backwards.
                if (state.canResume) {
                    DetailButton(
                        label = stringResource(R.string.tv_detail_resume),
                        onClick = { onPlay(state.resumePositionMillis) },
                        isPrimary = true,
                        modifier = Modifier.focusRequester(firstAction),
                    )
                    DetailButton(
                        label = stringResource(R.string.tv_detail_from_start),
                        onClick = { onPlay(0L) },
                    )
                } else {
                    DetailButton(
                        label = stringResource(R.string.tv_detail_play),
                        onClick = { onPlay(null) },
                        isPrimary = true,
                        modifier = Modifier.focusRequester(firstAction),
                    )
                }

                DetailButton(
                    label = stringResource(
                        if (state.isFavorite) {
                            R.string.tv_detail_unfavourite
                        } else {
                            R.string.tv_detail_favourite
                        },
                    ),
                    onClick = onToggleFavorite,
                )
            }
        }
    }
}
