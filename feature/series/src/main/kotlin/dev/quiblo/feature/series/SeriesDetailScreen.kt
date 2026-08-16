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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.quiblo.core.data.MERGED_SEASON_NUMBER
import dev.quiblo.core.data.MetadataRefresh
import dev.quiblo.core.data.ScanRefusal
import dev.quiblo.core.data.SeriesPreference
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.Season
import dev.quiblo.core.model.SeriesDetails
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.designsystem.AutoDirection
import dev.quiblo.feature.browse.DetailOverlayActions
import dev.quiblo.feature.browse.TitleFacts
import dev.quiblo.feature.browse.runtimeLabel
import dev.quiblo.source.api.SourceError
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    channelId: Long,
    onBack: () -> Unit,
    /**
     * Plays one episode.
     *
     * The third argument is where to start: null for "use whatever this episode's own
     * resume point says", which is what tapping it in the list means, and an explicit value
     * for the two buttons that override it.
     */
    onEpisodeClick: (Episode, Channel, Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesDetailViewModel = koinViewModel(parameters = { parametersOf(channelId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The resume point is watched rather than re-read on returning to the foreground. A read on
    // resume raced the player's own write of the position it had just finished with, and lost it
    // often enough that backing out of a film offered "Play" for something four minutes in. See
    // `observeResumePosition`.

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
                    state = state,
                    onEpisodeClick = onEpisodeClick,
                    onRemoveFromHistory = viewModel::removeFromHistory,
                    onRefreshMetadata = viewModel::refreshMetadata,
                    onMerged = viewModel::setMerged,
                    onDescending = viewModel::setDescending,
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

/**
 * What to do with a series that has been started: continue it, restart it, or forget it.
 *
 * All three only appear together. A "start from beginning" beside nothing to resume is the
 * same button as "play", and a "remove from history" for a series with no history is a
 * control that can only ever do nothing.
 */
@Composable
private fun ResumeActions(
    episode: Episode,
    positionMillis: Long,
    firstEpisode: Episode?,
    onResume: () -> Unit,
    onStartFromBeginning: (Episode) -> Unit,
    onRemoveFromHistory: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Offered only when the provider actually listed an episode to go back to.
            firstEpisode?.let { first ->
                OutlinedButton(
                    onClick = { onStartFromBeginning(first) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Filled.Replay, contentDescription = null)
                    Text(
                        text = stringResource(R.string.series_start_over),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            TextButton(onClick = onRemoveFromHistory, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Filled.DeleteOutline, contentDescription = null)
                Text(
                    text = stringResource(R.string.series_remove_history),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
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
    state: SeriesDetailUiState.Success,
    onEpisodeClick: (Episode, Channel, Long?) -> Unit,
    onRemoveFromHistory: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onMerged: (Boolean) -> Unit,
    onDescending: (Boolean) -> Unit,
) {
    val channel = state.channel
    val details = state.details
    // Keyed on the arrangement: merging collapses several seasons into one, so an index of 4
    // would point at nothing the moment the switch is flipped.
    var selectedSeasonIndex by remember(state.preference) { mutableIntStateOf(0) }
    val defaultSeasonName = stringResource(R.string.series_season_label, 1)
    val seasons = state.seasons.ifEmpty {
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
            SeriesHeader(channel = channel, details = details, metadata = state.metadata)
        }

        item {
            RefreshMetadata(
                canRefresh = state.canRefreshMetadata,
                isWorking = state.isEnriching,
                result = state.refreshResult,
                onRefresh = onRefreshMetadata,
            )
        }

        // Only when there is something to resume. An always-present button that sometimes
        // starts episode one is worse than no button, because it stops meaning anything.
        state.resumeEpisode?.let { resumeEpisode ->
            item {
                ResumeActions(
                    episode = resumeEpisode,
                    positionMillis = state.resumePositionMillis,
                    firstEpisode = state.firstEpisode,
                    onResume = { onEpisodeClick(resumeEpisode, channel, state.resumePositionMillis) },
                    onStartFromBeginning = { first -> onEpisodeClick(first, channel, 0L) },
                    onRemoveFromHistory = onRemoveFromHistory,
                )
            }
        }

        item {
            SeriesArrangement(
                preference = state.preference,
                onMerged = onMerged,
                onDescending = onDescending,
            )
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
                            label = { Text(season.displayName()) },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = currentSeason.displayName(),
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
                    // Merging puts episode 3 of season 2 beside episode 3 of season 5, so the
                    // season has to survive on the row or the list is ambiguous at exactly the
                    // point it is most useful.
                    showSeason = state.preference.isMerged,
                    // Null, not zero: an episode tapped in the list starts wherever it was
                    // left, which is what its own resume point already records.
                    onClick = { onEpisodeClick(episode, channel, null) },
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
            val title = details.title.ifBlank { channel.name }
            AutoDirection(title) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Drawn even with no metadata service configured, because the year is usually the
            // panel's own and a screen that hides it until somebody pastes an API key is hiding
            // a fact it already has.
            TitleFacts(
                metadata = metadata ?: TitleMetadata(),
                modifier = Modifier.padding(top = 6.dp),
                year = details.releaseYear ?: metadata?.releaseYear,
            )

            // The panel's own synopsis is preferred over TMDB's: it describes the thing the
            // user is about to play, in the language their provider serves it in.
            val overview = details.overview?.takeIf { it.isNotBlank() } ?: metadata?.overview
            if (!overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AutoDirection(overview) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeItem(episode: Episode, showSeason: Boolean, onClick: () -> Unit) {
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
                    // The season is carried only when the list is merged. Adding it always
                    // would repeat, on every one of a season's rows, the number written at the
                    // top of that same list.
                    text = if (showSeason) {
                        stringResource(
                            R.string.series_episode_label_merged,
                            episode.seasonNumber,
                            episode.episodeNumber,
                            episode.title,
                        )
                    } else {
                        stringResource(
                            R.string.series_episode_label,
                            episode.episodeNumber,
                            episode.title,
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Under the title rather than beside it: a phone row is narrow, and a length
                // competing for the same line would shorten the title on every episode to make
                // room for a fact most panels do not even send.
                runtimeLabel(episode.durationSeconds)?.let { length ->
                    Text(
                        text = length,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * "Refresh information", and what happened when it was pressed.
 *
 * The film screen's twin, and deliberately a copy rather than a shared composable: the two
 * screens keep their own strings so a series can say something a film should not, and one
 * composable in a third module for eleven lines of column would be the smallest possible module.
 * If a third screen ever wants it, that is the moment to move it.
 *
 * **Absent rather than disabled without a key** — `AC-META-01` means it cannot work, and a
 * greyed-out button still invites the press that does nothing.
 */
@Composable
private fun RefreshMetadata(
    canRefresh: Boolean,
    isWorking: Boolean,
    result: MetadataRefresh?,
    onRefresh: () -> Unit,
) {
    if (!canRefresh) return

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = onRefresh, enabled = !isWorking) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Text(
                text = stringResource(
                    if (isWorking) R.string.series_refresh_working else R.string.series_refresh_metadata,
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        val message = when (result) {
            is MetadataRefresh.Updated -> R.string.series_refresh_updated
            MetadataRefresh.NoMatch -> R.string.series_refresh_no_match
            is MetadataRefresh.Refused -> when (result.reason) {
                ScanRefusal.RATE_LIMITED -> R.string.series_refresh_rate_limited
                ScanRefusal.KEY_REJECTED -> R.string.series_refresh_key_rejected
                ScanRefusal.UNAVAILABLE -> R.string.series_refresh_unavailable
            }
            null -> null
        }

        message?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
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

/**
 * The two controls that decide how a long series is read.
 *
 * `INC-F6`. On the screen itself rather than behind a menu, because the series this matters for
 * are the ones with a thousand episodes and the viewer's problem is that the newest is a thousand
 * rows away — a fix behind two taps is a fix they will not find.
 *
 * Both are remembered per series, per profile, so this is the last time they touch them.
 */
@Composable
private fun SeriesArrangement(
    preference: SeriesPreference,
    onMerged: (Boolean) -> Unit,
    onDescending: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FilterChip(
            selected = preference.isMerged,
            onClick = { onMerged(!preference.isMerged) },
            label = { Text(stringResource(R.string.series_merge_seasons)) },
        )
        FilterChip(
            selected = preference.isDescending,
            onClick = { onDescending(!preference.isDescending) },
            label = { Text(stringResource(R.string.series_newest_first)) },
        )
    }
}

/**
 * What to call a season on screen.
 *
 * The merged one carries no name — `:core:data` holds no display strings — so it is named here,
 * where the language is known.
 */
@Composable
private fun Season.displayName(): String =
    if (seasonNumber == MERGED_SEASON_NUMBER && name.isEmpty()) {
        stringResource(R.string.series_all_episodes)
    } else {
        name
    }
