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
import dev.quiblo.core.model.MediaKind

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
    data class Film(override val channel: Channel) : TvPlaybackRequest

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
        val streamUrl: String,
        val episodeTitle: String,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val startPositionMillis: Long? = null,
    ) : TvPlaybackRequest
}

/**
 * How to play the row at [index], or null when it is not something to play directly.
 *
 * The null is a series, which carries no stream of its own and opens a screen instead. A
 * favourites list holds every kind at once, so the decision has to be made per item rather
 * than per screen — deciding it per screen is what let a series reach the player at all.
 */
fun playbackRequestFor(items: List<Channel>, index: Int): TvPlaybackRequest? {
    val channel = items.getOrNull(index) ?: return null
    return when (channel.kind) {
        MediaKind.LIVE -> TvPlaybackRequest.Live(channel, items, index)
        MediaKind.VOD -> TvPlaybackRequest.Film(channel)
        MediaKind.SERIES -> null
    }
}
