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

package dev.quiblo.tv.ui.sources

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.LiveFormat
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.ui.common.TvFocusRow
import dev.quiblo.tv.ui.common.TvTextField

/**
 * The add-source form.
 *
 * Xtream and M3U in one form rather than a mode toggle: the fields a user leaves empty say
 * which they meant. A URL alone is a playlist; a URL with a username and password is an
 * account. That removes a control from a screen where every control costs a D-pad press.
 *
 * **One form, two callers.** Settings → Sources opens it, and so does the last page of the
 * first-launch flow, where a viewer who has just agreed to the terms is asked for the playlist
 * they will need before anything in this app has something to show. A second copy for the
 * second caller is how a fix lands in one and is forgotten in the other, which is the failure
 * `TvTextField` was written to end.
 *
 * The form asks for no width of its own. Whoever composes it decides how wide the column is
 * and where on the panel it sits — Sources centres it, and so does consent.
 */
@Composable
internal fun TvAddSourceForm(
    focusRequester: FocusRequester,
    onAddM3u: (String, String) -> Boolean,
    onAddXtream: (String, String, String, String) -> Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * What the second button says.
     *
     * "Cancel" is right in Settings, where the viewer chose to open this. It is wrong on first
     * launch, where the same button means "not now" rather than "undo" — and a viewer who reads
     * Cancel there can reasonably think it cancels the install.
     */
    cancelLabel: String = stringResource(R.string.tv_sources_cancel),
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Set when a save was refused, so the refusal says something. A form that simply does
    // nothing when Save is pressed is indistinguishable from a broken button.
    var wasRejected by remember { mutableStateOf(false) }

    val canSubmit = url.isNotBlank()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_sources_form_hint),
            color = HINT_COLOUR,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (wasRejected) {
            Text(
                text = stringResource(R.string.tv_sources_incomplete),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TvTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.tv_sources_name),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        TvTextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.tv_sources_url),
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.fillMaxWidth(),
        )
        TvTextField(
            value = username,
            onValueChange = { username = it },
            label = stringResource(R.string.tv_sources_username),
            modifier = Modifier.fillMaxWidth(),
        )
        TvTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.tv_sources_password),
            isPassword = true,
            isLast = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvFocusRow(
                label = stringResource(R.string.tv_sources_save),
                onClick = {
                    val accepted = when {
                        !canSubmit -> false
                        // Neither credential given means a playlist; either one given means
                        // an account, and an account needs both.
                        username.isBlank() && password.isBlank() -> onAddM3u(name, url)
                        else -> onAddXtream(name, url, username, password)
                    }
                    wasRejected = !accepted
                },
                modifier = Modifier.width(BUTTON_WIDTH),
                // The one thing on this screen a viewer is here to press.
                hasGlow = true,
            )
            TvFocusRow(
                label = cancelLabel,
                onClick = onCancel,
                modifier = Modifier.width(BUTTON_WIDTH),
            )
        }
    }
}

internal val BUTTON_WIDTH = 220.dp

/**
 * How wide a column of this form is, wherever it is composed.
 *
 * The figure the consent screen's body already uses. It was a fraction of the panel — sixty
 * per cent of whatever the television happened to be — which is not a width so much as a
 * guess, and on a wide panel it produced a form nobody could read across.
 */
internal val FORM_WIDTH = 720.dp

private val HINT_COLOUR = Color.White.copy(alpha = 0.6f)

/**
 * Edits a source in place (`BUG-042`): the add form's fields, filled in, for one source.
 *
 * The kind is the source's and is not offered: an account does not become a playlist by having its
 * password cleared. The password starts empty and empty keeps the stored one, so it is never read
 * back onto a screen in front of whoever is in the room (AC-XT-04).
 */
@Composable
internal fun TvEditSourceForm(
    focusRequester: FocusRequester,
    initialName: String,
    initialUrl: String,
    initialUsername: String,
    isAccount: Boolean,
    initialLiveFormat: LiveFormat,
    onSave: (name: String, url: String, username: String, password: String, liveFormat: LiveFormat) -> Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var username by remember(initialUsername) { mutableStateOf(initialUsername) }
    var liveFormat by remember(initialLiveFormat) { mutableStateOf(initialLiveFormat) }
    var password by remember { mutableStateOf("") }
    var wasRejected by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.tv_sources_edit_hint),
            color = HINT_COLOUR,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (wasRejected) {
            Text(
                text = stringResource(R.string.tv_sources_incomplete),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TvTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.tv_sources_name),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        TvTextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.tv_sources_url),
            keyboardType = KeyboardType.Uri,
            isLast = !isAccount,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isAccount) {
            TvTextField(
                value = username,
                onValueChange = { username = it },
                label = stringResource(R.string.tv_sources_username),
                modifier = Modifier.fillMaxWidth(),
            )
            TvTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.tv_sources_password_keep),
                isPassword = true,
                isLast = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Which container live channels are asked for (`BUG-043`). Every choice on screen, like
            // every other setting on this television: seeing the alternatives is how it is understood.
            Text(
                text = stringResource(R.string.tv_sources_live_format),
                color = HINT_COLOUR,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusGroup()) {
                LiveFormat.entries.forEach { format ->
                    TvChip(
                        label = stringResource(format.tvLabelRes()),
                        isSelected = format == liveFormat,
                        onClick = { liveFormat = format },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvFocusRow(
                label = stringResource(R.string.tv_sources_edit_save),
                onClick = { wasRejected = !onSave(name, url, username, password, liveFormat) },
                modifier = Modifier.width(BUTTON_WIDTH),
                hasGlow = true,
            )
            TvFocusRow(
                label = stringResource(R.string.tv_sources_cancel),
                onClick = onCancel,
                modifier = Modifier.width(BUTTON_WIDTH),
            )
        }
    }
}

private fun LiveFormat.tvLabelRes(): Int = when (this) {
    LiveFormat.AUTO -> R.string.tv_sources_live_format_auto
    LiveFormat.HLS -> R.string.tv_sources_live_format_hls
    LiveFormat.TS -> R.string.tv_sources_live_format_ts
}
