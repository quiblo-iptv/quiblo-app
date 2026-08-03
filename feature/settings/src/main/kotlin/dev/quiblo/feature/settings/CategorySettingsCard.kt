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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.MediaKind

/**
 * Turning categories off, and renaming them.
 *
 * Both edits are local and neither touches the provider. Hiding removes a category from the
 * browse screens without removing its channels from the database, so nothing is lost and
 * turning it back on costs no refresh. Renaming stores a second name alongside the
 * provider's, which stays the key — a refresh reassigns everything else, and an edit keyed
 * to anything but the original title would come unstuck the first time a playlist reloaded.
 */
@Composable
internal fun CategorySettingsCard(
    selectedKind: MediaKind,
    categories: List<Category>,
    onSelectKind: (MediaKind) -> Unit,
    onSetHidden: (Category, Boolean) -> Unit,
    onRename: (Category, String?) -> Unit,
) {
    var renaming: Category? by remember { mutableStateOf(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_categories_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_categories_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaKind.entries.forEach { kind ->
                    FilterChip(
                        selected = kind == selectedKind,
                        onClick = { onSelectKind(kind) },
                        label = { Text(stringResource(kind.labelRes())) },
                    )
                }
            }

            if (categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_categories_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }

            // Bounded and scrollable: an Xtream account can carry hundreds of categories,
            // and an unbounded list would make the rest of Settings unreachable.
            LazyColumn(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(max = LIST_MAX_HEIGHT),
            ) {
                items(items = categories, key = { it.title }) { category ->
                    CategoryRow(
                        category = category,
                        onToggle = { onSetHidden(category, !category.isHidden) },
                        onRename = { renaming = category },
                    )
                }
            }
        }
    }

    renaming?.let { category ->
        RenameDialog(
            category = category,
            onConfirm = {
                onRename(category, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun CategoryRow(category: Category, onToggle: () -> Unit, onRename: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The provider's own name is shown underneath a rename, so a user can still tell
            // which category they are looking at after renaming several of them.
            val subtitle = if (category.customName.isNullOrBlank()) {
                stringResource(R.string.settings_categories_count, category.itemCount)
            } else {
                stringResource(R.string.settings_categories_renamed_from, category.title)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onRename) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.settings_categories_rename),
            )
        }

        Switch(checked = !category.isHidden, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun RenameDialog(category: Category, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(category.customName ?: category.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_categories_rename)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_categories_new_name)) },
                )
                Text(
                    text = stringResource(R.string.settings_categories_rename_hint, category.title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.settings_categories_save))
            }
        },
        dismissButton = {
            Row {
                // Clearing restores the provider's name rather than setting an empty one.
                TextButton(onClick = { onConfirm(null) }) {
                    Text(stringResource(R.string.settings_categories_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_categories_cancel))
                }
            }
        },
    )
}

private fun MediaKind.labelRes(): Int = when (this) {
    MediaKind.LIVE -> R.string.settings_categories_live
    MediaKind.VOD -> R.string.settings_categories_movies
    MediaKind.SERIES -> R.string.settings_categories_series
}

private val LIST_MAX_HEIGHT = 320.dp
