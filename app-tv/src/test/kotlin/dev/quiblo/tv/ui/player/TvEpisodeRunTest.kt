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

import dev.quiblo.core.media.PlaybackStatus
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Season
import dev.quiblo.tv.ui.series.episodeRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What "the next episode" means, which is the whole of this feature.
 *
 * Everything here is arithmetic over a list, and it is worth a test rather than a reading for
 * one reason: every mistake it can make is silent. A run built in the wrong order plays a
 * series backwards, an off-by-one index plays every episode twice, and a wrap at the end
 * restarts a series somebody has just finished — from an unattended countdown, with nobody in
 * the room. None of the three looks like a crash.
 */
class TvEpisodeRunTest {

    @Test
    fun `the run is in broadcast order however the seasons were listed`() {
        // What a viewer sees with "newest first" on: the seasons reversed, and the episodes
        // inside them reversed too.
        val reversed = listOf(
            Season(2, "Season 2", listOf(episode("2-2", 2, 2), episode("2-1", 2, 1))),
            Season(1, "Season 1", listOf(episode("1-2", 1, 2), episode("1-1", 1, 1))),
        )

        assertEquals(
            listOf("1-1", "1-2", "2-1", "2-2"),
            episodeRun(reversed).map { it.id },
        )
    }

    @Test
    fun `merging the seasons does not change what follows what`() {
        // Merged, the screen holds one season whose own number is a sentinel. The episodes
        // inside it keep the season they came from, which is what sorts them back.
        val merged = listOf(
            Season(
                seasonNumber = -1,
                name = "",
                episodes = listOf(episode("2-1", 2, 1), episode("1-1", 1, 1), episode("1-2", 1, 2)),
            ),
        )

        assertEquals(listOf("1-1", "1-2", "2-1"), episodeRun(merged).map { it.id })
    }

    @Test
    fun `stepping forward moves one episode and starts it from the beginning`() {
        val playing = request(index = 0)

        val next = playing.steppedBy(1)

        assertNotNull(next)
        assertEquals("1-2", next?.episodeId)
        assertEquals(1, next?.runIndex)
        // Zero rather than null. Null means "wherever it was left", and an episode nobody has
        // watched has nowhere to be left — passing it on would resume the *previous* episode's
        // position into a stream that has never been opened.
        assertEquals(0L, next?.startPositionMillis)
    }

    @Test
    fun `stepping back moves one episode`() {
        val previous = request(index = 2).steppedBy(-1)

        assertEquals("1-2", previous?.episodeId)
        assertEquals(1, previous?.runIndex)
    }

    /**
     * The difference between a series and a channel list, stated as a test.
     *
     * `Live.zappedBy` wraps deliberately — past the last channel is the first, as a television
     * does it. A series is a thing that finishes, so this must not, and the countdown makes the
     * consequence of getting it wrong unattended.
     */
    @Test
    fun `a series does not wrap at either end`() {
        assertNull(request(index = 2).steppedBy(1))
        assertNull(request(index = 0).steppedBy(-1))
    }

    @Test
    fun `an episode with no run behind it offers no steps`() {
        // Reachable: nothing stops a future screen constructing a request without one, and the
        // answer has to be "no next episode" rather than an index into an empty list.
        val alone = request(index = 0).copy(run = emptyList())

        assertFalse(alone.hasNext)
        assertFalse(alone.hasPrevious)
        assertNull(alone.steppedBy(1))
    }

    @Test
    fun `the ends of a series know they are the ends`() {
        assertTrue(request(index = 0).hasNext)
        assertFalse(request(index = 0).hasPrevious)

        assertFalse(request(index = 2).hasNext)
        assertTrue(request(index = 2).hasPrevious)
    }

    @Test
    fun `the offer only appears at the end of an episode that has one after it`() {
        assertTrue(offerFor(request(index = 0)))

        // The finale. There is nothing to offer, so the banner would be a control pointing at
        // an episode that does not exist.
        assertFalse(offerFor(request(index = 2)))

        // Said no. It stays no until the request changes, which is a new episode.
        assertFalse(offerFor(request(index = 0), isDismissed = true))
    }

    @Test
    fun `nothing but an ended episode offers the next one`() {
        // The dangerous one is ERROR. A stream that failed halfway has a Try again on screen,
        // and skipping to the next episode instead would quietly drop the half nobody watched.
        listOf(
            PlaybackStatus.PLAYING,
            PlaybackStatus.PAUSED,
            PlaybackStatus.BUFFERING,
            PlaybackStatus.IDLE,
            PlaybackStatus.ERROR,
        ).forEach { status ->
            assertFalse(
                offerFor(request(index = 0), status = status),
                "$status must not offer the next episode",
            )
        }
    }

    @Test
    fun `a film never offers a next episode`() {
        val film = TvPlaybackRequest.Film(channel)

        assertFalse(shouldOfferNextEpisode(film, PlaybackStatus.ENDED, playingId = null, isDismissed = false))
    }

    /**
     * The gap between pressing next and the next episode starting.
     *
     * Stepping replaces the request the instant the button is pressed, and the engine goes on
     * reporting the previous episode's ending until the new one has been read out of the
     * database and prepared. For that gap every other condition says yes — the request is an
     * episode, it has one after it, and the dismissal was reset by the new request — so without
     * this the banner slides straight back in for an episode that has not started, between every
     * pair of episodes of a series watched through.
     *
     * **This is the offer chasing its own tail**, and it is what the engine's own id settles: an
     * ending only means something for the episode it belongs to.
     */
    @Test
    fun `the offer waits for the engine to catch up with a step`() {
        val finished = request(index = 0)
        val stepped = finished.steppedBy(1)!!

        // The moment after the press: the request has moved, the engine has not.
        assertFalse(
            shouldOfferNextEpisode(stepped, PlaybackStatus.ENDED, playingId = finished.streamUrl, isDismissed = false),
        )

        // And the episode that actually ended still offers its successor, which is the case
        // above with only the request put back — so this is a guard and not an off switch.
        assertTrue(
            shouldOfferNextEpisode(
                finished,
                PlaybackStatus.ENDED,
                playingId = finished.streamUrl,
                isDismissed = false,
            ),
        )
    }

    @Test
    fun `an episode nothing has loaded yet offers nothing`() {
        // Before the first prepare, and after a load that found no channel row at all.
        assertFalse(offerFor(request(index = 0), playingId = null))
    }

    private fun offerFor(
        request: TvPlaybackRequest.Episode,
        status: PlaybackStatus = PlaybackStatus.ENDED,
        playingId: String? = request.streamUrl,
        isDismissed: Boolean = false,
    ) = shouldOfferNextEpisode(request, status, playingId, isDismissed)

    private val channel = Channel(
        id = 7L,
        sourceId = 1L,
        name = "A series",
        streamUrl = "",
        kind = MediaKind.SERIES,
    )

    private val run = listOf(episode("1-1", 1, 1), episode("1-2", 1, 2), episode("2-1", 2, 1))

    private fun request(index: Int) = TvPlaybackRequest.Episode(
        channel = channel,
        episodeId = run[index].id,
        streamUrl = run[index].streamUrl,
        episodeTitle = run[index].title,
        seasonNumber = run[index].seasonNumber,
        episodeNumber = run[index].episodeNumber,
        run = run,
        runIndex = index,
    )

    private fun episode(id: String, season: Int, number: Int) = Episode(
        id = id,
        title = "Episode $number",
        seasonNumber = season,
        episodeNumber = number,
        streamUrl = "https://example.invalid/$id",
    )
}
