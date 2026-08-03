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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SeekInterval

/**
 * Playback tuning.
 *
 * Every chip here writes through to DataStore on tap and the player picks the change up
 * from the same flow. That is the whole point of the card: the screen this replaces had
 * controls that looked settable and changed nothing, which is worse than not offering them
 * (see c0bf585).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackSettingsCard(
    settings: PlayerSettings,
    onSeekInterval: (SeekInterval) -> Unit,
    onBufferMode: (BufferMode) -> Unit,
    onMaxBitrate: (MaxBitrateCap) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_playback_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            ChipGroup(
                labelRes = R.string.settings_playback_skip,
                descriptionRes = R.string.settings_playback_skip_summary,
                options = SeekInterval.entries,
                selected = settings.seekInterval,
                labelFor = { stringResource(R.string.settings_seconds, it.seconds) },
                onSelect = onSeekInterval,
            )

            ChipGroup(
                labelRes = R.string.settings_playback_buffer,
                // Names the one thing a user cannot discover by tapping: this does not
                // apply to whatever is already playing.
                descriptionRes = R.string.settings_playback_buffer_summary,
                options = BufferMode.entries,
                selected = settings.bufferMode,
                labelFor = { stringResource(it.labelRes()) },
                onSelect = onBufferMode,
            )

            ChipGroup(
                labelRes = R.string.settings_playback_bitrate,
                descriptionRes = R.string.settings_playback_bitrate_summary,
                options = MaxBitrateCap.entries,
                selected = settings.maxBitrate,
                labelFor = { stringResource(it.labelRes()) },
                onSelect = onMaxBitrate,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    @StringRes labelRes: Int,
    @StringRes descriptionRes: Int,
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text(
        text = stringResource(descriptionRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )
    // Wraps rather than scrolls: every option must be reachable without discovering that
    // the row scrolls sideways.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelFor(option)) },
            )
        }
    }
}

@StringRes
private fun BufferMode.labelRes(): Int = when (this) {
    BufferMode.LOW -> R.string.settings_buffer_low
    BufferMode.BALANCED -> R.string.settings_buffer_balanced
    BufferMode.HIGH -> R.string.settings_buffer_high
}

@StringRes
private fun MaxBitrateCap.labelRes(): Int = when (this) {
    MaxBitrateCap.UNLIMITED -> R.string.settings_bitrate_unlimited
    MaxBitrateCap.EIGHT_MBPS -> R.string.settings_bitrate_8
    MaxBitrateCap.FOUR_MBPS -> R.string.settings_bitrate_4
    MaxBitrateCap.TWO_MBPS -> R.string.settings_bitrate_2
}
