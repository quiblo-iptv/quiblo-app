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

package dev.quiblo.player.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import dev.quiblo.core.model.AppTab
import dev.quiblo.player.R
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes (docs/PLAN.md §1).
 *
 * Routes are declared in `:app` rather than in the feature modules so that features stay
 * independent of one another and of the graph's shape. A feature exposes a composable;
 * `:app` decides where it sits.
 */
@Serializable
data object LiveRoute

@Serializable
data object VodRoute

@Serializable
data object SeriesRoute

@Serializable
data class SeriesDetailRoute(val channelId: Long)

@Serializable
data object FavoritesRoute

@Serializable
data object SourcesRoute

@Serializable
data object SettingsRoute

/** One film: artwork, overview, and how to start it. */
@Serializable
data class MovieDetailRoute(val channelId: Long)

/** Full-screen playback of one item. */
@Serializable
data class PlayerRoute(
    val channelId: Long,
    /**
     * Where the viewer was when they chose this, as a `WatchOrigin` name.
     *
     * A string because a navigation route is serialized, and because the enum lives in a module
     * this one does not otherwise need. Carried rather than derived: by the time the player has a
     * stream URL, a title typed into a search box and the first tile of a row look identical.
     */
    val origin: String = "ROW",
    val streamUrl: String? = null,
    val title: String? = null,
    /**
     * Where to start, overriding the saved resume point.
     *
     * Null means "decide for me", which is what a tap straight from a list wants. The
     * movie screen sets it explicitly so that Start from beginning can override a resume
     * position rather than being silently ignored.
     */
    val startPositionMillis: Long? = null,
    /**
     * Where this episode sits in its series, when it is one.
     *
     * Carried so the history entry can say "S2 E4" without the player having to go back to
     * the provider for an episode list it was never given.
     */
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

/**
 * The destinations reachable from the bottom navigation bar.
 *
 * Settings is deliberately excluded: it is a top-app-bar action, keeping the bar at five
 * items where Material 3 recommends three to five.
 */
enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    /**
     * Which switchable tab this is, or null for one that cannot be switched off (`029` #5).
     *
     * Sources is the null. It is the only way into the screen that adds a playlist, so an app
     * whose bar could lose it is an app somebody can lock themselves out of configuring — and
     * `AppTab` deliberately has no entry for it, which is what makes that unrepresentable rather
     * than merely avoided.
     */
    val tab: AppTab? = null,
) {
    LIVE(LiveRoute, R.string.destination_live, Icons.Filled.LiveTv, AppTab.LIVE),
    VOD(VodRoute, R.string.destination_vod, Icons.Filled.Movie, AppTab.MOVIES),
    SERIES(SeriesRoute, R.string.destination_series, Icons.Filled.Tv, AppTab.SERIES),
    FAVORITES(FavoritesRoute, R.string.destination_favorites, Icons.Filled.Favorite, AppTab.FAVOURITES),
    SOURCES(SourcesRoute, R.string.destination_sources, Icons.AutoMirrored.Filled.List),
    ;

    companion object {
        /** The bar as this viewer has arranged it, with Sources always at the end of it. */
        fun visible(hidden: Set<AppTab>): List<TopLevelDestination> =
            entries.filter { it.tab == null || it.tab !in hidden }
    }
}
