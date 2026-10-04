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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.feature.settings.LaunchUpdateViewModel
import dev.quiblo.feature.settings.UpdateAvailableDialog
import org.koin.androidx.compose.koinViewModel

/**
 * "New version available", once per launch (`029` #7).
 *
 * Asked once there is something on screen rather than from `Application.onCreate` — a dialog over
 * a blank window is a dialog a viewer meets before the app. The ViewModel itself decides whether to
 * ask at all; this only says when.
 *
 * **Drawn by [ProfileGate], in front of the chooser as well as the shell** (`BUG-061`). It used to
 * live inside the shell, so an app that could not get past "who is watching" could not offer the
 * release that fixed it: 0.27.0 held its database for as long as a large catalogue took to
 * upgrade, the chooser sat empty, and the way out was never shown.
 */
@Composable
internal fun LaunchUpdatePrompt() {
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
}

/**
 * Where *Update now* sends a handset.
 *
 * The releases page rather than the APK's own URL: the viewer arrives at a page that says what
 * changed and offers both builds by name, which is a better place to be handed an installer than a
 * download that has already started.
 */
private const val RELEASES_PAGE = "https://github.com/quiblo-iptv/quiblo-app/releases/latest"
