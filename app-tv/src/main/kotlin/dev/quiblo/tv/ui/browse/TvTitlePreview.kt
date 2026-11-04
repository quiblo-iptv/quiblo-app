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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.quiblo.core.common.cleanedForDisplay
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.tv.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/**
 * Where a focused poster says which title it is, for the preview banner above it.
 *
 * A composition local for the reason [dev.quiblo.tv.ui.common.LocalAmbientSink] is one: the
 * alternative is a parameter through the list, every row and every poster — the composables
 * `TvBrowseScrollStabilityTest` measures — so that a banner can be told what to say.
 */
internal val LocalFocusedTileSink = staticCompositionLocalOf<(TvTile) -> Unit> { {} }

/**
 * A catalogue with a preview of the focused title above it: the title's picture on the right,
 * fading out towards the middle, and its name, year, certificate and plot on the left.
 *
 * The rows are pushed down to make the room rather than drawn under the banner, and [content]
 * is told how much height that leaves so a shelf can size its posters to still fit a whole row
 * with its heading — see `posterWidthFor`. A grid ignores it and scrolls.
 *
 * Before the remote reaches the catalogue the banner shows [firstTile], so arriving on the tab
 * is not a strip of empty space.
 */
@Composable
@Suppress("LongParameterList")
internal fun TvCatalogueWithPreview(
    firstTile: TvTile?,
    ratings: Map<String, Double>,
    posters: Map<String, String>,
    /** See `BrowseViewModel.titlePreview`: cached first, the network only once the remote rests. */
    preview: (Channel) -> Flow<TitleMetadata?>,
    modifier: Modifier = Modifier,
    content: @Composable (available: Dp) -> Unit,
) {
    var focusedTile by remember { mutableStateOf<TvTile?>(null) }
    val wanted = focusedTile ?: firstTile

    // Remembered, because a static local that changes recomposes everything under it — and
    // under it is the whole catalogue.
    val sink = remember { { tile: TvTile -> focusedTile = tile } }

    // Held back a moment, so a remote held down along a row does not set the banner flickering
    // through every title it passes. The first answer is shown at once.
    val shown by produceState(initialValue = wanted, wanted?.key) {
        if (value != null) delay(PREVIEW_FOLLOW_MILLIS)
        value = wanted
    }

    // Keyed on the title, so moving on cancels the lookup — including the network half, which
    // waits for the remote to rest before it asks anything. The banner changes title only once
    // the first, local answer is in, so a new title arrives with its year rather than a moment
    // without it and a second crossfade a moment later.
    val lookup by produceState<PreviewLookup?>(initialValue = null, shown?.key) {
        val tile = shown
        val channel = tile?.channel
        if (channel == null) {
            value = tile?.let { PreviewLookup(it, null) }
            return@produceState
        }
        preview(channel).collect { value = PreviewLookup(tile, it) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bannerHeight = (maxHeight * BANNER_SHARE).coerceIn(BANNER_MIN_HEIGHT, BANNER_MAX_HEIGHT)
        val belowBanner = maxHeight - bannerHeight
        Column(modifier = Modifier.fillMaxSize()) {
            val current = lookup
            TvTitlePreview(
                tile = current?.tile,
                metadata = current?.metadata,
                rating = current?.let { ratings[it.tile.key] ?: it.metadata?.rating },
                fallbackArtworkUrl = current?.let { posters[it.tile.key] },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight),
            )
            CompositionLocalProvider(LocalFocusedTileSink provides sink) {
                content(belowBanner)
            }
        }
    }
}

/** A title and what is known about it so far. Null metadata is "nothing to add", not "loading". */
private data class PreviewLookup(val tile: TvTile, val metadata: TitleMetadata?)

/** Everything the banner draws for one title, so a change of title crossfades as one picture. */
private data class PreviewFace(
    val title: String,
    val artworkUrl: String?,
    val year: Int?,
    val ageRating: String?,
    val rating: Double?,
    val kind: MediaKind,
    val overview: String?,
)

/**
 * The banner itself. Takes up exactly the height it is given whatever the title has, so a film
 * with no plot does not pull the rows below it up and a film with one push them down.
 */
@Composable
private fun TvTitlePreview(
    tile: TvTile?,
    metadata: TitleMetadata?,
    rating: Double?,
    fallbackArtworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val face = tile?.let {
        PreviewFace(
            title = it.name.cleanedForDisplay(),
            // The wide picture when there is one: a poster cropped to a letterbox is a forehead.
            artworkUrl = metadata?.backdropUrl?.takeIf(String::isNotBlank)
                ?: it.artworkUrl?.takeIf(String::isNotBlank)
                ?: fallbackArtworkUrl?.takeIf(String::isNotBlank)
                ?: metadata?.posterUrl?.takeIf(String::isNotBlank),
            year = metadata?.releaseYear,
            ageRating = metadata?.ageRating?.takeIf(String::isNotBlank),
            rating = rating,
            kind = it.kind,
            overview = metadata?.overview?.takeIf(String::isNotBlank),
        )
    }

    Box(modifier = modifier.padding(bottom = BANNER_BOTTOM_GAP)) {
        Crossfade(
            targetState = face,
            animationSpec = tween(PREVIEW_CROSSFADE_MILLIS),
            label = "titlePreview",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            if (current != null) PreviewContent(current)
        }
    }
}

