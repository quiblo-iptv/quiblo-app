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

package dev.vibrato.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vibrato.core.data.ChannelRepository
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.SeriesDetails
import dev.vibrato.source.api.SeriesDetailsResult
import dev.vibrato.source.api.SourceError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SeriesDetailUiState {
    data object Loading : SeriesDetailUiState
    data class Success(val channel: Channel, val details: SeriesDetails) : SeriesDetailUiState
    data class Error(val error: SourceError) : SeriesDetailUiState
}

class SeriesDetailViewModel(
    private val channelId: Long,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeriesDetailUiState>(SeriesDetailUiState.Loading)
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    fun loadDetails() {
        _uiState.value = SeriesDetailUiState.Loading
        viewModelScope.launch {
            val channel = channelRepository.findById(channelId)
            if (channel == null) {
                _uiState.value = SeriesDetailUiState.Error(SourceError.NotFound)
                return@launch
            }

            when (val result = channelRepository.getSeriesDetails(channel)) {
                is SeriesDetailsResult.Failure -> _uiState.value = SeriesDetailUiState.Error(result.error)
                is SeriesDetailsResult.Success -> _uiState.value = SeriesDetailUiState.Success(channel, result.details)
            }
        }
    }
}
