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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * One of the pictures a profile can wear.
 *
 * **Generated from a fixed set the app ships, never a file the viewer chose.** That decision is
 * what makes this feature small: no storage-access picker, no copy in app storage, no size cap,
 * no file to delete when a profile is deleted, and nothing of a viewer's that can end up inside
 * an export. `AC-DATA` promises a backup is portable, and a backup carrying somebody's
 * photograph is a different promise than the one this project made.
 *
 * [key] is what the database stores, and it is a name rather than an index on purpose: a
 * position would move the moment this list is reordered, and every profile on every device
 * would silently change its face.
 */
data class AvatarFace(
    val key: String,
    val icon: ImageVector,
    val background: Color,
)

/**
 * The set on offer, in the order the chooser shows them.
 *
 * Symbols rather than drawn portraits. `015` calls these "illustrated faces" and this is the
 * honest version of that: nine recognisable shapes on nine backgrounds, shipped as vectors that
 * scale from a 32dp corner control to a television's chooser without a second asset. Adding a
 * real illustration set later changes this list and nothing else, because what is stored is the
 * key and not the picture.
 *
 * The backgrounds are fixed rather than drawn from the theme. A viewer picks the blue one; it
 * stays the blue one in dark mode, in light mode, and on the television.
 */
val AvatarFaces: List<AvatarFace> = listOf(
    AvatarFace("face", Icons.Filled.Face, Color(0xFF5C6BC0)),
    AvatarFace("star", Icons.Filled.Star, Color(0xFFEF6C00)),
    AvatarFace("pets", Icons.Filled.Pets, Color(0xFF00897B)),
    AvatarFace("games", Icons.Filled.SportsEsports, Color(0xFF8E24AA)),
    AvatarFace("music", Icons.Filled.MusicNote, Color(0xFFD81B60)),
    AvatarFace("rocket", Icons.Filled.RocketLaunch, Color(0xFF1E88E5)),
    AvatarFace("football", Icons.Filled.SportsSoccer, Color(0xFF43A047)),
    AvatarFace("sofa", Icons.Filled.Weekend, Color(0xFF6D4C41)),
)

/** The face this key names, or null — for a profile that predates avatars, or a key we dropped. */
fun avatarFaceFor(key: String?): AvatarFace? = key?.let { k -> AvatarFaces.firstOrNull { it.key == k } }

/**
 * A profile's picture, at [size], always a circle.
 *
 * Takes a name and a key rather than a `Profile`, so this module depends on nothing. It is read
 * by the chooser on both apps and by the control beside the settings gear, and a design-system
 * module that imported the domain model to draw a circle would be the first thread pulling the
 * app's layers through it.
 *
 * **A null [avatar] is not a blank circle.** It draws the viewer's initial on a colour derived
 * from their name, so a profile created before this feature existed — or one whose owner never
 * opened the picker — still has something a household recognises at a glance across a room.
 */
@Composable
fun ProfileAvatar(
    name: String,
    avatar: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val face = avatarFaceFor(avatar)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(face?.background ?: colourForName(name)),
        contentAlignment = Alignment.Center,
    ) {
        if (face != null) {
            Icon(
                imageVector = face.icon,
                // The name beside it is the label. Announcing "star" as well would have a
                // screen reader read out a decoration the viewer did not choose for meaning.
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * ICON_FRACTION),
            )
        } else {
            Text(
                text = initialOf(name),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * INITIAL_FRACTION).sp,
            )
        }
    }
}

/**
 * The first character of a name, uppercased, or a placeholder when there is nothing to take.
 *
 * Reads the first *code point* rather than the first `Char`, so a name beginning with an emoji
 * or any character outside the basic plane yields that character instead of half of it.
 */
private fun initialOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val codePoint = trimmed.codePointAt(0)
    return String(Character.toChars(codePoint)).uppercase()
}

/**
 * A stable colour for a name.
 *
 * `String.hashCode` is specified by the language rather than left to an implementation, so this
 * is the same colour on a phone, on the television, and after a backup is restored onto a new
 * device. That is the whole requirement: a household identifies a profile by its colour long
 * before it reads the name, and a colour that changed between devices would be worse than none.
 *
 * The hash is shifted into a positive range before the modulus rather than negated, because
 * `Int.MIN_VALUE` has no positive counterpart and `abs` returns it unchanged — which is how a
 * name at that one hash would have indexed the list backwards.
 */
private fun colourForName(name: String): Color =
    FALLBACK_COLOURS[(name.hashCode().toLong() - Int.MIN_VALUE).mod(FALLBACK_COLOURS.size)]

/** Distinct at a distance and dark enough for white to read on all of them. */
private val FALLBACK_COLOURS = listOf(
    Color(0xFF3949AB),
    Color(0xFF00838F),
    Color(0xFF6A1B9A),
    Color(0xFFAD1457),
    Color(0xFF2E7D32),
    Color(0xFFE65100),
)

/** The symbol fills a little over half the circle, leaving it reading as a badge rather than a tile. */
private const val ICON_FRACTION = 0.55f

/** An initial sits smaller than the symbol does: a letter needs less room to be recognised. */
private const val INITIAL_FRACTION = 0.42f