@Composable
private fun PreviewContent(face: PreviewFace) {
    Box(modifier = Modifier.fillMaxSize()) {
        face.artworkUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .bleedingPastTheBanner()
                    .fadedLikeACloud(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(TEXT_SHARE)
                .padding(start = TEXT_START_PADDING),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = face.title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PreviewFacts(face)
            face.overview?.let { overview ->
                Text(
                    text = overview,
                    color = Color.White.copy(alpha = OVERVIEW_ALPHA),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = OVERVIEW_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** Year, certificate, score, kind — whichever of them are known, on one line. */
@Composable
private fun PreviewFacts(face: PreviewFace) {
    val kindLabel = when (face.kind) {
        MediaKind.VOD -> stringResource(R.string.tv_kind_movie)
        MediaKind.SERIES -> stringResource(R.string.tv_kind_series)
        MediaKind.LIVE -> null
    }
    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        face.year?.let { FactText(it.toString()) }
        // Boxed, the way a certificate is printed on a sleeve, so it reads as a rating and not
        // as another word in the line.
        face.ageRating?.let { certificate ->
            Text(
                text = certificate,
                color = Color.White.copy(alpha = FACT_ALPHA),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = CERTIFICATE_BORDER_ALPHA), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        face.rating?.let { FactText("★ %.1f".format(it)) }
        kindLabel?.let { FactText(it) }
    }
}

@Composable
private fun FactText(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = FACT_ALPHA),
        fontSize = 14.sp,
        maxLines = 1,
    )
}

/**
 * Lays the picture out larger than the banner: up into the gap under the tab bar, out to the
 * right-hand edge of the screen, and a little down towards the rows.
 *
 * A picture the exact size of its box has edges, however softly they are faded — the eye finds
 * the rectangle. Letting it run past the banner on three sides, with every one of those sides
 * faded to nothing, is what makes it read as light in the room rather than an image on a card.
 * It takes the banner's size in layout, so nothing around it moves; it only *draws* further.
 */
private fun Modifier.bleedingPastTheBanner(): Modifier = layout { measurable, constraints ->
    val top = ARTWORK_BLEED_TOP.roundToPx()
    val end = ARTWORK_BLEED_END.roundToPx()
    val bottom = ARTWORK_BLEED_BOTTOM.roundToPx()
    val width = (constraints.maxWidth * ARTWORK_SHARE).roundToInt() + end
    val height = constraints.maxHeight + top + bottom
    val picture = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        picture.place(x = constraints.maxWidth - width + end, y = -top)
    }
}

/**
 * Fades the picture out on every side, so no edge of it can be seen: a cloud with the cover in
 * it, not a rectangle.
 *
 * Strongest on the left, where the title and the plot are written over it — the picture is
 * barely there until well past the middle of the banner. The other three sides are short fades,
 * just long enough that the picture dissolves into the screen rather than stopping at it.
 *
 * A mask rather than a dark gradient laid over it: the screen behind is the ambient light, not
 * black, and a black wash would draw a dark rectangle on it. Each gradient eases in over several
 * stops rather than one straight ramp, which on a television shows as a visible band.
 */
private fun Modifier.fadedLikeACloud(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = CLOUD_ACROSS,
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = CLOUD_DOWN,
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * The cloud's opacity across the picture, left to right: a long, eased fade from the left where
 * the text is, solid through the right of the middle, and a short fade into the screen's edge.
 *
 * Built once: a gradient with no start or end set stretches to whatever it is drawn into.
 */
private val CLOUD_ACROSS = Brush.horizontalGradient(
    0f to Color.Transparent,
    0.18f to Color.Black.copy(alpha = 0.06f),
    0.34f to Color.Black.copy(alpha = 0.3f),
    0.48f to Color.Black.copy(alpha = 0.72f),
    0.6f to Color.Black,
    0.88f to Color.Black,
    1f to Color.Transparent,
)

/** And top to bottom: short eased fades at both ends, into the tab bar's gap and into the rows. */
private val CLOUD_DOWN = Brush.verticalGradient(
    0f to Color.Transparent,
    0.12f to Color.Black.copy(alpha = 0.35f),
    0.3f to Color.Black,
    0.62f to Color.Black,
    0.84f to Color.Black.copy(alpha = 0.35f),
    1f to Color.Transparent,
)

/** A third of the catalogue's height, within limits that keep a row of posters readable below. */
private const val BANNER_SHARE = 0.36f
private val BANNER_MIN_HEIGHT = 140.dp
private val BANNER_MAX_HEIGHT = 220.dp
private val BANNER_BOTTOM_GAP = 8.dp

/** How much of the banner's width the picture spans, before it bleeds past the right edge. */
private const val ARTWORK_SHARE = 0.74f

/**
 * Up into the gap under the tab bar. Less than the bar's own 32dp bottom padding, so the fade
 * never reaches the tab labels.
 */
private val ARTWORK_BLEED_TOP = 28.dp

/** Out to the screen's edge: the shell's side padding, which the catalogue sits inside. */
private val ARTWORK_BLEED_END = 48.dp

/** A little down behind the first row's heading, where it is fading out anyway. */
private val ARTWORK_BLEED_BOTTOM = 20.dp

private const val TEXT_SHARE = 0.48f

/** In line with the artwork of the posters below, which sit inside their own focus growth. */
private val TEXT_START_PADDING = 10.dp

private const val OVERVIEW_MAX_LINES = 3
private const val OVERVIEW_ALPHA = 0.8f
private const val FACT_ALPHA = 0.75f
private const val CERTIFICATE_BORDER_ALPHA = 0.6f

private const val PREVIEW_FOLLOW_MILLIS = 150L
private const val PREVIEW_CROSSFADE_MILLIS = 250
