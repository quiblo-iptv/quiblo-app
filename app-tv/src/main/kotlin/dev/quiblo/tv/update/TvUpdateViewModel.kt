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

package dev.quiblo.tv.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.quiblo.core.network.update.DownloadResult
import dev.quiblo.core.network.update.ReleaseChecker
import dev.quiblo.core.network.update.ReleaseDownloader
import dev.quiblo.core.network.update.UpdateCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * What the update row is currently showing.
 *
 * Every branch says something. A check that quietly does nothing is indistinguishable from a
 * broken button, which is the fault this project has deleted features for — so there is no
 * silent state in here, and the failures carry which failure it was.
 */
sealed interface TvUpdateState {

    data object Idle : TvUpdateState

    data object Checking : TvUpdateState

    data object UpToDate : TvUpdateState

    data class Available(val version: String, val release: UpdateCheck.Available) : TvUpdateState

    data class Downloading(val version: String) : TvUpdateState

    /** The APK is on disk and its checksum matched. [file] is named so the viewer can find it. */
    data class Ready(val version: String, val file: File) : TvUpdateState

    data class Failed(val reason: Reason) : TvUpdateState {
        enum class Reason { OFFLINE, UNREACHABLE, NO_ASSET, MALFORMED, CHECKSUM, NO_CHECKSUM, INSTALLER_REFUSED }
    }
}

/**
 * Check for updates, on a television that has no store to update it from.
 *
 * **Nothing here runs unprompted.** [check] is called by a button press and by nothing else — no
 * launch check, no schedule, no background worker. `tv_consent_terms_body` promises that nothing
 * leaves the device except to the servers a viewer named themselves, and this project's own
 * releases page joins that list only for as long as somebody is standing in front of the screen
 * asking about it.
 *
 * @param currentVersion `BuildConfig.VERSION_NAME`, passed in rather than read here so the
 *   comparison can be tested against versions this build does not have.
 * @param updatesDirectory where the APK lands. Named by the caller because it has to be the
 *   directory the manifest's file-provider paths share, and a second opinion about which
 *   directory that is would be a `content://` URI the installer cannot open.
 */
class TvUpdateViewModel(
    private val checker: ReleaseChecker,
    private val downloader: ReleaseDownloader,
    private val currentVersion: String,
    private val updatesDirectory: File,
) : ViewModel() {

    private val _state = MutableStateFlow<TvUpdateState>(TvUpdateState.Idle)
    val state: StateFlow<TvUpdateState> = _state.asStateFlow()

    fun check() {
        if (_state.value is TvUpdateState.Checking || _state.value is TvUpdateState.Downloading) return

        _state.value = TvUpdateState.Checking
        viewModelScope.launch {
            _state.value = when (val outcome = checker.check(currentVersion, ReleaseChecker.TV_ASSET_PREFIX)) {
                is UpdateCheck.UpToDate -> TvUpdateState.UpToDate
                is UpdateCheck.Available -> TvUpdateState.Available(outcome.version, outcome)
                is UpdateCheck.Failed -> TvUpdateState.Failed(outcome.reason.asFailure())
            }
        }
    }

    fun download() {
        val available = _state.value as? TvUpdateState.Available ?: return

        _state.value = TvUpdateState.Downloading(available.version)
        viewModelScope.launch {
            val destination = File(updatesDirectory, "quiblo-tv-${available.version}.apk")
            val outcome = downloader.download(
                apkUrl = available.release.apkUrl,
                checksumUrl = available.release.checksumUrl,
                into = destination,
            )
            _state.value = when (outcome) {
                is DownloadResult.Downloaded -> TvUpdateState.Ready(available.version, outcome.file)
                DownloadResult.ChecksumMismatch -> TvUpdateState.Failed(TvUpdateState.Failed.Reason.CHECKSUM)
                DownloadResult.NoChecksum -> TvUpdateState.Failed(TvUpdateState.Failed.Reason.NO_CHECKSUM)
                DownloadResult.Unreachable -> TvUpdateState.Failed(TvUpdateState.Failed.Reason.UNREACHABLE)
            }
        }
    }

    /**
     * The system installer would not open.
     *
     * Reported rather than swallowed, because the screen's answer to it is the useful one: the
     * file is downloaded and verified, and it can be installed from a file manager.
     */
    fun installerRefused() {
        _state.value = TvUpdateState.Failed(TvUpdateState.Failed.Reason.INSTALLER_REFUSED)
    }

    /** Puts the row back to a button, after a viewer has read whatever it said. */
    fun dismiss() {
        _state.value = TvUpdateState.Idle
    }

    private fun UpdateCheck.Failed.Reason.asFailure(): TvUpdateState.Failed.Reason = when (this) {
        UpdateCheck.Failed.Reason.OFFLINE -> TvUpdateState.Failed.Reason.OFFLINE
        UpdateCheck.Failed.Reason.UNREACHABLE -> TvUpdateState.Failed.Reason.UNREACHABLE
        UpdateCheck.Failed.Reason.NO_ASSET -> TvUpdateState.Failed.Reason.NO_ASSET
        UpdateCheck.Failed.Reason.MALFORMED -> TvUpdateState.Failed.Reason.MALFORMED
    }
}
