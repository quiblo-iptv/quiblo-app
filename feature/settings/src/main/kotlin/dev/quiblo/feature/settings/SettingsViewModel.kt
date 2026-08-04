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

package dev.quiblo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.quiblo.core.data.CategoryRepository
import dev.quiblo.core.data.ChannelLogoRepository
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.SourceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.backup.BackupRepository
import dev.quiblo.core.data.backup.ImportResult
import dev.quiblo.core.model.Appearance
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SeekInterval
import dev.quiblo.core.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the TMDB key field is reporting. */
sealed interface TmdbCheck {
    data object Idle : TmdbCheck
    data object Checking : TmdbCheck
    data object Accepted : TmdbCheck
    data object Rejected : TmdbCheck
    data object Cleared : TmdbCheck
}

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

// One function per control on a screen that is a list of unrelated controls: backup,
// appearance, playback tuning, categories, film metadata, channel logos. The count tracks
// the number of settings, not any tangle between them — each is two or three lines that
// touch one repository — so splitting it would produce six ViewModels for one screen and a
// composable that has to fetch all six.
@Suppress("TooManyFunctions")
class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val playerSettingsRepository: PlayerSettingsRepository,
    private val metadataRepository: TitleMetadataRepository,
    private val categoryRepository: CategoryRepository,
    private val sourceRepository: SourceRepository,
    private val channelLogoRepository: ChannelLogoRepository,
) : ViewModel() {

    private val categoryKind = MutableStateFlow(MediaKind.LIVE)
    val selectedCategoryKind: StateFlow<MediaKind> = categoryKind.asStateFlow()

    /**
     * Every category of the chosen kind, hidden ones included.
     *
     * The editing screen is the one place hidden categories must still appear — hiding one
     * somewhere it can never be un-hidden would be a trap rather than a setting.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<Category>> = combine(
        sourceRepository.observeSources(),
        categoryKind,
    ) { sources, kind -> sources.firstOrNull()?.id to kind }
        .flatMapLatest { (sourceId, kind) ->
            if (sourceId == null) flowOf(emptyList()) else categoryRepository.observeAllCategories(sourceId, kind)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), emptyList())

    fun selectCategoryKind(kind: MediaKind) {
        categoryKind.value = kind
    }

    fun setCategoryHidden(category: Category, hidden: Boolean) = viewModelScope.launch {
        categoryRepository.setCategoryHidden(categoryKind.value, category.title, hidden)
    }

    fun renameCategory(category: Category, name: String?) = viewModelScope.launch {
        categoryRepository.renameCategory(categoryKind.value, category.title, name)
    }

    init {
        viewModelScope.launch { metadataRepository.load() }
    }

    /**
     * Whether missing channel logos are filled in from the iptv-org reference list.
     *
     * False until the store has been read, which is also the value it holds when the
     * feature has never been turned on — the two are the same thing to this screen.
     */
    val channelLogosEnabled: StateFlow<Boolean> = channelLogoRepository.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), false)

    fun setChannelLogosEnabled(enabled: Boolean) = viewModelScope.launch {
        channelLogoRepository.setEnabled(enabled)
    }

    /** The saved TMDB key, or null when the feature is off. */
    val tmdbApiKey: StateFlow<String?> = metadataRepository.apiKey

    private val _tmdbCheck = MutableStateFlow<TmdbCheck>(TmdbCheck.Idle)
    val tmdbCheck: StateFlow<TmdbCheck> = _tmdbCheck.asStateFlow()

    /**
     * Saves the key only once the service has accepted it.
     *
     * A key that is silently wrong produces a films screen that is quietly missing its
     * descriptions, with nothing anywhere to say why. One request at the moment of typing
     * turns that into an answer.
     */
    fun saveTmdbKey(apiKey: String) = viewModelScope.launch {
        val cleaned = apiKey.trim()
        if (cleaned.isBlank()) {
            metadataRepository.setApiKey(null)
            _tmdbCheck.value = TmdbCheck.Cleared
            return@launch
        }

        _tmdbCheck.value = TmdbCheck.Checking
        if (metadataRepository.validate(cleaned)) {
            metadataRepository.setApiKey(cleaned)
            _tmdbCheck.value = TmdbCheck.Accepted
        } else {
            _tmdbCheck.value = TmdbCheck.Rejected
        }
    }

    fun clearTmdbKey() = viewModelScope.launch {
        metadataRepository.setApiKey(null)
        _tmdbCheck.value = TmdbCheck.Cleared
    }

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

    val appearance: StateFlow<Appearance> = playerSettingsRepository.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), Appearance())

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch {
        playerSettingsRepository.setThemeMode(value)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        playerSettingsRepository.setDynamicColor(enabled)
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
