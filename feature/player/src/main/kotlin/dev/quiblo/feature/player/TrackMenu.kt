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

import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.TrackOption

/**
 * What a viewer can choose between while something is playing.
 *
 * **This is the model only; each app draws it.** The phone and the television agree about what
 * the choices *are* and disagree about everything else — a sheet against a ten-foot list, a
 * finger against a D-pad — and the lesson `PLAN-TV.md` §2 records is that sharing the decisions
 * and splitting the drawing is what stops the two frontends diverging in substance.
 *
 * It exists because of `agile/012` **#023**, which was filed as a feature and is not one.
 * `Media3PlayerController` has exposed `audioTracks`, `textTracks`, `selectAudioTrack` and
 * `selectTextTrack` since the player was built. **AC-PLAY-04 requires a viewer to be able to
 * switch between them and there has never been a way in** — the phone cycled subtitles from a
 * single button and offered nothing for audio, and the television offered neither. Working code
 * with no path to it is the hollow-feature shape inverted, and it is a defect rather than
 * scope.
 *
 * `013` INC-F11 puts subtitle styling in this menu and `014` puts a version switcher in it, so
 * it is built as a list of sections rather than as two fixed lists.
 */
data class TrackMenu(val sections: List<TrackMenuSection>) {

    /** Nothing to choose between. The control that opens this should not be shown at all. */
    val isEmpty: Boolean get() = sections.isEmpty()
}

/** One heading and the choices under it. */
data class TrackMenuSection(
    val kind: TrackMenuKind,
    val entries: List<TrackMenuEntry>,
)

enum class TrackMenuKind {
    AUDIO,
    SUBTITLES,
}

/**
 * One choice.
 *
 * [trackId] is null for "off", which only subtitles have: a stream with no audio selected is
 * not a thing a viewer ever wants, and offering it would be a way to break playback from a menu.
 */
data class TrackMenuEntry(
    val trackId: String?,
    val label: String,
    val isSelected: Boolean,
)

/**
 * Builds the menu for the current playback state.
 *
 * [offLabel] is passed in rather than resolved here because this module's model layer has no
 * composition to read a string resource from, and the alternative — an untranslatable literal —
 * is the exact fault #017 was.
 *
 * **Audio appears only when there is a genuine choice.** One audio track is not a decision, and
 * a menu listing a single option a viewer cannot change is furniture. Subtitles appear whenever
 * the container declares any, because "off" is always a real choice there.
 */
fun trackMenu(state: PlaybackState, offLabel: String): TrackMenu {
    val sections = buildList {
        if (state.audioTracks.size > 1) {
            add(TrackMenuSection(TrackMenuKind.AUDIO, state.audioTracks.map { it.toEntry() }))
        }

        if (state.textTracks.isNotEmpty()) {
            val entries = buildList {
                add(
                    TrackMenuEntry(
                        trackId = null,
                        label = offLabel,
                        // Selected when nothing else is: a container can declare subtitles and
                        // start with none showing, which is the usual case.
                        isSelected = state.textTracks.none { it.isSelected },
                    ),
                )
                addAll(state.textTracks.map { it.toEntry() })
            }
            add(TrackMenuSection(TrackMenuKind.SUBTITLES, entries))
        }
    }

    return TrackMenu(sections)
}

private fun TrackOption.toEntry() = TrackMenuEntry(
    trackId = id,
    label = label,
    isSelected = isSelected,
)
