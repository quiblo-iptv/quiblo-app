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

package dev.vibrato.player.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vibrato.player.R
import dev.vibrato.player.navigation.PlayerRoute
import dev.vibrato.player.navigation.SettingsRoute
import dev.vibrato.player.navigation.TopLevelDestination
import dev.vibrato.player.navigation.VibratoNavHost
import kotlin.reflect.KClass

/**
 * The app shell: top bar, bottom navigation and the navigation host.
 *
 * Playback is chrome-free — when the player is on screen the bars are hidden, which is
 * the behaviour AC-PLAY-10 will build on in M2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibratoApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isPlayer = currentDestination.matches(PlayerRoute::class)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!isPlayer) {
                TopAppBar(
                    title = { Text(text = stringResource(currentDestination.titleRes())) },
                    actions = {
                        IconButton(onClick = { navController.navigateSingleTop(SettingsRoute) }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.destination_settings),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!isPlayer) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination.matches(destination.route::class)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateSingleTop(destination.route) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(text = stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        VibratoNavHost(
            navController = navController,
            // Hiding the bars is not enough to reach the screen edges. Scaffold still
            // reports the window insets in its content padding, so the player was inset by
            // the status bar height and drew a strip of surface colour above the video.
            // Full screen has to mean ignoring that padding, not just removing the bars.
            modifier = if (isPlayer) Modifier else Modifier.padding(innerPadding),
        )
    }
}

/**
 * True when [this] destination, or any parent of it, is [route].
 *
 * Uses `hasRoute` rather than comparing `destination.route` to a qualified name by hand.
 * The generated route string carries its arguments — `…/PlayerRoute/{channelId}?title=…` —
 * so string surgery has to guess where the name ends, and it guessed wrong for the one
 * route that takes arguments. That left the player unable to recognise itself and the app
 * chrome drawn over full-screen playback.
 */
private fun NavDestination?.matches(route: KClass<*>): Boolean {
    if (this == null) return false
    return hierarchy.any { it.hasRoute(route) }
}

/** The top-app-bar title for the destination currently on screen. */
private fun NavDestination?.titleRes(): Int {
    TopLevelDestination.entries.forEach { destination ->
        if (matches(destination.route::class)) return destination.labelRes
    }
    return if (matches(SettingsRoute::class)) {
        R.string.destination_settings
    } else {
        R.string.app_name
    }
}

/**
 * Navigates to a top-level destination without stacking duplicates, preserving each
 * destination's own back stack — the standard Material bottom-bar behaviour.
 */
private fun androidx.navigation.NavHostController.navigateSingleTop(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
