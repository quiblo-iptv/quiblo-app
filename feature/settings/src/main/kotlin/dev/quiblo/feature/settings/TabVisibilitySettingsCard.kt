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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.AppTab

/**
 * Which of the four switchable tabs this viewer wants in the bar (`029` #5).
 *
 * **Shown as what is on rather than what is hidden.** The setting is stored as a set of hidden
 * tabs because that is the shape that lets a tab added later default to visible — but a screen
 * that offered "hide Live" would leave every switch off in a bar where everything is on, which
 * reads as a screen that has not loaded. The switches say what the bar says.
 *
 * The last visible tab cannot be turned off; the repository refuses it and the switch springs
 * back. That is a clamp rather than a disabled control because which one is last changes as the
 * others are switched, and a control that greys out under the finger is worse than one that
 * simply will not go.
 */
@Composable
internal fun TabVisibilitySettingsCard(
    hidden: Set<AppTab>,
    onSetHidden: (AppTab, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_tabs_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_tabs_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            AppTab.entries.forEach { tab ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(tab.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = tab !in hidden,
                        onCheckedChange = { visible -> onSetHidden(tab, !visible) },
                    )
                }
            }
        }
    }
}

/**
 * The app's own word for a tab.
 *
 * Here rather than on [AppTab] itself: the enum lives in `:core:model`, which carries no Android
 * resources, and a settings module that named its own strings on a model type would be the first
 * step to that module owning wording for every screen.
 */
@StringRes
private fun AppTab.labelRes(): Int = when (this) {
    AppTab.LIVE -> R.string.settings_tab_live
    AppTab.MOVIES -> R.string.settings_tab_movies
    AppTab.SERIES -> R.string.settings_tab_series
    AppTab.FAVOURITES -> R.string.settings_tab_favourites
}
