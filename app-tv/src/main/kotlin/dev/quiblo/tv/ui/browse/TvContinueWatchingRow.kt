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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.tv.R

/**
 * "Continue watching": what was started and not finished, newest first.
 *
 * #006. The first row above the catalogue rather than a section inside it, because it is a
 * different kind of list — short, ordered by *when* rather than by name, and the one thing a
 * returning viewer is most likely to be looking for. A row that scrolled away with the
 * alphabet would defeat the point of having one.
 *
 * Wider tiles than the poster rows, and each carries a progress bar. A poster answers "what
 * is this"; a continue-watching tile has to answer "how far in was I", which is the only
 * reason to look at this row at all.
 *
 * A series appears once, at the episode last watched — six tiles for six episodes of one
 * programme is a list of one thing. That collapsing is `WatchHistoryRepository`'s, so the
 * phone and the television agree about it.
 */
@Composable
fun TvContinueWatchingRow(
    entries: List<HistoryEntry>,
    posters: Map<String, String>,
    onClick: (HistoryEntry) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.tv_continue_watching),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = TITLE_GAP),
        )

        // No content padding and no spacing beyond what the tiles carry themselves — exactly
        // as the poster rows do it. See [ContinueTile] for why the room has to live inside
        // the tile rather than around the row (#012).
        LazyRow {
            items(items = entries, key = { it.stableKey }) { entry ->
                ContinueTile(
                    entry = entry,
                    // The provider's own artwork first; the metadata service only fills a
                    // gap, exactly as the poster rows do.
                    artworkUrl = entry.artworkUrl?.takeIf { it.isNotBlank() }
                        ?: posters[entry.titleKey],
                    onClick = { onClick(entry) },
                    onRemove = { onRemove(entry) },
                )
            }
        }
    }
}

@Composable
private fun ContinueTile(
    entry: HistoryEntry,
    artworkUrl: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    /*
     * Long-press arms the tile; a second press removes it. `INC-F3`, television half.
     *
     * **Not a dialog, and not a dropdown menu.** This app contains no modal at all — the
     * category rename in `TvSettingsScreen` is a pencil that reveals a field, and that is the
     * house style. A `DropdownMenu` is a phone control, and a first `AlertDialog` here would set
     * the pattern for every one after it by accident, on the one row whose click handling is
     * load-bearing (see the `clickable`/`graphicsLayer` note below).
     *
     * So the confirm is the tile itself: its label becomes the question, and the ordinary
     * centre press answers it. A remote can always do that, and there is nothing to get stuck
     * inside.
     *
     * **Disarming on focus loss is the safety.** Walking away is how a viewer says no, and it
     * costs no extra control — which matters because there is no Cancel to put anywhere.
     */
    var isArmed by remember(entry.stableKey) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Walking away is how a viewer says no. Without this the tile would stay armed and the next
    // ordinary press on it — possibly minutes later — would delete instead of play.
    LaunchedEffect(isFocused) { if (!isFocused) isArmed = false }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) FOCUSED_SCALE else 1f,
        label = "continueScale",
    )

    /*
     * The room a focused tile grows into lives here, inside the tile, on both axes.
     *
     * It used to be `contentPadding` on the row, **vertically only** — so the first tile had
     * no horizontal room at all, and a `LazyRow` clips to its own bounds, which cut its scaled
     * left edge flat (#012). Round 2 fixed exactly this in `Poster` and wrote down why; the
     * note lived next to the poster and the fix did not travel to this row.
     *
     * That is the lesson worth keeping over the fix: **a correction that lives in one row
     * implementation is not applied to the other by being written down near it.** Both rows
     * now carry their growth the same way, which is the only version of "shared" that holds.
     */
    Box(
        modifier = Modifier.padding(
            horizontal = FOCUS_GROWTH_HORIZONTAL,
            vertical = FOCUS_GROWTH,
        ),
    ) {
        // `clickable` outside `graphicsLayer`, so this tile's focusable does not sit inside the
        // animating scale and report a size that grows every frame. The reasoning is `Poster`'s
        // and is written out at length there; the same fault was in this tile, and a continue
        // row that shook while the catalogue below it held still would be its own bug.
        Column(
            modifier = Modifier
                .width(TILE_WIDTH)
                // `combinedClickable` in exactly the position `clickable` held, outside the
                // `graphicsLayer` — the note above is why, and moving it is how #008 comes back.
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isArmed) {
                            isArmed = false
                            onRemove()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = { isArmed = true },
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(TILE_ASPECT_RATIO)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isFocused) 3.dp else 0.dp,
                        color = if (isFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    ),
            ) {
                if (artworkUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                } else {
                    SubcomposeAsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // How far in, along the bottom of the artwork. Drawn only when the length is
                // known: a bar with no denominator would be a guess rendered as a fact.
                if (entry.durationMillis > 0L) {
                    val progress = (entry.positionMillis.toFloat() / entry.durationMillis)
                        .coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(Color.White),
                        )
                    }
                }
            }

            Text(
                text = if (isArmed) {
                    stringResource(R.string.tv_continue_remove_confirm)
                } else {
                    entry.title
                },
                color = if (isArmed) {
                    Color(0xFFFF8A80)
                } else {
                    Color.White.copy(alpha = if (isFocused) 1f else 0.75f)
                },
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )

            // The episode, when this is one. A series is listed by its own name, so without
            // this a viewer cannot tell which episode "continue" would resume.
            // Bound to locals rather than asserted: these are `val`s in another module, which is
            // the one case Kotlin will not smart-cast, and !! on something already null-checked
            // reads as a real nullability risk when there is none.
            val season = entry.seasonNumber
            val episode = entry.episodeNumber
            if (season != null && episode != null) {
                Text(
                    text = stringResource(
                        R.string.tv_series_episode_number,
                        season,
                        episode,
                    ),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private val TILE_WIDTH = 260.dp
private const val TILE_ASPECT_RATIO = 16f / 9f
private const val FOCUSED_SCALE = 1.08f

/**
 * How far a focused tile grows past its own top and bottom edge, rounded up.
 *
 * The tile is 260dp wide at 16:9 plus its label, near enough 200dp tall; [FOCUSED_SCALE]
 * scales it about its centre, so it gains about 16dp of height and half of that goes upwards.
 */
private val FOCUS_GROWTH = 12.dp

/**
 * How far it grows past its left and right edges.
 *
 * Half of what it gains in width: 260dp scaled by 1.08 is 21dp wider, so 10.5dp each side.
 * Rounded up. Without this the *first* tile is clipped flat by the row's own bounds — which is
 * #012, and which the poster rows learned in round 2.
 */
private val FOCUS_GROWTH_HORIZONTAL = 11.dp
private val TITLE_GAP = 16.dp
