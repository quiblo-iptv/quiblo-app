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

package dev.quiblo.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timeline's arithmetic, with no player and no panel in front of it.
 *
 * The behaviour that was asked for is "each click stacks on the last", and that is a sum rather
 * than a screen — so it is tested as one. Everything here would otherwise only be provable by
 * pressing a remote and watching a film move, which is how the seek buttons went four versions
 * without anybody noticing they re-buffered on every press.
 */
class TvScrubStateTest {

    /**
     * Ten presses are one target ten steps away, not ten targets one step away.
     *
     * This is the whole feature. Measuring from the player's position every time would produce
     * the same answer ten times over, because the player has not been told anything yet.
     */
    @Test
    fun `presses stack on each other rather than each measuring from the player`() {
        val scrub = TvScrubState(stepMillis = STEP)

        repeat(3) { scrub.nudge(direction = 1, positionMillis = ORIGIN, durationMillis = DURATION) }

        assertEquals(ORIGIN + 3 * STEP, scrub.targetMillis)
    }

    @Test
    fun `back and forward cancel out`() {
        val scrub = TvScrubState(stepMillis = STEP)

        scrub.nudge(1, ORIGIN, DURATION)
        scrub.nudge(-1, ORIGIN, DURATION)

        assertEquals(ORIGIN, scrub.targetMillis)
    }

    /**
     * A long run moves further per press than a short one.
     *
     * Without this a two-hour film is fourteen hundred presses end to end at the five-second
     * interval, which is not a timeline anybody can use. Asserted as "further than the flat
     * rate would have gone" rather than against the factors themselves, so tuning the
     * acceleration does not mean rewriting the test that proves it exists.
     */
    @Test
    fun `a long run accelerates`() {
        val scrub = TvScrubState(stepMillis = STEP)
        val presses = 12

        repeat(presses) { scrub.nudge(1, ORIGIN, DURATION) }

        val flatRate = ORIGIN + presses * STEP
        assertTrue(
            "12 presses moved ${scrub.targetMillis}, no further than the flat rate $flatRate",
            (scrub.targetMillis ?: 0L) > flatRate,
        )
    }

    /** The first few presses are worth exactly the interval chosen in Settings, and no more. */
    @Test
    fun `a short run does not accelerate`() {
        val scrub = TvScrubState(stepMillis = STEP)

        repeat(2) { scrub.nudge(1, ORIGIN, DURATION) }

        assertEquals(ORIGIN + 2 * STEP, scrub.targetMillis)
    }

    @Test
    fun `the mark cannot be pushed past the end of the film`() {
        val scrub = TvScrubState(stepMillis = STEP)

        repeat(50) { scrub.nudge(1, DURATION - STEP, DURATION) }

        assertEquals(DURATION, scrub.targetMillis)
    }

    @Test
    fun `the mark cannot be pushed before the start of the film`() {
        val scrub = TvScrubState(stepMillis = STEP)

        repeat(50) { scrub.nudge(-1, STEP, DURATION) }

        assertEquals(0L, scrub.targetMillis)
    }

    @Test
    fun `committing hands back the target once, and nothing on the second call`() {
        val scrub = TvScrubState(stepMillis = STEP)
        scrub.nudge(1, ORIGIN, DURATION)

        assertEquals(ORIGIN + STEP, scrub.commit())
        assertNull("A settled scrub must not seek again on the next tick.", scrub.commit())
    }

    /**
     * The mark stays on the bar after the commit, and goes when the player arrives.
     *
     * Clearing it on the commit is the version that snaps the bar backwards for as long as the
     * position poll takes to notice — which is up to a poll interval of watching the film jump
     * back to where it was.
     */
    @Test
    fun `the mark survives the commit and is cleared by arriving`() {
        val scrub = TvScrubState(stepMillis = STEP)
        scrub.nudge(1, ORIGIN, DURATION)
        val target = scrub.commit()!!

        scrub.settle(ORIGIN)
        assertEquals("Still where it was sent — the player has not moved yet.", target, scrub.targetMillis)

        scrub.settle(target)
        assertNull(scrub.targetMillis)
    }

    /** A keyframe seek lands near the target rather than on it, and near is arrived. */
    @Test
    fun `arriving near enough counts as arriving`() {
        val scrub = TvScrubState(stepMillis = STEP)
        scrub.nudge(1, ORIGIN, DURATION)
        val target = scrub.commit()!!

        scrub.settle(target - KEYFRAME_DRIFT)

        assertNull(scrub.targetMillis)
    }

    /** A run still being typed is not settled out from under the viewer. */
    @Test
    fun `a pending run is never settled away`() {
        val scrub = TvScrubState(stepMillis = STEP)
        scrub.nudge(1, ORIGIN, DURATION)

        scrub.settle(scrub.targetMillis!!)

        assertEquals(ORIGIN + STEP, scrub.targetMillis)
    }

    @Test
    fun `cancelling leaves nothing to seek to`() {
        val scrub = TvScrubState(stepMillis = STEP)
        scrub.nudge(1, ORIGIN, DURATION)

        scrub.cancel()

        assertNull(scrub.targetMillis)
        assertNull(scrub.commit())
    }

    private companion object {
        const val STEP = 10_000L
        const val ORIGIN = 300_000L
        const val DURATION = 7_200_000L

        /** Inside the tolerance `TvScrubState` allows for a long GOP. */
        const val KEYFRAME_DRIFT = 2_000L
    }
}
