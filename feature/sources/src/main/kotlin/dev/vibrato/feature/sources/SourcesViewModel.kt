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

package dev.vibrato.feature.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vibrato.core.data.RefreshOutcome
import dev.vibrato.core.data.SourceRepository
import dev.vibrato.core.model.Source
import dev.vibrato.core.model.SourceKind
import dev.vibrato.source.api.SourceError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the add-source form is currently doing. */
sealed interface AddSourceState {

    data object Idle : AddSourceState

    data object Working : AddSourceState

    /**
     * @param skippedEntries entries the parser could not use. Surfaced to the user rather
     *   than swallowed (AC-PL-04).
     */
    data class Added(val channelCount: Int, val skippedEntries: Int) : AddSourceState

    data class Failed(val error: SourceError) : AddSourceState
}

class SourcesViewModel(
    private val repository: SourceRepository,
) : ViewModel() {

    val sources: StateFlow<List<Source>> = repository.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _addState = MutableStateFlow<AddSourceState>(AddSourceState.Idle)
    val addState: StateFlow<AddSourceState> = _addState.asStateFlow()

    /**
     * Adds an M3U playlist by URL or by picked document.
     *
     * @param name the user's label. Falls back to a derived name when left blank, because
     *   forcing a name before the user knows what the playlist contains is friction.
     */
    fun addM3uSource(name: String, location: String) {
        if (location.isBlank()) return

        _addState.value = AddSourceState.Working
        viewModelScope.launch {
            val label = name.ifBlank { deriveName(location) }
            when (val outcome = repository.addSource(label, location, SourceKind.M3U)) {
                is RefreshOutcome.Failure -> _addState.value = AddSourceState.Failed(outcome.error)

                is RefreshOutcome.Success -> _addState.value = AddSourceState.Added(
                    channelCount = outcome.report.parsedEntries,
                    skippedEntries = outcome.report.skippedEntries,
                )
            }
        }
    }

    fun refresh(sourceId: Long) {
        _addState.value = AddSourceState.Working
        viewModelScope.launch {
            when (val outcome = repository.refresh(sourceId)) {
                is RefreshOutcome.Failure -> _addState.value = AddSourceState.Failed(outcome.error)
                is RefreshOutcome.Success -> _addState.value = AddSourceState.Added(
                    channelCount = outcome.report.parsedEntries,
                    skippedEntries = outcome.report.skippedEntries,
                )
            }
        }
    }

    fun delete(sourceId: Long) {
        viewModelScope.launch { repository.deleteSource(sourceId) }
    }

    fun dismissResult() {
        _addState.value = AddSourceState.Idle
    }

    /** A readable label from the last meaningful path segment, never the full URL. */
    private fun deriveName(location: String): String =
        location.substringAfterLast('/')
            .substringBefore('?')
            .removeSuffix(".m3u")
            .removeSuffix(".m3u8")
            .ifBlank { "Playlist" }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
