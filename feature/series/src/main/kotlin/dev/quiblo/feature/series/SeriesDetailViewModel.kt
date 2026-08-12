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

package dev.quiblo.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.MetadataRefresh
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.SeriesDetails
import dev.quiblo.core.model.TitleMetadata
import dev.quiblo.source.api.SeriesDetailsResult
import dev.quiblo.source.api.SourceError
import kotlinx.coroutines.Job
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
        /**
         * The very first episode the provider lists, for "start from beginning".
         *
         * Null when the series has no episodes at all, which panels do occasionally return.
         * Distinct from [resumeEpisode] on purpose: starting a series over means episode
         * one, not the current episode from zero — that is what the episode list is for.
         */
        val firstEpisode: Episode? = null,
        /** From TMDB, when the user has enabled it and a match was found. */
        val metadata: TitleMetadata? = null,
        /**
         * What the last press of refresh did, or null.
         *
         * Kept in the state rather than raised as an event because it survives a rotation,
         * and a message about a request the viewer made is exactly the sort of thing that
         * should not vanish because the screen turned.
         */
        val refreshResult: MetadataRefresh? = null,
        /**
         * Whether a metadata key is configured.
         *
         * The refresh control is **absent** rather than disabled when it is false. `AC-META-01`
         * says nothing here issues a request without a key, so a button that cannot work is a
         * hollow control — and a greyed-out one still invites the press that does nothing.
         */
        val canRefreshMetadata: Boolean = false,
        /** True while a refresh is in flight, so the control can say so. */
        val isEnriching: Boolean = false,
        /**
         * Whether this series is favourited.
         *
         * Streamed rather than taken from [channel], which is a snapshot from when the
         * screen opened and would not reflect a tap made on this screen.
         */
        val isFavorite: Boolean = false,
    ) : SeriesDetailUiState
    data class Error(val error: SourceError) : SeriesDetailUiState
}

