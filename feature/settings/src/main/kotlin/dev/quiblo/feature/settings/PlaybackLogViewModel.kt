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
import dev.quiblo.core.data.diagnostics.Diagnosis
import dev.quiblo.core.data.diagnostics.PlaybackLog
import dev.quiblo.feature.player.reportText
import kotlinx.coroutines.flow.StateFlow

/**
 * The last playback failures on this device, for the Playback log in Settings (`FEAT-035`).
 *
 * Its own ViewModel rather than one more flow on [SettingsViewModel], which already takes a dozen
 * collaborators for things that have nothing to do with playback. Both apps read it.
 */
class PlaybackLogViewModel(log: PlaybackLog) : ViewModel() {

    /** Newest first, at most twenty, memory only. */
    val entries: StateFlow<List<Diagnosis>> = log.entries

    /** Every entry as one block of text for the clipboard. Redacted already, like each line. */
    fun reportText(): String = entries.value.joinToString(separator = "\n") { it.reportText() }
}
