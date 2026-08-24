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

package dev.quiblo.player.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.designsystem.LocalAmbientArtwork
import dev.quiblo.designsystem.ambientBackdrop
import dev.quiblo.designsystem.rememberAmbient
import dev.quiblo.feature.settings.LaunchUpdateViewModel
import dev.quiblo.feature.settings.UpdateAvailableDialog
import dev.quiblo.player.R
import dev.quiblo.player.navigation.MovieDetailRoute
import dev.quiblo.player.navigation.PlayerRoute
import dev.quiblo.player.navigation.QuibloNavHost
import dev.quiblo.player.navigation.SeriesDetailRoute
import dev.quiblo.player.navigation.SettingsRoute
import dev.quiblo.player.navigation.TopLevelDestination
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.reflect.KClass

/**
 * The app shell: top bar, bottom navigation and the navigation host.
 *
 * Playback is chrome-free — when the player is on screen the bars are hidden, which is
 * the behaviour AC-PLAY-10 will build on in M2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuibloApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    /*
     * The bar as this viewer has arranged it (`029` #5).
     *
     * Read from the repository rather than through a ViewModel of its own: the shell has no state
     * of its own to hold and a ViewModel wrapping one flow would be a class that only forwards.
     * It follows the active profile, so switching person redraws the bar.
     */
    val settings: PlayerSettingsRepository = koinInject()
    val hiddenTabs by settings.hiddenTabs.collectAsStateWithLifecycle(emptySet())
    val destinations = remember(hiddenTabs) { TopLevelDestination.visible(hiddenTabs) }
    val currentDestination = backStackEntry?.destination
    val isPlayer = currentDestination.matches(PlayerRoute::class)

    // Detail screens carry their own app bar, with the item's title and a back arrow. The
    // shell drawing a second one above them stacked two bars and showed "Quiblo" over a
    // screen that already said what it was — a whole row of a tablet's height spent
    // repeating the app's own name.
    val ownsItsAppBar = isPlayer ||
        currentDestination.matches(MovieDetailRoute::class) ||
        currentDestination.matches(SeriesDetailRoute::class)

    /*
     * Off a tab that has just been switched off, and off the one the graph opens on (`029` #5).
     *
     * The graph's start destination is Live, and a viewer who hides Live would otherwise open the
     * app onto a screen with no way back to it in the bar. Hiding the tab you are standing on has
     * the same shape, one press later.
     *
     * Only the top-level tabs are checked. A film opened from a hidden shelf is not a tab, and a
     * viewer reading one should not be thrown out of it because a switch changed underneath.
     */
    val strandedOn = destinations.takeIf { it.isNotEmpty() }?.let { visible ->
        TopLevelDestination.entries
            .firstOrNull { it !in visible && currentDestination.matches(it.route::class) }
            ?.let { visible.first() }
    }
    LaunchedEffect(strandedOn) {
        strandedOn?.let { navController.navigateSingleTop(it.route) }
    }

    /*
     * The room the catalogue sits in, and it is the shell's to paint.
     *
     * A detail screen knows which poster is on it; only this knows how big the window is.
     * `drawBehind` clips to the node it is on and the pools are sized as fractions of it, so
     * when the film screen painted its own backdrop it painted it inside the Scaffold's content
     * padding — light that stopped in a hard edge under the status bar and above the navigation
     * bar, with plain surface colour either side. Same seam the television had, same answer:
     * one full-bleed layer at the root, fed from wherever. See [LocalAmbientArtwork].
     */
    /*
     * "New version available", once per launch (`029` #7).
     *
     * Asked from the shell rather than from `Application.onCreate` so that it happens after there
     * is something on screen — a dialog over a blank window is a dialog a viewer meets before the
     * app. The ViewModel itself decides whether to ask at all; this only says when.
     */
    val context = LocalContext.current
    val updates: LaunchUpdateViewModel = koinViewModel()
    val newRelease by updates.available.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updates.check() }

    newRelease?.let { release ->
        UpdateAvailableDialog(
            availableVersion = release.version,
            installedVersion = updates.installedVersion,
            // The releases page rather than a download, and the difference is deliberate. This app
            // holds no `REQUEST_INSTALL_PACKAGES` and should not: a handset has a browser, a
            // downloads folder and a package installer the viewer already knows, and asking for
            // the permission to reimplement all three would be asking for the one permission
            // AC-NFR-04 exists to keep this app free of. The television, which has none of those,
            // is the reason that code exists there and not here.
            onUpdate = {
                updates.dismiss()
                context.startActivity(Intent(Intent.ACTION_VIEW, RELEASES_PAGE.toUri()))
            },
            onDismiss = updates::dismiss,
        )
    }

    var ambientArtwork: String? by remember { mutableStateOf(null) }
    val ambient = rememberAmbient(ambientArtwork)

    // Cleared on the way out of any screen that lit it, so backing out of a film does not leave
    // its colours behind an unrelated list.
    DisposableEffect(currentDestination) { onDispose { ambientArtwork = null } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .ambientBackdrop(ambient),
    ) {
        CompositionLocalProvider(LocalAmbientArtwork provides { ambientArtwork = it }) {
            Scaffold(
                // Transparent, so the light painted behind this reaches the screen rather than stopping
                // at the Scaffold's own opaque surface.
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (!ownsItsAppBar) {
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
                            destinations.forEach { destination ->
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
                QuibloNavHost(
                    navController = navController,
                    // Hiding the bars is not enough to reach the screen edges. Scaffold still
                    // reports the window insets in its content padding, so the player was inset by
                    // the status bar height and drew a strip of surface colour above the video.
                    // Full screen has to mean ignoring that padding, not just removing the bars.
                    modifier = if (isPlayer) Modifier else Modifier.padding(innerPadding),
                )
            }
        }
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

/**
 * Where *Update now* sends a handset.
 *
 * The releases page rather than the APK's own URL: the viewer arrives at a page that says what
 * changed and offers both builds by name, which is a better place to be handed an installer than a
 * download that has already started.
 */
private const val RELEASES_PAGE = "https://github.com/quiblo-iptv/quiblo-app/releases/latest"
