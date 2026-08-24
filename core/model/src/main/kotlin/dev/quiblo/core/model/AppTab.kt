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
 * The four destinations a viewer is allowed to switch off (`029` #5).
 *
 * **Four, not every tab, and the omissions are the design.** An account with no live channels
 * shows a Live tab that opens on nothing, and a household that never watches series walks past
 * Series to reach Films every evening — those are the ones worth hiding. Search, Sources and the
 * television's Home are not: two of them are the only way to reach something that is not on a
 * shelf, and hiding the third leaves a shell that opens on a tab it has been told not to draw.
 *
 * **A type of its own rather than either app's tab enum**, because the two shells do not agree on
 * what a tab is — the phone has Sources in the bar and the television has Home and a search
 * button — and the setting is stored once for both. Each app maps its own bar onto this; a tab
 * with no entry here cannot be hidden, which is how Search stays reachable without a special case
 * in the store.
 *
 * The stored value is [name], so a tab this app later renames on screen keeps whatever was chosen
 * for it, and a stored name that matches nothing is ignored rather than hiding something else.
 */
enum class AppTab {
    LIVE,
    MOVIES,
    SERIES,
    FAVOURITES,
    ;

    companion object {
        /** The stored names that still mean something, as tabs. Anything else is dropped. */
        fun decode(stored: Set<String>): Set<AppTab> =
            stored.mapNotNullTo(mutableSetOf()) { name -> entries.firstOrNull { it.name == name } }
    }
}
