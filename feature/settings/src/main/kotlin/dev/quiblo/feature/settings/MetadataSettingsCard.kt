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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.quiblo.core.data.MetadataScanState
import dev.quiblo.core.data.ScanRefusal

/**
 * Optional film information from The Movie Database.
 *
 * Off unless the user pastes their own key, and off is the default. Quiblo ships no key:
 * one bundled into an open-source app is a shared credential anybody can extract, it would
 * be rate-limited across every install at once, and it would make every copy of the app
 * contact a third party whether its owner wanted that or not.
 *
 * The key is stored encrypted, alongside playlist passwords rather than beside the playback
 * preferences, because that is what it is.
 */
@Composable
internal fun MetadataSettingsCard(
    savedKey: String?,
    check: TmdbCheck,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    scan: MetadataScanState,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onDismissScan: () -> Unit,
) {
    var draft by remember(savedKey) { mutableStateOf(savedKey.orEmpty()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_metadata_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_metadata_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_metadata_key)) },
                // Masked like any other credential. It identifies its owner to TMDB and is
                // rate-limited against them, so it does not belong on screen in a room.
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Button(
                    onClick = { onSave(draft) },
                    enabled = check != TmdbCheck.Checking && draft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.settings_metadata_save))
                }
                if (!savedKey.isNullOrBlank()) {
                    OutlinedButton(onClick = onClear) {
                        Text(stringResource(R.string.settings_metadata_clear))
                    }
                }
            }

            CheckStatus(check = check, hasSavedKey = !savedKey.isNullOrBlank())

            // Only with a key. Without one there is nothing to ask, and a button that cannot
            // work is worse than an absent one.
            if (!savedKey.isNullOrBlank()) {
                CatalogueScan(
                    state = scan,
                    onStart = onStartScan,
                    onCancel = onCancelScan,
                    onDismiss = onDismissScan,
                )
            }

            // TMDB's terms require this wording wherever their data is used.
            Text(
                text = stringResource(R.string.settings_metadata_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/**
 * Looking up the whole catalogue at once, rather than a poster at a time as somebody browses.
 *
 * A progress bar and a count, because on a large playlist this is the better part of an hour
 * and a spinner for an hour says nothing. The scan does not belong to this screen — leaving
 * and coming back finds it where it is.
 */
@Composable
private fun CatalogueScan(
    state: MetadataScanState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isWorking = state is MetadataScanState.Preparing || state is MetadataScanState.Running

    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(
            text = stringResource(R.string.settings_metadata_scan_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.settings_metadata_scan_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (state is MetadataScanState.Running && state.total > 0) {
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }

        scanMessage(state)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (state is MetadataScanState.Stopped) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            if (isWorking) {
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.settings_metadata_scan_stop))
                }
            } else {
                Button(onClick = onStart) {
                    Text(stringResource(R.string.settings_metadata_scan_start))
                }
                if (state !is MetadataScanState.Idle) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_metadata_scan_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun scanMessage(state: MetadataScanState): String? = when (state) {
    MetadataScanState.Idle -> null
    MetadataScanState.Preparing -> stringResource(R.string.settings_metadata_scan_preparing)
    is MetadataScanState.Running ->
        stringResource(R.string.settings_metadata_scan_progress, state.done, state.total, state.found)

    is MetadataScanState.Finished ->
        stringResource(R.string.settings_metadata_scan_finished, state.found, state.missing)

    is MetadataScanState.Cancelled ->
        stringResource(R.string.settings_metadata_scan_cancelled, state.found)

    is MetadataScanState.Stopped -> stringResource(
        when (state.reason) {
            ScanRefusal.RATE_LIMITED -> R.string.settings_metadata_scan_rate_limited
            ScanRefusal.KEY_REJECTED -> R.string.settings_metadata_scan_key_rejected
            ScanRefusal.UNAVAILABLE -> R.string.settings_metadata_scan_unavailable
        },
        state.found,
    )
}

@Composable
private fun CheckStatus(check: TmdbCheck, hasSavedKey: Boolean) {
    val message = when (check) {
        TmdbCheck.Checking -> stringResource(R.string.settings_metadata_checking)
        TmdbCheck.Accepted -> stringResource(R.string.settings_metadata_accepted)
        TmdbCheck.Rejected -> stringResource(R.string.settings_metadata_rejected)
        TmdbCheck.Cleared -> stringResource(R.string.settings_metadata_cleared)
        TmdbCheck.Idle -> if (hasSavedKey) {
            stringResource(R.string.settings_metadata_on)
        } else {
            stringResource(R.string.settings_metadata_off)
        }
    }

    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = if (check == TmdbCheck.Rejected) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = 10.dp),
    )
}
