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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Settings.
 *
 * Everything here does something. The screen previously offered theme, buffer, bitrate and
 * seek pickers that were wired to nothing at all; they are gone, and what replaces them is
 * backup (AC-DATA-01 → 04) and the licence list (AC-LEGAL-03).
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val playerSettings by viewModel.playerSettings.collectAsStateWithLifecycle()
    val tmdbKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle()
    val tmdbCheck by viewModel.tmdbCheck.collectAsStateWithLifecycle()
    val metadataScan by viewModel.metadataScan.collectAsStateWithLifecycle()
    val profilesViewModel: ProfilesViewModel = koinViewModel()
    val profilesState by profilesViewModel.uiState.collectAsStateWithLifecycle()
    val categoryKind by viewModel.selectedCategoryKind.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val channelLogosEnabled by viewModel.channelLogosEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SAF, so the file lands wherever the user chooses and the app needs no storage
    // permission at all (AC-DATA-01).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri: Uri? ->
        if (uri == null) {
            viewModel.dismiss()
        } else {
            viewModel.export { contents ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(contents.toByteArray()) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            viewModel.dismiss()
        } else {
            val contents = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            viewModel.import(contents)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Read through stringResource rather than context.getString so it re-resolves on a
        // locale or configuration change like every other string on the screen.
        val defaultFilename = stringResource(R.string.settings_backup_default_filename)

        // No title here: the host screen's app bar already shows one, and repeating it
        // reads as a rendering fault.
        BackupCard(
            isWorking = backupState is BackupUiState.Working,
            onExport = { exportLauncher.launch(defaultFilename) },
            onImport = { importLauncher.launch(arrayOf(BACKUP_MIME_TYPE, ANY_MIME_TYPE)) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        AppearanceSettingsCard(
            appearance = appearance,
            onThemeMode = viewModel::setThemeMode,
            onDynamicColor = viewModel::setDynamicColor,
        )

        Spacer(modifier = Modifier.height(16.dp))

        ProfileCard(
            name = profilesState.active?.name,
            isGuest = profilesState.active?.isGuest == true,
            onSwitch = profilesViewModel::switchProfile,
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlaybackSettingsCard(
            settings = playerSettings,
            onSeekInterval = viewModel::setSeekInterval,
            onBufferMode = viewModel::setBufferMode,
            onMaxBitrate = viewModel::setMaxBitrate,
        )

        Spacer(modifier = Modifier.height(16.dp))

        CategorySettingsCard(
            selectedKind = categoryKind,
            categories = categories,
            onSelectKind = viewModel::selectCategoryKind,
            onSetHidden = viewModel::setCategoryHidden,
            onRename = viewModel::renameCategory,
        )

        Spacer(modifier = Modifier.height(16.dp))

        MetadataSettingsCard(
            savedKey = tmdbKey,
            check = tmdbCheck,
            onSave = viewModel::saveTmdbKey,
            onClear = viewModel::clearTmdbKey,
            scan = metadataScan,
            onStartScan = viewModel::startMetadataScan,
            onCancelScan = viewModel::cancelMetadataScan,
            onDismissScan = viewModel::dismissMetadataScan,
        )

        Spacer(modifier = Modifier.height(16.dp))

        ChannelLogoSettingsCard(
            isEnabled = channelLogosEnabled,
            onToggle = viewModel::setChannelLogosEnabled,
        )

        Spacer(modifier = Modifier.height(16.dp))

        LicensesCard()
    }

    BackupResultDialog(state = backupState, onDismiss = viewModel::dismiss)
}

@Composable
private fun BackupCard(
    isWorking: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_backup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_backup_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, enabled = !isWorking) {
                    Text(text = stringResource(R.string.settings_backup_export))
                }
                OutlinedButton(onClick = onImport, enabled = !isWorking) {
                    Text(text = stringResource(R.string.settings_backup_import))
                }
            }

            if (isWorking) {
                Text(
                    text = stringResource(R.string.settings_backup_working),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LicensesCard() {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_licenses_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_licenses_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.settings_licenses_hide else R.string.settings_licenses_show,
                    ),
                )
            }

            if (expanded) {
                THIRD_PARTY_LICENSES.forEach { entry ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = entry.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${entry.coordinates}\n${entry.license}\n${entry.url}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Reports what an export or import actually did.
 *
 * An import that restores nothing gets its own message rather than a success count of
 * zero, because "0 sources restored" reads as a failure when it usually means the file
 * was already applied.
 */
@Composable
private fun BackupResultDialog(state: BackupUiState, onDismiss: () -> Unit) {
    val message = when (state) {
        BackupUiState.Idle, BackupUiState.Working -> return

        is BackupUiState.Exported -> stringResource(R.string.settings_backup_exported)

        BackupUiState.Failed -> stringResource(R.string.settings_backup_failed)

        is BackupUiState.ImportRejected -> stringResource(
            R.string.settings_backup_rejected,
            state.fileVersion,
            state.supportedVersion,
        )

        is BackupUiState.Imported -> importedMessage(state)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_backup_dismiss))
            }
        },
    )
}

@Composable
private fun importedMessage(state: BackupUiState.Imported): String {
    if (state.sourcesRestored == 0 && state.favoritesRestored == 0) {
        return stringResource(R.string.settings_backup_nothing_restored)
    }

    val sources = pluralStringResource(
        R.plurals.settings_backup_sources_restored,
        state.sourcesRestored,
        state.sourcesRestored,
    )
    val favorites = pluralStringResource(
        R.plurals.settings_backup_favorites_restored,
        state.favoritesRestored,
        state.favoritesRestored,
    )
    val credentials = if (state.credentialsNeeded.isEmpty()) {
        ""
    } else {
        "\n" + stringResource(
            R.string.settings_backup_credentials_needed,
            state.credentialsNeeded.joinToString(", "),
        )
    }

    return "$sources $favorites$credentials"
}

private const val BACKUP_MIME_TYPE = "application/json"

/** Some file pickers will not surface a .json written by another app under its own type. */
private const val ANY_MIME_TYPE = "*/*"
