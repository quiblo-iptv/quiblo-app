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

package dev.vibrato.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vibrato.core.data.SourceRepository
import dev.vibrato.core.model.Category
import dev.vibrato.core.model.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the live list needs to render. */
data class LiveUiState(
    val hasSource: Boolean = false,
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val channels: List<Channel> = emptyList(),
)

/**
 * Drives the live channel list.
 *
 * Filtering happens in the database rather than in composition, so a 20,000-entry
 * playlist neither blocks a frame nor holds a second filtered copy in memory
 * (AC-PL-05).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModel(
    private val repository: SourceRepository,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<String?>(null)

    /** M1 renders the first configured source. Multi-source selection arrives in M3. */
    private val activeSourceId: StateFlow<Long?> = repository.observeSources()
        .map { sources -> sources.firstOrNull()?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val uiState: StateFlow<LiveUiState> = activeSourceId
        .flatMapLatest { sourceId ->
            if (sourceId == null) {
                flowOf(LiveUiState(hasSource = false))
            } else {
                combine(
                    repository.observeCategories(sourceId),
                    selectedCategory,
                ) { categories, selected -> categories to selected }
                    .flatMapLatest { (categories, selected) ->
                        val channelFlow = if (selected == null) {
                            repository.observeChannels(sourceId)
                        } else {
                            repository.observeChannelsInGroup(sourceId, selected)
                        }
                        channelFlow.map { channels ->
                            LiveUiState(
                                hasSource = true,
                                categories = categories,
                                selectedCategory = selected,
                                channels = channels,
                            )
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LiveUiState())

    val selected: StateFlow<String?> = selectedCategory.asStateFlow()

    fun selectCategory(groupTitle: String?) {
        selectedCategory.value = groupTitle
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
