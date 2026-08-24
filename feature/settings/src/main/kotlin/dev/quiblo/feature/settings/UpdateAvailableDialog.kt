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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * "New version available", with two ways out (`029` #7).
 *
 * **Two buttons and no third.** *Update now* and *Later* are the whole of it: an offer a viewer
 * cannot refuse is not an offer, and a "never ask again" would be a setting hidden inside a dialog
 * where nobody could find it to undo. The switch that turns the whole check off lives in Settings,
 * where a setting belongs.
 *
 * Dismissing by tapping outside means *Later*, which is what dismissing a dialog has always meant.
 *
 * @param onUpdate what *Update now* does, which differs by app and is why it is a parameter: the
 *   television downloads the APK and hands it to the system installer, and the phone — which holds
 *   no install permission and should not — opens the releases page.
 */
@Composable
fun UpdateAvailableDialog(
    availableVersion: String,
    installedVersion: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_update_available_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.settings_update_available_body,
                    availableVersion,
                    installedVersion,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(text = stringResource(R.string.settings_update_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_update_later))
            }
        },
    )
}
