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

package dev.quiblo.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.ui.common.insistOnFocus
import dev.quiblo.tv.update.TvApkInstaller
import dev.quiblo.tv.update.TvUpdateState
import dev.quiblo.tv.update.TvUpdateViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * "New version available", over whatever the app opened on (`029` #7).
 *
 * **Drawn rather than dialogued.** This app has no `AlertDialog` anywhere and this is not the place
 * to grow the first one: a Material dialog on a television is sized for a hand, takes focus by
 * rules written for a touchscreen, and puts its buttons where a remote does not expect them. A
 * panel drawn in the middle of the screen with two chips on it is the shape every other choice in
 * this app already has.
 *
 * **It is the same state machine the settings row uses**, so *Update now* downloads by exactly the
 * path the button there does — and a viewer who opens Settings mid-download finds the row already
 * saying so, rather than a second copy of the same work.
 *
 * Back means *Later*, which is what Back over a panel has always meant. It is consumed here so the
 * press cannot also arm the exit underneath.
 */
@Composable
internal fun TvUpdatePrompt(viewModel: TvUpdateViewModel = koinViewModel()) {
    val showing by viewModel.launchPrompt.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Asked once for the life of the ViewModel; the guard is inside, so a recomposition here is
    // free. From the shell rather than from `Application.onCreate` so it happens after there is
    // something on screen — a panel over a blank window is a panel a viewer meets before the app.
    LaunchedEffect(Unit) { viewModel.checkOnLaunch() }

    if (!showing) return

    /*
     * The installer, the moment the file is verified.
     *
     * Not another press. A viewer who chose *Update now* has already said what they want, and the
     * system's own installer asks them again anyway — a second chip between the two would be this
     * app asking a question the platform is about to ask better.
     */
    LaunchedEffect(state) {
        val ready = state as? TvUpdateState.Ready ?: return@LaunchedEffect
        if (!TvApkInstaller.install(context, ready.file)) viewModel.installerRefused()
    }

    BackHandler { viewModel.dismissLaunchPrompt() }

    val firstAction = remember { FocusRequester() }
    LaunchedEffect(state) {
        if (state is TvUpdateState.Available) firstAction.insistOnFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(PANEL_WIDTH)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = PANEL_ALPHA))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text(
                text = stringResource(R.string.tv_update_available_title),
                color = Color.White,
                fontSize = 24.sp,
            )

            Text(
                text = promptBody(state, viewModel.installedVersion),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
            )

            // While the file is coming down there is nothing to press: a second command mid
            // download is the one input this panel has nothing sensible to do with. The line
            // above says what is happening, and Back still closes it.
            if (state is TvUpdateState.Available) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvChip(
                        label = stringResource(R.string.tv_update_now),
                        isSelected = true,
                        onClick = viewModel::download,
                        modifier = Modifier.focusRequester(firstAction),
                    )
                    TvChip(
                        label = stringResource(R.string.tv_update_later),
                        isSelected = false,
                        onClick = viewModel::dismissLaunchPrompt,
                    )
                }
            }
        }
    }
}

/**
 * What the panel says, which is not always the offer.
 *
 * *Update now* leaves the panel up rather than closing it, because a download is the one thing a
 * viewer needs to be told is happening — and a panel that vanished on the press would leave the
 * app looking exactly as it did before, with several megabytes quietly arriving.
 */
@Composable
private fun promptBody(state: TvUpdateState, installedVersion: String): String = when (state) {
    is TvUpdateState.Available ->
        stringResource(R.string.tv_update_available_body, state.version, installedVersion)

    is TvUpdateState.Downloading ->
        stringResource(R.string.tv_settings_update_downloading, state.version)

    is TvUpdateState.Ready ->
        stringResource(R.string.tv_settings_update_ready, state.version, state.file.name)

    // Every failure the download can produce, in the wording the settings row already uses.
    is TvUpdateState.Failed -> stringResource(state.reason.promptMessage())

    // Not reachable while the panel is up: it is shown on `Available` and only ever moves on to
    // the states above. Written out because the alternative is an `else` that would swallow a
    // state added later, which is the fault every `when` in this file is exhaustive to avoid.
    TvUpdateState.Idle, TvUpdateState.Checking, TvUpdateState.UpToDate ->
        stringResource(R.string.tv_settings_update_checking)
}

/** The download's failures, worded as the settings row words them. */
private fun TvUpdateState.Failed.Reason.promptMessage(): Int = when (this) {
    TvUpdateState.Failed.Reason.OFFLINE -> R.string.tv_settings_update_offline
    TvUpdateState.Failed.Reason.UNREACHABLE -> R.string.tv_settings_update_unreachable
    TvUpdateState.Failed.Reason.NO_ASSET -> R.string.tv_settings_update_no_asset
    TvUpdateState.Failed.Reason.MALFORMED -> R.string.tv_settings_update_malformed
    TvUpdateState.Failed.Reason.CHECKSUM -> R.string.tv_settings_update_checksum
    TvUpdateState.Failed.Reason.NO_CHECKSUM -> R.string.tv_settings_update_no_checksum
    TvUpdateState.Failed.Reason.INSTALLER_REFUSED -> R.string.tv_settings_update_installer_refused
}

/** Dark enough that the panel is plainly in front, light enough to see what it is in front of. */
private const val SCRIM_ALPHA = 0.72f

private const val PANEL_ALPHA = 0.92f

/** Wide enough for two lines of the body at 15sp, and no wider than a sofa can read comfortably. */
private val PANEL_WIDTH = 560.dp
