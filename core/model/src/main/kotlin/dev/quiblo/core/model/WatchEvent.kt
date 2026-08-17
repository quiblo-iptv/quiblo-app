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

package dev.quiblo.core.model

/**
 * Where a viewer was when they decided to watch something.
 *
 * **A choice costs different amounts to make, and that is what this records.** Typing a title into
 * a search box is somebody who arrived wanting that thing; pressing the first tile of a row is
 * somebody taking what was offered. Both end in the same playback and the resume table cannot tell
 * them apart, so the difference has to be recorded when it happens or not at all.
 */
enum class WatchOrigin {
    /** Typed for. The strongest statement of intent this app can observe. */
    SEARCH,

    /** Chosen off a shelf — a category row, a grid, a suggestion. The ordinary case. */
    ROW,

    /** Opened from favourites, which is a choice made twice. */
    FAVOURITE,

    /** Picked up where it was left. Says something about the title and little about the choice. */
    CONTINUE,
}

/**
 * What the viewer said about something after watching it.
 *
 * Two buttons rather than five stars. A star rating asks somebody to be precise about a feeling
 * they are not precise about, and the precision is then treated as data; up and down is the
 * question people actually answer. [NONE] is the overwhelming majority and is not a middle value —
 * it is the absence of one, and the scorer treats it as such rather than as three out of five.
 */
enum class Opinion {
    UP,
    DOWN,
    NONE,
}

/**
 * One viewing, kept as an event rather than folded into a position.
 *
 * `resume_positions` holds one row per title, which is the right shape for "where was I" and
 * cannot answer three of the questions a suggestion wants: how *often* something was watched — a
 * film seen five times is a comfort film and the strongest signal in the house — at what hour, and
 * from where. All three are facts about an occasion, and an occasion is what this is.
 */
data class WatchEvent(
    val stableKey: String,
    val kind: MediaKind,
    /** The title as the catalogue had it, so a scorer can join it to the metadata cache. */
    val title: String,
    val startedAtEpochMillis: Long,
    /** How much was watched, 0 to 1. */
    val fraction: Double,
    val origin: WatchOrigin,
)
