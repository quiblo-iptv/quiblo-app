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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One film listed four times, shown once.
 *
 * A panel that carries a film in SD, HD, FHD and 4K sends four entries, and one that also carries
 * a subtitled cut sends eight. The catalogue then reads as four times its real size, in which the
 * same title is met over and over and nothing is ever found twice.
 *
 * **Off by default, and the summary says what it hides.** Merging removes rows a provider sent,
 * and hiding rows nobody asked to hide is how a viewer comes to believe their account is missing
 * something. The detail screen offers whatever was folded away, which is what makes this a change
 * of view rather than a loss.
 */
@Composable
internal fun DuplicateTitlesSettingsCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    /**
     * Whether the shelves are collapsed into one grid as well (`029` #3).
     *
     * A second switch inside the same card rather than a card of its own, because it is only
     * meaningful while the first is on: a provider that lists one film four times usually files
     * those four under four categories, so merging the copies leaves the same title reachable from
     * several shelves and the catalogue still reads as several catalogues.
     */
    isCategoriesMerged: Boolean,
    onToggleCategories: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_merge_titles_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_merge_titles_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }

            // Hidden rather than disabled while merging is off. A greyed switch invites a press
            // that does nothing and says nothing; a control that is not there yet is explained by
            // the switch above it, which is the one that has to be turned on first.
            if (isEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_merge_categories_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.settings_merge_categories_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Switch(checked = isCategoriesMerged, onCheckedChange = onToggleCategories)
                }
            }
        }
    }
}
