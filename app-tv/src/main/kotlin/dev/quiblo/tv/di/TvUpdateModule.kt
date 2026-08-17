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

package dev.quiblo.tv.di

import android.content.Context
import dev.quiblo.tv.BuildConfig
import dev.quiblo.tv.update.TvApkInstaller
import dev.quiblo.tv.update.TvUpdateViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The one thing the television app owns that the phone does not.
 *
 * A television has no store to update itself from, so Settings offers to fetch the newest release
 * APK from this project's own releases page. The phone has the same problem and does not yet have
 * the answer; when it does, this moves into a shared module rather than being copied.
 *
 * Every dependency is named rather than positional. `single { Thing(get(), get()) }` compiles
 * whatever order the parameters are in, and Koin resolves by type at runtime — which is how a
 * version string and a directory path get swapped without anything complaining until a viewer
 * presses the button.
 */
val tvUpdateModule: Module = module {
    viewModel {
        TvUpdateViewModel(
            checker = get(),
            downloader = get(),
            currentVersion = BuildConfig.VERSION_NAME,
            updatesDirectory = TvApkInstaller.updatesDirectory(get<Context>()),
        )
    }
}
