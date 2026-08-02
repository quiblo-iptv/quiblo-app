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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vibrato.player.R
import dev.vibrato.player.navigation.PlayerRoute
import dev.vibrato.player.navigation.SettingsRoute
import dev.vibrato.player.navigation.TopLevelDestination
import dev.vibrato.player.navigation.VibratoNavHost

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
    val isPlayer = currentDestination.matches(PlayerRoute::class.qualifiedName)

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
                        val selected = currentDestination.matches(destination.route::class.qualifiedName)
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
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** True when [this] destination, or any parent of it, is the route named [qualifiedName]. */
private fun NavDestination?.matches(qualifiedName: String?): Boolean {
    if (this == null || qualifiedName == null) return false
    return hierarchy.any { it.route?.substringBefore('/') == qualifiedName }
}

/** The top-app-bar title for the destination currently on screen. */
private fun NavDestination?.titleRes(): Int {
    TopLevelDestination.entries.forEach { destination ->
        if (matches(destination.route::class.qualifiedName)) return destination.labelRes
    }
    return if (matches(SettingsRoute::class.qualifiedName)) {
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
