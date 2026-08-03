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

package dev.quiblo.tv.ui.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Programme
import dev.quiblo.feature.browse.BrowseViewModel
import dev.quiblo.feature.browse.di.browseParams
import dev.quiblo.tv.R
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * The Live channel list.
 *
 * A conventional television channel list rather than a poster grid: a channel's artwork is
 * a small wide logo, and a grid of those is unreadable from across a room.
 */
@Composable
fun TvLiveScreen(
    /** Hands over the whole list, not just the item: the player needs it to zap. */
    onPlay: (List<Channel>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = koinViewModel(
        key = "tv-live",
        parameters = { browseParams(MediaKind.LIVE, favoritesOnly = false) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    /**
     * The row the remote is currently resting on.
     *
     * Guide data is fetched from here rather than from "row became visible", which is what
     * the phone app uses. A held-down D-pad flies through a list far faster than a finger
     * ever scrolls, and fetching per row passed would issue a burst of requests per second
     * — the exact behaviour that got this project's account blocked once already. Only the
     * row focus settles on is worth a request.
     */
    var focusedChannel: Channel? by remember { mutableStateOf(null) }

    LaunchedEffect(focusedChannel) {
        val channel = focusedChannel ?: return@LaunchedEffect
        delay(FOCUS_SETTLE_MILLIS)
        viewModel.onRowVisible(channel)
    }

    when {
        state.isLoading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }

        state.items.isEmpty() -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.tv_no_channels),
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        else -> LazyColumn(
            state = rememberLazyListState(),
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(items = state.items, key = { _, item -> item.id }) { index, channel ->
                ChannelRow(
                    // The provider's own ordering is the channel numbering a viewer knows;
                    // the panel's `num` field is not stored, so position stands in for it.
                    number = index + 1,
                    channel = channel,
                    nowPlaying = state.nowPlaying[channel.stableKey],
                    onFocused = { focusedChannel = channel },
                    onClick = { onPlay(state.items, index) },
                )
            }
        }
    }
}

/**
 * One channel.
 *
 * Focus is the only cue a viewer has about where they are, so it is loud: a lifted
 * background, a border, and full-strength text against a dimmed rest of the list.
 */
@Composable
private fun ChannelRow(
    number: Int,
    channel: Channel,
    nowPlaying: Programme?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else IDLE_ALPHA,
        label = "rowAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            // clickable rather than focusable: it is focusable too, and it maps the D-pad
            // centre and Enter onto onClick, which is how a remote "presses" a row.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = number.toString(),
            color = Color.White.copy(alpha = contentAlpha * 0.75f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(56.dp),
        )

        ChannelLogo(logoUrl = channel.logoUrl, alpha = contentAlpha)

        Text(
            text = channel.name,
            color = Color.White.copy(alpha = contentAlpha),
            fontSize = 18.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(CHANNEL_NAME_WIDTH),
        )

        NowPlaying(
            programme = nowPlaying,
            alpha = contentAlpha,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChannelLogo(logoUrl: String?, alpha: Float) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 40.dp)
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNullOrBlank()) {
            LogoPlaceholder(alpha)
        } else {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loading = { LogoPlaceholder(alpha) },
                error = { LogoPlaceholder(alpha) },
            )
        }
    }
}

@Composable
private fun LogoPlaceholder(alpha: Float) {
    Icon(
        imageVector = Icons.Filled.LiveTv,
        contentDescription = null,
        tint = Color.White.copy(alpha = alpha * 0.4f),
        modifier = Modifier.size(22.dp),
    )
}

/**
 * What is on now, with how far through it is.
 *
 * Silent when there is no guide rather than showing a placeholder — an M3U source has no
 * programme data at all, and an empty row of dashes is worse than nothing (AC-EPG-04).
 */
@Composable
private fun NowPlaying(programme: Programme?, alpha: Float, modifier: Modifier = Modifier) {
    if (programme == null) {
        Box(modifier = modifier)
        return
    }

    val now = System.currentTimeMillis()
    val span = (programme.endEpochMillis - programme.startEpochMillis).coerceAtLeast(1L)
    val progress = ((now - programme.startEpochMillis).toFloat() / span).coerceIn(0f, 1f)

    Column(modifier = modifier) {
        Text(
            text = programme.title,
            color = Color.White.copy(alpha = alpha * 0.85f),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth(PROGRESS_WIDTH_FRACTION)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = alpha * 0.20f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(Color.White.copy(alpha = alpha * 0.75f)),
            )
        }
    }
}

/**
 * How long focus must rest on a row before its guide is fetched.
 *
 * Long enough that scrolling past a row costs nothing, short enough that stopping on one
 * feels immediate.
 */
private const val FOCUS_SETTLE_MILLIS = 450L
private const val IDLE_ALPHA = 0.6f
private const val PROGRESS_WIDTH_FRACTION = 0.5f
private val CHANNEL_NAME_WIDTH = 320.dp
