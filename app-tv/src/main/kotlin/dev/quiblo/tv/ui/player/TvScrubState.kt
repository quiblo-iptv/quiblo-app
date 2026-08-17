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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Where a viewer is dragging the timeline to, before the player is told.
 *
 * **Presses stack; they do not each seek.** The transport row's rewind and forward buttons jump
 * the moment they are pressed, which is right for one correction and wrong for crossing a film:
 * eight presses became eight seeks, and a decoder asked to re-buffer eight times in two seconds
 * shows a viewer eight stutters and lands somewhere they did not aim for. Here every press moves
 * a *pending* mark, the bar draws it, and the player is told once, when the pressing stops.
 *
 * **It accelerates**, because it has to. At the viewer's own five-second interval a two-hour film
 * is fourteen hundred presses from one end to the other. After a short run of presses the step
 * grows, so a held direction crosses a film in a few seconds while a single press is still worth
 * exactly the interval that was chosen in Settings.
 *
 * **The target survives the commit.** Clearing it there would put the old position back on the bar
 * for however long the position poll takes to notice the seek — a snap backwards, on the frame
 * after the viewer let go. Instead it is cleared by [settle], once the player's own position has
 * arrived where it was sent.
 *
 * No Compose UI in here and no player: this is the arithmetic, so it can be tested as arithmetic.
 */
@Stable
internal class TvScrubState(private val stepMillis: Long) {

    /** Where the scrub is heading, or `null` when nobody is scrubbing. */
    var targetMillis: Long? by mutableStateOf(null)
        private set

    /** True between the first press and the commit — the window in which more presses stack. */
    var isPending: Boolean by mutableStateOf(false)
        private set

    private var presses = 0

    /**
     * Moves the pending mark one step in [direction] (`-1` back, `1` forward).
     *
     * Measured from the pending mark once there is one, and from the real position before that.
     * Measuring from the real position every time is what would make a run of presses worth one
     * press: the player has not moved yet, so every step would compute the same target.
     */
    fun nudge(direction: Int, positionMillis: Long, durationMillis: Long) {
        val from = targetMillis ?: positionMillis
        presses++
        targetMillis = (from + direction * stepFor(presses)).coerceIn(0L, durationMillis)
        isPending = true
    }

    /**
     * Ends the run and says where to seek to.
     *
     * @return the position to seek to, or `null` when there is nothing pending — the timer that
     *   calls this fires on a state change and must be harmless when it fires on the settle.
     */
    fun commit(): Long? {
        if (!isPending) return null
        isPending = false
        presses = 0
        return targetMillis
    }

    /** Drops the mark once the player has actually arrived where it was sent. */
    fun settle(positionMillis: Long) {
        val target = targetMillis ?: return
        if (isPending) return
        if (kotlin.math.abs(positionMillis - target) <= SETTLE_TOLERANCE_MILLIS) targetMillis = null
    }

    /** Abandons the run without seeking — the viewer left the bar, or the controls went away. */
    fun cancel() {
        targetMillis = null
        isPending = false
        presses = 0
    }

    private fun stepFor(pressCount: Int): Long = stepMillis * when {
        pressCount <= SLOW_PRESSES -> 1
        pressCount <= MEDIUM_PRESSES -> MEDIUM_FACTOR
        else -> FAST_FACTOR
    }

    private companion object {
        /**
         * How long a run stays at the viewer's own interval.
         *
         * Long enough that a correction — "back a bit, I missed that line" — is never
         * accelerated, which is the case the interval in Settings was chosen for.
         */
        const val SLOW_PRESSES = 4

        const val MEDIUM_PRESSES = 9
        const val MEDIUM_FACTOR = 4
        const val FAST_FACTOR = 12

        /**
         * How close counts as arrived.
         *
         * A player does not seek to the millisecond — it lands on the nearest keyframe, which on
         * a long GOP can be a second or two away. Anything tighter than that never settles, and
         * the mark stays on the bar for the rest of the film.
         */
        const val SETTLE_TOLERANCE_MILLIS = 3_000L
    }
}
