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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind

/**
 * A poster card: artwork edge to edge, a gradient foot, and the title over it.
 *
 * Handles a live channel too, which is what lets Favourites — the one screen holding every
 * kind at once — be a single uniform grid. A live channel's "logo" is a small wide badge on
 * a transparent background, so it is fitted inside the tile rather than cropped to fill it;
 * cropping one to 2:3 gives you a corner of a logo. The tile keeps its shape either way, so
 * the grid does not go ragged.
 *
 * The gradient is not decoration. Poster art is arbitrary, so a title drawn straight onto
 * it is legible or not depending on the film, and a scrim is the only way to make the
 * contrast a property of the card instead of a property of the artwork.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelArtCard(
    channel: Channel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    rating: Double? = null,
) {
    // A long title is truncated until asked for. Hover covers a mouse or trackpad; long
    // press is the equivalent on a touch screen, where there is no hover to speak of.
    var scrollTitle by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val animateTitle = isHovered || scrollTitle

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(POSTER_ASPECT_RATIO)
            .hoverable(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { scrollTitle = !scrollTitle },
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Artwork(logoUrl = channel.logoUrl, isLogo = channel.kind == MediaKind.LIVE)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            // Transparent well past the halfway point so the scrim darkens
                            // the title area without dulling the artwork it exists to
                            // showcase.
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                SCRIM_START to Color.Transparent,
                                1f to Color.Black.copy(alpha = SCRIM_ALPHA),
                            ),
                        ),
                    ),
            )

            // Top left, opposite the heart, so the two never collide on a narrow tile.
            rating?.let {
                RatingBadge(
                    rating = it,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp),
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.browse_favorite),
                    // White rather than a theme colour: this sits on artwork, not on a
                    // surface, so the theme's contrast guarantees do not apply.
                    tint = if (channel.isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                )
            }

            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                // basicMarquee is a no-op when the text fits, so short titles are
                // unaffected and only the ones that actually overflow move.
                overflow = if (animateTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .then(if (animateTitle) Modifier.basicMarquee() else Modifier),
            )
        }
    }
}

@Composable
private fun Artwork(logoUrl: String?, isLogo: Boolean) {
    if (logoUrl.isNullOrBlank()) {
        ArtworkPlaceholder(isLogo = isLogo)
        return
    }

    SubcomposeAsyncImage(
        model = logoUrl,
        contentDescription = null,
        // Crop for a poster, because the card is a poster frame and letterboxing artwork
        // inside it would reintroduce the bars this layout exists to remove.
        //
        // Fit for a live channel, whose "logo" is a small wide badge on a transparent
        // background. Cropping one to 2:3 shows a corner of a logo — so Favourites, which
        // holds every kind at once, keeps the tile shape and changes only what happens
        // inside it. One grid, two ways of filling a frame.
        contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .padding(if (isLogo) LOGO_INSET else 0.dp),
        loading = { ArtworkPlaceholder(isLogo = isLogo) },
        error = { ArtworkPlaceholder(isLogo = isLogo) },
    )
}

@Composable
private fun ArtworkPlaceholder(isLogo: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isLogo) Icons.Filled.LiveTv else Icons.Filled.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Standard poster proportions, so mixed artwork sizes still line up in the grid. */
private const val POSTER_ASPECT_RATIO = 2f / 3f

/** Keeps a wide channel logo clear of the tile's rounded corners and its two badges. */
private val LOGO_INSET = 16.dp
private const val SCRIM_START = 0.55f
private const val SCRIM_ALPHA = 0.85f
