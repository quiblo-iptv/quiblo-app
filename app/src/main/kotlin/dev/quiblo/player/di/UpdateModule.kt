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

package dev.quiblo.player.di

import dev.quiblo.core.network.update.ReleaseChecker
import dev.quiblo.feature.settings.LaunchUpdateViewModel
import dev.quiblo.player.BuildConfig
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The launch-time update check, wired for the handset build (`029` #7).
 *
 * Here rather than in `:feature:settings` because the two things this needs are the two things a
 * feature module cannot know: which `BuildConfig` is the app's, and which of the release's two
 * APKs belongs to it. Offering a phone the television build is offering the wrong app, which is
 * why [ReleaseChecker.PHONE_ASSET_PREFIX] carries the version's own `v`.
 *
 * Every dependency is named rather than positional. Koin resolves by type and does not type-check
 * the order, so two `String` parameters in a positional list is a version and an asset prefix
 * waiting to be swapped without anything complaining.
 */
val updateModule: Module = module {
    viewModel {
        LaunchUpdateViewModel(
            checker = get(),
            settings = get(),
            currentVersion = BuildConfig.VERSION_NAME,
            assetPrefix = ReleaseChecker.PHONE_ASSET_PREFIX,
        )
    }
}
