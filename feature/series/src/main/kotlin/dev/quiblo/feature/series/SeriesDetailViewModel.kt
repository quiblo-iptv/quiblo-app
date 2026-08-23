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
import dev.quiblo.core.data.SeriesPreference
import dev.quiblo.core.data.SeriesPreferenceRepository
import dev.quiblo.core.data.TitleMetadataRepository
import dev.quiblo.core.data.TitleOpinionRepository
import dev.quiblo.core.data.TitleVersion
import dev.quiblo.core.data.TitleVersionsRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.data.arrangedBy
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Episode
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.Opinion
import dev.quiblo.core.model.Season
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
        /**
         * How this viewer reads this series: merged or by season, newest first or oldest.
         *
         * `INC-F6`. Held in the state rather than read by the screen so that the arrangement
         * and the seasons it produced can never disagree — they are recomputed together.
         */
        val preference: SeriesPreference = SeriesPreference(),
        /**
         * The seasons as they should be drawn, already arranged.
         *
         * Separate from `details.seasons`, which stays the provider's own answer. A screen that
         * sorted on every recomposition would sort a thousand episodes on every frame, and a
         * screen that sorted in place would lose the original order it needs to go back to.
         */
        val seasons: List<Season> = emptyList(),
        /** True while a refresh is in flight, so the control can say so. */
        val isEnriching: Boolean = false,
        /**
         * Whether this series is favourited.
         *
         * Streamed rather than taken from [channel], which is a snapshot from when the
         * screen opened and would not reflect a tap made on this screen.
         */
        val isFavorite: Boolean = false,
        /** What this viewer said about the series, if anything. See `TitleOpinionRepository`. */
        val opinion: Opinion = Opinion.NONE,
        /**
         * The other ways the provider lists this series — the 4K copy, the subtitled cut.
         *
         * Empty unless the merge setting is on and there is more than one, for the reason
         * `TitleVersionsRepository` gives.
         */
        val versions: List<TitleVersion> = emptyList(),
    ) : SeriesDetailUiState
    data class Error(val error: SourceError) : SeriesDetailUiState
}

// One repository per question this screen answers — the catalogue, the metadata, the resume
// point, how this viewer reads a series, what they thought of it, and which copies of it the
// provider carries. Bundling any two of them would be a type that exists to satisfy a count.
@Suppress("LongParameterList")
class SeriesDetailViewModel(
    private val channelId: Long,
    private val channelRepository: ChannelRepository,
    private val metadataRepository: TitleMetadataRepository,
    private val historyRepository: WatchHistoryRepository,
    private val preferences: SeriesPreferenceRepository,
    private val opinions: TitleOpinionRepository,
    private val versions: TitleVersionsRepository,
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
                        // The provider's own order until the preference arrives, which is one
                        // frame later. Leaving it empty would flash an episode-less screen.
                        seasons = result.details.seasons,
                    )

                    observeFavorite(channel)
                    observeOpinion(channel)
                    observeVersions(channel)
                    observeResumePosition(episodes.map { it.streamUrl })
                    observePreference(channel.stableKey)
                    enrich(channel)
                }
            }
        }
    }

    /**
     * Records what the viewer thought of the series, or takes it back.
     *
     * The series rather than the episode: nobody has an opinion about episode four of season two
     * separately from the show, and a thumbs-down on one episode that removed the whole series
     * from suggestions would be answering a question that was not asked.
     */
    fun rate(opinion: Opinion) {
        val current = _uiState.value as? SeriesDetailUiState.Success ?: return
        viewModelScope.launch {
            val next = if (current.opinion == opinion) Opinion.NONE else opinion
            opinions.set(current.channel.name, MediaKind.SERIES, next)
        }
    }

    /** The other listings of this series, watched for as long as the screen is open. */
    private fun observeVersions(channel: Channel) {
        viewModelScope.launch {
            versions.observeVersions(channel).collect { found ->
                (_uiState.value as? SeriesDetailUiState.Success)?.let {
                    _uiState.value = it.copy(versions = found)
                }
            }
        }
    }

    private fun observeOpinion(channel: Channel) {
        viewModelScope.launch {
            opinions.observe(channel.name).collect { opinion ->
                (_uiState.value as? SeriesDetailUiState.Success)?.let {
                    _uiState.value = it.copy(opinion = opinion)
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
     * Watches the resume point, so coming back from playback moves the Resume button on.
     *
     * Only the position, never the episode list: a full reload would go back to the provider for
     * a list that has not changed in the four minutes since it was fetched, which is the sort of
     * request that gets an account throttled.
     *
     * **A flow rather than a read on returning to the foreground**, and that is the fix for the
     * defect where backing out of an episode offered **Play** for something four minutes in. The
     * screen used to read once, from a lifecycle effect that fires at the same moment the player
     * above it is being disposed and writing the position it finished with — and this screen's
     * read is the heavier of the two, over every episode's key, which widened the window it lost
     * in. Nothing here waits on that ordering now.
     */
    private fun observeResumePosition(episodeKeys: List<String>) {
        viewModelScope.launch {
            historyRepository.observeMostRecentlyWatched(episodeKeys).collect { watched ->
                (_uiState.value as? SeriesDetailUiState.Success)?.let { current ->
                    val episodes = current.details.seasons.flatMap { it.episodes }
                    _uiState.value = current.copy(
                        resumeEpisode = watched?.let { (key, _) -> episodes.firstOrNull { it.streamUrl == key } },
                        resumePositionMillis = watched?.second ?: 0L,
                    )
                }
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

    /**
     * Follows this viewer's arrangement for this series, and re-arranges when it changes.
     *
     * Collected for as long as the screen is open rather than read once, because switching
     * profile while a series is open should show that person's arrangement — the preference is
     * theirs, not the screen's.
     */
    private fun observePreference(seriesKey: String) {
        viewModelScope.launch {
            preferences.observe(seriesKey).collect { preference ->
                (_uiState.value as? SeriesDetailUiState.Success)?.let {
                    _uiState.value = it.copy(
                        preference = preference,
                        seasons = it.details.seasons.arrangedBy(preference),
                    )
                }
            }
        }
    }

    /** Merge every season into one list, or put them back. */
    fun setMerged(isMerged: Boolean) = updatePreference { it.copy(isMerged = isMerged) }

    /** Newest episode first, or oldest. */
    fun setDescending(isDescending: Boolean) = updatePreference { it.copy(isDescending = isDescending) }

    private fun updatePreference(block: (SeriesPreference) -> SeriesPreference) {
        val state = _uiState.value as? SeriesDetailUiState.Success ?: return
        viewModelScope.launch {
            preferences.set(state.channel.stableKey, block(state.preference))
        }
    }
}
