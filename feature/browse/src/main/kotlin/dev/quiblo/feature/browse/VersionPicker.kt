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

package dev.quiblo.feature.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.quiblo.core.data.TitleVersion

/**
 * The other ways the provider lists this title, as a row of chips.
 *
 * **The other half of the merge setting.** With merging on, the catalogue shows one row where a
 * panel sent four — the SD, HD, FHD and 4K copies of one film — and this is where the other three
 * went. Without it, merging would be a setting that quietly loses a viewer their 4K copy.
 *
 * Absent when there is nothing to choose between: every title on a panel that lists each one
 * once, and every title at all while merging is off. A picker offering the thing already on
 * screen is the hollow-control shape this project deletes rather than draws.
 */
@Composable
fun VersionPicker(
    versions: List<TitleVersion>,
    shownId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (versions.size < 2) return

    Column(modifier = modifier.padding(top = 16.dp)) {
        Text(
            text = stringResource(R.string.detail_versions),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            versions.forEach { version ->
                FilterChip(
                    selected = version.channel.id == shownId,
                    onClick = { onSelect(version.channel.id) },
                    label = { Text(version.label) },
                )
            }
        }
    }
}
