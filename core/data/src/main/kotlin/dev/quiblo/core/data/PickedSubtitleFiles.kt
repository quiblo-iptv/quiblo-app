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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Everything [SubtitleRepository] needs from the device, and nothing else.
 *
 * Three methods, all of them the platform's answer to a question the repository has no other way
 * to ask. Pulling them out is what keeps the decisions — what is this file, is it readable, where
 * does the copy go — in a class that can be tested with a byte array and a temporary folder.
 */
interface PickedSubtitleFiles {

    /** The file's own name as the picker reports it, or null when it will not say. */
    fun nameOf(uri: String): String?

    /**
     * Up to [limit] bytes of the file, or null when it cannot be opened at all.
     *
     * **The limit is the point of this signature.** It used to read the whole file and let the
     * caller check the size afterwards, which cannot work: the picker hands back whatever the
     * viewer tapped, and a mis-tapped film is several gigabytes that are on the heap before
     * anything gets to refuse them. The read stops at the limit instead, and the caller decides
     * what an over-long file means.
     *
     * @return at most [limit] bytes. A result of exactly [limit] means the file may be longer.
     */
    fun bytesOf(uri: String, limit: Int): ByteArray?

    /** Where copies live. Created if it is not there yet. */
    fun storageDirectory(): File
}

/**
 * The real one, over `ContentResolver`.
 *
 * Copies live in `filesDir` and not in the cache. The system is free to empty a cache under
 * storage pressure, and a subtitle a viewer chose by hand disappearing on its own is worse than
 * one that was never offered. A subtitle file is tens of kilobytes; there is nothing to save by
 * putting it somewhere the system may reclaim.
 *
 * Every call is wrapped: a picker can return a URI that another app has already revoked, and the
 * honest answer to that is "no file", not a crash inside a subtitle menu.
 */
class AndroidPickedSubtitleFiles(context: Context) : PickedSubtitleFiles {

    private val appContext = context.applicationContext

    override fun nameOf(uri: String): String? = runCatching {
        val parsed = Uri.parse(uri)
        appContext.contentResolver
            .query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: parsed.lastPathSegment
    }.getOrNull()

    /**
     * Reads at most [limit] bytes, and never more, whatever the picker handed over.
     *
     * Only `IOException` and `SecurityException` are caught, which are the two honest answers
     * from a revoked or unreadable URI. The blanket `runCatching` this replaced caught
     * `Throwable` — so an `OutOfMemoryError` from reading a multi-gigabyte file became a
     * cheerful "no file" while the heap was already in trouble.
     */
    override fun bytesOf(uri: String, limit: Int): ByteArray? = try {
        appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readAtMost(limit) }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /**
     * A bounded read loop.
     *
     * `readBytes()` is unbounded, which is the bug this exists to close, and `readNBytes` is a
     * Java 9 API this project's minSdk cannot rely on.
     */
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read <= 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    override fun storageDirectory(): File =
        File(appContext.filesDir, SUBTITLE_DIRECTORY).apply { mkdirs() }

    private companion object {
        const val SUBTITLE_DIRECTORY = "subtitles"
        const val READ_CHUNK_BYTES = 16 * 1024
    }
}
