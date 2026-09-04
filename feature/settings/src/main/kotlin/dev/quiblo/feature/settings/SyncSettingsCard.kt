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

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.CatalogueSyncInterval

/**
 * How often Quiblo re-reads the viewer's provider on its own (`FEAT-031`).
 *
 * **This used to be four days and was not settable.** Four days was recorded openly as a guess in
 * `024`, and it was wrong in the direction that matters: a provider adds a film and a household
 * cannot see it for most of a week. The right number is not ours to know, though — it depends on
 * how often one provider adds things and how tolerant one panel is of being asked, and both of
 * those facts live with the viewer.
 *
 * App-wide rather than per profile, like the update check: it says how often this box talks to a
 * provider, which is not a statement about whoever last chose a profile.
 */
@Composable
internal fun SyncSettingsCard(
    interval: CatalogueSyncInterval,
    onInterval: (CatalogueSyncInterval) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_sync_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            ChipGroup(
                labelRes = R.string.settings_sync_interval,
                descriptionRes = R.string.settings_sync_interval_summary,
                options = CatalogueSyncInterval.entries,
                selected = interval,
                labelFor = { stringResource(it.labelRes()) },
                onSelect = onInterval,
            )
        }
    }
}

@StringRes
private fun CatalogueSyncInterval.labelRes(): Int = when (this) {
    CatalogueSyncInterval.FOUR_HOURS -> R.string.settings_sync_four_hours
    CatalogueSyncInterval.EIGHT_HOURS -> R.string.settings_sync_eight_hours
    CatalogueSyncInterval.TWELVE_HOURS -> R.string.settings_sync_twelve_hours
    CatalogueSyncInterval.DAILY -> R.string.settings_sync_daily
}
