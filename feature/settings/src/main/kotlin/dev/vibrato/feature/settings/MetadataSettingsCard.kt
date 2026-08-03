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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

/**
 * Optional film information from The Movie Database.
 *
 * Off unless the user pastes their own key, and off is the default. Vibrato ships no key:
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
