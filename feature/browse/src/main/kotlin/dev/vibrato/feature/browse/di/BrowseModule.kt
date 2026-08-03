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

package dev.vibrato.feature.browse.di

import dev.vibrato.core.model.MediaKind
import dev.vibrato.feature.browse.BrowseViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * Wiring owned by `:feature:browse`.
 *
 * One ViewModel definition serves four screens; the caller supplies the content kind and
 * whether to restrict to favourites.
 */
val browseModule: Module = module {
    viewModel { (kind: MediaKind, favoritesOnly: Boolean) ->
        BrowseViewModel(
            kind = kind,
            favoritesOnly = favoritesOnly,
            sourceRepository = get(),
            channelRepository = get(),
        )
    }
}

/** Koin parameters for a browse screen. */
fun browseParams(kind: MediaKind, favoritesOnly: Boolean = false) = parametersOf(kind, favoritesOnly)
