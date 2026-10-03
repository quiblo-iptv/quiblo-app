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

package dev.quiblo.source.xtream

import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.Credentials
import dev.quiblo.source.api.PanelBlockStore
import dev.quiblo.source.api.SeriesDetailsResult
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The password is never part of what is stored, only of what is played (`BUG-041`).
 *
 * Synthetic panel throughout (AC-LEGAL-04).
 */
class XtreamCredentialsAtPlayTimeTest {

    private class Store(var value: Credentials?) : CredentialStore {
        override suspend fun credentials(sourceId: Long) = value
        override suspend fun put(sourceId: Long, credentials: Credentials) = Unit
        override suspend fun clear(sourceId: Long) = Unit
    }

    private object NoBlock : PanelBlockStore {
        override suspend fun blockedUntil(): Long = 0L
        override suspend fun setBlockedUntil(epochMillis: Long) = Unit
    }

    private val store = Store(Credentials("someone", "s3cret"))
    private val request = SourceRequest(sourceId = 4L, location = "panel.example.invalid:8080")

    private val bodies = mapOf(
        null to """{"user_info":{"auth":1,"status":"Active"}}""",
        "get_live_categories" to """[{"category_id":"1","category_name":"News"}]""",
        "get_live_streams" to """[{"stream_id":101,"name":"Alpha","category_id":"1"}]""",
        "get_vod_categories" to """[{"category_id":"2","category_name":"Films"}]""",
        "get_vod_streams" to """[{"stream_id":7,"name":"A Film","category_id":"2","container_extension":"mkv"}]""",
        "get_series_info" to """{"episodes":{"1":[{"id":"501","episode_num":1,"container_extension":"mp4"}]}}""",
    )

    private val source = createXtreamSource(
        HttpClient(
            MockEngine { call ->
                respond(
                    content = bodies[call.url.parameters["action"]] ?: "[]",
                    headers = headersOf("Content-Type", "application/json"),
                )
            },
        ),
        store,
        NoBlock,
    )

    @Test
    fun `nothing a load returns carries the username or the password`() = runTest {
        val loaded = source.load(request) as SourceResult.Success

        loaded.channels.forEach { channel ->
            assertFalse("someone" in channel.streamUrl, channel.streamUrl)
            assertFalse("s3cret" in channel.streamUrl, channel.streamUrl)
            assertFalse("panel.example.invalid" in channel.streamUrl, channel.streamUrl)
        }
    }

    @Test
    fun `nor does an episode, whose stream url is also its identity in history`() = runTest {
        val details = (source.seriesDetails(request, "9") as SeriesDetailsResult.Success).details
        val episode = details.seasons.single().episodes.single()

        assertEquals("xtream:series/501.mp4", episode.streamUrl)
    }

    @Test
    fun `the url is built when it is played, from the credential store`() = runTest {
        assertEquals(
            "http://panel.example.invalid:8080/live/someone/s3cret/101.ts",
            source.playbackUrl(request, "xtream:live/101"),
        )
        assertEquals(
            "http://panel.example.invalid:8080/movie/someone/s3cret/7.mkv",
            source.playbackUrl(request, "xtream:movie/7.mkv"),
        )
        assertEquals(
            "http://panel.example.invalid:8080/series/someone/s3cret/501.mp4",
            source.playbackUrl(request, "xtream:series/501.mp4"),
        )
    }

    @Test
    fun `a password changed in the store is the one the next play uses`() = runTest {
        store.value = Credentials("someone", "n3w")

        assertEquals(
            "http://panel.example.invalid:8080/live/someone/n3w/101.ts",
            source.playbackUrl(request, "xtream:live/101"),
        )
    }

    @Test
    fun `something that is not a locator is played as it is`() = runTest {
        val url = "http://cdn.example.invalid/x.m3u8"
        assertEquals(url, source.playbackUrl(request, url))
    }

    @Test
    fun `a malformed locator is not turned into a url`() {
        listOf("xtream:", "xtream:live/", "xtream:radio/1", "xtream:movie/a/b.mkv").forEach {
            assertNull(XtreamUrl.resolve("http://h.invalid", "u", "p", it), it)
        }
    }
}
