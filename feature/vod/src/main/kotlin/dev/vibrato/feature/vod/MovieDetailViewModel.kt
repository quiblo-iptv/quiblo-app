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

package dev.vibrato.feature.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vibrato.core.data.ChannelRepository
import dev.vibrato.core.data.MovieMetadataRepository
import dev.vibrato.core.model.Channel
import dev.vibrato.core.model.MovieMetadata
import dev.vibrato.core.model.VodDetails
import dev.vibrato.source.api.VodDetailsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the movie screen renders. */
sealed interface MovieDetailUiState {

    data object Loading : MovieDetailUiState

    /**
     * @property details null when the source cannot describe films — an M3U playlist has
     *   no plot to fetch. The screen still has artwork and a title, so this is a reduced
     *   screen rather than an error.
     * @property resumePositionMillis where a previous viewing stopped, or zero.
     */
    data class Ready(
        val channel: Channel,
        val details: VodDetails? = null,
        val resumePositionMillis: Long = 0L,
        /** From TMDB, when the user has enabled it and a match was found. */
        val metadata: MovieMetadata? = null,
    ) : MovieDetailUiState {

        val canResume: Boolean get() = resumePositionMillis > RESUME_THRESHOLD_MILLIS

        private companion object {
            /**
             * Below this, "resume" would drop the viewer back essentially at the start and
             * offering it as a distinct choice is just noise.
             */
            const val RESUME_THRESHOLD_MILLIS = 10_000L
        }
    }

    data object NotFound : MovieDetailUiState
}

/**
 * Loads one film's artwork, overview and resume point.
 *
 * The overview is fetched separately from the catalogue listing because the listing does
 * not carry one — asking for a plot per entry across a whole VOD library would be a
 * request nobody reads the result of.
 */
class MovieDetailViewModel(
    private val channelId: Long,
    private val channelRepository: ChannelRepository,
    private val metadataRepository: MovieMetadataRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Loading)
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Re-reads the resume point, so returning from playback updates the buttons. */
    fun refreshResumePosition() {
        val current = _uiState.value as? MovieDetailUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(
                resumePositionMillis = channelRepository.resumePosition(current.channel.stableKey),
            )
        }
    }

    private fun load() {
        viewModelScope.launch {
            val channel = channelRepository.findById(channelId)
            if (channel == null) {
                _uiState.value = MovieDetailUiState.NotFound
                return@launch
            }

            // Show what we already have before the network call. The artwork and title come
            // from the catalogue, so the screen is useful immediately and the overview
            // fills in when it arrives.
            _uiState.value = MovieDetailUiState.Ready(
                channel = channel,
                resumePositionMillis = channelRepository.resumePosition(channel.stableKey),
            )

            val details = (channelRepository.getVodDetails(channelId) as? VodDetailsResult.Success)?.details
            (_uiState.value as? MovieDetailUiState.Ready)?.let { _uiState.value = it.copy(details = details) }

            // Fetched after the provider's own details rather than alongside: the screen is
            // already complete without it, and enrichment must never be what a viewer waits
            // for. Returns null when the feature is off, which is the default.
            metadataRepository.load()
            val metadata = metadataRepository.forTitle(channel.name)
            (_uiState.value as? MovieDetailUiState.Ready)?.let { _uiState.value = it.copy(metadata = metadata) }
        }
    }
}
