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
import dev.vibrato.core.model.Episode
import dev.vibrato.core.model.SeriesDetails
import dev.vibrato.source.api.SeriesDetailsResult
import dev.vibrato.source.api.SourceError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SeriesDetailUiState {
    data object Loading : SeriesDetailUiState

    /**
     * @property resumeEpisode the episode most recently watched, if any, with where it
     *   stopped. Drives the Resume button — a series is watched episode by episode, so
     *   "continue" means the last one touched rather than the furthest through.
     */
    data class Success(
        val channel: Channel,
        val details: SeriesDetails,
        val resumeEpisode: Episode? = null,
        val resumePositionMillis: Long = 0L,
    ) : SeriesDetailUiState
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
                is SeriesDetailsResult.Success -> {
                    val episodes = result.details.seasons.flatMap { it.episodes }
                    // Resume keys are the episode stream URLs, because that is what the
                    // player records against when it is handed a custom URL.
                    val watched = channelRepository.mostRecentlyWatched(episodes.map { it.streamUrl })
                    _uiState.value = SeriesDetailUiState.Success(
                        channel = channel,
                        details = result.details,
                        resumeEpisode = watched?.let { (key, _) -> episodes.firstOrNull { it.streamUrl == key } },
                        resumePositionMillis = watched?.second ?: 0L,
                    )
                }
            }
        }
    }
}
