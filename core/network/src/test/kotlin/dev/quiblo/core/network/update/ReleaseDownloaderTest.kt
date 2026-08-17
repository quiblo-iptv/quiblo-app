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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest

/**
 * The one place Quiblo downloads something and offers to install it.
 *
 * Every case here is about what happens when the bytes are *not* the published ones. That is the
 * whole reason the checksum step exists, and it is the step that is easy to write, easy to
 * believe, and impossible to notice is broken — a verification that always passes looks exactly
 * like a verification that works.
 */
class ReleaseDownloaderTest {

    @TempDir
    lateinit var directory: File

    @Test
    fun `a file matching its published checksum is kept`() = runTest {
        val downloader = downloaderServing(APK_BYTES, sha256(APK_BYTES))
        val destination = File(directory, "quiblo-tv.apk")

        val result = downloader.download(APK_URL, CHECKSUM_URL, destination)

        val downloaded = assertInstanceOf(DownloadResult.Downloaded::class.java, result)
        assertEquals(APK_BYTES, downloaded.file.readText())
    }

    /** `sha256sum` writes `<hash>  <filename>`; the hash is the first word of it. */
    @Test
    fun `the checksum file's filename column is not part of the hash`() = runTest {
        val downloader = downloaderServing(APK_BYTES, "${sha256(APK_BYTES)}  quiblo-tv-v0.19.0.apk")

        val result = downloader.download(APK_URL, CHECKSUM_URL, File(directory, "quiblo-tv.apk"))

        assertInstanceOf(DownloadResult.Downloaded::class.java, result)
    }

    /**
     * Bytes that are not the published bytes are refused, and the file does not survive.
     *
     * Leaving it on disk would be worse than not checking at all: a file manager two menus away
     * will happily install whatever is sitting in the updates directory, and the viewer would
     * have been told the download failed.
     */
    @Test
    fun `a mismatched download is refused and deleted`() = runTest {
        val downloader = downloaderServing("something else entirely", sha256(APK_BYTES))
        val destination = File(directory, "quiblo-tv.apk")

        val result = downloader.download(APK_URL, CHECKSUM_URL, destination)

        assertEquals(DownloadResult.ChecksumMismatch, result)
        assertFalse(destination.exists(), "A rejected APK must not be left where anything can install it.")
    }

    /** A release with nothing to verify against is not installed on trust. */
    @Test
    fun `a release with no checksum is not downloaded at all`() = runTest {
        val downloader = downloaderServing(APK_BYTES, sha256(APK_BYTES))
        val destination = File(directory, "quiblo-tv.apk")

        val result = downloader.download(APK_URL, checksumUrl = null, into = destination)

        assertEquals(DownloadResult.NoChecksum, result)
        assertFalse(destination.exists())
    }

    @Test
    fun `a checksum that cannot be fetched stops the download`() = runTest {
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })
        val destination = File(directory, "quiblo-tv.apk")

        val result = ReleaseDownloader(client).download(APK_URL, CHECKSUM_URL, destination)

        assertEquals(DownloadResult.Unreachable, result)
        assertFalse(destination.exists())
    }

    @Test
    fun `a download that fails part way leaves nothing behind`() = runTest {
        var call = 0
        val client = HttpClient(
            MockEngine {
                call++
                if (call == 1) respond(sha256(APK_BYTES)) else throw java.io.IOException("cut off")
            },
        )
        val destination = File(directory, "quiblo-tv.apk")

        val result = ReleaseDownloader(client).download(APK_URL, CHECKSUM_URL, destination)

        assertEquals(DownloadResult.Unreachable, result)
        assertFalse(destination.exists())
    }

    /** The directory the APK goes into may not exist yet on a first check. */
    @Test
    fun `the destination directory is created`() = runTest {
        val downloader = downloaderServing(APK_BYTES, sha256(APK_BYTES))
        val nested = File(directory, "updates/quiblo-tv.apk")

        downloader.download(APK_URL, CHECKSUM_URL, nested)

        assertTrue(nested.exists())
    }

    /**
     * The checksum request comes first, and the APK second.
     *
     * Written down because the order is what makes "no checksum, no download" true rather than
     * "download, then find out there was nothing to check it against".
     */
    private fun downloaderServing(apkBody: String, checksumBody: String): ReleaseDownloader {
        val engine = MockEngine { request ->
            if (request.url.toString() == CHECKSUM_URL) respond(checksumBody) else respond(apkBody)
        }
        return ReleaseDownloader(HttpClient(engine))
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val APK_URL = "https://example.invalid/quiblo-tv-v0.19.0.apk"
        const val CHECKSUM_URL = "https://example.invalid/quiblo-tv-v0.19.0.apk.sha256"

        /** Not a real APK. The verification is over bytes and does not care which bytes. */
        const val APK_BYTES = "PK pretend this is a television build"
    }
}
