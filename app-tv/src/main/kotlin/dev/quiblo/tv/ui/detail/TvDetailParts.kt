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

package dev.quiblo.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.designsystem.AutoDirection
import dev.quiblo.tv.ui.common.travellingGlow

/**
 * The parts a film and a series detail screen both need.
 *
 * Shared rather than written twice because #005 and #007 ask for the same things about two
 * different kinds of title — artwork, a plot, a score, a cast, and a way to start watching.
 * Two copies is how one of them ends up with the age rating and the other does not.
 */

/** Cover art, at a size a poster actually reads at from across a room. */
@Composable
fun DetailArtwork(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(ARTWORK_WIDTH)
            .aspectRatio(POSTER_ASPECT_RATIO)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp),
            )
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The one-line facts: score, year, length, age rating, genres.
 *
 * Anything absent is omitted rather than shown blank. A panel supplies some of these and a
 * metadata service the rest, and a row of empty labels is worse than a shorter row.
 *
 * The order is the order a listings page uses, and it is not arbitrary: the score decides
 * whether to read on, the year and the length decide whether to start it tonight, and the
 * genres are the widest and go last so the truncation lands on them.
 */
@Composable
fun DetailFacts(
    rating: Double?,
    ageRating: String?,
    genres: List<String>,
    /** The year of release, or of a series' first broadcast. */
    year: Int? = null,
    /** Already written out — `1h 52m`. See `runtimeLabel`. A series has none. */
    runtime: String? = null,
    /** Anything else this kind of title carries. A series puts its season count here. */
    extra: String? = null,
) {
    val parts = buildList {
        rating?.let { add("★ %.1f".format(it)) }
        year?.let { add(it.toString()) }
        runtime?.takeIf { it.isNotBlank() }?.let(::add)
        extra?.takeIf { it.isNotBlank() }?.let(::add)
        ageRating?.takeIf { it.isNotBlank() }?.let(::add)
        genres.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
    }
    if (parts.isEmpty()) return

    Text(
        text = parts.joinToString("  ·  "),
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * The plot, and who made it.
 *
 * [isEnriching] exists because "not asked yet" and "asked, and there is nothing" look
 * identical in the data and must not look identical on screen — asserting "no description"
 * for the moment before the answer lands reads as a wrong answer rather than a pending one.
 */
@Composable
fun DetailOverview(
    overview: String?,
    isEnriching: Boolean,
    author: String? = null,
    authorLabel: String? = null,
    cast: List<String> = emptyList(),
) {
    when {
        isEnriching && overview.isNullOrBlank() -> Text(
            text = "…",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 14.dp),
        )

        !overview.isNullOrBlank() -> AutoDirection(overview) {
            Text(
                text = overview,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                maxLines = OVERVIEW_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )
        }
    }

    if (!author.isNullOrBlank() && !authorLabel.isNullOrBlank()) {
        Text(
            text = "$authorLabel: $author",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    if (cast.isNotEmpty()) {
        Text(
            text = cast.joinToString(", "),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A focusable action. The only kind of control these screens have. */
@Composable
fun DetailButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .background(
                color = when {
                    isFocused -> Color.White
                    isPrimary -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.10f)
                },
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            // Only on the primary action, only while the remote is elsewhere, and at a third of
            // the search field's brightness. It says "this is the one" to somebody scanning the
            // screen; it must not compete with the focus ring or with the form above it.
            .travellingGlow(
                isActive = isPrimary && !isFocused,
                cornerRadius = 10.dp,
                intensity = PRIMARY_GLOW,
            )
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            // Inverted while focused: a white fill needs dark text, and the focused control
            // has to be unmistakable from the other side of a room.
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = if (isPrimary || isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            // Clipped was the default and it is the worse of the two failures: "Add to
            // favourites" was cut mid-word with nothing to say it had been (#014). The row
            // these sit in wraps now, so this should not fire — it is here for the label that
            // is longer in some other language than any of ours is in English.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Title, big, with whatever the provider called it. */
@Composable
fun DetailTitle(title: String) {
    AutoDirection(title) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 26.sp,
            // Without an explicit line height the default is too tight for type this large
            // and the two lines overlap — visible immediately on any title long enough to
            // wrap, which on a panel's own naming is most of them.
            lineHeight = 33.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Centres a message — loading, or a title the provider has since dropped. */
@Composable
fun DetailMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/** Whatever the metadata service knew, or nothing at all when it was never asked. */
fun TitleMetadata?.genresOrEmpty(): List<String> = this?.genres.orEmpty()

/** The gap between the artwork and everything written beside it. */
val DETAIL_COLUMN_GAP = 32.dp

/**
 * Cover width, sized for the screen rather than for a design mock.
 *
 * The target panel is 960x540dp, and after overscan there are 444dp of height to spend. At
 * the old 260dp the artwork alone was 390dp of that — nearly the whole screen — which left a
 * sliver for an episode list and pushed the season picker out of reach entirely. 160dp is
 * 240dp tall, which leaves room for the actions and the start of the list beneath them.
 */
private val ARTWORK_WIDTH = 160.dp
private const val POSTER_ASPECT_RATIO = 2f / 3f

/** Three lines beside a 240dp cover, so the plot never pushes the actions off screen. */
private const val OVERVIEW_MAX_LINES = 3

/** A third of the search field's, which is the brightest thing this modifier is used at. */
private const val PRIMARY_GLOW = 0.34f
