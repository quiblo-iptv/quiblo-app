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

package dev.quiblo.feature.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.model.HistoryEntry

/**
 * "Continue watching": everything started and not finished, newest first.
 *
 * A horizontal strip above the catalogue rather than a section within it, because it is a
 * different kind of list — short, ordered by when rather than by name, and the one thing a
 * returning viewer is most likely to be looking for. A grid cell that scrolled away with
 * the alphabet would defeat the point of having it.
 *
 * A series appears once, at the episode last watched, with its season and number beneath
 * the series' name. Six tiles for six episodes of one programme is a list of one thing.
 */
@Composable
internal fun ContinueWatchingRow(
    entries: List<HistoryEntry>,
    /** Artwork found for entries whose provider supplied none, keyed by title identity. */
    posters: Map<String, String>,
    onClick: (HistoryEntry) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.browse_continue_watching),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = entries, key = { it.stableKey }) { entry ->
                ContinueWatchingCard(
                    entry = entry,
                    // The provider's own artwork first. The metadata service fills the
                    // gap only when there is one — see [BrowseUiState.posters].
                    artworkUrl = entry.artworkUrl?.takeIf { it.isNotBlank() } ?: posters[entry.titleKey],
                    onClick = { onClick(entry) },
                    onRemove = { onRemove(entry) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingCard(
    entry: HistoryEntry,
    artworkUrl: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT_RATIO)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (artworkUrl.isNullOrBlank()) {
                Placeholder()
            } else {
                SubcomposeAsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { Placeholder() },
                    error = { Placeholder() },
                )
            }

            // The play affordance sits on the artwork rather than beside the title: the
            // whole tile is the button, and the icon is what says so.
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
            )

            // Drawn only when the player actually reported a length. A bar pinned at zero
            // for every item whose duration was never read says something untrue.
            entry.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browse_history_remove)) },
                onClick = {
                    menuExpanded = false
                    onRemove()
                },
            )
        }

        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        // Only for an episode, and only when the provider numbered it. A film has no second
        // line, and an unnumbered episode gets none rather than "S0 E0".
        val season = entry.seasonNumber
        val episode = entry.episodeNumber
        if (season != null && episode != null) {
            Text(
                text = stringResource(R.string.browse_continue_episode, season, episode),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Placeholder() {
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
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Narrower than a grid tile, so the row reads as a strip rather than as another grid. */
private val CARD_WIDTH = 104.dp
private const val POSTER_ASPECT_RATIO = 2f / 3f
