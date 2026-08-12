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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Programme
import dev.quiblo.feature.browse.GuideBlock
import dev.quiblo.feature.browse.guideTimeline
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.tryRequestFocus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A channel's whole listing, walked with the remote (INC-F4).
 *
 * **A strip across the bottom, not a dialog.** This app has no modals and is not getting its
 * first one here: the channel list stays on screen above the panel, dimmed, so a viewer reading
 * tonight's listings has not lost the thing they were reading them for. Back closes it.
 *
 * **What is on now is said, not only marked.** A now-line drawn across the strip is invisible
 * to a viewer three metres away who is looking at whichever block has focus, so the block
 * carries its own mark and the header says it in words.
 *
 * Where each block sits is [guideTimeline]'s arithmetic, shared with the phone. The drawing is
 * not shared: this one is walked left and right with a D-pad and the phone's is dragged.
 */
@Composable
internal fun TvGuidePanel(
    channel: Channel,
    schedule: Flow<List<Programme>>,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listing by schedule.collectAsStateWithLifecycle(initialValue = emptyList())

    // Opening the panel is what asks the panel for the whole listing. Holding the centre button
    // is a viewer asking once, which is the only shape the heavier call is safe in (AC-TV-05).
    LaunchedEffect(channel.stableKey) { onOpen() }

    // The clock is read once, when the panel opens. A window that slid while the panel was open
    // would move every block under a focus already resting on one.
    val openedAt = remember(channel.stableKey) { System.currentTimeMillis() }
    val timeline = remember(listing, openedAt) { guideTimeline(listing, openedAt) }

    var focused: Programme? by remember(channel.stableKey) { mutableStateOf(null) }

    // Thirteen hours at a fixed width per hour, so a block's width is how long it runs.
    val totalWidth = HOUR_WIDTH *
        ((timeline.endEpochMillis - timeline.startEpochMillis).toFloat() / MILLIS_PER_HOUR)

    val listState = rememberLazyListState()

    // One requester for as long as the panel is open, re-pointed at whichever block is now.
    // Rebuilding it when the listing arrives would leave the effect below holding the old one.
    val openingBlock = remember(channel.stableKey) { FocusRequester() }

    /**
     * The block the panel opens on: what is playing, or the first programme there is.
     *
     * Not simply the first block — the first block is a gap whenever a panel's listing starts
     * later than the window does, and a gap is not focusable. A remote pointed at one is a
     * remote that appears dead (AC-TV-02).
     */
    val openingIndex = remember(timeline) {
        timeline.blocks.indexOfFirst { it.isNow }
            .takeIf { it >= 0 }
            ?: timeline.blocks.indexOfFirst { it.programme != null }
    }

    // The opening block is usually off screen, and a FocusRequester attached to a composable a
    // lazy row has not composed focuses nothing — so the row is moved there first and given a
    // frame to build it. Re-runs when the full listing lands and moves what is on now.
    LaunchedEffect(openingIndex) {
        if (openingIndex < 0) return@LaunchedEffect
        listState.scrollToItem(openingIndex)
        withFrameNanos { }
        openingBlock.tryRequestFocus()
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Dim rather than cover, for the same reason the track menu does: the list a viewer
            // opened this from stays readable behind it.
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PANEL_MARGIN)
                .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(14.dp))
                .padding(vertical = 18.dp),
        ) {
            PanelHeader(channel = channel, programme = focused, isNow = focused.isNowIn(timeline.blocks))

            if (timeline.isEmpty) {
                Text(
                    text = stringResource(R.string.tv_guide_none),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = PANEL_PADDING, vertical = 24.dp),
                )
                return@Column
            }

            LazyRow(
                state = listState,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .height(BLOCK_HEIGHT)
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(horizontal = PANEL_PADDING),
            ) {
                itemsIndexed(
                    items = timeline.blocks,
                    // A gap has no programme to be named after, and two gaps in one listing are
                    // not the same gap, so position is the only stable identity here.
                    key = { index, _ -> index },
                ) { index, block ->
                    val width = totalWidth * block.widthFraction
                    val programme = block.programme
                    if (programme == null) {
                        Gap(width)
                    } else {
                        GuideCell(
                            programme = programme,
                            isNow = block.isNow,
                            width = width,
                            onFocused = { focused = programme },
                            modifier = if (index == openingIndex) {
                                Modifier.focusRequester(openingBlock)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The channel, and whichever block the remote is resting on.
 *
 * The header is where a listing is actually read: a block is as wide as the programme is long,
 * so a half-hour one has room for a title and nothing else. Times, plot and "on now" live here,
 * at a size that reads from a sofa.
 */
@Composable
private fun PanelHeader(channel: Channel, programme: Programme?, isNow: Boolean) {
    val zone = remember { ZoneId.systemDefault() }

    Column(modifier = Modifier.padding(horizontal = PANEL_PADDING)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = channel.name,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (isNow) {
                Text(
                    text = stringResource(R.string.tv_guide_on_now),
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        Text(
            text = programme?.title.orEmpty(),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        Text(
            text = programme?.let {
                stringResource(
                    R.string.tv_guide_time,
                    it.startEpochMillis.asClockTime(zone),
                    it.endEpochMillis.asClockTime(zone),
                )
            }.orEmpty(),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            text = programme?.description.orEmpty(),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * One programme, as wide as it is long, and focusable.
 *
 * Focus is a border and a lifted background and nothing that moves. A focusable that grows
 * inside a scrolling container is what made the whole TV list shake once already.
 */
@Composable
private fun GuideCell(
    programme: Programme,
    isNow: Boolean,
    width: Dp,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    val zone = remember { ZoneId.systemDefault() }

    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(
                color = when {
                    isFocused -> Color.White.copy(alpha = 0.20f)
                    isNow -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.05f)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            // clickable rather than focusable, the same way a channel row is: it makes the cell
            // focusable and maps the D-pad centre onto it. Pressing a programme does nothing yet
            // — there is nothing to do with a listing but read it — so the press is swallowed
            // rather than closing the panel under the viewer.
            .clickable(interactionSource = interactionSource, indication = null) { }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = programme.startEpochMillis.asClockTime(zone),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            maxLines = 1,
        )
        Text(
            text = programme.title,
            color = Color.White.copy(alpha = if (isFocused || isNow) 1f else 0.8f),
            fontSize = 16.sp,
            fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** An hour nobody listed, drawn so a hole in a listing looks like one. */
@Composable
private fun Gap(width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp)),
    )
}

/** True when the focused block is the one playing, for the badge in the header. */
private fun Programme?.isNowIn(blocks: List<GuideBlock>): Boolean =
    this != null && blocks.any { it.isNow && it.programme == this }

/** A UTC instant in the television's own zone (AC-EPG-03). */
private fun Long.asClockTime(zone: ZoneId): String =
    Instant.ofEpochMilli(this).atZone(zone).format(CLOCK_FORMAT)

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * How much of the strip an hour takes.
 *
 * Set by a television's viewing distance rather than by any screen size: wide enough that a
 * half-hour programme still holds a title readable from a sofa.
 */
private val HOUR_WIDTH = 200.dp
private val BLOCK_HEIGHT = 96.dp
private val PANEL_MARGIN = 24.dp
private val PANEL_PADDING = 28.dp
private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
