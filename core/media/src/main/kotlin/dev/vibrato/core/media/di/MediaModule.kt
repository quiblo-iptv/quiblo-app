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

package dev.vibrato.core.media.di

import android.content.Context
import dev.vibrato.core.media.Media3PlayerController
import dev.vibrato.core.media.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wiring owned by `:core:media`.
 *
 * The controller is created per request rather than as a singleton: the player screen
 * owns its lifetime and releases it, so a decoder is never held while the user is merely
 * browsing (AC-PLAY-09).
 */
val mediaModule: Module = module {
    factory<PlayerController> {
        Media3PlayerController(
            context = get<Context>(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }
}
