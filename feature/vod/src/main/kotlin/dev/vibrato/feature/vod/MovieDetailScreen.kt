/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.VodDetails
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One film: artwork, title, overview, and how to start watching it.
 *
 * Sits between the catalogue and the player so that resuming is a choice rather than an
 * assumption. Playback used to start from the saved position automatically, which is right
 * most of the time and impossible to override the rest of the time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    channelId: Long,
    onBack: () -> Unit,
    onPlay: (Channel, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MovieDetailViewModel = koinViewModel(parameters = { parametersOf(channelId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? MovieDetailUiState.Ready)?.channel?.name.orEmpty()
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.movie_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            MovieDetailUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            MovieDetailUiState.NotFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.movie_not_found))
            }

            is MovieDetailUiState.Ready -> MovieDetail(
                state = state,
                onPlay = onPlay,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun MovieDetail(
    state: MovieDetailUiState.Ready,
    onPlay: (Channel, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = state.details?.coverUrl?.takeIf { it.isNotBlank() } ?: state.channel.logoUrl

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Backdrop(artworkUrl = artwork, title = state.channel.name)

        Column(modifier = Modifier.padding(16.dp)) {
            state.details?.let { MetadataLine(it) }

            PlaybackButtons(state = state, onPlay = onPlay)

            val overview = state.details?.overview?.takeIf { it.isNotBlank() }
            if (overview != null) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 20.dp),
                )
            } else {
                // Said plainly rather than left blank: an empty space reads as a screen
                // that failed to load, and a playlist that carries no plot has not failed.
                Text(
                    text = stringResource(R.string.movie_no_overview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

/** Artwork with a gradient foot, so the title stays legible over arbitrary posters. */
@Composable
private fun Backdrop(artworkUrl: String?, title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        if (artworkUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        } else {
            SubcomposeAsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataLine(details: VodDetails) {
    val parts = listOfNotNull(
        details.releaseDate?.takeIf { it.isNotBlank() },
        details.genre?.takeIf { it.isNotBlank() },
        details.durationSeconds?.takeIf { it > 0 }?.let {
            stringResource(R.string.movie_minutes, it / SECONDS_PER_MINUTE)
        },
        details.rating?.takeIf { it.isNotBlank() },
    )
    if (parts.isEmpty()) return

    Text(
        text = parts.joinToString(SEPARATOR),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackButtons(
    state: MovieDetailUiState.Ready,
    onPlay: (Channel, Long) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.canResume) {
            // Resume leads, because a film with a saved position is one someone was part
            // way through and almost certainly means to continue.
            Button(onClick = { onPlay(state.channel, state.resumePositionMillis) }) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(R.string.movie_resume, state.resumePositionMillis.asClock()),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedButton(onClick = { onPlay(state.channel, 0L) }) {
                Icon(imageVector = Icons.Filled.Replay, contentDescription = null)
                Text(
                    text = stringResource(R.string.movie_start_over),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        } else {
            Button(onClick = { onPlay(state.channel, 0L) }) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(R.string.movie_play),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val SEPARATOR = "  ·  "
private const val MILLIS_PER_SECOND = 1000
private const val MINUTES_PER_HOUR = 60

/** h:mm, which is the resolution a resume label wants — seconds would be noise. */
private fun Long.asClock(): String {
    val totalMinutes = this / MILLIS_PER_SECOND / SECONDS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "%d:%02d".format(hours, minutes) else "%d min".format(minutes)
}
