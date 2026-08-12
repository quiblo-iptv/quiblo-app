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
import java.io.File

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

    /** The whole file, or null when it cannot be opened at all. */
    fun bytesOf(uri: String): ByteArray?

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

    override fun bytesOf(uri: String): ByteArray? = runCatching {
        appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
    }.getOrNull()

    override fun storageDirectory(): File =
        File(appContext.filesDir, SUBTITLE_DIRECTORY).apply { mkdirs() }

    private companion object {
        const val SUBTITLE_DIRECTORY = "subtitles"
    }
}
