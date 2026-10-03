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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.Profile
import dev.quiblo.designsystem.AvatarFacePicker
import dev.quiblo.designsystem.ProfileAvatar

/**
 * Everybody who has a profile, to rename, re-face or delete (`FEAT-039`).
 *
 * **Guest is not listed.** It is a session rather than somebody, and it ends by leaving —
 * deleting it from here would be a second way to do the one thing "Switch profile" already does.
 */
@Composable
fun ManageProfilesDialog(
    profiles: List<Profile>,
    onRename: (Profile, String) -> Unit,
    onSetAvatar: (Profile, String?) -> Unit,
    onDelete: (Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Profile?>(null) }
    val named = profiles.filterNot { it.isGuest }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profiles_manage_title)) },
        text = {
            Column {
                named.forEach { profile ->
                    ListItem(
                        leadingContent = { ProfileAvatar(name = profile.name, avatar = profile.avatar, size = 40.dp) },
                        headlineContent = { Text(profile.name) },
                        supportingContent = { Text(stringResource(R.string.settings_profiles_manage_row)) },
                        modifier = Modifier.clickable { editing = profile },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_profiles_done)) }
        },
    )

    editing?.let { profile ->
        EditProfileDialog(
            profile = profile,
            onSave = { name, avatar ->
                if (name.trim() != profile.name) onRename(profile, name)
                if (avatar != profile.avatar) onSetAvatar(profile, avatar)
                editing = null
            },
            onDelete = {
                onDelete(profile)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/**
 * One profile: its name, its face, and the way out of existence.
 *
 * Public because the chooser opens it too, on a long press — the place somebody looks for "change
 * my name" is the tile with their name on it. Delete asks first, and says what goes with it.
 */
@Composable
fun EditProfileDialog(
    profile: Profile,
    onSave: (name: String, avatar: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var avatar by remember(profile.id) { mutableStateOf(profile.avatar) }
    var confirmingDelete by remember(profile.id) { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.settings_profiles_delete_title, profile.name)) },
            text = { Text(stringResource(R.string.settings_profiles_delete_detail)) },
            confirmButton = {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.settings_profiles_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.settings_profiles_keep))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profiles_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_profiles_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.settings_profiles_face),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                AvatarFacePicker(
                    selected = avatar,
                    onSelect = { avatar = if (avatar == it) null else it },
                )
                TextButton(
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_profiles_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, avatar) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.settings_profiles_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_profiles_cancel)) }
        },
    )
}
