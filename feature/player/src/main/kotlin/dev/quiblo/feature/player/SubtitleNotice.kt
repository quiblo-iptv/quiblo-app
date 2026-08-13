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

/**
 * What to tell a viewer after they picked a subtitle file (INC-F10).
 *
 * A type rather than a message, for the reason `PlaybackError` is one: the wording belongs to the
 * screen so that it stays translatable, and both apps say the same thing in their own way.
 *
 * [ATTACHED] is on this list on purpose. Attaching restarts the stream, so a viewer sees a second
 * of buffering that they did not ask for and would otherwise read as a fault — saying that the
 * file was loaded is what makes that second explicable.
 */
enum class SubtitleNotice {
    ATTACHED,

    /** Readable, but nothing in it looks like subtitles. Nearly always the wrong file. */
    NOT_SUBTITLES,

    UNREADABLE,

    TOO_LARGE,

    /** No app on this device can pick a file. Common on a television, and not the viewer's fault. */
    NO_PICKER,
}
