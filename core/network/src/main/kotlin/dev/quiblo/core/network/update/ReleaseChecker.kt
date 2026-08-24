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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What asking GitHub for the newest release came back with. */
sealed interface UpdateCheck {

    /** This build is the newest published release, or newer than it. */
    data object UpToDate : UpdateCheck

    /**
     * There is a newer release, and this is the file to fetch.
     *
     * @param checksumUrl the `.sha256` published beside the APK. Present on every release this
     *   project has made; `null` means the release is missing it, and the caller decides whether
     *   to install something it cannot verify.
     */
    data class Available(
        val version: String,
        val apkUrl: String,
        val checksumUrl: String?,
        val notes: String,
    ) : UpdateCheck

    /** Nothing was learned. Distinguished from [UpToDate] because a viewer must not be told
     *  they are current when in fact nobody could be reached. */
    data class Failed(val reason: Reason) : UpdateCheck {
        enum class Reason { OFFLINE, UNREACHABLE, NO_ASSET, MALFORMED }
    }
}

/**
 * Asks GitHub whether a newer Quiblo has been published.
 *
 * **Only when a viewer asks.** Nothing here runs on launch or on a schedule: the app calls out to
 * exactly the hosts a user configured, and its own releases page is only added to that list at the
 * moment somebody presses the button. A player that phones home unprompted is the thing
 * `tv_consent_terms_body` promises this one is not.
 *
 * **The release lane is what makes this possible.** Every release publishes
 * `quiblo-tv-vX.Y.Z.apk`, `quiblo-vX.Y.Z.apk` and a `.sha256` beside each — so the asset names
 * are a contract, and [assetPrefix] is how a caller says which of the two apps it is.
 *
 * @param assetPrefix `quiblo-tv-` on a television, `quiblo-` on a phone. The two APKs sit in one
 *   release, and offering a television the handset build is offering the wrong app.
 */
class ReleaseChecker(
    private val client: HttpClient,
    private val connectivity: ConnectivityChecker,
    private val repository: String = QUIBLO_REPOSITORY,
) {

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun check(currentVersion: String, assetPrefix: String): UpdateCheck {
        if (!connectivity.isOnline()) return UpdateCheck.Failed(UpdateCheck.Failed.Reason.OFFLINE)

        val body = try {
            val response = client.get("$API_ROOT/$repository/releases/latest")
            if (!response.status.isSuccess()) {
                return UpdateCheck.Failed(UpdateCheck.Failed.Reason.UNREACHABLE)
            }
            response.bodyAsText()
        } catch (_: Exception) {
            // Every transport failure is one answer to a viewer: it could not be reached. The
            // distinction between a DNS failure and a timeout is not something a television
            // screen can usefully say.
            return UpdateCheck.Failed(UpdateCheck.Failed.Reason.UNREACHABLE)
        }

        val release = try {
            JSON.decodeFromString<GitHubRelease>(body)
        } catch (_: Exception) {
            return UpdateCheck.Failed(UpdateCheck.Failed.Reason.MALFORMED)
        }

        val latest = AppVersion.parse(release.tagName)
            ?: return UpdateCheck.Failed(UpdateCheck.Failed.Reason.MALFORMED)
        val running = AppVersion.parse(currentVersion)
            ?: return UpdateCheck.Failed(UpdateCheck.Failed.Reason.MALFORMED)

        if (latest <= running) return UpdateCheck.UpToDate

        val apk = release.assets.firstOrNull { it.name.startsWith(assetPrefix) && it.name.endsWith(APK) }
            ?: return UpdateCheck.Failed(UpdateCheck.Failed.Reason.NO_ASSET)

        return UpdateCheck.Available(
            version = release.tagName,
            apkUrl = apk.downloadUrl,
            checksumUrl = release.assets.firstOrNull { it.name == apk.name + CHECKSUM }?.downloadUrl,
            notes = release.body.orEmpty(),
        )
    }

    /**
     * Only the fields that are read.
     *
     * A GitHub release carries several dozen, and `ignoreUnknownKeys` plus four properties is
     * both smaller and harder to break than a faithful model of an API this project does not own.
     */
    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val assets: List<Asset> = emptyList(),
        val body: String? = null,
    )

    @Serializable
    private data class Asset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    companion object {
        const val QUIBLO_REPOSITORY = "quiblo-iptv/quiblo-app"

        /** What the television's APK is called on every release the lane has published. */
        const val TV_ASSET_PREFIX = "quiblo-tv-"

        /**
         * The handset APK, and the `v` is load-bearing.
         *
         * The two files are `quiblo-vX.Y.Z.apk` and `quiblo-tv-vX.Y.Z.apk`, so a prefix of
         * `quiblo-` matches both and the first asset GitHub happens to list wins — which is how a
         * phone would be offered the television build. Including the version's own `v` is what
         * makes the two prefixes disjoint.
         */
        const val PHONE_ASSET_PREFIX = "quiblo-v"

        private const val API_ROOT = "https://api.github.com/repos"
        private const val APK = ".apk"
        private const val CHECKSUM = ".sha256"

        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
