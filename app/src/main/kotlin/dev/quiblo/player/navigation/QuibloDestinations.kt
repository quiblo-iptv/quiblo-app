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
) {
    LIVE(LiveRoute, R.string.destination_live, Icons.Filled.LiveTv),
    VOD(VodRoute, R.string.destination_vod, Icons.Filled.Movie),
    SERIES(SeriesRoute, R.string.destination_series, Icons.Filled.Tv),
    FAVORITES(FavoritesRoute, R.string.destination_favorites, Icons.Filled.Favorite),
    SOURCES(SourcesRoute, R.string.destination_sources, Icons.AutoMirrored.Filled.List),
}
