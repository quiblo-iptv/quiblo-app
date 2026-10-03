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

package dev.quiblo.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pick a face, or none — the phone's picker, for making a profile and for changing one later.
 *
 * A row of the whole set rather than a dialog behind a button: there are eight of them, they
 * are the size of a thumb, and a picker that has to be opened is a picker most people creating
 * a profile will never see. Selecting the chosen one again clears it, which is the only way
 * back to the initial-on-a-colour fallback once a face has been tapped.
 *
 * No "none" tile. An empty circle offered beside eight pictures reads as a broken one, and the
 * fallback is not an absence — it is what the circle draws when nobody has chosen.
 *
 * Here rather than in the app since `FEAT-039`, because the profile chooser and Settings → Manage
 * profiles both offer it, and two copies of one picker is how they come to offer different faces.
 */
@Composable
fun AvatarFacePicker(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        AvatarFaces.forEach { face ->
            val isSelected = face.key == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(face.key) }
                    .padding(3.dp),
            ) {
                ProfileAvatar(name = face.key, avatar = face.key, size = PICKER_AVATAR_SIZE)
            }
        }
    }
}

/** A thumb's width, so the whole set fits without the row becoming a screen of its own. */
private val PICKER_AVATAR_SIZE = 44.dp
