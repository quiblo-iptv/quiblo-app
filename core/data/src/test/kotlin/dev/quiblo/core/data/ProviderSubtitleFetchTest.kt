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
package dev.quiblo.core.data

import dev.quiblo.core.database.dao.PickedSubtitleDao
import dev.quiblo.core.database.entity.PickedSubtitleEntity
import dev.quiblo.core.model.SubtitleFile
import dev.quiblo.source.api.ContentFetcher
import dev.quiblo.source.api.FetchResult
import dev.quiblo.source.api.FetchedBody
import dev.quiblo.source.api.SourceError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * A panel's subtitle is fetched and checked before the engine sees it (`BUG-045`).
 *
 * Handed to the engine as a URL, a dead one failed the whole film. Every URL here is synthetic.
 */
class ProviderSubtitleFetchTest {

    @TempDir
    lateinit var storage: File

    private val remote = SubtitleFile(
        uri = "http://panel.example.invalid/subtitle/12345.srt",
        label = "English",
        mimeType = "application/x-subrip",
        language = "en",
    )

    private val srt = "1\n00:00:01,000 --> 00:00:04,000\nHello\n"

    private fun repository(
        fetcher: ContentFetcher?,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = SubtitleRepository(
        dao = object : PickedSubtitleDao {
            override suspend fun forTitle(stableKey: String): PickedSubtitleEntity? = null
            override suspend fun all(): List<PickedSubtitleEntity> = emptyList()
            override suspend fun upsert(subtitle: PickedSubtitleEntity) = Unit
            override suspend fun delete(stableKey: String) = Unit
        },
        files = object : PickedSubtitleFiles {
            override fun nameOf(uri: String): String? = null
            override fun bytesOf(uri: String, limit: Int): ByteArray? = null
            override fun storageDirectory() = storage
        },
        ioDispatcher = dispatcher,
        fetcher = fetcher,
    )

    /** Answers every request with [bytes], or with [failure]. */
    private fun serving(bytes: ByteArray? = null, failure: SourceError? = null) = object : ContentFetcher {
        override fun handles(location: String) = true

        override suspend fun <T> fetch(location: String, read: suspend (FetchedBody) -> T): FetchResult<T> =
            if (failure != null) {
                FetchResult.Failure(failure)
            } else {
                FetchResult.Success(read(FetchedBody(contentType = null, stream = bytes!!.inputStream())))
            }
    }

    @Test
    fun `a subtitle that arrives is played from a local copy, not from the panel`() = runTest {
        val result = repository(serving(srt.toByteArray())).fetchProviderSubtitle(remote)

        val fetched = (result as ProviderSubtitleResult.Fetched).subtitle
        assertTrue(fetched.uri.startsWith("file://"), fetched.uri)
        assertEquals(srt, File(fetched.uri.removePrefix("file://")).readText())
        assertEquals("English", fetched.label)
        assertEquals("en", fetched.language)
    }

    @Test
    fun `its format is read from what arrived, not from its name`() = runTest {
        val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:04.000\nHello\n"

        val result = repository(serving(vtt.toByteArray())).fetchProviderSubtitle(remote)

        assertEquals("text/vtt", (result as ProviderSubtitleResult.Fetched).subtitle.mimeType)
    }

    @Test
    fun `a dead link is unavailable, and nothing more`() = runTest {
        val result = repository(serving(failure = SourceError.NotFound)).fetchProviderSubtitle(remote)

        assertEquals(ProviderSubtitleResult.Unavailable, result)
    }

    @Test
    fun `a panel's error page is not subtitles`() = runTest {
        val page = "<!DOCTYPE html><html><body>00:00:01,000 --> 00:00:04,000 Not found</body></html>"

        val result = repository(serving(page.toByteArray())).fetchProviderSubtitle(remote)

        assertEquals(ProviderSubtitleResult.Unavailable, result)
    }

    @Test
    fun `a link that turns out to be a film is refused at the cap`() = runTest {
        val tooLarge = ByteArray(MAX_SUBTITLE_BYTES + 1) { 'a'.code.toByte() }

        val result = repository(serving(tooLarge)).fetchProviderSubtitle(remote)

        assertEquals(ProviderSubtitleResult.Unavailable, result)
    }

    @Test
    fun `a server that never answers is given up on`() = runTest {
        val silent = object : ContentFetcher {
            override fun handles(location: String) = true
            override suspend fun <T> fetch(location: String, read: suspend (FetchedBody) -> T): FetchResult<T> =
                awaitCancellation()
        }

        val result = repository(silent, StandardTestDispatcher(testScheduler)).fetchProviderSubtitle(remote)

        assertEquals(ProviderSubtitleResult.Unavailable, result)
    }

    private companion object {
        /** Mirrors `SubtitleRepository.MAX_SUBTITLE_BYTES`, which is private to it. */
        const val MAX_SUBTITLE_BYTES = 8 * 1024 * 1024
    }
}
