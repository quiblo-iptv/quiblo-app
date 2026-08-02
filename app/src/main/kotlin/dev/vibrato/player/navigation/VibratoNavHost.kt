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
import dev.vibrato.feature.favorites.FavoritesScreen
import dev.vibrato.feature.live.LiveScreen
import dev.vibrato.feature.player.PlayerScreen
import dev.vibrato.feature.series.SeriesScreen
import dev.vibrato.feature.settings.SettingsScreen
import dev.vibrato.feature.sources.SourcesScreen
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
        composable<LiveRoute> { LiveScreen() }
        composable<VodRoute> { VodScreen() }
        composable<SeriesRoute> { SeriesScreen() }
        composable<FavoritesRoute> { FavoritesScreen() }
        composable<SourcesRoute> { SourcesScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<PlayerRoute> { PlayerScreen() }
    }
}
