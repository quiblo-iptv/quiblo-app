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

import dev.quiblo.core.common.SUBTITLE_SNIFF_BYTES
import dev.quiblo.core.common.SubtitleFormat
import dev.quiblo.core.common.decodeSubtitle
import dev.quiblo.core.common.sniffSubtitleFormat
import dev.quiblo.core.common.subtitleFormatOfName
import dev.quiblo.core.common.subtitleLanguageOfName
import dev.quiblo.core.database.dao.PickedSubtitleDao
import dev.quiblo.core.database.entity.PickedSubtitleEntity
import dev.quiblo.core.model.SubtitleFile
import dev.quiblo.core.model.SubtitleOrigin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Subtitle files a viewer picked from their device, kept against the title (INC-F10).
 *
 * **The file is copied, not referenced.** The picker returns a `content://` URI carrying a grant
 * that dies with this process, so remembering the URI would remember something that stops working
 * the next time the app is opened — the shape of bug that looks like a storage failure and is not.
 * Copying also gives the one place to fix the encoding: a `.srt` written in windows-1256 is read
 * as Arabic here and written back out as UTF-8, so the engine, which assumes UTF-8, is right.
 *
 * Everything that touches the device is behind [PickedSubtitleFiles], which is what lets the part
 * worth testing — read this file, decide what it is, write it back readable, remember where —
 * be tested with bytes and a temporary directory rather than an emulator.
 */
class SubtitleRepository(
    private val dao: PickedSubtitleDao,
    private val files: PickedSubtitleFiles,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * The subtitle picked for [stableKey], if there is one that still exists.
     *
     * A row whose file has gone — a restored backup, cleared storage, a bug — is deleted rather
     * than returned. Handing the engine a path to nothing produces a track that fails to load and
     * a menu entry that does nothing when chosen, which is the worst of the three answers.
     */
    suspend fun forTitle(stableKey: String): List<SubtitleFile> = withContext(ioDispatcher) {
        val row = dao.forTitle(stableKey) ?: return@withContext emptyList()
        val file = File(row.storedPath)
        if (!file.exists()) {
            dao.delete(stableKey)
            return@withContext emptyList()
        }
        listOf(
            SubtitleFile(
                uri = file.toPlaybackUri(),
                label = row.label,
                mimeType = row.mimeType,
                language = row.language,
                origin = SubtitleOrigin.PICKED,
            ),
        )
    }

    /**
     * Copies the file at [pickedUri] into this app and remembers it for [stableKey].
     *
     * The format is read from the file's own first few kilobytes and only falls back to its name,
     * because a name is a claim: panels and download sites both serve `.srt` files that are
     * WebVTT inside, and the engine is told what it is actually being handed.
     *
     * Replaces whatever was picked for this title before, file and row together.
     */
    suspend fun attach(stableKey: String, pickedUri: String): AttachResult =
        withContext(ioDispatcher) {
            val name = files.nameOf(pickedUri).orEmpty()
            val bytes = files.bytesOf(pickedUri) ?: return@withContext AttachResult.Unreadable
            if (bytes.size > MAX_SUBTITLE_BYTES) return@withContext AttachResult.TooLarge

            val text = decodeSubtitle(bytes)
            val format = sniffSubtitleFormat(text.take(SUBTITLE_SNIFF_BYTES))
                ?: subtitleFormatOfName(name)
                ?: return@withContext AttachResult.NotSubtitles

            AttachResult.Attached(store(stableKey, name, text, format))
        }

    /** Forgets the picked file and deletes the copy. Anything the panel supplies is untouched. */
    suspend fun detach(stableKey: String) = withContext(ioDispatcher) {
        dao.forTitle(stableKey)?.let { File(it.storedPath).delete() }
        dao.delete(stableKey)
    }

    private suspend fun store(
        stableKey: String,
        name: String,
        text: String,
        format: SubtitleFormat,
    ): SubtitleFile {
        val directory = files.storageDirectory()
        val fingerprint = fingerprint(stableKey)
        val file = File(directory, "$fingerprint.${format.extension}")
        file.writeText(text)

        // The copy this title had before, if it was in another format. Same title, same
        // fingerprint, different extension — so replacing the row does not replace the file.
        directory
            .listFiles { candidate -> candidate != file && candidate.name.startsWith(fingerprint) }
            ?.forEach { it.delete() }

        // A picked file always has a name, unlike one a panel supplies: the viewer chose it out
        // of a list, so the thing they chose it by is the thing the menu should call it.
        val label = name.ifBlank { file.name }
        val subtitle = SubtitleFile(
            uri = file.toPlaybackUri(),
            label = label,
            mimeType = format.mimeType,
            language = subtitleLanguageOfName(name),
            origin = SubtitleOrigin.PICKED,
        )

        dao.upsert(
            PickedSubtitleEntity(
                stableKey = stableKey,
                storedPath = file.absolutePath,
                label = label,
                mimeType = subtitle.mimeType,
                language = subtitle.language,
                pickedAt = now(),
            ),
        )
        return subtitle
    }

    /** A stable, filesystem-safe name for a title whose key may well be a URL. */
    private fun fingerprint(stableKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(stableKey.toByteArray())
            .take(FINGERPRINT_BYTES)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        /** Well past any real subtitle file, and well short of anything worth holding in memory. */
        const val MAX_SUBTITLE_BYTES = 8 * 1024 * 1024

        const val FINGERPRINT_BYTES = 16
    }
}

/**
 * Built by hand rather than through `Uri`, so this class stays testable off a device.
 *
 * The path is this app's own storage and the name is hexadecimal, so there is nothing in it that
 * needs escaping — which is the only reason building a URI by concatenation is defensible here.
 */
private fun File.toPlaybackUri(): String = "file://$absolutePath"

/** What happened when a viewer picked a file. */
sealed interface AttachResult {

    data class Attached(val subtitle: SubtitleFile) : AttachResult

    /** Readable, but nothing in it looks like any subtitle format. Usually the wrong file. */
    data object NotSubtitles : AttachResult

    /** The picker returned something this app could not open at all. */
    data object Unreadable : AttachResult

    data object TooLarge : AttachResult
}
