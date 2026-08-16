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
import dev.quiblo.core.data.MetadataRefresh
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.WatchHistoryRepository
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
    private val historyRepository: WatchHistoryRepository,
) : ViewModel() {

    private var isRefreshing = false

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

    /**
     * Forgets this film.
     *
     * The resume point goes with it, because they are the same record: someone removing a
     * film from "continue watching" is saying they are not going to continue it, and
     * leaving a hidden position behind so that pressing Play silently resumes half an hour
     * in would be the opposite of what they asked for.
     */
    fun removeFromHistory() {
        val current = _uiState.value as? MovieDetailUiState.Ready ?: return
        viewModelScope.launch {
            historyRepository.removeFromHistory(current.channel.stableKey)
            (_uiState.value as? MovieDetailUiState.Ready)?.let {
                _uiState.value = it.copy(resumePositionMillis = 0L)
            }
        }
    }

    /**
     * Watches the resume point, so returning from playback updates the buttons — whenever it lands.
     *
     * **This used to be a single read from a lifecycle effect, and that was a race it lost.** The
     * effect fires the instant this screen returns to the foreground, which on the television is
     * the same instant the player above it is being disposed and is writing the position it
     * finished with. Nothing ordered the two. When the read won, the screen offered **Play** for a
     * film the viewer was four minutes into — and, having read once, it never asked again, so the
     * only way to correct it was to leave the screen and come back.
     *
     * A flow does not order the two either. It removes the need to: a write that lands a hundred
     * milliseconds late still lands, and the button follows it.
     */
    private fun observeResumePosition(channel: Channel) {
        viewModelScope.launch {
            historyRepository.observeResumePosition(channel.stableKey).collect { position ->
                (_uiState.value as? MovieDetailUiState.Ready)?.let {
                    _uiState.value = it.copy(resumePositionMillis = position)
                }
            }
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
                resumePositionMillis = historyRepository.resumePosition(channel.stableKey),
                isFavorite = channel.isFavorite,
                isEnriching = true,
            )

            observeFavorite(channel)
            observeResumePosition(channel)

            val details = (channelRepository.getVodDetails(channelId) as? VodDetailsResult.Success)?.details
            (_uiState.value as? MovieDetailUiState.Ready)?.let { _uiState.value = it.copy(details = details) }

            // Fetched after the provider's own details rather than alongside: the screen is
            // already complete without it, and enrichment must never be what a viewer waits
            // for. Returns null when the feature is off, which is the default.
            metadataRepository.load()
            val metadata = metadataRepository.forTitle(channel.name, MediaKind.VOD)
            (_uiState.value as? MovieDetailUiState.Ready)?.let {
                _uiState.value = it.copy(
                    metadata = metadata,
                    isEnriching = false,
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
        val channel = (_uiState.value as? MovieDetailUiState.Ready)?.channel ?: return
        if (isRefreshing) return
        isRefreshing = true

        viewModelScope.launch {
            update { it.copy(isEnriching = true, refreshResult = null) }
            val outcome = metadataRepository.refresh(channel.name, MediaKind.VOD)
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

    private fun update(block: (MovieDetailUiState.Ready) -> MovieDetailUiState.Ready) {
        (_uiState.value as? MovieDetailUiState.Ready)?.let { _uiState.value = block(it) }
    }
}
