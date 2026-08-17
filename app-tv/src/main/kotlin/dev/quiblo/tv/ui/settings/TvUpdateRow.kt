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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.update.TvApkInstaller
import dev.quiblo.tv.update.TvUpdateState
import dev.quiblo.tv.update.TvUpdateViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Check for updates, under the version it is checking.
 *
 * **A television has no store.** Quiblo is installed from an APK, so without this a viewer three
 * releases behind has no way of knowing — and no way of doing anything about it that does not
 * involve a laptop, a USB stick and a file manager.
 *
 * **Every state says something.** Idle, checking, up to date, one available, downloading, ready,
 * and six distinct failures — each with its own line. A row that goes quiet when something goes
 * wrong is indistinguishable from a row whose button is broken, and this project has deleted
 * features for exactly that.
 *
 * **Nothing is checked unprompted.** The button is the only thing that calls out to GitHub.
 */
@Composable
internal fun TvUpdateRow(viewModel: TvUpdateViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(LABEL_WIDTH)) {
            Text(
                text = stringResource(R.string.tv_settings_update_label),
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 22.sp,
            )
            Text(
                text = state.detail(),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Spacer(modifier = Modifier.width(COLUMN_GAP))

        // Checking and downloading have no button on purpose: a second press while a download is
        // running is the one input this row has nothing sensible to do with.
        when (state) {
            is TvUpdateState.Checking, is TvUpdateState.Downloading -> Unit

            is TvUpdateState.Available -> TvChip(
                label = stringResource(R.string.tv_settings_update_download),
                isSelected = true,
                onClick = viewModel::download,
            )

            is TvUpdateState.Ready -> TvChip(
                label = stringResource(R.string.tv_settings_update_install),
                isSelected = true,
                onClick = {
                    val ready = state as TvUpdateState.Ready
                    if (!TvApkInstaller.install(context, ready.file)) viewModel.installerRefused()
                },
            )

            else -> TvChip(
                label = stringResource(R.string.tv_settings_update_check),
                isSelected = false,
                onClick = viewModel::check,
            )
        }
    }
}

/**
 * The line under the label.
 *
 * A `when` over the state rather than a nullable message assembled at each call site: every
 * branch has to be written down, so a state added later cannot ship with nothing to say.
 */
@Composable
private fun TvUpdateState.detail(): String = when (this) {
    is TvUpdateState.Idle -> stringResource(R.string.tv_settings_update_idle)
    is TvUpdateState.Checking -> stringResource(R.string.tv_settings_update_checking)
    is TvUpdateState.UpToDate -> stringResource(R.string.tv_settings_update_current)
    is TvUpdateState.Available -> stringResource(R.string.tv_settings_update_available, version)
    is TvUpdateState.Downloading -> stringResource(R.string.tv_settings_update_downloading, version)
    is TvUpdateState.Ready -> stringResource(R.string.tv_settings_update_ready, version, file.name)
    is TvUpdateState.Failed -> stringResource(reason.message())
}

/** Every way this can fail, and what each one is worth saying to somebody holding a remote. */
private fun TvUpdateState.Failed.Reason.message(): Int = when (this) {
    TvUpdateState.Failed.Reason.OFFLINE -> R.string.tv_settings_update_offline
    TvUpdateState.Failed.Reason.UNREACHABLE -> R.string.tv_settings_update_unreachable
    TvUpdateState.Failed.Reason.NO_ASSET -> R.string.tv_settings_update_no_asset
    TvUpdateState.Failed.Reason.MALFORMED -> R.string.tv_settings_update_malformed
    TvUpdateState.Failed.Reason.CHECKSUM -> R.string.tv_settings_update_checksum
    TvUpdateState.Failed.Reason.NO_CHECKSUM -> R.string.tv_settings_update_no_checksum
    TvUpdateState.Failed.Reason.INSTALLER_REFUSED -> R.string.tv_settings_update_installer_refused
}
