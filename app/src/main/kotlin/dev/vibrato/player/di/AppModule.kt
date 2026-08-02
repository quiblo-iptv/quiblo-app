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

package dev.vibrato.player.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Root Koin module.
 *
 * Empty at M0. Per-layer modules (`:core:database`, `:core:network`, `:core:data`,
 * `:source:*`) are declared in their own modules and aggregated here as they land,
 * keeping `:app` the single assembly point (docs/PLAN.md §2).
 */
val appModule: Module = module {
    // Intentionally empty until M1 introduces the first repository.
}
