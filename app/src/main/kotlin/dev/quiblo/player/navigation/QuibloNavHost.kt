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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.quiblo.core.model.WatchOrigin
import dev.quiblo.feature.favorites.FavoritesScreen
import dev.quiblo.feature.live.LiveScreen
import dev.quiblo.feature.player.PlayerScreen
import dev.quiblo.feature.series.SeriesDetailScreen
import dev.quiblo.feature.series.SeriesScreen
import dev.quiblo.feature.settings.SettingsScreen
import dev.quiblo.feature.sources.SourcesScreen
import dev.quiblo.feature.vod.MovieDetailScreen
import dev.quiblo.feature.vod.VodScreen

/**
 * The single navigation graph for the app.
 *
 * Every destination is an empty screen at M0. The graph exists now so that adding real
 * content in later milestones is a change to one feature module, not to navigation.
 */
@Composable
fun QuibloNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = LiveRoute,
        modifier = modifier,
    ) {
        val play: (dev.quiblo.core.model.Channel) -> Unit = { channel ->
            navController.navigate(PlayerRoute(channel.id))
        }

        composable<LiveRoute> { LiveScreen(onItemClick = play) }
        // Movies open a detail screen rather than playing immediately: resuming should be
        // a choice, and there is nowhere else to offer a plot.
        composable<VodRoute> {
            VodScreen(onItemClick = { channel -> navController.navigate(MovieDetailRoute(channel.id)) })
        }
        composable<MovieDetailRoute> { entry ->
            MovieDetailScreen(
                channelId = entry.toRoute<MovieDetailRoute>().channelId,
                onBack = { navController.popBackStack() },
                onPlay = { channel, startMillis ->
                    navController.navigate(
                        PlayerRoute(channelId = channel.id, startPositionMillis = startMillis),
                    )
                },
            )
        }
        composable<SeriesRoute> {
            SeriesScreen(
                onItemClick = { channel ->
                    navController.navigate(SeriesDetailRoute(channel.id))
                },
            )
        }
        composable<SeriesDetailRoute> { entry ->
            val route = entry.toRoute<SeriesDetailRoute>()
            SeriesDetailScreen(
                channelId = route.channelId,
                onBack = { navController.popBackStack() },
                onEpisodeClick = { episode, seriesChannel, startMillis ->
                    navController.navigate(
                        PlayerRoute(
                            channelId = seriesChannel.id,
                            streamUrl = episode.streamUrl,
                            title = "${seriesChannel.name} - ${episode.title}",
                            startPositionMillis = startMillis,
                            seasonNumber = episode.seasonNumber,
                            episodeNumber = episode.episodeNumber,
                        ),
                    )
                },
            )
        }
        composable<FavoritesRoute> {
            // Opening something out of favourites is a choice made twice, and the scorer weighs it
            // that way. Everything else on this screen goes through `play`, which is the row case.
            FavoritesScreen(
                onItemClick = { channel ->
                    navController.navigate(PlayerRoute(channel.id, origin = WatchOrigin.FAVOURITE.name))
                },
            )
        }
        composable<SourcesRoute> { SourcesScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<PlayerRoute> { entry ->
            val route = entry.toRoute<PlayerRoute>()
            PlayerScreen(
                channelId = route.channelId,
                streamUrl = route.streamUrl,
                title = route.title,
                startPositionMillis = route.startPositionMillis,
                seasonNumber = route.seasonNumber,
                episodeNumber = route.episodeNumber,
                // Unknown names fall back to the ordinary case rather than crashing a player: a
                // route is a serialized string, and a signal is not worth a stack trace.
                origin = runCatching { WatchOrigin.valueOf(route.origin) }.getOrDefault(WatchOrigin.ROW),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
