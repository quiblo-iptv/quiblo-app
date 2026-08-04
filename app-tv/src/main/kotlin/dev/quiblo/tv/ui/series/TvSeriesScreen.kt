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

package dev.quiblo.tv.ui.series

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode
import dev.quiblo.feature.series.SeriesDetailUiState
import dev.quiblo.feature.series.SeriesDetailViewModel
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.detail.DETAIL_COLUMN_GAP
import dev.quiblo.tv.ui.detail.DetailArtwork
import dev.quiblo.tv.ui.detail.DetailButton
import dev.quiblo.tv.ui.detail.DetailFacts
import dev.quiblo.tv.ui.detail.DetailOverview
import dev.quiblo.tv.ui.detail.DetailTitle
import dev.quiblo.tv.ui.detail.genresOrEmpty
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A series, and the episode to play.
 *
 * This screen exists because a series row is not playable. Its `streamUrl` is not an
 * episode — episodes are fetched from the panel per series and are never rows in the
 * channel table — so the television handing a series straight to the player could only ever
 * produce the "plays nothing, shows a series as a channel" behaviour reported as #009.
 *
 * It carries everything a film's screen carries — artwork, plot, score, cast, favouriting
 * and a resume point — because #007's complaint was precisely that a series had none of
 * them. The presentation is shared with the film screen rather than written twice, so the
 * two cannot drift into showing different facts about the same kind of thing.
 */
@Composable
fun TvSeriesScreen(
    channel: Channel,
    onPlayEpisode: (Episode, Long?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SeriesDetailViewModel = koinViewModel(
        key = "tv-series-${channel.id}",
        parameters = { parametersOf(channel.id) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            is SeriesDetailUiState.Loading -> Centered { CircularProgressIndicator(color = Color.White) }

            is SeriesDetailUiState.Error -> Centered {
                Text(
                    text = stringResource(R.string.tv_series_unavailable),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            is SeriesDetailUiState.Success -> Loaded(
                state = current,
                onPlayEpisode = onPlayEpisode,
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }
}

@Composable
private fun Loaded(
    state: SeriesDetailUiState.Success,
    onPlayEpisode: (Episode, Long?) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    // Seasons are picked, episodes are walked. A panel can return thirty seasons and a
    // season can hold a hundred episodes, so a single flat list would put the remote a very
    // long way from anything.
    var selectedSeason by remember(state.details.seriesId) { mutableIntStateOf(0) }
    val seasons = state.details.seasons
    val episodes = seasons.getOrNull(selectedSeason)?.episodes.orEmpty()

    val episodeFocus = remember { FocusRequester() }
    LaunchedEffect(state.details.seriesId) { runCatching { episodeFocus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Everything a film's screen shows, because #007 asks for exactly that: a series
        // was missing the information, the score and the history a film had.
        Row(horizontalArrangement = Arrangement.spacedBy(DETAIL_COLUMN_GAP)) {
            DetailArtwork(
                url = state.channel.logoUrl?.takeIf { it.isNotBlank() }
                    ?: state.details.coverUrl
                    ?: state.metadata?.posterUrl,
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                DetailTitle(state.details.title)

                DetailFacts(
                    rating = state.metadata?.rating,
                    ageRating = state.metadata?.ageRating,
                    genres = state.metadata.genresOrEmpty(),
                    extra = pluralSeasons(seasons.size),
                )

                DetailOverview(
                    overview = state.details.overview?.takeIf { it.isNotBlank() }
                        ?: state.metadata?.overview,
                    // False, and not `metadata == null`. The panel's own details are
                    // already loaded by the time this composes, and the metadata service is
                    // only asked at all when a key is configured — so "no metadata" is the
                    // permanent answer for most users, and a spinner against it would never
                    // resolve. `SeriesDetailViewModel` exposes no in-flight flag to do
                    // better with; when it does, this is the line to change.
                    isEnriching = false,
                    author = state.metadata?.author,
                    authorLabel = state.metadata?.authorLabel?.name
                        ?.lowercase()?.replaceFirstChar(Char::uppercase),
                    cast = state.metadata?.topCast.orEmpty(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .focusGroup(),
                ) {
                    // Continue where the viewer left off, which for a series means the
                    // episode last touched rather than the furthest through.
                    state.resumeEpisode?.let { episode ->
                        DetailButton(
                            label = stringResource(
                                R.string.tv_series_resume,
                                episode.seasonNumber,
                                episode.episodeNumber,
                            ),
                            onClick = { onPlayEpisode(episode, state.resumePositionMillis) },
                            isPrimary = true,
                        )
                    }

                    state.firstEpisode?.let { first ->
                        DetailButton(
                            label = stringResource(
                                if (state.resumeEpisode == null) {
                                    R.string.tv_detail_play
                                } else {
                                    R.string.tv_detail_from_start
                                },
                            ),
                            onClick = { onPlayEpisode(first, null) },
                            isPrimary = state.resumeEpisode == null,
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

        if (seasons.isEmpty()) {
            Centered {
                Text(
                    text = stringResource(R.string.tv_series_no_episodes),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            return@Column
        }

        if (seasons.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                items(items = seasons, key = { it.seasonNumber }) { season ->
                    SeasonChip(
                        label = season.name.ifBlank {
                            stringResource(R.string.tv_series_season, season.seasonNumber)
                        },
                        isSelected = seasons.indexOf(season) == selectedSeason,
                        onClick = { selectedSeason = seasons.indexOf(season) },
                    )
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxSize()
                .focusRequester(episodeFocus)
                .focusGroup(),
        ) {
            items(items = episodes, key = { it.id }) { episode ->
                val isResume = episode.id == state.resumeEpisode?.id
                EpisodeRow(
                    episode = episode,
                    // Only the episode actually left part-way through resumes. Every other
                    // one starts at the beginning, which is what pressing it means.
                    onClick = {
                        onPlayEpisode(episode, state.resumePositionMillis.takeIf { isResume })
                    },
                )
            }
        }
    }
}

@Composable
private fun SeasonChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Text(
        text = label,
        color = Color.White.copy(alpha = if (isFocused || isSelected) 1f else 0.6f),
        fontSize = 15.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val alpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else IDLE_ALPHA,
        label = "episodeAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                R.string.tv_series_episode_number,
                episode.seasonNumber,
                episode.episodeNumber,
            ),
            color = Color.White.copy(alpha = alpha * 0.75f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(88.dp),
        )

        Text(
            text = episode.title,
            color = Color.White.copy(alpha = alpha),
            fontSize = 16.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private const val IDLE_ALPHA = 0.65f

/** "3 seasons", or nothing at all when the provider listed none. */
@Composable
private fun pluralSeasons(count: Int): String? =
    if (count <= 0) null else pluralStringResource(R.plurals.tv_series_seasons, count, count)
