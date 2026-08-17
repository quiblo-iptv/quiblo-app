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

import dev.quiblo.core.network.ConnectivityChecker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

/**
 * What the update check makes of a real GitHub release payload.
 *
 * The fixture below is the shape of `releases/latest` for this project: two APKs and a checksum
 * beside each, because both applications ship out of one release. Choosing the wrong one of those
 * two is the mistake that cannot be caught by reading the code — both names start `quiblo-`.
 */
class ReleaseCheckerTest {

    private val online = ConnectivityChecker { true }
    private val offline = ConnectivityChecker { false }

    @Test
    fun `an older build is offered the newer release`() = runTest {
        val outcome = checkerReturning(RELEASE_JSON).check("0.18.0", ReleaseChecker.TV_ASSET_PREFIX)

        val available = assertInstanceOf(UpdateCheck.Available::class.java, outcome)
        assertEquals("v0.19.0", available.version)
    }

    /**
     * The television build, never the handset one.
     *
     * Both sit in the same release and both start `quiblo-`, so a prefix match that is one
     * character short installs the phone app onto a television — which will install, and will
     * then be an app with no leanback launcher on a device with no touchscreen.
     */
    @Test
    fun `the television APK is picked out of a release holding both`() = runTest {
        val outcome = checkerReturning(RELEASE_JSON).check("0.18.0", ReleaseChecker.TV_ASSET_PREFIX)

        val available = assertInstanceOf(UpdateCheck.Available::class.java, outcome)
        assertEquals("https://example.invalid/quiblo-tv-v0.19.0.apk", available.apkUrl)
        assertEquals("https://example.invalid/quiblo-tv-v0.19.0.apk.sha256", available.checksumUrl)
    }

    @Test
    fun `the newest build is told it is the newest`() = runTest {
        assertEquals(
            UpdateCheck.UpToDate,
            checkerReturning(RELEASE_JSON).check("0.19.0", ReleaseChecker.TV_ASSET_PREFIX),
        )
    }

    /** A build ahead of the releases page — an alpha off a branch — is not offered a downgrade. */
    @Test
    fun `a build newer than the release is not offered it`() = runTest {
        assertEquals(
            UpdateCheck.UpToDate,
            checkerReturning(RELEASE_JSON).check("0.20.0", ReleaseChecker.TV_ASSET_PREFIX),
        )
    }

    @Test
    fun `a release with no television build says so`() = runTest {
        val outcome = checkerReturning(PHONE_ONLY_JSON).check("0.18.0", ReleaseChecker.TV_ASSET_PREFIX)

        assertEquals(UpdateCheck.Failed(UpdateCheck.Failed.Reason.NO_ASSET), outcome)
    }

    @Test
    fun `an offline television is not told it is up to date`() = runTest {
        val checker = ReleaseChecker(HttpClient(MockEngine { respond("") }), offline)

        assertEquals(
            UpdateCheck.Failed(UpdateCheck.Failed.Reason.OFFLINE),
            checker.check("0.18.0", ReleaseChecker.TV_ASSET_PREFIX),
        )
    }

    @Test
    fun `an unreachable releases page is not told as up to date either`() = runTest {
        val checker = ReleaseChecker(HttpClient(MockEngine { throw UnknownHostException() }), online)

        assertEquals(
            UpdateCheck.Failed(UpdateCheck.Failed.Reason.UNREACHABLE),
            checker.check("0.18.0", ReleaseChecker.TV_ASSET_PREFIX),
        )
    }

    @Test
    fun `an error status is unreachable rather than up to date`() = runTest {
        val checker = ReleaseChecker(
            HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }),
            online,
        )

        assertEquals(
            UpdateCheck.Failed(UpdateCheck.Failed.Reason.UNREACHABLE),
            checker.check("0.18.0", ReleaseChecker.TV_ASSET_PREFIX),
        )
    }

    @Test
    fun `a body that is not a release is refused rather than parsed loosely`() = runTest {
        val outcome = checkerReturning("{\"message\":\"Not Found\"}")
            .check("0.18.0", ReleaseChecker.TV_ASSET_PREFIX)

        assertEquals(UpdateCheck.Failed(UpdateCheck.Failed.Reason.MALFORMED), outcome)
    }

    @Test
    fun `a tag nobody can read is refused`() = runTest {
        val outcome = checkerReturning(RELEASE_JSON.replace("v0.19.0\",", "nightly\","))
            .check("0.18.0", ReleaseChecker.TV_ASSET_PREFIX)

        assertEquals(UpdateCheck.Failed(UpdateCheck.Failed.Reason.MALFORMED), outcome)
    }

    private fun checkerReturning(body: String): ReleaseChecker {
        val engine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        return ReleaseChecker(HttpClient(engine), online)
    }

    private companion object {
        /** The shape of this project's own `releases/latest`, cut to the fields that are read. */
        val RELEASE_JSON = """
            {
              "tag_name": "v0.19.0",
              "body": "What changed.",
              "assets": [
                {
                  "name": "quiblo-tv-v0.19.0.apk",
                  "browser_download_url": "https://example.invalid/quiblo-tv-v0.19.0.apk"
                },
                {
                  "name": "quiblo-tv-v0.19.0.apk.sha256",
                  "browser_download_url": "https://example.invalid/quiblo-tv-v0.19.0.apk.sha256"
                },
                {
                  "name": "quiblo-v0.19.0.apk",
                  "browser_download_url": "https://example.invalid/quiblo-v0.19.0.apk"
                },
                {
                  "name": "quiblo-v0.19.0.apk.sha256",
                  "browser_download_url": "https://example.invalid/quiblo-v0.19.0.apk.sha256"
                }
              ]
            }
        """.trimIndent()

        val PHONE_ONLY_JSON = """
            {
              "tag_name": "v0.19.0",
              "assets": [
                {
                  "name": "quiblo-v0.19.0.apk",
                  "browser_download_url": "https://example.invalid/quiblo-v0.19.0.apk"
                }
              ]
            }
        """.trimIndent()
    }
}
