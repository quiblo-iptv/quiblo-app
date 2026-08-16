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

package dev.quiblo.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A scope that outlives every screen, for the small number of writes that must finish.
 *
 * **This exists because a resume point was being cancelled mid-write.** On the phone the player's
 * ViewModel belongs to its navigation entry, so pressing back clears that entry's `ViewModelStore`
 * and cancels its `viewModelScope` — including the coroutine that was on its way to save where the
 * viewer had got to. The `onCleared` fallback cannot cover it either: androidx closes the scope
 * *before* calling `onCleared`, so a `viewModelScope.launch` there is a no-op by construction.
 *
 * A write that answers "where was I" is not the screen's work, it is the app's, and it belongs to
 * something that does not go away when a screen does.
 *
 * **Its own type rather than a bare `CoroutineScope`.** Koin resolves by type and does not
 * type-check what it hands over, so a `single<CoroutineScope>` is a mis-wiring waiting for the day
 * a second scope is registered — and the failure would be work quietly running in the wrong place.
 * A named type cannot be confused with anything.
 *
 * Nothing long-running belongs here. A scope with no lifecycle is a scope nothing can stop, so it
 * carries writes that take milliseconds and never a loop, a collector, or anything that waits on
 * the network. Scheduled work belongs to WorkManager, which the platform can stop.
 */
class ApplicationScope(
    /**
     * The scope everything is launched on.
     *
     * A parameter rather than a fixed construction so a test can hand in its own clock — the
     * property worth testing here is that a write does *not* run on the screen's scope, and that
     * can only be asserted by two scopes being distinguishable.
     */
    delegate: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : CoroutineScope by delegate
