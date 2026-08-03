/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class EpgGridRow(
    val channelId: Long,
    val channelName: String,
    val programmes: List<EpgGridItem>,
)

data class EpgGridItem(
    val title: String,
    val timeRange: String,
    val isNowPlaying: Boolean = false,
)

@Composable
fun EpgGridScreen(
    rows: List<EpgGridRow>,
    onChannelClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        // Time header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 8.dp),
        ) {
            Box(modifier = Modifier.width(120.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Channel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(modifier = Modifier.horizontalScroll(scrollState)) {
                listOf("08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00").forEach { time ->
                    Box(
                        modifier = Modifier.width(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Channel rows
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = rows, key = { it.channelId }) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Channel label
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .padding(8.dp)
                            .clickable { onChannelClick(row.channelId) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = row.channelName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Programmes timeline row
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState),
                    ) {
                        row.programmes.forEach { prog ->
                            Card(
                                modifier = Modifier
                                    .width(140.dp)
                                    .padding(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (prog.isNowPlaying) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                ),
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(
                                        text = prog.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (prog.isNowPlaying) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = prog.timeRange,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
