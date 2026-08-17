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

package dev.quiblo.core.network.update

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** How a download ended. */
sealed interface DownloadResult {

    /** The file is on disk and its checksum matched the one published beside it. */
    data class Downloaded(val file: File) : DownloadResult

    /** The bytes arrived and are not the bytes that were published. The file is not kept. */
    data object ChecksumMismatch : DownloadResult

    /** The release published no checksum, so nothing could be verified. */
    data object NoChecksum : DownloadResult

    data object Unreachable : DownloadResult
}

/**
 * Fetches a release APK and proves it is the one that was published.
 *
 * **The checksum is not optional and not advisory.** This is the one place in Quiblo that
 * downloads an executable and hands it to the package installer, and a download that goes
 * straight there is a download that installs whatever arrived — a truncated file, a captive
 * portal's login page, or a substitution somewhere between GitHub and the sofa. So the `.sha256`
 * the release lane publishes beside every APK is fetched too, and a file that does not match it
 * is deleted rather than offered.
 *
 * **A release with no checksum is refused**, rather than installed with a warning. Every release
 * this project has made publishes one; a release that does not is a release something has gone
 * wrong with, and "are you sure?" on a television is a button people press.
 *
 * Ktor never leaves `:core:network`, which is why the download lives here rather than beside the
 * installer that uses it.
 */
class ReleaseDownloader(private val client: HttpClient) {

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun download(apkUrl: String, checksumUrl: String?, into: File): DownloadResult {
        if (checksumUrl == null) return DownloadResult.NoChecksum

        return withContext(Dispatchers.IO) {
            try {
                val expected = readChecksum(checksumUrl) ?: return@withContext DownloadResult.Unreachable

                into.parentFile?.mkdirs()
                val streamed = streamTo(apkUrl, into)
                if (!streamed) return@withContext DownloadResult.Unreachable

                if (sha256Of(into).equals(expected, ignoreCase = true)) {
                    DownloadResult.Downloaded(into)
                } else {
                    // Not kept, not renamed aside, not left for a later attempt to find: an APK
                    // that failed its checksum is the one file this app must not leave lying
                    // where a file manager can install it.
                    into.delete()
                    DownloadResult.ChecksumMismatch
                }
            } catch (_: Exception) {
                into.delete()
                DownloadResult.Unreachable
            }
        }
    }

    /**
     * Reads the hash out of a `.sha256` file.
     *
     * `sha256sum` writes `<hash>  <filename>`, and some tools write the hash alone. The first
     * whitespace-delimited word is both.
     */
    private suspend fun readChecksum(url: String): String? {
        val response = client.get(url)
        if (!response.status.isSuccess()) return null
        return response.bodyAsText().trim().substringBefore(' ').trim().takeIf { it.isNotEmpty() }
    }

    /** Streamed rather than buffered: an APK is tens of megabytes and a television has little. */
    private suspend fun streamTo(url: String, destination: File): Boolean =
        client.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) return@execute false
            response.bodyAsChannel().toInputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            true
        }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
