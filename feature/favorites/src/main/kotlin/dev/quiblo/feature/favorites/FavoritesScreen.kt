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

package dev.quiblo.feature.favorites

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.feature.browse.BrowseScope
import dev.quiblo.feature.browse.BrowseScreen
import dev.quiblo.feature.browse.BrowseViewModel
import dev.quiblo.feature.browse.di.browseParams
import org.koin.androidx.compose.koinViewModel

/**
 * FavoritesScreen.
 *
 * A thin wrapper over the shared browse UI. The screens differ in what they show, not in
 * how they behave, so there is one implementation rather than four.
 */
@Composable
fun FavoritesScreen(
    onItemClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BrowseViewModel = koinViewModel(
        key = "favorites",
        parameters = { browseParams(MediaKind.LIVE, BrowseScope.FAVOURITES) },
    )
    BrowseScreen(
        viewModel = viewModel,
        onItemClick = onItemClick,
        emptyMessage = stringResource(R.string.feature_favorites_empty),
        modifier = modifier,
        // The same poster grid as Movies and Series. Favourites holds live channels too,
        // and the card handles that by fitting a wide logo inside the tile rather than
        // cropping it — so the shelf stays one uniform grid instead of two.
        showArtworkCards = true,
    )
}
