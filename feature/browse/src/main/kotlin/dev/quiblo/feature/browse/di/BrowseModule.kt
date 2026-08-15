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

package dev.quiblo.feature.browse.di

import dev.quiblo.core.model.MediaKind
import dev.quiblo.feature.browse.BrowseFeed
import dev.quiblo.feature.browse.BrowseScope
import dev.quiblo.feature.browse.BrowseViewModel
import dev.quiblo.feature.browse.SearchViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * Wiring owned by `:feature:browse`.
 *
 * One ViewModel definition serves five screens; the caller supplies the content kind and
 * which feed of it to show.
 */
val browseModule: Module = module {
    viewModel { (feed: BrowseFeed) ->
        BrowseViewModel(
            feed = feed,
            sourceRepository = get(),
            channelRepository = get(),
            categoryRepository = get(),
            guideRepository = get(),
            metadataRepository = get(),
            historyRepository = get(),
            channelLogoRepository = get(),
            popularTitles = get(),
            recommendations = get(),
        )
    }

    viewModel {
        SearchViewModel(
            sourceRepository = get(),
            searchRepository = get(),
            metadataRepository = get(),
            playerSettingsRepository = get(),
        )
    }
}

/** Koin parameters for a browse screen. */
fun browseParams(kind: MediaKind, scope: BrowseScope = BrowseScope.CATALOGUE) =
    parametersOf(BrowseFeed(kind = kind, scope = scope))

/**
 * Koin parameters for the newest-first feed.
 *
 * Its own function because [BrowseFeed.kind] means nothing in this scope — the feed spans films
 * and series at once — and every branch that reads `kind` is already excluded by the scope. The
 * value below is a placeholder, and this is the one place it exists rather than a puzzle at each
 * call site about why a "recently added" screen declares itself as films.
 */
fun recentlyAddedParams() = browseParams(MediaKind.VOD, BrowseScope.RECENTLY_ADDED)

/**
 * Koin parameters for the television's For You tab.
 *
 * [BrowseFeed.kind] is a placeholder here for the same reason it is in [recentlyAddedParams]: the
 * feed spans films and series at once, and every branch that reads `kind` is excluded by the
 * scope.
 */
fun forYouParams() = browseParams(MediaKind.VOD, BrowseScope.FOR_YOU)
