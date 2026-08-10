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

package dev.quiblo.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiblo.feature.player.TrackMenu
import dev.quiblo.feature.player.TrackMenuEntry
import dev.quiblo.feature.player.TrackMenuKind
import dev.quiblo.tv.R

/**
 * Audio and subtitles, chosen with the remote.
 *
 * `agile/012` #023: the engine has been able to do this since it was written and no television
 * screen offered it, so AC-PLAY-04 has never passed on this app. What was missing was never the
 * capability — it was the way in.
 *
 * **A panel down the right, not a dialog across the middle.** The video keeps playing and stays
 * visible, which is the point: a viewer switching to an Arabic dub wants to hear the result
 * while the list is still open, and a modal that blacks out the picture makes that impossible.
 * It also keeps the menu clear of the controls bar along the bottom, so opening it does not
 * cover what is playing.
 *
 * `013` INC-F11 adds subtitle styling to this panel and `014` adds a version switcher, which is
 * why it draws whatever sections it is handed rather than an audio list and a subtitle list.
 */
@Composable
internal fun TvTrackMenu(
    menu: TrackMenu,
    onSelect: (TrackMenuKind, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstEntry = remember { FocusRequester() }

    // Something must hold focus the moment this appears or the remote looks dead — AC-TV-02,
    // and the failure this project has already had on three screens.
    LaunchedEffect(Unit) { runCatching { firstEntry.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Dim rather than cover: the picture stays legible behind the panel, so a change
            // of audio track can be heard and seen without shutting the list.
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd,
    ) {
        LazyColumn(
            modifier = Modifier
                .width(PANEL_WIDTH)
                .padding(PANEL_MARGIN)
                .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(14.dp))
                .padding(vertical = 16.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            menu.sections.forEachIndexed { sectionIndex, section ->
                item(key = "heading-${section.kind}") {
                    Text(
                        text = stringResource(
                            when (section.kind) {
                                TrackMenuKind.AUDIO -> R.string.tv_player_audio
                                TrackMenuKind.SUBTITLES -> R.string.tv_player_subtitles
                            },
                        ),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = if (sectionIndex == 0) 0.dp else 14.dp,
                            bottom = 6.dp,
                        ),
                    )
                }

                itemsIndexedEntries(section.entries) { entryIndex, entry ->
                    TrackRow(
                        entry = entry,
                        onClick = { onSelect(section.kind, entry.trackId) },
                        modifier = if (sectionIndex == 0 && entryIndex == 0) {
                            Modifier.focusRequester(firstEntry)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

/**
 * `itemsIndexed` over entries, keyed so a re-selection does not rebuild the list.
 *
 * A track id can repeat across sections — a container is free to number its audio and its
 * subtitles from zero — so the key carries the label too rather than the id alone.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedEntries(
    entries: List<TrackMenuEntry>,
    row: @Composable (Int, TrackMenuEntry) -> Unit,
) {
    entries.forEachIndexed { index, entry ->
        item(key = "${entry.trackId}-${entry.label}-$index") { row(index, entry) }
    }
}

@Composable
private fun TrackRow(entry: TrackMenuEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // A tick rather than a highlight, because the focused row is already highlighted and
        // "where the remote is" and "what is playing" are two facts a viewer needs at once.
        Text(
            text = if (entry.isSelected) SELECTED_MARK else " ",
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 15.sp,
        )

        Text(
            text = entry.label,
            color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontWeight = if (entry.isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Wide enough for a language name and a codec note, and no wider — it sits over the video. */
private val PANEL_WIDTH = 300.dp
private val PANEL_MARGIN = 32.dp
private const val SELECTED_MARK = "✓"
