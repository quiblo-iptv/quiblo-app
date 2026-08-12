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

package dev.quiblo.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.quiblo.core.model.SubtitleColor
import dev.quiblo.core.model.SubtitleOpacity
import dev.quiblo.core.model.SubtitleStyle
import dev.quiblo.core.model.SubtitleTextSize

/**
 * The appearance rows in the player's subtitle menu (INC-F11).
 *
 * Three short lists rather than a settings screen: size, the colour of the words, and the box
 * behind them. Everything about the shape of this is downstream of where it lives — inside the
 * player, over the film, driven by a remote as often as by a finger. There is no slider and no
 * colour wheel, because neither is usable from a sofa, and a fourth question nobody asked would
 * make the panel taller than the video.
 *
 * **"Match system" is the first row and the default.** Android carries a caption style that a
 * person may already have set once for every app, and someone who enlarged captions there has
 * answered this question. Picking anything explicit here turns matching off; picking Match system
 * throws the explicit choices away rather than remembering them, so the row means exactly what it
 * says.
 */
@Composable
fun rememberSubtitleAppearance(style: SubtitleStyle): List<TrackMenuSection> = listOf(
    TrackMenuSection(
        kind = TrackMenuKind.SUBTITLE_SIZE,
        entries = buildList {
            add(
                TrackMenuEntry(
                    trackId = null,
                    label = stringResource(R.string.player_subtitles_match_system),
                    isSelected = style.matchSystem,
                ),
            )
            SubtitleTextSize.entries.forEach { size ->
                add(
                    TrackMenuEntry(
                        trackId = size.name,
                        label = stringResource(size.labelRes()),
                        isSelected = !style.matchSystem && style.textSize == size,
                    ),
                )
            }
        },
    ),
    TrackMenuSection(
        kind = TrackMenuKind.SUBTITLE_TEXT_COLOUR,
        // Black text is not offered. It is unreadable against most of what a film looks like,
        // and the outline that would rescue it is the same black.
        entries = listOf(SubtitleColor.WHITE, SubtitleColor.YELLOW).map { colour ->
            TrackMenuEntry(
                trackId = colour.name,
                label = stringResource(colour.labelRes()),
                isSelected = !style.matchSystem && style.textColor == colour,
            )
        },
    ),
    TrackMenuSection(
        kind = TrackMenuKind.SUBTITLE_BACKGROUND,
        entries = listOf(
            TrackMenuEntry(
                trackId = BACKGROUND_NONE,
                label = stringResource(R.string.player_subtitles_background_none),
                isSelected = !style.matchSystem && style.backgroundOpacity == SubtitleOpacity.NONE,
            ),
            TrackMenuEntry(
                trackId = BACKGROUND_LIGHT,
                label = stringResource(R.string.player_subtitles_background_light),
                isSelected = !style.matchSystem && style.backgroundOpacity == SubtitleOpacity.LIGHT,
            ),
            TrackMenuEntry(
                trackId = BACKGROUND_SOLID,
                label = stringResource(R.string.player_subtitles_background_solid),
                isSelected = !style.matchSystem && style.backgroundOpacity == SubtitleOpacity.SOLID,
            ),
        ),
    ),
)

/**
 * This style with one appearance choice applied, or null when [id] means nothing here.
 *
 * A plain function rather than a branch inside the view model, because this is the whole of
 * INC-F11's behaviour — which row changes what, and what "match system" throws away — and none of
 * it needs a player, a coroutine or a device to check.
 *
 * A null [id] is the Match system row. It returns the defaults rather than keeping the explicit
 * choices behind a flag: the row says the system decides, and a style that quietly remembers a
 * size it is not using is one that surprises whoever turns matching off again.
 */
fun SubtitleStyle.withChoice(kind: TrackMenuKind, id: String?): SubtitleStyle? = when {
    id == null -> SubtitleStyle(matchSystem = true)

    kind == TrackMenuKind.SUBTITLE_SIZE ->
        SubtitleTextSize.entries.firstOrNull { it.name == id }
            ?.let { copy(matchSystem = false, textSize = it) }

    kind == TrackMenuKind.SUBTITLE_TEXT_COLOUR ->
        SubtitleColor.entries.firstOrNull { it.name == id }
            ?.let { copy(matchSystem = false, textColor = it) }

    kind == TrackMenuKind.SUBTITLE_BACKGROUND -> backgroundChoice(id)?.let { (colour, opacity) ->
        copy(matchSystem = false, background = colour, backgroundOpacity = opacity)
    }

    // An audio or subtitle row reaching here is a caller mistake, not a style change.
    else -> null
}

/**
 * The background is offered as one question — none, faint or solid — and stored as two.
 *
 * Somebody deciding about the box behind the words is not thinking about a colour and an alpha.
 * The renderer needs both; the menu asks the question people actually have.
 */
private fun backgroundChoice(id: String): Pair<SubtitleColor, SubtitleOpacity>? = when (id) {
    BACKGROUND_NONE -> SubtitleColor.TRANSPARENT to SubtitleOpacity.NONE
    BACKGROUND_LIGHT -> SubtitleColor.BLACK to SubtitleOpacity.LIGHT
    BACKGROUND_SOLID -> SubtitleColor.BLACK to SubtitleOpacity.SOLID
    else -> null
}

const val BACKGROUND_NONE = "background_none"
const val BACKGROUND_LIGHT = "background_light"
const val BACKGROUND_SOLID = "background_solid"

private fun SubtitleTextSize.labelRes() = when (this) {
    SubtitleTextSize.SMALL -> R.string.player_subtitles_size_small
    SubtitleTextSize.MEDIUM -> R.string.player_subtitles_size_medium
    SubtitleTextSize.LARGE -> R.string.player_subtitles_size_large
    SubtitleTextSize.EXTRA_LARGE -> R.string.player_subtitles_size_extra_large
}

private fun SubtitleColor.labelRes() = when (this) {
    SubtitleColor.WHITE -> R.string.player_subtitles_colour_white
    SubtitleColor.YELLOW -> R.string.player_subtitles_colour_yellow
    SubtitleColor.BLACK -> R.string.player_subtitles_colour_black
    SubtitleColor.TRANSPARENT -> R.string.player_subtitles_background_none
}
