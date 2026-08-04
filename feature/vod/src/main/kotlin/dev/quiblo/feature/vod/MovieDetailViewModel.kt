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

package dev.quiblo.feature.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.core.model.VodDetails
import dev.quiblo.source.api.VodDetailsResult
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
        val metadata: TitleMetadata? = null,
        /**
         * Whether this film is favourited.
         *
         * Held in the state rather than read from [channel], which is a snapshot taken when
         * the screen opened and would still say "not favourited" after the user tapped the
         * heart on this very screen.
         */
        val isFavorite: Boolean = false,
        /**
         * True while the description is still being fetched.
         *
         * Needed because "we have not asked yet" and "we asked and there is nothing" look
         * identical in the data and must not look identical on screen. Without it the
         * screen asserted "no description" for the fraction of a second before the answer
         * arrived, which reads as a wrong answer rather than a pending one.
         */
        val isEnriching: Boolean = false,
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
    private val metadataRepository: TitleMetadataRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Loading)
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * Adds or removes this film from favourites.
     *
     * Here as well as on the poster grid because a detail screen is where the decision is
     * actually made: the grid is where a title is recognised, this is where it is read
     * about, and requiring a trip back to the grid to act on that is the kind of gap
     * nobody notices while writing the grid.
     */
    fun toggleFavorite() {
        val current = _uiState.value as? MovieDetailUiState.Ready ?: return
        viewModelScope.launch { channelRepository.toggleFavorite(current.channel) }
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
                isFavorite = channel.isFavorite,
                isEnriching = true,
            )

            observeFavorite(channel)

            val details = (channelRepository.getVodDetails(channelId) as? VodDetailsResult.Success)?.details
            (_uiState.value as? MovieDetailUiState.Ready)?.let { _uiState.value = it.copy(details = details) }

            // Fetched after the provider's own details rather than alongside: the screen is
            // already complete without it, and enrichment must never be what a viewer waits
            // for. Returns null when the feature is off, which is the default.
            metadataRepository.load()
            val metadata = metadataRepository.forTitle(channel.name, MediaKind.VOD)
            (_uiState.value as? MovieDetailUiState.Ready)?.let {
                _uiState.value = it.copy(metadata = metadata, isEnriching = false)
            }
        }
    }

    /**
     * Keeps the heart in step with the database for as long as the screen is open.
     *
     * A separate coroutine from [load] because it never completes, and putting a
     * `collect` in the middle of that function would stop everything after it from running.
     */
    private fun observeFavorite(channel: Channel) {
        viewModelScope.launch {
            channelRepository.observeIsFavorite(channel).collect { isFavorite ->
                (_uiState.value as? MovieDetailUiState.Ready)?.let {
                    _uiState.value = it.copy(isFavorite = isFavorite)
                }
            }
        }
    }
}
