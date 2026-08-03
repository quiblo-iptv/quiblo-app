/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.tv.ui

import androidx.annotation.StringRes
import dev.vibrato.tv.R

/** The top-level destinations, in the order they sit in the bar. */
internal enum class TvTab(@param:StringRes val labelRes: Int) {
    LIVE(R.string.tv_tab_live),
    MOVIES(R.string.tv_tab_movies),
    SERIES(R.string.tv_tab_series),
    FAVOURITES(R.string.tv_tab_favourites),
}
