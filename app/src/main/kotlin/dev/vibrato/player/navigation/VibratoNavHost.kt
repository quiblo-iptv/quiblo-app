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

package dev.vibrato.player.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.vibrato.feature.favorites.FavoritesScreen
import dev.vibrato.feature.live.LiveScreen
import dev.vibrato.feature.player.PlayerScreen
import dev.vibrato.feature.series.SeriesDetailScreen
import dev.vibrato.feature.series.SeriesScreen
import dev.vibrato.feature.settings.SettingsScreen
import dev.vibrato.feature.sources.SourcesScreen
import dev.vibrato.feature.vod.MovieDetailScreen
import dev.vibrato.feature.vod.VodScreen

/**
 * The single navigation graph for the app.
 *
 * Every destination is an empty screen at M0. The graph exists now so that adding real
 * content in later milestones is a change to one feature module, not to navigation.
 */
@Composable
fun VibratoNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = LiveRoute,
        modifier = modifier,
    ) {
        val play: (dev.vibrato.core.model.Channel) -> Unit = { channel ->
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
                onEpisodeClick = { episode, seriesChannel ->
                    navController.navigate(
                        PlayerRoute(
                            channelId = seriesChannel.id,
                            streamUrl = episode.streamUrl,
                            title = "${seriesChannel.name} - ${episode.title}",
                        ),
                    )
                },
            )
        }
        composable<FavoritesRoute> { FavoritesScreen(onItemClick = play) }
        composable<SourcesRoute> { SourcesScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<PlayerRoute> { entry ->
            val route = entry.toRoute<PlayerRoute>()
            PlayerScreen(
                channelId = route.channelId,
                streamUrl = route.streamUrl,
                title = route.title,
                startPositionMillis = route.startPositionMillis,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
