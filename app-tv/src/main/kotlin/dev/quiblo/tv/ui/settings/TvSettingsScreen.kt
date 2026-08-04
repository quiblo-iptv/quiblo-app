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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.SeekInterval
import dev.quiblo.feature.settings.SettingsViewModel
import dev.quiblo.tv.R
import org.koin.androidx.compose.koinViewModel

/**
 * Settings, on a remote.
 *
 * Reuses `SettingsViewModel` whole — every control here writes through the same store the
 * phone writes through, so a setting means the same thing on both and can only be wrong in
 * one place. The gear in the top bar used to be focusable with no `onClick` at all, which
 * is what #004 was: not a missing screen so much as a control wired to nothing.
 *
 * **Two phone settings are deliberately absent: theme mode and dynamic colour.** The
 * television theme is always dark and says so in its own documentation — a television is
 * watched at a distance in a dim room, and it has no wallpaper for a dynamic palette to be
 * drawn from. Putting the controls here would mean shipping two switches that change
 * nothing on screen, which is the exact shape of the "hollow feature" this project has
 * already had to delete once.
 */
@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(key = "tv-settings"),
) {
    val playerSettings by viewModel.playerSettings.collectAsStateWithLifecycle()
    val channelLogosEnabled by viewModel.channelLogosEnabled.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.tv_settings),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }

        item { SectionHeading(stringResource(R.string.tv_settings_playback)) }

        item {
            OptionRow(
                label = stringResource(R.string.tv_settings_seek_interval),
                options = SeekInterval.entries,
                selected = playerSettings.seekInterval,
                labelFor = { stringResource(R.string.tv_settings_seconds, it.seconds) },
                onSelect = viewModel::setSeekInterval,
            )
        }

        item {
            OptionRow(
                label = stringResource(R.string.tv_settings_buffer),
                options = BufferMode.entries,
                selected = playerSettings.bufferMode,
                labelFor = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                onSelect = viewModel::setBufferMode,
            )
        }

        item {
            OptionRow(
                label = stringResource(R.string.tv_settings_bitrate),
                options = MaxBitrateCap.entries,
                selected = playerSettings.maxBitrate,
                labelFor = { cap ->
                    cap.bitsPerSecond
                        ?.let { stringResource(R.string.tv_settings_mbps, it / MBPS) }
                        ?: stringResource(R.string.tv_settings_unlimited)
                },
                onSelect = viewModel::setMaxBitrate,
            )
        }

        item { SectionHeading(stringResource(R.string.tv_settings_artwork)) }

        item {
            OptionRow(
                label = stringResource(R.string.tv_settings_channel_logos),
                description = stringResource(R.string.tv_settings_channel_logos_detail),
                options = listOf(false, true),
                selected = channelLogosEnabled,
                labelFor = {
                    stringResource(if (it) R.string.tv_settings_on else R.string.tv_settings_off)
                },
                onSelect = viewModel::setChannelLogosEnabled,
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

/**
 * One setting: a name on the left, its choices as a row of chips on the right.
 *
 * Every choice is on screen rather than behind a dialog, because a remote opening a dialog
 * to pick one of four values is three presses where one will do, and because seeing the
 * alternatives is most of how a viewer understands what a setting means. Up and down move
 * between settings; left and right move between the values of one.
 */
@Composable
private fun <T> OptionRow(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(LABEL_WIDTH)) {
            Text(text = label, color = Color.White, fontSize = 17.sp)
            description?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.focusGroup(),
        ) {
            options.forEach { option ->
                OptionChip(
                    label = labelFor(option),
                    isSelected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Text(
        text = label,
        color = Color.White.copy(alpha = if (isFocused || isSelected) 1f else 0.6f),
        fontSize = 15.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = if (isSelected) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val LABEL_WIDTH = 360.dp
private const val MBPS = 1_000_000
