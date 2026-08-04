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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Back and favourite, floating over a detail screen instead of sitting in a title bar.
 *
 * A detail screen already states what it is about, in full, at a size you can read — so an
 * app bar carrying the same title above it says the name twice and spends a strip of the
 * screen doing it. On a phone in landscape that strip is a meaningful fraction of the
 * visible height, and the title it holds is the one thing on the screen that was never in
 * doubt.
 *
 * The two controls it did carry are worth keeping, so they stay, as buttons on the content.
 * Each gets its own circular scrim because what sits behind them is arbitrary: a backdrop
 * in portrait, a plain surface in landscape, and any artwork at all underneath. A scrim
 * makes contrast a property of the button rather than a property of the film.
 */
@Composable
fun DetailOverlayActions(
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ScrimIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.detail_back),
            onClick = onBack,
        )

        ScrimIconButton(
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            // The description says what pressing it will do, not what the icon looks like.
            contentDescription = stringResource(
                if (isFavorite) R.string.browse_favorite_remove else R.string.browse_favorite_add,
            ),
            onClick = onToggleFavorite,
            // The one place a theme colour is right here: a filled heart is a state, and
            // the accent is what says so.
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
        )
    }
}

@Composable
private fun ScrimIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(color = Color.Black.copy(alpha = SCRIM_ALPHA), shape = CircleShape),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}

private const val SCRIM_ALPHA = 0.45f
