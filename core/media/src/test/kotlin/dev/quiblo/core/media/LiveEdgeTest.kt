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

package dev.quiblo.core.media

import androidx.media3.common.PlaybackException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A live stream that fell behind its window is rejoined, not retried into the same wall (`BUG-036`). */
class LiveEdgeTest {

    private val behind = EngineFailure(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)

    @Test
    fun `falling behind the live window rejoins the live edge`() {
        assertTrue(rejoinsLiveEdge(behind, rejoinsSoFar = 0))
    }

    @Test
    fun `rejoining is bounded, so a broken window still ends in an error`() {
        assertTrue(rejoinsLiveEdge(behind, rejoinsSoFar = MAX_LIVE_EDGE_REJOINS - 1))
        assertFalse(rejoinsLiveEdge(behind, rejoinsSoFar = MAX_LIVE_EDGE_REJOINS))
    }

    @Test
    fun `nothing else is mistaken for falling behind`() {
        listOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        ).forEach { code ->
            assertFalse(rejoinsLiveEdge(EngineFailure(code), rejoinsSoFar = 0), "code $code")
        }
    }

    @Test
    fun `a stream behind its window that cannot be rejoined is not called gone or refused`() {
        // It falls through to the ordinary ladder as UNKNOWN, which is retried mid-playback.
        val error = classify(behind)
        assertTrue(error == PlaybackError.UNKNOWN, "$error")
    }
}