class SeriesDetailViewModel(
    private val channelId: Long,
    private val channelRepository: ChannelRepository,
    private val metadataRepository: TitleMetadataRepository,
    private val historyRepository: WatchHistoryRepository,
) : ViewModel() {

    private var isRefreshing = false

    private val _uiState = MutableStateFlow<SeriesDetailUiState>(SeriesDetailUiState.Loading)
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    private var favoriteJob: Job? = null

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
                    val watched = historyRepository.mostRecentlyWatched(episodes.map { it.streamUrl })
                    _uiState.value = SeriesDetailUiState.Success(
                        channel = channel,
                        details = result.details,
                        resumeEpisode = watched?.let { (key, _) -> episodes.firstOrNull { it.streamUrl == key } },
                        resumePositionMillis = watched?.second ?: 0L,
                        firstEpisode = episodes.firstOrNull(),
                        isFavorite = channel.isFavorite,
                    )

                    observeFavorite(channel)
                    enrich(channel)
                }
            }
        }
    }

    /**
     * Adds or removes this series from favourites.
     *
     * The detail screen is where the decision is made — the grid is where a title is
     * recognised, this is where it is read about — so the control belongs on both.
     */
    fun toggleFavorite() {
        val current = _uiState.value as? SeriesDetailUiState.Success ?: return
        viewModelScope.launch { channelRepository.toggleFavorite(current.channel) }
    }

    /**
     * Forgets every episode of this series.
     *
     * The whole series rather than the current episode, because that is what the button
     * says and what the tile it removes represents. Deleting only the last episode would
     * leave the series in "continue watching" at the episode before it, which looks like
     * the button did nothing.
     */
    fun removeFromHistory() {
        val current = _uiState.value as? SeriesDetailUiState.Success ?: return
        viewModelScope.launch {
            historyRepository.removeSeriesFromHistory(current.channel.stableKey)
            (_uiState.value as? SeriesDetailUiState.Success)?.let {
                _uiState.value = it.copy(resumeEpisode = null, resumePositionMillis = 0L)
            }
        }
    }

    /**
     * Re-reads the resume point, so coming back from playback moves the Resume button on.
     *
     * A full reload would go back to the provider for an episode list that has not changed
     * in the four minutes since it was fetched — the sort of request that gets an account
     * throttled. Only the position can have changed, so only the position is re-read.
     */
    fun refreshResumePosition() {
        val current = _uiState.value as? SeriesDetailUiState.Success ?: return
        viewModelScope.launch {
            val episodes = current.details.seasons.flatMap { it.episodes }
            val watched = historyRepository.mostRecentlyWatched(episodes.map { it.streamUrl })
            (_uiState.value as? SeriesDetailUiState.Success)?.let {
                _uiState.value = it.copy(
                    resumeEpisode = watched?.let { (key, _) -> episodes.firstOrNull { e -> e.streamUrl == key } },
                    resumePositionMillis = watched?.second ?: 0L,
                )
            }
        }
    }

    /**
     * Asks the metadata service about the series, if the user has enabled it.
     *
     * After the provider's own details rather than alongside them, and never awaited by
     * anything on screen: the episode list is the screen, and enrichment is decoration on
     * a screen that already works. Returns null when the feature is off, which is the
     * default.
     */
    private fun enrich(channel: Channel) {
        viewModelScope.launch {
            metadataRepository.load()
            val metadata = metadataRepository.forTitle(channel.name, MediaKind.SERIES)
            (_uiState.value as? SeriesDetailUiState.Success)?.let {
                _uiState.value = it.copy(
                    metadata = metadata,
                    // Read after load(), which is what fills the key from storage. Reading it
                    // before would report "no key" on every cold open of this screen.
                    canRefreshMetadata = metadataRepository.isEnabled,
                )
            }
        }
    }

    /**
     * Keeps the heart in step with the database for as long as the screen is open.
     *
     * The previous collector is cancelled first because [loadDetails] is the retry button:
     * without this, every failed load a user retries past leaves another collector running
     * on the same flow.
     */
    private fun observeFavorite(channel: Channel) {
        favoriteJob?.cancel()
        favoriteJob = viewModelScope.launch {
            channelRepository.observeIsFavorite(channel).collect { isFavorite ->
                (_uiState.value as? SeriesDetailUiState.Success)?.let {
                    _uiState.value = it.copy(isFavorite = isFavorite)
                }
            }
        }
    }

    /**
     * Asks the metadata service about this title again, ignoring what is cached.
     *
     * `INC-F7`. The reason the intake asks for it is artwork that never arrived or a plot that
     * is out of date, so **the outcome has to be visible**: the poster changes, or a message
     * says what happened. A button whose only feedback is that the screen looks the same is
     * indistinguishable from a button that does nothing.
     *
     * A refusal leaves the existing record alone — that is the repository's promise, and this
     * reports it rather than blanking the screen.
     */
    fun refreshMetadata() {
        val channel = (_uiState.value as? SeriesDetailUiState.Success)?.channel ?: return
        if (isRefreshing) return
        isRefreshing = true

        viewModelScope.launch {
            update { it.copy(isEnriching = true, refreshResult = null) }
            val outcome = metadataRepository.refresh(channel.name, MediaKind.SERIES)
            update {
                it.copy(
                    isEnriching = false,
                    refreshResult = outcome,
                    metadata = (outcome as? MetadataRefresh.Updated)?.metadata ?: it.metadata,
                )
            }
            isRefreshing = false
        }
    }

    /** Dismisses the message the refresh left behind, so it does not outlive the screen. */
    fun dismissRefreshResult() {
        update { it.copy(refreshResult = null) }
    }

    private fun update(block: (SeriesDetailUiState.Success) -> SeriesDetailUiState.Success) {
        (_uiState.value as? SeriesDetailUiState.Success)?.let { _uiState.value = block(it) }
    }
}
