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

import dev.quiblo.core.model.LiveFormat
import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.Credentials
import dev.quiblo.source.api.PanelBlockStore
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import dev.quiblo.source.xtream.dto.UserInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Which container a live channel is asked for (`BUG-043`).
 *
 * It was always `.ts`, so an account limited to HLS failed on every live channel.
 */
class XtreamLiveFormatTest {

    private val json = XtreamClient.defaultJson

    @Test
    fun `automatic asks for hls when the account may use it`() {
        assertEquals("m3u8", XtreamUrl.liveExtension(LiveFormat.AUTO, setOf("m3u8", "ts")))
        assertEquals("m3u8", XtreamUrl.liveExtension(LiveFormat.AUTO, setOf("m3u8")))
    }

    @Test
    fun `automatic asks for ts when hls is not allowed, or nobody said`() {
        assertEquals("ts", XtreamUrl.liveExtension(LiveFormat.AUTO, setOf("ts", "rtmp")))
        assertEquals("ts", XtreamUrl.liveExtension(LiveFormat.AUTO, null))
        assertEquals("ts", XtreamUrl.liveExtension(LiveFormat.AUTO, emptySet()))
    }

    @Test
    fun `a choice made by hand is kept whatever the panel says`() {
        assertEquals("ts", XtreamUrl.liveExtension(LiveFormat.TS, setOf("m3u8")))
        assertEquals("m3u8", XtreamUrl.liveExtension(LiveFormat.HLS, setOf("ts")))
    }

    @Test
    fun `the choice reaches the url, and only for live`() {
        val extension = XtreamUrl.liveExtension(LiveFormat.AUTO, setOf("m3u8"))
        val hls = { locator: String -> XtreamUrl.resolve("http://h.invalid", "u", "p", locator, extension) }
        assertEquals("http://h.invalid/live/u/p/101.m3u8", hls("xtream:live/101"))
        assertEquals("http://h.invalid/movie/u/p/7.mkv", hls("xtream:movie/7.mkv"))
    }

    @Test
    fun `allowed formats are read whatever case they arrive in`() {
        val user = json.decodeFromString<UserInfo>("""{"allowed_output_formats":["M3U8"," ts ","rtmp"]}""")

        assertEquals(listOf("m3u8", "ts", "rtmp"), user.allowedOutputFormats)
    }

    @Test
    fun `a panel that sends something other than a list has said nothing`() {
        assertNull(json.decodeFromString<UserInfo>("""{"allowed_output_formats":"m3u8"}""").allowedOutputFormats)
        assertNull(json.decodeFromString<UserInfo>("""{}""").allowedOutputFormats)
    }

    @Test
    fun `a load reports the formats the account may use, for the source to keep`() = runTest {
        val source = createXtreamSource(
            HttpClient(
                MockEngine { call ->
                    val body = when (call.url.parameters["action"]) {
                        null -> """{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["m3u8","ts"]}}"""
                        "get_live_categories" -> """[{"category_id":"1","category_name":"News"}]"""
                        "get_live_streams" -> """[{"stream_id":101,"name":"Alpha","category_id":"1"}]"""
                        else -> "[]"
                    }
                    respond(body, headers = headersOf("Content-Type", "application/json"))
                },
            ),
            object : CredentialStore {
                override suspend fun credentials(sourceId: Long) = Credentials("u", "p")
                override suspend fun put(sourceId: Long, credentials: Credentials) = Unit
                override suspend fun clear(sourceId: Long) = Unit
            },
            object : PanelBlockStore {
                override suspend fun blockedUntil(): Long = 0L
                override suspend fun setBlockedUntil(epochMillis: Long) = Unit
            },
        )

        val result = source.load(SourceRequest(1L, "panel.example.invalid")) as SourceResult.Success

        assertEquals(setOf("m3u8", "ts"), result.allowedLiveFormats)
    }
}
