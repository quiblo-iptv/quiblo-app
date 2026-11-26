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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.quiblo.core.common.cleanedForDisplay
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.designsystem.LocalAmbientArtwork
import dev.quiblo.designsystem.posterLift

/**
 * A poster card, as the television draws one: the artwork in a thin rounded frame, the title
 * underneath it, and a lift towards the viewer when it is reached for.
 *
 * **The title moved off the poster when the phone took the television's look.** It used to sit
 * on a gradient foot over the artwork, which made the contrast a property of the card — and also
 * put a dark band across the bottom third of every poster in the grid. The television never did
 * that: its posters are pictures, edge to edge, and their names sit beneath them in the room's own
 * colour. Underneath, the contrast is the theme's to guarantee, and the poster is all poster.
 *
 * Touching it lifts it and lights the room from it — [posterLift], and the same
 * [LocalAmbientArtwork] the detail screens report to — which is what a remote's focus does on the
 * television: the thing being reached for changes the colour of the room around it.
 *
 * Handles a live channel too, which is what lets Favourites — the one screen holding every
 * kind at once — be a single uniform grid. A live channel's "logo" is a small wide badge on
 * a transparent background, so it is fitted inside the tile rather than cropped to fill it;
 * cropping one to 2:3 gives you a corner of a logo. The tile keeps its shape either way, so
 * the grid does not go ragged.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelArtCard(
    channel: Channel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    rating: Double? = null,
    /**
     * Artwork from the metadata service, used only when the provider supplied none.
     *
     * Series are where this matters. Panels commonly carry a series' cover on the details
     * endpoint and nothing on the catalogue entry, so the Series grid was a wall of
     * placeholder icons for titles the app could describe perfectly well once opened. The
     * provider's own artwork still wins wherever it exists: it is the art for the thing
     * being served, where this is a guess from a title search.
     */
    fallbackPosterUrl: String? = null,
) {
    // A long title is truncated until asked for. Hover covers a mouse or trackpad; long
    // press is the equivalent on a touch screen, where there is no hover to speak of.
    var scrollTitle by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val animateTitle = isHovered || scrollTitle
    val isReachedFor = isPressed || isFocused || isHovered
    val artworkUrl = channel.logoUrl?.takeIf { it.isNotBlank() } ?: fallbackPosterUrl

    LightTheRoomWhileReachedFor(
        isReachedFor = isReachedFor,
        // A channel's logo is a badge on transparency, not a picture worth lighting a room from.
        artworkUrl = artworkUrl.takeIf { channel.kind != MediaKind.LIVE },
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT_RATIO)
                .posterLift(interactionSource, POSTER_SHAPE)
                .clip(POSTER_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .hoverable(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = { scrollTitle = !scrollTitle },
                ),
        ) {
            Artwork(
                logoUrl = artworkUrl,
                isLogo = channel.kind == MediaKind.LIVE,
            )

            // A short wash under the two badges only, so a white heart holds on a white poster.
            // The rest of the artwork is left alone: the poster is the point of the tile.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(BADGE_WASH_HEIGHT)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = BADGE_WASH_ALPHA), Color.Transparent),
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
        }

        // The television's caption: under the picture, quieter than it until it is reached for.
        Text(
            text = channel.name.cleanedForDisplay(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(
                alpha = if (isReachedFor) 1f else IDLE_TITLE_ALPHA,
            ),
            maxLines = 1,
            softWrap = false,
            // basicMarquee is a no-op when the text fits, so short titles are
            // unaffected and only the ones that actually overflow move.
            overflow = if (animateTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 8.dp)
                .then(if (animateTitle) Modifier.basicMarquee() else Modifier),
        )
    }
}

/**
 * The poster being reached for lights the room, as focus does on the television.
 *
 * Reported on the press rather than on sight, so scrolling a grid past a hundred posters asks for
 * nothing. See [LocalAmbientArtwork].
 */
@Composable
private fun LightTheRoomWhileReachedFor(isReachedFor: Boolean, artworkUrl: String?) {
    val ambientSink = LocalAmbientArtwork.current
    LaunchedEffect(isReachedFor) {
        if (isReachedFor && artworkUrl != null) ambientSink(artworkUrl)
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

/** The television's corner: rounder than this and a wall of posters reads as a wall of buttons. */
private val POSTER_SHAPE = RoundedCornerShape(8.dp)

/** How far down the poster the wash under the badges reaches, and how dark it starts. */
private const val BADGE_WASH_HEIGHT = 0.28f
private const val BADGE_WASH_ALPHA = 0.45f

/** The television's caption alpha, for a title nobody is reaching for. */
private const val IDLE_TITLE_ALPHA = 0.78f
