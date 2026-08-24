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

package dev.quiblo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.network.update.ReleaseChecker
import dev.quiblo.core.network.update.UpdateCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Asks once, when the app opens, whether there is a newer Quiblo (`029` #7).
 *
 * **Quiblo is installed from an APK and there is no store behind it.** Nothing tells a viewer that
 * the build on their television is eight months old, and a player that silently stays on an old
 * release is a player whose fixes never arrive. That is the case for asking without being asked,
 * and it is the only thing in this app that does.
 *
 * Four rules keep it from becoming the thing the promise in the consent copy was about:
 *
 * - **It is a setting, and it can be turned off.** Off means no request is made at all, not that
 *   the answer is hidden. See `PlayerSettingsRepository.checkUpdatesOnLaunch`.
 * - **It asks this project's own releases page and nothing else.** No analytics, no identifier, no
 *   payload — a `GET` for a public JSON document, the same one the button in Settings fetches.
 * - **Once per process.** [check] is guarded, so a configuration change, a profile switch or
 *   backing out to the shell does not ask again.
 * - **Silence is silence.** Every outcome but "there is a newer one" leaves [available] null: a
 *   viewer who opens the app to watch something is not told that a check they did not ask for has
 *   succeeded, or failed, or that they are up to date.
 *
 * @param currentVersion `BuildConfig.VERSION_NAME`, passed in rather than read here so the
 *   comparison can be tested against versions this build does not have.
 * @param assetPrefix which app's APK to look for. See [ReleaseChecker].
 */
class LaunchUpdateViewModel(
    private val checker: ReleaseChecker,
    private val settings: PlayerSettingsRepository,
    private val currentVersion: String,
    private val assetPrefix: String,
) : ViewModel() {

    private val _available = MutableStateFlow<UpdateCheck.Available?>(null)

    /** The newer release, once one is known. Null in every other case, including failure. */
    val available: StateFlow<UpdateCheck.Available?> = _available.asStateFlow()

    /** The version this build is, for the dialog to say what is being replaced. */
    val installedVersion: String = currentVersion

    private var asked = false

    /**
     * Runs the check, at most once for the life of this ViewModel.
     *
     * Called from the shell rather than from `init` so that it happens after there is something on
     * screen: a dialog that arrives before the first frame is a dialog over a blank window, and on
     * a television it would take focus before the catalogue has any.
     */
    fun check() {
        if (asked) return
        asked = true

        viewModelScope.launch {
            if (!settings.checkUpdatesOnLaunch.first()) return@launch
            val outcome = checker.check(currentVersion, assetPrefix)
            _available.value = outcome as? UpdateCheck.Available
        }
    }

    /**
     * "Later".
     *
     * Clears the offer for this session and nothing more. There is deliberately no "skip this
     * version": a viewer who dismisses an update is saying not now, and a switch that records
     * which release they said no to is a second setting that nobody can find again to undo.
     */
    fun dismiss() {
        _available.value = null
    }
}
