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
import dev.quiblo.core.model.SubtitleOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class SubtitleRepositoryTest {

    @TempDir
    lateinit var storage: File

    private val dao = FakePickedSubtitleDao()

    private class FakePickedSubtitleDao : PickedSubtitleDao {
        val rows = mutableMapOf<String, PickedSubtitleEntity>()

        override suspend fun forTitle(stableKey: String) = rows[stableKey]

        override suspend fun all() = rows.values.toList()

        override suspend fun upsert(subtitle: PickedSubtitleEntity) {
            rows[subtitle.stableKey] = subtitle
        }

        override suspend fun delete(stableKey: String) {
            rows.remove(stableKey)
        }
    }

    private inner class FakeFiles(
        private val name: String?,
        private val bytes: ByteArray?,
    ) : PickedSubtitleFiles {
        override fun nameOf(uri: String) = name

        /** Truncates at [limit], the way the real reader does, so the cap is actually exercised. */
        override fun bytesOf(uri: String, limit: Int) = bytes?.copyOf(minOf(bytes.size, limit))

        override fun storageDirectory() = storage
    }

    private fun repository(name: String?, bytes: ByteArray?) = SubtitleRepository(
        dao = dao,
        files = FakeFiles(name, bytes),
        // Unconfined rather than a test dispatcher: this repository reads and writes real
        // files, so there is nothing to virtualise and two schedulers would only fight.
        ioDispatcher = Dispatchers.Unconfined,
        now = { FIXED_NOW },
    )

    private val arabicSrt = """
        1
        00:00:01,000 --> 00:00:04,000
        الحلقة الأولى من هذا المسلسل تبدأ الليلة على الشاشة
    """.trimIndent()

    @Test
    fun `a picked file is copied, remembered and offered back`() = runTest {
        val repository = repository("Dune.2021.ar.srt", arabicSrt.toByteArray())

        val result = repository.attach(KEY, "content://picker/1")

        assertTrue(result is AttachResult.Attached)
        val subtitle = (result as AttachResult.Attached).subtitle
        assertEquals("Dune.2021.ar.srt", subtitle.label)
        assertEquals("ar", subtitle.language)
        assertEquals("application/x-subrip", subtitle.mimeType)
        assertEquals(SubtitleOrigin.PICKED, subtitle.origin)
        assertEquals(listOf(subtitle), repository.forTitle(KEY))
    }

    @Test
    fun `a windows-1256 file is stored as utf-8, which is what the engine reads`() = runTest {
        val encoded = runCatching { arabicSrt.toByteArray(Charset.forName("windows-1256")) }.getOrNull()
        assumeTrue(encoded != null, "windows-1256 is not available on this platform")

        repository("subs.srt", encoded).attach(KEY, "content://picker/1")

        // The whole point of copying rather than referencing. Handed to the engine untouched,
        // these bytes are not valid UTF-8 and the film plays under a line of symbols.
        val stored = File(dao.rows.getValue(KEY).storedPath)
        assertEquals(arabicSrt, stored.readText(StandardCharsets.UTF_8))
    }

    @Test
    fun `the file is believed over its name`() = runTest {
        // A WebVTT file served as `.srt`, which panels and download sites both do.
        val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:04.000\nHello\n"

        val result = repository("subs.srt", vtt.toByteArray()).attach(KEY, "content://picker/1")

        assertEquals("text/vtt", (result as AttachResult.Attached).subtitle.mimeType)
    }

    @Test
    fun `the name is used only when the content says nothing`() = runTest {
        val result = repository("subs.srt", "".toByteArray()).attach(KEY, "content://picker/1")

        assertEquals("application/x-subrip", (result as AttachResult.Attached).subtitle.mimeType)
    }

    @Test
    fun `the wrong file is refused rather than stored`() = runTest {
        val result = repository("holiday.jpg", "not subtitles at all".toByteArray())
            .attach(KEY, "content://picker/1")

        assertEquals(AttachResult.NotSubtitles, result)
        assertNull(dao.rows[KEY])
    }

    @Test
    fun `a file that will not open is reported, not crashed on`() = runTest {
        assertEquals(
            AttachResult.Unreadable,
            repository("subs.srt", null).attach(KEY, "content://picker/1"),
        )
    }

    @Test
    fun `picking a second file replaces the first, on disk as well as in the row`() = runTest {
        repository("subs.srt", arabicSrt.toByteArray()).attach(KEY, "content://picker/1")
        val first = File(dao.rows.getValue(KEY).storedPath)

        repository("subs.vtt", "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi\n".toByteArray())
            .attach(KEY, "content://picker/2")

        val second = File(dao.rows.getValue(KEY).storedPath)
        assertFalse(first.exists(), "the replaced copy is still on disk")
        assertTrue(second.exists())
        assertEquals(1, repository("x", null).forTitle(KEY).size)
    }

    @Test
    fun `a row whose file has gone is forgotten rather than handed to the engine`() = runTest {
        val repository = repository("subs.srt", arabicSrt.toByteArray())
        repository.attach(KEY, "content://picker/1")
        assertNotNull(dao.rows[KEY])

        File(dao.rows.getValue(KEY).storedPath).delete()

        assertEquals(emptyList<Any>(), repository.forTitle(KEY))
        assertNull(dao.rows[KEY])
    }

    @Test
    fun `detaching removes both the row and the copy`() = runTest {
        val repository = repository("subs.srt", arabicSrt.toByteArray())
        repository.attach(KEY, "content://picker/1")
        val stored = File(dao.rows.getValue(KEY).storedPath)

        repository.detach(KEY)

        assertFalse(stored.exists())
        assertNull(dao.rows[KEY])
    }

    @Test
    fun `two titles do not share a copy`() = runTest {
        val repository = repository("subs.srt", arabicSrt.toByteArray())

        repository.attach(KEY, "content://picker/1")
        repository.attach("http://panel.invalid/other.mkv", "content://picker/1")

        assertEquals(2, dao.rows.size)
        assertEquals(2, dao.rows.values.map { it.storedPath }.toSet().size)
    }

    @Test
    fun `a file past the cap is refused without being read whole`() = runTest {
        var requestedLimit = Int.MAX_VALUE
        val oversized = ByteArray(MAX_SUBTITLE_BYTES + 64) { '.'.code.toByte() }
        val files = object : PickedSubtitleFiles {
            override fun nameOf(uri: String) = "huge.srt"
            override fun bytesOf(uri: String, limit: Int): ByteArray {
                requestedLimit = limit
                return oversized.copyOf(minOf(oversized.size, limit))
            }
            override fun storageDirectory() = storage
        }
        val repository = SubtitleRepository(
            dao = dao,
            files = files,
            ioDispatcher = Dispatchers.Unconfined,
            now = { FIXED_NOW },
        )

        val result = repository.attach(KEY, "content://picker/1")

        assertTrue(result is AttachResult.TooLarge)
        assertNull(dao.rows[KEY], "an over-long file was still written down")
        // The guard is only a guard if it bounds the read. Asking for the whole file and
        // measuring it afterwards is how a mis-tapped film becomes an OutOfMemoryError.
        assertEquals(MAX_SUBTITLE_BYTES + 1, requestedLimit)
    }

    private companion object {
        const val KEY = "http://panel.invalid/movie/1.mkv"
        const val FIXED_NOW = 1_770_000_000_000L

        /** Mirrors `SubtitleRepository.MAX_SUBTITLE_BYTES`, which is private to it. */
        const val MAX_SUBTITLE_BYTES = 8 * 1024 * 1024
    }
}
