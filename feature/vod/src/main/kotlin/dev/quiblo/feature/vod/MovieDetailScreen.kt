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

package dev.quiblo.feature.vod

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.data.MetadataRefresh
import dev.quiblo.core.data.ScanRefusal
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Opinion
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.core.model.VodDetails
import dev.quiblo.core.model.releaseYearIn
import dev.quiblo.designsystem.AutoDirection
import dev.quiblo.feature.browse.DetailOverlayActions
import dev.quiblo.feature.browse.TitleFacts
import dev.quiblo.feature.browse.runtimeLabel
import dev.quiblo.feature.browse.runtimeLabelFromMinutes
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

    // The resume point is watched rather than re-read on returning to the foreground. A read on
    // resume raced the player's own write of the position it had just finished with, and lost it
    // often enough that backing out of a film offered "Play" for something four minutes in. See
    // `observeResumePosition`.

    // No app bar. The screen states the film's name in full, at a readable size, a few
    // pixels below where a title bar would have repeated it — see [DetailOverlayActions].
    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            MovieDetailUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            MovieDetailUiState.NotFound -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.movie_not_found))
            }

            is MovieDetailUiState.Ready -> MovieDetail(
                state = state,
                onPlay = onPlay,
                onRemoveFromHistory = viewModel::removeFromHistory,
                onRate = viewModel::rate,
                onRefreshMetadata = viewModel::refreshMetadata,
            )
        }

        DetailOverlayActions(
            // Before the film has loaded there is nothing to favourite, so the heart shows
            // its empty state and the toggle is a no-op the ViewModel already guards. Back
            // still works, which is the one control that must never depend on a load.
            isFavorite = (uiState as? MovieDetailUiState.Ready)?.isFavorite == true,
            onBack = onBack,
            onToggleFavorite = viewModel::toggleFavorite,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Two layouts for the same content.
 *
 * Portrait gets a wide backdrop; landscape puts a poster beside the text instead. A
 * full-width backdrop on a landscape tablet costs most of the visible height before a word
 * of the description, which is the shape a phone layout takes when it is simply stretched.
 */
@Composable
@Suppress("LongParameterList")
private fun MovieDetail(
    state: MovieDetailUiState.Ready,
    onPlay: (Channel, Long) -> Unit,
    onRemoveFromHistory: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onRate: (Opinion) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TMDB artwork first when it exists: it is a real poster at a known size, where a
    // provider's is whatever they happened to attach.
    val artwork = state.metadata?.posterUrl
        ?: state.details?.coverUrl?.takeIf { it.isNotBlank() }
        ?: state.channel.logoUrl
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                // Clears the floating back and favourite buttons, which in this layout have
                // no backdrop to sit on and would otherwise land on the poster.
                .padding(start = 16.dp, end = 16.dp, top = OVERLAY_ACTIONS_HEIGHT, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Poster(
                artworkUrl = artwork,
                modifier = Modifier
                    .width(POSTER_WIDTH)
                    .aspectRatio(POSTER_ASPECT_RATIO),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                AutoDirection(state.channel.name) {
                    Text(
                        text = state.channel.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Details(
                    state = state,
                    onPlay = onPlay,
                    onRemoveFromHistory = onRemoveFromHistory,
                    onRefreshMetadata = onRefreshMetadata,
                    onRate = onRate,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Backdrop(artworkUrl = artwork, title = state.channel.name)
        Details(
            state = state,
            onPlay = onPlay,
            onRemoveFromHistory = onRemoveFromHistory,
            onRefreshMetadata = onRefreshMetadata,
            onRate = onRate,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Metadata, buttons and overview — the part both orientations share. */
@Composable
@Suppress("LongParameterList")
private fun Details(
    state: MovieDetailUiState.Ready,
    onPlay: (Channel, Long) -> Unit,
    onRemoveFromHistory: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onRate: (Opinion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        state.details?.let { MetadataLine(it, state.metadata) }
        state.metadata?.let { TitleFacts(metadata = it, modifier = Modifier.padding(bottom = 12.dp)) }

        PlaybackButtons(
            state = state,
            onPlay = onPlay,
            onRemoveFromHistory = onRemoveFromHistory,
            onRate = onRate,
        )

        RefreshMetadata(
            canRefresh = state.canRefreshMetadata,
            isWorking = state.isEnriching,
            result = state.refreshResult,
            onRefresh = onRefreshMetadata,
        )

        // The provider's plot wins when it has one — it describes the file the user will
        // actually play. TMDB fills the very common case where the provider supplies none.
        val overview = state.details?.overview?.takeIf { it.isNotBlank() }
            ?: state.metadata?.overview?.takeIf { it.isNotBlank() }
        if (overview != null) {
            AutoDirection(overview) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        } else if (state.isEnriching) {
            // Not "there is no description" — we do not know that yet. Saying so before
            // the answer arrives is a wrong statement that corrects itself, which reads
            // worse than a wait.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.movie_overview_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        } else {
            // Said plainly rather than left blank: an empty space reads as a screen that
            // failed to load, and a playlist that carries no plot has not failed.
            Text(
                text = stringResource(R.string.movie_no_overview),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

/** Artwork with a gradient foot, so the title stays legible over arbitrary posters. */
@Composable
private fun Backdrop(artworkUrl: String?, title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BACKDROP_HEIGHT),
    ) {
        if (artworkUrl.isNullOrBlank()) {
            ArtworkPlaceholder()
        } else {
            SubcomposeAsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ArtworkPlaceholder() },
                error = { ArtworkPlaceholder() },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            SCRIM_START to Color.Transparent,
                            1f to Color.Black.copy(alpha = SCRIM_ALPHA),
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

/** The poster used by the landscape layout. */
@Composable
private fun Poster(artworkUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        if (artworkUrl.isNullOrBlank()) {
            ArtworkPlaceholder()
        } else {
            SubcomposeAsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ArtworkPlaceholder() },
                error = { ArtworkPlaceholder() },
            )
        }
    }
}

@Composable
private fun ArtworkPlaceholder() {
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
}

/**
 * Genres, certificate, score, director and cast, when the user has enabled enrichment.
 *
 * Every line is omitted rather than blanked when its field is missing. A film with no
 * credited director is not an error, and a row reading "Director: —" is worse than no row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataLine(details: VodDetails, metadata: TitleMetadata?) {
    val parts = listOfNotNull(
        // The year, not the whole date. A panel sends "2021-10-22"; nobody choosing a film
        // needs the day of the month, and the metadata service fills the gap for a playlist
        // that sends no date at all.
        (releaseYearIn(details.releaseDate) ?: metadata?.releaseYear)?.toString(),
        details.genre?.takeIf { it.isNotBlank() },
        // The panel's own length first, on the same rule as its artwork: it is timing the file
        // it is about to serve, where the service is timing whichever cut it holds.
        runtimeLabel(details.durationSeconds) ?: runtimeLabelFromMinutes(metadata?.runtimeMinutes),
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
    onRemoveFromHistory: () -> Unit,
    onRate: (Opinion) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // What the viewer thought, which is the one signal the app cannot observe and has to be
        // told. Always here rather than appearing after playback: somebody who saw a film
        // elsewhere has an opinion about it too, and a control that comes and goes is one nobody
        // finds. Pressing the filled one again takes the answer back.
        IconToggleButton(checked = state.opinion == Opinion.UP, onCheckedChange = { onRate(Opinion.UP) }) {
            Icon(
                imageVector = if (state.opinion == Opinion.UP) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = stringResource(R.string.movie_like),
            )
        }
        IconToggleButton(checked = state.opinion == Opinion.DOWN, onCheckedChange = { onRate(Opinion.DOWN) }) {
            Icon(
                imageVector = if (state.opinion == Opinion.DOWN) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = stringResource(R.string.movie_dislike),
            )
        }

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
            // Only alongside a resume point, because that is the only time there is
            // anything to remove. A permanently visible one would be a button that does
            // nothing on almost every film in the catalogue.
            TextButton(onClick = onRemoveFromHistory) {
                Icon(imageVector = Icons.Filled.DeleteOutline, contentDescription = null)
                Text(
                    text = stringResource(R.string.movie_remove_history),
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

private val POSTER_WIDTH = 200.dp
private val BACKDROP_HEIGHT = 240.dp
private const val SCRIM_START = 0.45f
private const val SCRIM_ALPHA = 0.85f
private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val SECONDS_PER_MINUTE = 60

/** Height of the floating action row, so a layout with no backdrop can clear it. */
private val OVERLAY_ACTIONS_HEIGHT = 56.dp

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

/**
 * "Refresh information", and what happened when it was pressed.
 *
 * **Absent rather than disabled without a key.** `AC-META-01` says nothing here issues a request
 * when the feature is off, so the control cannot work — and a greyed-out button still invites the
 * press that does nothing.
 *
 * The outcome is shown because the reason this exists is artwork that never arrived: if the
 * service has nothing, the screen looks identical afterwards, and a viewer with no message has
 * learned only that the button appears broken.
 */
@Composable
private fun RefreshMetadata(
    canRefresh: Boolean,
    isWorking: Boolean,
    result: MetadataRefresh?,
    onRefresh: () -> Unit,
) {
    if (!canRefresh) return

    Column(modifier = Modifier.padding(top = 12.dp)) {
        TextButton(onClick = onRefresh, enabled = !isWorking) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Text(
                text = stringResource(
                    if (isWorking) R.string.movie_refresh_working else R.string.movie_refresh_metadata,
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        val message = when (result) {
            is MetadataRefresh.Updated -> R.string.movie_refresh_updated
            MetadataRefresh.NoMatch -> R.string.movie_refresh_no_match
            is MetadataRefresh.Refused -> when (result.reason) {
                ScanRefusal.RATE_LIMITED -> R.string.movie_refresh_rate_limited
                ScanRefusal.KEY_REJECTED -> R.string.movie_refresh_key_rejected
                ScanRefusal.UNAVAILABLE -> R.string.movie_refresh_unavailable
            }
            null -> null
        }

        message?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                // A refusal changed nothing and is worth the error colour; the other two are
                // ordinary answers and would be shouting.
                color = if (result is MetadataRefresh.Refused) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
            )
        }
    }
}
