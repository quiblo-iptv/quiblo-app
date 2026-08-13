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

package dev.quiblo.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Audio and subtitles, on the phone.
 *
 * The television draws the same [TrackMenu] as a panel down the right, because a remote
 * navigates a list and a finger taps one. Both consume the same model, which is what stops the
 * two apps disagreeing about what a stream offers — the drift `PLAN-TV.md` §2 warns about, and
 * that `agile/004` found twice.
 *
 * A dialog rather than a bottom sheet: the video is behind it and playback is not paused, so
 * the scrim is deliberately light — switching to another audio track is a change a viewer wants
 * to *hear* with the list still open.
 */
@Composable
internal fun TrackMenuSheet(
    menu: TrackMenu,
    onSelect: (TrackMenuKind, String?) -> Unit,
    onAction: (TrackMenuActionKind) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = SHEET_MAX_WIDTH)
                    .background(Color(0xFF1B1B1B), RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                menu.sections.forEachIndexed { index, section ->
                    Text(
                        text = stringResource(
                            section.kind.headingRes(),
                        ),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = if (index == 0) 0.dp else 14.dp,
                            bottom = 6.dp,
                        ),
                    )

                    section.entries.forEach { entry ->
                        TrackRow(
                            label = entry.label,
                            isSelected = entry.isSelected,
                            onClick = {
                                onSelect(section.kind, entry.trackId)
                                onDismiss()
                            },
                        )
                    }

                    // Below the choices, never among them: an action is not a track, and a tick
                    // beside "Add a subtitle file" is not a state it can be in.
                    section.actions.forEach { action ->
                        TrackRow(
                            label = action.label,
                            isSelected = false,
                            onClick = {
                                onAction(action.kind)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A tick, so what is playing is legible without relying on colour alone.
        Text(
            text = if (isSelected) SELECTED_MARK else " ",
            color = Color.White,
            fontSize = 15.sp,
        )

        Text(
            text = label,
            color = Color.White.copy(alpha = if (isSelected) 1f else 0.85f),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val SHEET_MAX_WIDTH = 360.dp
private const val SELECTED_MARK = "✓"

/** One heading per section, so neither app has to hold its own copy of the mapping. */
internal fun TrackMenuKind.headingRes(): Int = when (this) {
    TrackMenuKind.AUDIO -> R.string.player_audio
    TrackMenuKind.SUBTITLES -> R.string.player_subtitles
    TrackMenuKind.SUBTITLE_SIZE -> R.string.player_subtitles_size
    TrackMenuKind.SUBTITLE_TEXT_COLOUR -> R.string.player_subtitles_text_colour
    TrackMenuKind.SUBTITLE_BACKGROUND -> R.string.player_subtitles_background
}
