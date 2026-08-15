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

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import dev.quiblo.core.data.GuideOutcome
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
        parameters = { browseParams(MediaKind.LIVE) },
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

    /** The channel whose full listing is open, or null when the panel is shut (INC-F4). */
    var guideFor: Channel? by remember { mutableStateOf(null) }

    LaunchedEffect(focusedChannel) {
        val channel = focusedChannel ?: return@LaunchedEffect
        delay(FOCUS_SETTLE_MILLIS)
        viewModel.onRowVisible(channel)
    }

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Only when there is a choice to make. A source with one category, or an M3U with
            // none at all, gets the full width for its channels rather than a rail holding a
            // single row (#002).
            if (state.categories.size > 1) {
                TvCategoryRail(
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    onSelect = viewModel::selectCategory,
                )
            }

            // A fresh state per category, so the list starts at the top when the filter
            // changes. Carrying a scroll position into a different set of channels leaves the
            // viewer somewhere arbitrary in a list they have not seen — and the position is
            // meaningless anyway, since row 400 of one category is not row 400 of another.
            val listState = remember(state.selectedCategory) { LazyListState() }

            when {
                // "No playlist" and "a playlist with no live channels" are two different things
                // to be told, and only one of them is the viewer's to fix. Search has drawn the
                // distinction since it was written; this screen told everyone to import a
                // playlist they had never added, which reads as a failure rather than as a step
                // they have not taken yet.
                !state.hasSource -> Message(stringResource(R.string.tv_no_source))

                state.items.isEmpty() -> Message(stringResource(R.string.tv_no_channels))

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(items = state.items, key = { _, item -> item.id }) { index, channel ->
                        ChannelRow(
                            // The provider's own ordering is the channel numbering a viewer
                            // knows; the panel's `num` field is not stored, so position stands
                            // in for it.
                            number = index + 1,
                            channel = channel,
                            nowPlaying = state.nowPlaying[channel.stableKey],
                            onFocused = { focusedChannel = channel },
                            onClick = { onPlay(state.items, index) },
                            onLongClick = { guideFor = channel },
                        )
                    }
                }
            }
        }

        // Along the bottom, under the list rather than in place of it. A guide that is not
        // arriving does not stop anyone watching television, so it is a footnote and never a
        // screen of its own — and this app has no dialogs to put it in either.
        guideTrouble(state.guideOutcome)?.let { message ->
            Text(
                text = stringResource(message),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(top = 12.dp),
            )
        }

        // Over the list rather than instead of it: this app has no dialogs, and the
        // listing a viewer opened is easier to read with the channel it belongs to still
        // on screen behind it.
        guideFor?.let { channel ->
            TvGuidePanel(
                channel = channel,
                schedule = remember(channel.stableKey) { viewModel.scheduleFor(channel) },
                onOpen = { viewModel.requestFullGuide(channel) },
                onDismiss = { guideFor = null },
            )
        }
    }
}

/**
 * What to say about a guide that is not arriving, or null when there is nothing to say.
 *
 * **Only two of the five outcomes get a sentence, and that is the design.** `STORED` means the
 * guide works. `UNSUPPORTED` is every M3U playlist and is not news — a playlist has never
 * carried listings and telling somebody so on every visit is nagging, not information.
 * `FAILED` is a network that is already visibly failing at everything else.
 *
 * The two that are left are the two a viewer can act on: their provider is refusing this app,
 * or their provider has no listings for these channels. Those are different conversations to
 * have with a provider, and until now both looked exactly like the guide being broken.
 *
 * A separate function rather than a `when` inside the layout so the mapping can be read — and
 * argued with — without reading Compose.
 */
@StringRes
private fun guideTrouble(outcome: GuideOutcome?): Int? = when (outcome) {
    GuideOutcome.BLOCKED -> R.string.tv_guide_blocked
    GuideOutcome.EMPTY -> R.string.tv_guide_none_from_provider
    GuideOutcome.STORED, GuideOutcome.UNSUPPORTED, GuideOutcome.FAILED, null -> null
}

/** Whatever this screen has to say when it has no list to draw. */
@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.headlineSmall,
        )
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
    onLongClick: () -> Unit,
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
            //
            // Combined, because holding the centre is the only spare gesture a remote has and
            // INC-F4 spends it on the full listing. A press still plays, which is what all but
            // one press in a hundred means.
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
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
