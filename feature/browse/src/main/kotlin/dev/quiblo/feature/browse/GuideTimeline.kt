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

import dev.quiblo.core.model.Programme

/**
 * One channel's listing laid out against elapsed time (INC-F4).
 *
 * **The arithmetic, with no Compose in it.** Both apps draw a timeline and they draw it
 * differently — a phone scrolls it under a finger, a television walks it with a D-pad — but
 * where each programme sits, how wide it is, and which one is on now are the same answers on
 * both. `PLAN-TV.md` §2's rule is to share the decisions and split the drawing, and this is the
 * decisions.
 *
 * @property nowFraction where the now-marker goes, from 0f to 1f, or null when the window does
 *   not contain now. A timeline scrolled to tomorrow has no marker, and drawing one at the edge
 *   would be worse than drawing none.
 */
data class GuideTimeline(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val blocks: List<GuideBlock>,
    val nowFraction: Float?,
) {
    val isEmpty: Boolean get() = blocks.none { it.programme != null }
}

/**
 * One span of the timeline.
 *
 * A null [programme] is a gap the provider did not fill. Gaps are blocks rather than nothing,
 * because the alternative is stretching the programmes on either side to cover the hole — which
 * makes a timeline that is missing an hour look like a timeline that is complete.
 *
 * @property isNow true for the block containing the moment this was built. Carried per block and
 *   not left to the marker alone: a television walks this list with a D-pad and a viewer reading
 *   from three metres needs the row itself to say it is on now.
 */
data class GuideBlock(
    val programme: Programme?,
    val startFraction: Float,
    val widthFraction: Float,
    val isNow: Boolean,
)

/**
 * Lays [programmes] out across a window around [nowEpochMillis].
 *
 * The window opens [hoursBehind] before now rather than at now itself, so the programme a viewer
 * is watching is visible from its beginning and not clipped to whatever is left of it. It runs
 * [hoursAhead] forward, which is a decision about how far a panel's data is worth trusting rather
 * than about screen width — the drawing scrolls.
 *
 * Programmes are clipped to the window, dropped when they have no duration, and never allowed to
 * overlap: panels do send overlapping listings, and two blocks drawn on top of each other is a
 * timeline nobody can read. Where they overlap the earlier one keeps its ground.
 */
fun guideTimeline(
    programmes: List<Programme>,
    nowEpochMillis: Long,
    hoursBehind: Int = DEFAULT_HOURS_BEHIND,
    hoursAhead: Int = DEFAULT_HOURS_AHEAD,
): GuideTimeline {
    val from = nowEpochMillis - hoursBehind * MILLIS_PER_HOUR
    val to = nowEpochMillis + hoursAhead * MILLIS_PER_HOUR
    val span = (to - from).toFloat()

    val blocks = mutableListOf<GuideBlock>()
    var cursor = from

    programmes
        .filter { it.endEpochMillis > it.startEpochMillis }
        .sortedBy { it.startEpochMillis }
        .forEach { programme ->
            val start = maxOf(programme.startEpochMillis, cursor)
            val end = minOf(programme.endEpochMillis, to)
            if (end <= start) return@forEach

            if (start > cursor) blocks += gap(cursor, start, from, span)
            blocks += GuideBlock(
                programme = programme,
                startFraction = (start - from) / span,
                widthFraction = (end - start) / span,
                isNow = programme.isLiveAt(nowEpochMillis),
            )
            cursor = end
        }

    if (blocks.isNotEmpty() && cursor < to) blocks += gap(cursor, to, from, span)

    return GuideTimeline(
        startEpochMillis = from,
        endEpochMillis = to,
        blocks = blocks,
        nowFraction = ((nowEpochMillis - from) / span).takeIf { it in 0f..1f },
    )
}

private fun gap(startMillis: Long, endMillis: Long, from: Long, span: Float) = GuideBlock(
    programme = null,
    startFraction = (startMillis - from) / span,
    widthFraction = (endMillis - startMillis) / span,
    isNow = false,
)

/**
 * An hour behind and twelve ahead.
 *
 * Behind, because the programme on now started before now and a viewer wants to see what it is,
 * not what is left of it. Ahead, because half a day is the horizon anybody plans over and panels
 * are unreliable well before the end of one.
 */
const val DEFAULT_HOURS_BEHIND = 1
const val DEFAULT_HOURS_AHEAD = 12

private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
