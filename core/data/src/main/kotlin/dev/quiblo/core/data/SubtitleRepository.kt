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
import dev.quiblo.source.api.ContentFetcher
import dev.quiblo.source.api.FetchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    /** Fetches a panel's subtitle before it goes anywhere near the engine (`BUG-045`). */
    private val fetcher: ContentFetcher? = null,
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

            // One byte past the cap, so "too long" is answered by the read itself rather than
            // after it. Asking for the whole file and measuring it afterwards is not a guard at
            // all — the file is already on the heap by the time the size is known, and a viewer
            // who mis-taps a film in the picker gets an OutOfMemoryError instead of a message.
            val bytes = files.bytesOf(pickedUri, MAX_SUBTITLE_BYTES + 1)
                ?: return@withContext AttachResult.Unreadable
            if (bytes.size > MAX_SUBTITLE_BYTES) return@withContext AttachResult.TooLarge

            val text = decodeSubtitle(bytes)
            val format = sniffSubtitleFormat(text.take(SUBTITLE_SNIFF_BYTES))
                ?: subtitleFormatOfName(name)
                ?: return@withContext AttachResult.NotSubtitles

            AttachResult.Attached(store(stableKey, name, text, format))
        }

    /**
     * Fetches a subtitle the panel lists and keeps a copy the engine can read (`BUG-045`).
     *
     * **The engine is never handed a panel's subtitle URL.** It loads a sidecar only once its
     * track is selected, and a sidecar that fails to load fails the whole item — so a dead link,
     * which panels list often, stopped a film that was playing fine. Fetched here instead, with a
     * short timeout, the same size cap and the same content check as a picked file, a dead link
     * costs one subtitle and the film plays on.
     *
     * The format is read from the bytes, never guessed from the URL, and something that is not
     * subtitles — a panel's HTML error page served with a 200 — is refused rather than handed on.
     * The copy is not remembered against the title: the panel offers it again next time.
     */
    suspend fun fetchProviderSubtitle(remote: SubtitleFile): ProviderSubtitleResult =
        withContext(ioDispatcher) {
            val fetcher = fetcher ?: return@withContext ProviderSubtitleResult.Unavailable
            val fetched = withTimeoutOrNull(PROVIDER_FETCH_TIMEOUT_MILLIS) {
                fetcher.fetch(remote.uri) { body -> body.bytes(MAX_SUBTITLE_BYTES + 1) }
            }
            val bytes = (fetched as? FetchResult.Success)?.value
                ?: return@withContext ProviderSubtitleResult.Unavailable
            if (bytes.size > MAX_SUBTITLE_BYTES) return@withContext ProviderSubtitleResult.Unavailable

            val text = decodeSubtitle(bytes)
            val head = text.take(SUBTITLE_SNIFF_BYTES)
            if (head.contains("<html", ignoreCase = true)) return@withContext ProviderSubtitleResult.Unavailable
            val format = sniffSubtitleFormat(head) ?: return@withContext ProviderSubtitleResult.Unavailable

            val directory = File(files.storageDirectory(), PROVIDER_DIRECTORY).apply { mkdirs() }
            val fingerprint = fingerprint(remote.uri)
            val file = File(directory, "$fingerprint.${format.extension}")
            file.writeText(text)
            pruneProviderCopies(directory, keep = file)

            ProviderSubtitleResult.Fetched(remote.copy(uri = file.toPlaybackUri(), mimeType = format.mimeType))
        }

    /** Keeps the newest few copies. Each is a few kilobytes, but nothing else ever deletes them. */
    private fun pruneProviderCopies(directory: File, keep: File) {
        directory.listFiles()
            ?.filter { it != keep }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_PROVIDER_COPIES - 1)
            ?.forEach { it.delete() }
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

        /** Short: the viewer is waiting, the film is playing, and a working server answers in one. */
        const val PROVIDER_FETCH_TIMEOUT_MILLIS = 5_000L

        const val PROVIDER_DIRECTORY = "provider"
        const val MAX_PROVIDER_COPIES = 50
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

/** What came of fetching a subtitle the panel lists (`BUG-045`). */
sealed interface ProviderSubtitleResult {

    /** @property subtitle the same subtitle, now pointing at a local copy the engine can read. */
    data class Fetched(val subtitle: SubtitleFile) : ProviderSubtitleResult

    /**
     * Not there, too slow, too large, or not subtitles. One answer for all of them: the viewer
     * can do nothing different about any of them, and the menu says the same thing either way.
     */
    data object Unavailable : ProviderSubtitleResult
}
