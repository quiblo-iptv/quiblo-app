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

package dev.quiblo.feature.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Source
import org.koin.androidx.compose.koinViewModel

/**
 * Lists the user's configured sources and lets them add, refresh, or remove one.
 *
 * Quiblo ships with nothing here and never populates it on the user's behalf
 * (docs/FREEZE.md §2). Everything in this list was typed or picked by the user.
 */
@Composable
fun SourcesScreen(
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = koinViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.sources_add)) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (sources.isEmpty()) {
                EmptySources()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = sources, key = { it.id }) { source ->
                        SourceRow(
                            source = source,
                            onRefresh = { viewModel.refresh(source.id) },
                            onDelete = { viewModel.delete(source.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }

            if (addState is AddSourceState.Working) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onConfirmM3u = { name, location ->
                showAddDialog = false
                viewModel.addM3uSource(name, location)
            },
            onConfirmXtream = { name, base, user, password ->
                showAddDialog = false
                viewModel.addXtreamSource(name, base, user, password)
            },
        )
    }

    ResultDialog(state = addState, onDismiss = viewModel::dismissResult)
}

@Composable
private fun EmptySources() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.feature_sources_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.feature_sources_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SourceRow(
    source: Source,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = source.name, style = MaterialTheme.typography.titleMedium)
            Text(
                // Deliberately the source kind, never the URL: a playlist address must not
                // end up in a screenshot pasted into a public issue (AC-LEGAL-04).
                text = if (source.lastRefreshedEpochMillis == null) {
                    stringResource(R.string.sources_never_loaded)
                } else {
                    source.kind.name
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sources_refresh))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.sources_delete))
        }
    }
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onConfirmM3u: (name: String, location: String) -> Unit,
    onConfirmXtream: (name: String, base: String, user: String, password: String) -> Unit,
) {
    var xtreamSelected by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // SAF, so Quiblo needs no storage permission at all (AC-NFR-04).
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onConfirmM3u(name, uri.toString())
    }

    val canConfirm = if (xtreamSelected) {
        url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    } else {
        url.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sources_add_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SourceKindSelector(
                    xtreamSelected = xtreamSelected,
                    onSelect = { xtreamSelected = it },
                )
                CommonFields(
                    name = name,
                    url = url,
                    xtreamSelected = xtreamSelected,
                    onNameChange = { name = it },
                    onUrlChange = { url = it },
                )
                if (xtreamSelected) {
                    XtreamCredentialFields(
                        username = username,
                        password = password,
                        onUsernameChange = { username = it },
                        onPasswordChange = { password = it },
                    )
                } else {
                    TextButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.sources_pick_file))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (xtreamSelected) onConfirmXtream(name, url, username, password) else onConfirmM3u(name, url)
                },
                enabled = canConfirm,
            ) {
                Text(stringResource(R.string.sources_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sources_cancel)) }
        },
    )
}

/** Chooses between an M3U playlist and an Xtream account. */
@Composable
private fun SourceKindSelector(xtreamSelected: Boolean, onSelect: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !xtreamSelected,
            onClick = { onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.sources_tab_m3u)) }
        SegmentedButton(
            selected = xtreamSelected,
            onClick = { onSelect(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.sources_tab_xtream)) }
    }
}

/** Label and address, shared by both source kinds. */
@Composable
private fun CommonFields(
    name: String,
    url: String,
    xtreamSelected: Boolean,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.sources_name_label)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = {
            Text(
                stringResource(
                    if (xtreamSelected) R.string.sources_xtream_url_label else R.string.sources_url_label,
                ),
            )
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}

/**
 * Username and password entry.
 *
 * The password is masked and never echoed back into any other state. It travels straight
 * to the encrypted store (AC-XT-04).
 */
@Composable
private fun XtreamCredentialFields(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.sources_xtream_user_label)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.sources_xtream_pass_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
    Text(
        text = stringResource(R.string.sources_xtream_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ResultDialog(state: AddSourceState, onDismiss: () -> Unit) {
    val message: String? = when (state) {
        is AddSourceState.Added -> {
            val counted = pluralStringResource(
                R.plurals.sources_channel_count,
                state.channelCount,
                state.channelCount,
            )
            val loaded = stringResource(R.string.sources_added, counted)
            if (state.skippedEntries > 0) {
                val skipped = pluralStringResource(
                    R.plurals.sources_skipped_count,
                    state.skippedEntries,
                    state.skippedEntries,
                )
                "$loaded $skipped"
            } else {
                loaded
            }
        }

        is AddSourceState.Failed -> {
            val arg = state.error.messageArg()
            if (arg != null) stringResource(state.error.messageRes(), arg) else stringResource(state.error.messageRes())
        }

        else -> null
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.sources_dismiss)) }
            },
        )
    }
}
