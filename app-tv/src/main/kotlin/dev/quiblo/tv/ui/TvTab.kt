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
import dev.quiblo.core.model.AppTab
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
internal enum class TvTab(
    @param:StringRes val labelRes: Int,
    val isIconOnly: Boolean = false,
    /**
     * Which switchable tab this is, or null for one that cannot be switched off (`029` #5).
     *
     * Search and Home are the nulls, and `AppTab` has no entry for either — which is what makes
     * hiding them unrepresentable rather than merely avoided. Search is the only way to reach a
     * title that is not on a shelf, and Home is where this shell opens: a bar that could lose it
     * would open on a tab it has been told not to draw.
     */
    val tab: AppTab? = null,
) {
    SEARCH(R.string.tv_tab_search, isIconOnly = true),

    /**
     * Three rows about three questions: what the provider added, what the world is watching of
     * the things the provider carries, and what this viewer is likely to want.
     *
     * Ahead of the catalogues rather than after them, because it is the only tab whose contents
     * change between one evening and the next: Movies and Series are the same thousands of titles
     * in the same provider order every time they are opened, and a viewer with nothing particular
     * in mind has no reason to walk past this one to reach them.
     *
     * It was Recently Added, which is now the first of its three rows.
     */
    FOR_YOU(R.string.tv_tab_for_you),
    LIVE(R.string.tv_tab_live, tab = AppTab.LIVE),
    MOVIES(R.string.tv_tab_movies, tab = AppTab.MOVIES),
    SERIES(R.string.tv_tab_series, tab = AppTab.SERIES),
    FAVOURITES(R.string.tv_tab_favourites, tab = AppTab.FAVOURITES),
    ;

    companion object {
        /** The bar as this viewer has arranged it, in the order the enum declares. */
        fun visible(hidden: Set<AppTab>): List<TvTab> = entries.filter { it.tab == null || it.tab !in hidden }
    }
}
