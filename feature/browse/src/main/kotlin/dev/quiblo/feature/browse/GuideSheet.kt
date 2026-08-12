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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Now and next for one channel (AC-EPG-02), and the rest of the day under it (INC-F4).
 *
 * Shown on long press so a plain tap still means "play", which is what a user reaches
 * for ninety-nine times out of a hundred.
 *
 * Opening the sheet is what asks the panel for the full listing — the one place in the app
 * where a viewer has said, in as many words, that they want more than now and next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuideSheet(
    channel: Channel,
    nowNext: Flow<List<Programme>>,
    schedule: Flow<List<Programme>>,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val programmes by nowNext.collectAsStateWithLifecycle(initialValue = emptyList())
    val listing by schedule.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(channel.stableKey) { onOpen() }

    // Built against the moment the sheet opened, not against a clock that ticks. A now-marker
    // that crept while the sheet was open would move every block under a finger already on its
    // way to one.
    val openedAt = remember(channel.stableKey) { System.currentTimeMillis() }
    val timeline = remember(listing, openedAt) { guideTimeline(listing, openedAt) }

    var selected: Programme? by remember(channel.stableKey) { mutableStateOf(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            if (programmes.isEmpty()) {
                Text(
                    text = stringResource(R.string.browse_no_guide),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
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

            // Only once there is more to show than the two entries above it. A strip holding
            // the same programmes twice is a wider way of saying nothing.
            if (!timeline.isEmpty && listing.size > programmes.take(2).size) {
                Text(
                    text = stringResource(R.string.browse_guide_full),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 28.dp),
                )

                GuideStrip(
                    timeline = timeline,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.padding(top = 8.dp),
                )

                // A tapped block, written out. Half-hour blocks are too narrow to hold a plot,
                // and on a phone they are too narrow to hold some titles whole.
                selected?.let { programme ->
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        ProgrammeBlock(programme = programme, label = null, showProgress = false)
                    }
                }
            }
        }
    }
}

/**
 * One programme written out: what it is called, when it runs, and what it is.
 *
 * A null [label] is the block a viewer picked off the timeline rather than one of the two the
 * sheet leads with — its position on the strip already said which one it is, so naming it
 * "Now" or "Next" would be wrong about half the time and redundant the other half.
 */
@Composable
private fun ProgrammeBlock(programme: Programme, label: String?, showProgress: Boolean) {
    val zone = remember { ZoneId.systemDefault() }
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
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
