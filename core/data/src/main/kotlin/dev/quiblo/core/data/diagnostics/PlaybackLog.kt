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

package dev.quiblo.core.data.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The last few playback diagnoses on this device, newest first (`FEAT-035`).
 *
 * **Memory only.** No disk, no network: the log exists so that a person reporting "it failed
 * three times last night" can open Settings and read what was found, and it is gone when the
 * process is. Each entry is a [Diagnosis], whose details are already redacted to a host.
 */
class PlaybackLog(private val capacity: Int = DEFAULT_CAPACITY) {

    private val _entries = MutableStateFlow<List<Diagnosis>>(emptyList())
    val entries: StateFlow<List<Diagnosis>> = _entries.asStateFlow()

    fun record(diagnosis: Diagnosis) {
        _entries.update { (listOf(diagnosis) + it).take(capacity) }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 20
    }
}
