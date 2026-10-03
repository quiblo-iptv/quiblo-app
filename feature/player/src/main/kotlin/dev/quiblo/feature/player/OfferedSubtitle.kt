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

import dev.quiblo.core.model.SubtitleFile

/**
 * A subtitle the panel lists for this film, not yet fetched (`BUG-045`).
 *
 * The engine is never handed a panel's subtitle URL: a sidecar that fails to load fails the whole
 * item, and panels list dead links often enough that "the film stops when I turn subtitles on" was
 * a common way for a film to fail. So these sit in the menu as offers. Choosing one fetches it, and
 * only a copy that arrived and turned out to be subtitles is played — the film restarting at the
 * same moment with it showing. One that does not arrive stays in the menu, marked, and can be
 * chosen again.
 *
 * @property id the menu's id for it, which never collides with an engine track's.
 * @property subtitle as the panel lists it, remote URL and all.
 */
data class OfferedSubtitle(
    val id: String,
    val subtitle: SubtitleFile,
    val status: OfferedSubtitleStatus = OfferedSubtitleStatus.OFFERED,
)

enum class OfferedSubtitleStatus {
    OFFERED,
    FETCHING,

    /** The last attempt failed. Still offered: a server that was slow a minute ago may not be now. */
    UNAVAILABLE,
}

/** Prefixed so a menu id is never mistaken for one of the engine's (`type:group:language`). */
internal const val OFFERED_SUBTITLE_PREFIX = "offered:"

internal fun isOfferedSubtitleId(trackId: String): Boolean = trackId.startsWith(OFFERED_SUBTITLE_PREFIX)
