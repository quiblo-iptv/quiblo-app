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

package dev.quiblo.tv.ui.browse

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import coil3.compose.AsyncImage
import dev.quiblo.core.common.cleanedForDisplay
import dev.quiblo.core.model.Channel
import dev.quiblo.feature.browse.HeroItem
import dev.quiblo.feature.browse.RatingBadge
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.AmbientRequest
import dev.quiblo.tv.ui.common.LocalAmbientSink
import kotlinx.coroutines.delay

/**
 * A hero slider for the home screen, showing featured titles with their metadata.
 *
 * Randomized from the available rows that have TMDB metadata. Cycles through items
 * automatically and allows D-pad Left / Right navigation between featured items.
 */
@Composable
fun TvHeroSlider(
    items: List<HeroItem>,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    var currentIndex by remember(items) { mutableIntStateOf(0) }
    val ambientSink = LocalAmbientSink.current

    LaunchedEffect(items, currentIndex) {
        val currentItem = items.getOrNull(currentIndex) ?: return@LaunchedEffect
        // Push the current artwork to the ambient light system
        ambientSink(AmbientRequest.Artwork(currentItem.metadata.backdropUrl ?: currentItem.metadata.posterUrl))

        delay(HERO_CYCLE_MILLIS)
        currentIndex = (currentIndex + 1) % items.size
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT),
    ) {
        Crossfade(
            targetState = items.getOrElse(currentIndex) { items.first() },
            animationSpec = tween(durationMillis = 600),
            label = "heroCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { item ->
            HeroContent(
                item = item,
                onPlay = { onPlay(item.channel) },
                onToggleFavorite = { onToggleFavorite(item.channel) },
            )
        }

        // Pagination Dots - focusable and navigable with D-Pad Left/Right
        HeroPaginationDots(
            itemCount = items.size,
            currentIndex = currentIndex,
            onNext = {
                if (items.size > 1) currentIndex = (currentIndex + 1) % items.size
            },
            onPrevious = {
                if (items.size > 1) currentIndex = if (currentIndex - 1 < 0) items.size - 1 else currentIndex - 1
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )

        // Another five featured titles, for a viewer who does not want any of these five. Below
        // the tab bar rather than beside it: the bar is what the remote walks up into from here,
        // and a control sharing that line would be reached on the way out of the slider.
        IconButton(
            onClick = onRefresh,
            scale = IconButtonDefaults.scale(focusedScale = REFRESH_FOCUSED_SCALE),
            colors = IconButtonDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = REFRESH_FOCUSED_FILL),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = REFRESH_TOP_PADDING, end = REFRESH_END_PADDING)
                .zIndex(1f),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.tv_hero_refresh),
                tint = Color.White.copy(alpha = REFRESH_TINT_ALPHA),
            )
        }
    }
}

@Composable
private fun HeroContent(
    item: HeroItem,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Backdrop image
        AsyncImage(
            model = item.metadata.backdropUrl ?: item.metadata.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Faded overlay - True blending.
        // Darker on the left for text legibility, transparent on the right for the picture.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.92f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                        endX = 1200f,
                    ),
                ),
        )

        // Soft vertical fade to black at the bottom to blend with the rows.
        // Also a light fade at the top to keep the tab bar legible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black,
                        ),
                    ),
                ),
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.7f)
                .padding(start = 64.dp, top = 80.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.channel.name.cleanedForDisplay(),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metadata row: Rating, Genres
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.metadata.rating?.let { rating ->
                    RatingBadge(rating = rating)
                    Spacer(modifier = Modifier.width(12.dp))
                }

                val genres = item.metadata.genres.take(3).joinToString(" • ")
                if (genres.isNotEmpty()) {
                    Text(
                        text = genres,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            item.metadata.overview?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: Play + Heart (Favorite)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroPlayButton(onClick = onPlay)
                HeroFavoriteButton(
                    isFavorite = item.channel.isFavorite,
                    onClick = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun HeroPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.tv_detail_play),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) Color.Black else Color.White,
        )
    }
}

@Composable
private fun HeroFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    /*
     * The chrome says where the remote is. The heart says what the state is (`029` #2).
     *
     * **It was red — filled, tinted, on a red ground, inside a red border — and red on a
     * television means one thing.** A slider that paints a red rectangle round a control the
     * moment a title is favourited reads as an error or a recording light, on the largest and
     * most glanced-at element in the app. Whether something is favourited is already said, once,
     * unambiguously, by whether the heart is filled or hollow; saying it four more times in a
     * colour that means something else is not emphasis, it is noise with a wrong meaning.
     *
     * So the box, the border and the tint follow *focus* only, exactly as the Play button beside
     * it does — the two now read as one row of controls rather than as one control and one alarm.
     */
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.tv_detail_unfavourite else R.string.tv_detail_favourite,
            ),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun HeroPaginationDots(
    itemCount: Int,
    currentIndex: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent,
            )
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.MediaFastForward, Key.MediaNext -> {
                        if (itemCount > 1) {
                            onNext()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft, Key.MediaRewind, Key.MediaPrevious -> {
                        if (itemCount > 1) {
                            onPrevious()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(itemCount) { index ->
            val isSelected = index == currentIndex
            Box(
                modifier = Modifier
                    .size(if (isSelected) 10.dp else 6.dp)
                    .background(
                        color = if (isSelected) {
                            if (isFocused) Color.White else Color.White.copy(alpha = 0.95f)
                        } else {
                            if (isFocused) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

private val HERO_HEIGHT = 560.dp
private const val HERO_CYCLE_MILLIS = 8_000L

/* The refresh control's own numbers: quiet until the remote reaches it, unmistakable when it does. */
private const val REFRESH_FOCUSED_SCALE = 1.2f
private const val REFRESH_FOCUSED_FILL = 0.2f
private const val REFRESH_TINT_ALPHA = 0.8f

/** Clear of the tab bar above it, which is 100dp of chrome on this panel. */
private val REFRESH_TOP_PADDING = 100.dp
private val REFRESH_END_PADDING = 64.dp
