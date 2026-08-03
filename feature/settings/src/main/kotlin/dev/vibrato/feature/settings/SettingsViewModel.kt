/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vibrato.core.data.PlayerSettingsRepository
import dev.vibrato.core.data.backup.BackupRepository
import dev.vibrato.core.data.backup.ImportResult
import dev.vibrato.core.model.BufferMode
import dev.vibrato.core.model.MaxBitrateCap
import dev.vibrato.core.model.PlayerSettings
import dev.vibrato.core.model.SeekInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the export/import controls are currently reporting. */
sealed interface BackupUiState {

    data object Idle : BackupUiState

    data object Working : BackupUiState

    data class Exported(val byteCount: Int) : BackupUiState

    data class Imported(
        val sourcesRestored: Int,
        val favoritesRestored: Int,
        val credentialsNeeded: List<String>,
    ) : BackupUiState

    data class ImportRejected(val fileVersion: Int, val supportedVersion: Int) : BackupUiState

    data object Failed : BackupUiState
}

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val playerSettingsRepository: PlayerSettingsRepository,
) : ViewModel() {

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    val playerSettings: StateFlow<PlayerSettings> = playerSettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), PlayerSettings())

    fun setSeekInterval(value: SeekInterval) = viewModelScope.launch {
        playerSettingsRepository.setSeekInterval(value)
    }

    fun setBufferMode(value: BufferMode) = viewModelScope.launch {
        playerSettingsRepository.setBufferMode(value)
    }

    fun setMaxBitrate(value: MaxBitrateCap) = viewModelScope.launch {
        playerSettingsRepository.setMaxBitrate(value)
    }

    /**
     * Produces the export payload and hands it to [write].
     *
     * The ViewModel never touches a URI or a ContentResolver — the caller owns the SAF
     * document it was granted, and this owns what goes in it.
     */
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    fun export(write: (String) -> Unit) {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            try {
                val contents = backupRepository.export()
                write(contents)
                _backupState.value = BackupUiState.Exported(contents.length)
            } catch (_: Exception) {
                _backupState.value = BackupUiState.Failed
            }
        }
    }

    fun import(contents: String?) {
        if (contents == null) {
            _backupState.value = BackupUiState.Failed
            return
        }

        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            _backupState.value = when (val result = backupRepository.import(contents)) {
                is ImportResult.Success -> BackupUiState.Imported(
                    sourcesRestored = result.sourcesRestored,
                    favoritesRestored = result.favoritesRestored,
                    credentialsNeeded = result.credentialsNeeded,
                )

                is ImportResult.VersionTooNew ->
                    BackupUiState.ImportRejected(result.fileVersion, result.supportedVersion)

                ImportResult.Unreadable -> BackupUiState.Failed
            }
        }
    }

    fun dismiss() {
        _backupState.value = BackupUiState.Idle
    }

    private companion object {
        /** Outlives a rotation, so the chips do not flicker back to defaults and settle. */
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
