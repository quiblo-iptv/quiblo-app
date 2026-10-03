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

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quiblo.core.data.diagnostics.Diagnosis
import dev.quiblo.feature.player.headline
import dev.quiblo.feature.player.icon
import dev.quiblo.feature.player.labelRes
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The last playback failures on this device and what was found (`FEAT-035`).
 *
 * For the person who says "it failed three times last night" and wants to know whose fault it
 * was: each entry is the verdict the player showed and the redacted evidence behind it. Memory
 * only, which the summary says — nothing here survives the app closing, and nothing is sent.
 */
@Composable
internal fun PlaybackLogCard(
    entries: List<Diagnosis>,
    reportText: () -> String,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_playback_log_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_playback_log_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_playback_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Quiblo", reportText())))
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.settings_playback_log_copy))
                }
                entries.forEach { entry ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    PlaybackLogEntry(entry)
                }
            }
        }
    }
}

@Composable
private fun PlaybackLogEntry(entry: Diagnosis) {
    Text(
        text = "${formatTime(entry.atEpochMillis)} · ${entry.title}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(
            imageVector = entry.verdict.side.icon(),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(entry.verdict.side.labelRes()),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
    Text(text = entry.headline(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    Text(
        text = entry.details,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun formatTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))
