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

import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode as SeriesEpisode

/**
 * What the television player has been asked to play.
 *
 * The three cases exist because they are genuinely three things, and flattening them into
 * "a [Channel] and a list" is what bug #009 was. A television used to be handed whatever
 * row the viewer pressed along with the list it came from, and it played all of them the
 * same way: a film got the channel-up key mapped to "zap to the next film", the controls
 * announced it as live, and a series — whose row carries no episode stream at all — was
 * asked to play a URL that plays nothing.
 *
 * Making the distinction a type rather than a runtime check means the player cannot be
 * handed a series by accident, and cannot forget to pass an episode's URL: there is no
 * shape of this class that expresses either mistake.
 */
sealed interface TvPlaybackRequest {

    /** The catalogue row behind this request. For an [Episode], the series itself. */
    val channel: Channel

    /** What to announce on screen when this starts. An episode is named, not its series. */
    val noticeTitle: String
        get() = when (this) {
            is Episode -> episodeTitle
            else -> channel.name
        }

    /**
     * A live channel, with the list it was chosen from.
     *
     * The queue travels with the request because zapping needs it and the player cannot
     * know it: up and down move through the list the viewer came from, and only the screen
     * that launched playback knows what that was.
     */
    data class Live(
        override val channel: Channel,
        val queue: List<Channel>,
        val index: Int,
    ) : TvPlaybackRequest {

        /**
         * The channel [direction] steps away, wrapping.
         *
         * Past the last channel is the first, as a television does it. Stopping dead at the
         * end of a 20,000-channel list would read as a fault rather than as a boundary.
         */
        fun zappedBy(direction: Int): Live {
            if (queue.isEmpty()) return this
            val next = ((index + direction) % queue.size + queue.size) % queue.size
            return Live(queue[next], queue, next)
        }
    }

    /**
     * A film.
     *
     * Seekable and resumable, with nothing above or below it to zap to — the next film in
     * a category is not "the next channel" in any sense a viewer means by pressing up.
     */
    data class Film(
        override val channel: Channel,
        /**
         * Where to start, or null for wherever it was left.
         *
         * Null and zero are different answers: null lets the player read the stored
         * position, and zero is a viewer deliberately starting the film again. Collapsing
         * them would make "start from the beginning" impossible to express.
         */
        val startPositionMillis: Long? = null,
    ) : TvPlaybackRequest

    /**
     * One episode of a series.
     *
     * The stream URL is carried rather than looked up because an episode is never a row in
     * the channel table: episodes come from the panel per series and are held for a
     * session. The season and episode numbers travel too, because the player records them
     * into history and cannot derive them from a URL.
     */
    data class Episode(
        override val channel: Channel,
        /**
         * The episode's own id.
         *
         * Carried so that backing out of the player can put focus back on the row this came
         * from (AC-TV-03, `agile/012` #020). The season and episode numbers would nearly
         * identify it and a panel that repeats a number across a season would make "nearly"
         * into a cursor that lands on the wrong row.
         */
        val episodeId: String,
        val streamUrl: String,
        val episodeTitle: String,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val startPositionMillis: Long? = null,
        /**
         * The whole series in the order it is watched, and where this episode sits in it.
         *
         * Here for the same reason [Live] carries its queue: the player is what asks for the
         * next episode and the player is the one screen that cannot know what the next one is.
         * Episodes are fetched per series and held for a session, so nothing the player could
         * query would have them.
         *
         * **In broadcast order, whatever order the list was drawn in.** The series screen can
         * show newest first, and under that arrangement the row below the one playing is the
         * episode *before* it — so a run built from the drawing would make "next" walk a series
         * backwards. The order things are watched in is not a display preference.
         *
         * Empty for an episode opened from somewhere with no run behind it, which is what
         * makes [steppedBy] answer null rather than the caller having to check first.
         */
        val run: List<SeriesEpisode> = emptyList(),
        val runIndex: Int = 0,
    ) : TvPlaybackRequest {

        /** True when there is an episode after this one to offer at the end of it. */
        val hasNext: Boolean get() = run.getOrNull(runIndex + 1) != null

        val hasPrevious: Boolean get() = runIndex > 0 && run.getOrNull(runIndex - 1) != null

        /**
         * The episode [direction] steps away, or null at either end of the series.
         *
         * **It does not wrap, and [Live.zappedBy] does.** A channel list is a ring a viewer
         * walks round; a series is a thing that finishes. Rolling off the finale into the pilot
         * would restart a series somebody has just finished watching, silently, from an
         * unattended countdown — which is the one outcome this feature must never produce.
         *
         * Always starts from the beginning: a next episode is one nobody has watched, so
         * there is no position to resume and an inherited one would drop the viewer into the
         * middle of it.
         */
        fun steppedBy(direction: Int): Episode? {
            val target = runIndex + direction
            val episode = run.getOrNull(target)?.takeIf { target >= 0 } ?: return null
            return copy(
                episodeId = episode.id,
                streamUrl = episode.streamUrl,
                episodeTitle = episode.title,
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
                startPositionMillis = 0L,
                runIndex = target,
            )
        }
    }
}
