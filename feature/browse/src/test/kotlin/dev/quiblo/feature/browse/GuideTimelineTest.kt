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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The arithmetic behind the timeline (INC-F4).
 *
 * Everything here is what a panel actually sends: listings that overlap, listings with holes in
 * them, and listings that start well before the window a viewer is looking at. Each of those
 * draws wrongly in a different way, and none of them are visible in a now/next label.
 */
class GuideTimelineTest {

    @Test
    fun `blocks sit where the clock puts them`() {
        val timeline = guideTimeline(
            programmes = listOf(programme("Now", NOW, NOW + HOUR)),
            nowEpochMillis = NOW,
        )

        val block = timeline.blocks.single { it.programme != null }
        // One hour behind, twelve ahead: a programme starting at now starts 1/13 of the way in
        // and runs for 1/13 of the width.
        assertEquals(1f / 13f, block.startFraction, TOLERANCE)
        assertEquals(1f / 13f, block.widthFraction, TOLERANCE)
    }

    @Test
    fun `the programme on now is marked, and only that one`() {
        val timeline = guideTimeline(
            programmes = listOf(
                programme("Before", NOW - HOUR, NOW),
                programme("Now", NOW, NOW + HOUR),
                programme("After", NOW + HOUR, NOW + 2 * HOUR),
            ),
            nowEpochMillis = NOW,
        )

        assertEquals(listOf("Now"), timeline.blocks.filter { it.isNow }.map { it.programme?.title })
    }

    @Test
    fun `a hole the panel left is a block of its own`() {
        val timeline = guideTimeline(
            programmes = listOf(
                programme("First", NOW, NOW + HOUR),
                programme("Second", NOW + 2 * HOUR, NOW + 3 * HOUR),
            ),
            nowEpochMillis = NOW,
        )

        val gaps = timeline.blocks.filter { it.programme == null }
        // The hour before the first programme, the hour between the two, and the rest of the day
        // after the second.
        assertEquals(3, gaps.size)
        assertEquals(1f / 13f, gaps[1].widthFraction, TOLERANCE)
    }

    @Test
    fun `overlapping listings never draw on top of each other`() {
        val timeline = guideTimeline(
            programmes = listOf(
                programme("First", NOW, NOW + 2 * HOUR),
                programme("Second", NOW + HOUR, NOW + 3 * HOUR),
            ),
            nowEpochMillis = NOW,
        )

        val blocks = timeline.blocks.filter { it.programme != null }
        assertEquals(2, blocks.size)
        // The earlier one keeps its ground: the later one starts where the first ends.
        assertEquals(
            blocks[0].startFraction + blocks[0].widthFraction,
            blocks[1].startFraction,
            TOLERANCE,
        )
        assertTrue(blocks.all { it.widthFraction > 0f })
    }

    @Test
    fun `a programme that began before the window is clipped, not dropped`() {
        val timeline = guideTimeline(
            programmes = listOf(programme("Film", NOW - 3 * HOUR, NOW + HOUR)),
            nowEpochMillis = NOW,
        )

        val block = timeline.blocks.single { it.programme != null }
        assertEquals(0f, block.startFraction, TOLERANCE)
        // Two hours of it are inside the window: the hour behind now, and the hour after.
        assertEquals(2f / 13f, block.widthFraction, TOLERANCE)
        assertTrue(block.isNow)
    }

    @Test
    fun `a listing entirely behind the window leaves the timeline empty`() {
        val timeline = guideTimeline(
            programmes = listOf(programme("Yesterday", NOW - 5 * HOUR, NOW - 4 * HOUR)),
            nowEpochMillis = NOW,
        )

        assertTrue(timeline.isEmpty)
    }

    @Test
    fun `a listing with no duration is dropped rather than drawn as a sliver`() {
        val timeline = guideTimeline(
            programmes = listOf(
                programme("Broken", NOW, NOW),
                programme("Backwards", NOW + 2 * HOUR, NOW + HOUR),
            ),
            nowEpochMillis = NOW,
        )

        assertTrue(timeline.isEmpty)
        assertTrue(timeline.blocks.none { it.programme != null })
    }

    @Test
    fun `no programmes at all is empty rather than one long gap`() {
        val timeline = guideTimeline(programmes = emptyList(), nowEpochMillis = NOW)

        assertTrue(timeline.isEmpty)
        assertTrue(timeline.blocks.isEmpty())
    }

    @Test
    fun `the now-marker sits an hour into the window`() {
        val timeline = guideTimeline(
            programmes = listOf(programme("Now", NOW, NOW + HOUR)),
            nowEpochMillis = NOW,
        )

        assertEquals(1f / 13f, timeline.nowFraction!!, TOLERANCE)
    }

    @Test
    fun `a window that does not contain now has no marker`() {
        val timeline = guideTimeline(
            programmes = listOf(programme("Tomorrow", NOW + 25 * HOUR, NOW + 26 * HOUR)),
            nowEpochMillis = NOW,
            hoursBehind = -24,
            hoursAhead = 36,
        )

        assertNull(timeline.nowFraction)
        assertFalse(timeline.isEmpty)
    }

    @Test
    fun `the window is the one the caller asked for`() {
        val timeline = guideTimeline(
            programmes = emptyList(),
            nowEpochMillis = NOW,
            hoursBehind = 2,
            hoursAhead = 4,
        )

        assertEquals(NOW - 2 * HOUR, timeline.startEpochMillis)
        assertEquals(NOW + 4 * HOUR, timeline.endEpochMillis)
    }

    private fun programme(title: String, start: Long, end: Long) = Programme(
        id = 0L,
        sourceId = 1L,
        channelKey = "bbc.one",
        title = title,
        startEpochMillis = start,
        endEpochMillis = end,
    )

    private companion object {
        const val HOUR = 60L * 60L * 1000L

        /** A round instant, so a failure reads as clock arithmetic rather than as noise. */
        const val NOW = 1_800_000_000_000L

        const val TOLERANCE = 0.0001f
    }
}
