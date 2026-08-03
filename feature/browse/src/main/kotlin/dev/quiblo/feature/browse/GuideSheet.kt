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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Programme
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Now and next for one channel (AC-EPG-02).
 *
 * Shown on long press so a plain tap still means "play", which is what a user reaches
 * for ninety-nine times out of a hundred.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuideSheet(
    channel: Channel,
    nowNext: Flow<List<Programme>>,
    onDismiss: () -> Unit,
) {
    val programmes by nowNext.collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(text = channel.name, style = MaterialTheme.typography.titleLarge)

            if (programmes.isEmpty()) {
                Text(
                    text = stringResource(R.string.browse_no_guide),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                programmes.take(2).forEachIndexed { index, programme ->
                    ProgrammeBlock(
                        programme = programme,
                        label = stringResource(
                            if (index == 0) R.string.browse_now else R.string.browse_next,
                        ),
                        showProgress = index == 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(programme: Programme, label: String, showProgress: Boolean) {
    val zone = remember { ZoneId.systemDefault() }
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = stringResource(
                    R.string.browse_guide_time,
                    programme.startEpochMillis.asLocalTime(zone),
                    programme.endEpochMillis.asLocalTime(zone),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = programme.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        programme.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (showProgress) {
            LinearProgressIndicator(
                progress = { programme.progressAt(System.currentTimeMillis()) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/**
 * Renders a UTC instant in the device's own zone.
 *
 * Programme times are stored as UTC milliseconds precisely so this conversion is the
 * only place a timezone is applied. A panel in another country therefore shows correct
 * local times without any per-provider offset handling (AC-EPG-03).
 */
private fun Long.asLocalTime(zone: ZoneId): String =
    Instant.ofEpochMilli(this).atZone(zone).format(TIME_FORMAT)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
