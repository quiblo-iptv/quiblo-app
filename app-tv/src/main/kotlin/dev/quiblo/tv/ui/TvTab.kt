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

package dev.quiblo.tv.ui

import androidx.annotation.StringRes
import dev.quiblo.tv.R

/**
 * The top-level destinations, in the order they sit in the bar.
 *
 * Search is first, and it is the only one drawn as an icon: a magnifier says "search" in
 * every language and needs no word beside it, and the leftmost position is where a remote
 * already is when the app opens. It is also where Back comes to rest — pressing Back on any
 * catalogue lands here, and pressing it again leaves the app.
 *
 * Sources is deliberately *not* here any more. It was a top-level destination for the whole
 * life of the television app, which put "add a playlist" — something a viewer does once —
 * one press away from what they do every day, and it lives in Settings now.
 */
internal enum class TvTab(@param:StringRes val labelRes: Int, val isIconOnly: Boolean = false) {
    SEARCH(R.string.tv_tab_search, isIconOnly = true),
    LIVE(R.string.tv_tab_live),
    MOVIES(R.string.tv_tab_movies),
    SERIES(R.string.tv_tab_series),
    FAVOURITES(R.string.tv_tab_favourites),
}
