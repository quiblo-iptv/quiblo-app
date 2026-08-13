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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.Programme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * A channel's listing drawn against elapsed time, on a phone (INC-F4).
 *
 * Time runs left to right at a fixed number of pixels per hour, so a two-hour film is twice the
 * block a one-hour one is and a viewer reads the shape of an evening without reading a single
 * time. Where each block sits is [guideTimeline]'s answer; this only turns fractions into dp.
 *
 * The strip opens scrolled to now rather than to the beginning of the window. The hour behind
 * exists so the programme playing is whole, not so anybody has to scroll past it.
 */
@Composable
internal fun GuideStrip(
    timeline: GuideTimeline,
    selected: Programme?,
    onSelect: (Programme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val zone = remember { ZoneId.systemDefault() }

    val hours = ((timeline.endEpochMillis - timeline.startEpochMillis).toFloat() / MILLIS_PER_HOUR)
    val totalWidth = HOUR_WIDTH * hours

    // Once, when the strip appears, and never again: a listing arriving from the panel while
    // somebody is already dragging must not pull the strip back out from under them. Now sits a
    // little in from the left edge rather than against it, because a viewer opening a guide is
    // looking for what is coming.
    LaunchedEffect(Unit) {
        val nowFraction = timeline.nowFraction ?: return@LaunchedEffect
        val target = with(density) { (totalWidth * nowFraction).toPx() - LEAD_IN.toPx() }
        scrollState.scrollTo(target.toInt().coerceAtLeast(0))
    }

    Column(modifier = modifier.horizontalScroll(scrollState)) {
        HourRuler(timeline = timeline, totalWidth = totalWidth, zone = zone)

        Box(modifier = Modifier.width(totalWidth)) {
            Row(modifier = Modifier.height(BLOCK_HEIGHT)) {
                timeline.blocks.forEach { block ->
                    val width = totalWidth * block.widthFraction
                    if (block.programme == null) {
                        Gap(width)
                    } else {
                        ProgrammeCell(
                            programme = block.programme,
                            isNow = block.isNow,
                            isSelected = block.programme == selected,
                            width = width,
                            zone = zone,
                            onClick = { onSelect(block.programme) },
                        )
                    }
                }
            }

            timeline.nowFraction?.let { fraction ->
                NowMarker(offset = totalWidth * fraction)
            }
        }
    }
}

/**
 * The clock along the top, ticked every hour.
 *
 * Labels sit at the hour boundaries inside the window rather than at the window's own edges,
 * which start at whatever minute the sheet was opened. A ruler reading 19:43, 20:43, 21:43 is a
 * measurement of nothing anybody thinks in.
 */
@Composable
private fun HourRuler(timeline: GuideTimeline, totalWidth: Dp, zone: ZoneId) {
    val span = (timeline.endEpochMillis - timeline.startEpochMillis).toFloat()
    // The next whole hour *in the device's zone*, not the next multiple of an hour since the
    // epoch: a zone offset by half an hour would otherwise tick at 19:30, 20:30, 21:30.
    val firstTick = Instant.ofEpochMilli(timeline.startEpochMillis)
        .atZone(zone)
        .truncatedTo(ChronoUnit.HOURS)
        .plusHours(1)
        .toInstant()
        .toEpochMilli()

    Box(modifier = Modifier.width(totalWidth).height(RULER_HEIGHT)) {
        generateSequence(firstTick) { it + MILLIS_PER_HOUR }
            .takeWhile { it < timeline.endEpochMillis }
            .forEach { tick ->
                val fraction = (tick - timeline.startEpochMillis) / span
                Text(
                    text = tick.asClockTime(zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .offset(x = totalWidth * fraction)
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp),
                )
            }
    }
}

/** One programme, as wide as it is long. */
@Composable
private fun ProgrammeCell(
    programme: Programme,
    isNow: Boolean,
    isSelected: Boolean,
    width: Dp,
    zone: ZoneId,
    onClick: () -> Unit,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isNow -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(end = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = programme.startEpochMillis.asClockTime(zone),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = programme.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * An hour the provider sent nothing for.
 *
 * Drawn, and drawn faintly. A hole in a listing is information — it is how a viewer tells a
 * channel that goes off air from a panel that has stopped answering.
 */
@Composable
private fun Gap(width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(end = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = GAP_ALPHA)),
    )
}

/** Where now is, as a line through the whole strip. */
@Composable
private fun NowMarker(offset: Dp) {
    Box(
        modifier = Modifier
            .offset(x = offset)
            .width(2.dp)
            .height(BLOCK_HEIGHT)
            .background(MaterialTheme.colorScheme.primary),
    )
}

/**
 * A UTC instant in the device's own zone (AC-EPG-03).
 *
 * A timeline makes an offset error obvious in a way a now/next label does not: every block on
 * the strip is an hour out at once, so this conversion is the one place it can go wrong and the
 * one place it is applied.
 */
private fun Long.asClockTime(zone: ZoneId): String =
    Instant.ofEpochMilli(this).atZone(zone).format(CLOCK_FORMAT)

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * How much of the strip an hour takes.
 *
 * Wide enough that a half-hour programme still has room for a title, which is the smallest
 * block a listing routinely contains.
 */
private val HOUR_WIDTH = 168.dp
private val BLOCK_HEIGHT = 76.dp
private val RULER_HEIGHT = 18.dp
private val LEAD_IN = 24.dp
private const val GAP_ALPHA = 0.35f
private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
