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

package dev.quiblo.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.Season
import dev.quiblo.core.model.SeriesDetails
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.feature.browse.DetailOverlayActions
import dev.quiblo.feature.browse.TitleFacts
import dev.quiblo.source.api.SourceError
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    channelId: Long,
    onBack: () -> Unit,
    onEpisodeClick: (Episode, Channel) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesDetailViewModel = koinViewModel(parameters = { parametersOf(channelId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // No app bar, for the same reason as the film screen: the header states the series'
    // name in full a few pixels below where a title bar would have repeated it.
    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is SeriesDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is SeriesDetailUiState.Error -> {
                SeriesDetailError(
                    error = state.error,
                    onRetry = viewModel::loadDetails,
                )
            }

            is SeriesDetailUiState.Success -> {
                SeriesDetailContent(
                    channel = state.channel,
                    details = state.details,
                    metadata = state.metadata,
                    resumeEpisode = state.resumeEpisode,
                    resumePositionMillis = state.resumePositionMillis,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }

        DetailOverlayActions(
            isFavorite = (uiState as? SeriesDetailUiState.Success)?.isFavorite == true,
            onBack = onBack,
            onToggleFavorite = viewModel::toggleFavorite,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Continues the series from the episode last watched. */
@Composable
private fun ResumeButton(
    episode: Episode,
    positionMillis: Long,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
        Text(
            text = stringResource(
                R.string.series_resume,
                episode.seasonNumber,
                episode.episodeNumber,
                positionMillis.asResumeClock(),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Height of the floating action row, so content starts clear of it. */
private val OVERLAY_ACTIONS_HEIGHT = 56.dp

private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** h:mm, or minutes below an hour. Seconds are noise on a resume label. */
private fun Long.asResumeClock(): String {
    val totalMinutes = this / MILLIS_PER_SECOND / SECONDS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "%d:%02d".format(hours, minutes) else "%d min".format(minutes)
}

/**
 * The failure state.
 *
 * Carries the specific reason and a way to act on it. A retry matters here because the
 * most common failure — a provider throttling the account — clears on its own, and
 * without a button the only way back is to leave the screen and come in again.
 */
@Composable
private fun SeriesDetailError(
    error: SourceError,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            val arg = error.seriesMessageArg()
            Text(
                text = if (arg != null) {
                    stringResource(error.seriesMessageRes(), arg)
                } else {
                    stringResource(error.seriesMessageRes())
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.series_error_retry))
            }
        }
    }
}

@Composable
private fun SeriesDetailContent(
    channel: Channel,
    details: SeriesDetails,
    metadata: TitleMetadata?,
    resumeEpisode: Episode?,
    resumePositionMillis: Long,
    onEpisodeClick: (Episode, Channel) -> Unit,
) {
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val defaultSeasonName = stringResource(R.string.series_season_label, 1)
    val seasons = details.seasons.ifEmpty {
        listOf(Season(seasonNumber = 1, name = defaultSeasonName, episodes = emptyList()))
    }
    val currentSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.first()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        // Starts below the floating back and favourite buttons. The list scrolls under them
        // afterwards, which is the intended behaviour — they stay reachable at any depth in
        // a long episode list, where an app bar that scrolled away would not.
        contentPadding = PaddingValues(top = OVERLAY_ACTIONS_HEIGHT, bottom = 24.dp),
    ) {
        item {
            SeriesHeader(channel = channel, details = details, metadata = metadata)
        }

        // Only when there is something to resume. An always-present button that sometimes
        // starts episode one is worse than no button, because it stops meaning anything.
        if (resumeEpisode != null) {
            item {
                ResumeButton(
                    episode = resumeEpisode,
                    positionMillis = resumePositionMillis,
                    onClick = { onEpisodeClick(resumeEpisode, channel) },
                )
            }
        }

        if (seasons.size > 1) {
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(seasons.size) { index ->
                        val season = seasons[index]
                        FilterChip(
                            selected = index == selectedSeasonIndex,
                            onClick = { selectedSeasonIndex = index },
                            label = { Text(season.name) },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = currentSeason.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (currentSeason.episodes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.series_no_episodes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(currentSeason.episodes) { episode ->
                EpisodeItem(
                    episode = episode,
                    onClick = { onEpisodeClick(episode, channel) },
                )
            }
        }
    }
}

@Composable
private fun SeriesHeader(channel: Channel, details: SeriesDetails, metadata: TitleMetadata?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        // TMDB artwork first when there is any: it is a real poster at a known size, where
        // a provider's cover is whatever they happened to attach.
        val coverUrl = metadata?.posterUrl ?: details.coverUrl ?: channel.logoUrl
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl.isNullOrBlank()) {
                Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                SubcomposeAsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        Icon(
                            imageVector = Icons.Filled.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = details.title.ifBlank { channel.name },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            metadata?.let {
                TitleFacts(metadata = it, modifier = Modifier.padding(top = 6.dp))
            }

            // The panel's own synopsis is preferred over TMDB's: it describes the thing the
            // user is about to play, in the language their provider serves it in.
            val overview = details.overview?.takeIf { it.isNotBlank() } ?: metadata?.overview
            if (!overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EpisodeItem(episode: Episode, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.series_play),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.series_episode_label,
                        episode.episodeNumber,
                        episode.title,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
