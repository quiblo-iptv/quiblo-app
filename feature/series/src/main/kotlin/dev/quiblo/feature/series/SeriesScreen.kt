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

package dev.quiblo.feature.series

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.feature.browse.BrowseScreen
import dev.quiblo.feature.browse.BrowseViewModel
import dev.quiblo.feature.browse.di.browseParams
import org.koin.androidx.compose.koinViewModel

/**
 * SeriesScreen.
 *
 * A thin wrapper over the shared browse UI. The screens differ in what they show, not in
 * how they behave, so there is one implementation rather than four.
 */
@Composable
fun SeriesScreen(
    onItemClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BrowseViewModel = koinViewModel(
        key = "series",
        parameters = { browseParams(MediaKind.SERIES) },
    )
    BrowseScreen(
        viewModel = viewModel,
        onItemClick = onItemClick,
        emptyMessage = stringResource(R.string.feature_series_empty),
        modifier = modifier,
        // Series artwork is poster art, exactly as movie artwork is.
        showArtworkCards = true,
    )
}
