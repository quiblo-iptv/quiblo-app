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

package dev.quiblo.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.quiblo.core.data.AttachResult
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.SubtitleRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.media.PlayableItem
import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.PlayerController
import dev.quiblo.core.model.AspectRatioMode
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SubtitleFile
import dev.quiblo.core.model.SubtitleOrigin
import dev.quiblo.source.api.VodDetailsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the player for one playback session.
 *
 * The controller is released in [onCleared], so leaving the screen frees the decoder
 * rather than leaving it running behind the browse UI (AC-PLAY-09).
 *
 * Note what this class does not import: nothing from Media3 or ExoPlayer. It talks only
 * to [PlayerController] (docs/FREEZE.md §4.4).
 */
class PlayerViewModel(
    private val controller: PlayerController,
    private val channelRepository: ChannelRepository,
    private val historyRepository: WatchHistoryRepository,
    private val subtitleRepository: SubtitleRepository,
    settingsRepository: PlayerSettingsRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = controller.state

    /**
     * The persisted tuning, pushed into the engine as it changes.
     *
     * The UI needs it too — the skip buttons are labelled with the interval — so this is
     * one flow feeding both rather than the screen reading the store separately and the
     * two drifting apart.
     */
    val settings: StateFlow<PlayerSettings> = settingsRepository.settings
        .onEach(controller::applySettings)
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerSettings())

    /**
     * How the video is fitted to the screen.
     *
     * Deliberately not persisted. It is a response to how one particular stream is framed —
     * a 4:3 channel pillarboxed inside a 16:9 transport, say — so carrying the choice over
     * to the next channel would be wrong more often than right.
     */
    private val _aspectRatioMode = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatioMode: StateFlow<AspectRatioMode> = _aspectRatioMode.asStateFlow()

    private var loadedChannelId: Long? = null

    /**
     * What was last handed to the engine.
     *
     * Kept because attaching a subtitle file means preparing the same item again with one more
     * track on it, and the alternative — reconstructing it from the arguments [load] was called
     * with — is a second copy of that construction waiting to drift from the first.
     */
    private var prepared: PlayableItem? = null

    /**
     * The outcome of the last attempt to attach a subtitle file, for the screen to say something
     * about. Null once it has been said.
     */
    private val _subtitleNotice = MutableStateFlow<SubtitleNotice?>(null)
    val subtitleNotice: StateFlow<SubtitleNotice?> = _subtitleNotice.asStateFlow()

    /**
     * What is playing, in the terms history is recorded in.
     *
     * Held here rather than read back at save time because saving happens on the way out,
     * when the screen is already going and a database read racing teardown is the kind of
     * thing that silently loses the last position of every session.
     */
    private var playing: PlayingItem? = null

    /**
     * The item being played, described well enough to write a history row.
     *
     * @property seriesStableKey set only for an episode, whose own identity is its stream
     *   URL and which therefore has no row of its own to be looked up from.
     */
    private data class PlayingItem(
        val sourceId: Long,
        val kind: MediaKind,
        val title: String,
        val artworkUrl: String?,
        val seriesStableKey: String? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
    )

    fun cycleAspectRatio() {
        val modes = AspectRatioMode.entries
        _aspectRatioMode.value = modes[(_aspectRatioMode.value.ordinal + 1) % modes.size]
    }

    /** Skips by the configured interval, clamped so a rewind cannot run past the start. */
    fun skipBy(direction: Int) {
        val delta = settings.value.seekInterval.millis * direction
        controller.seekTo((state.value.positionMillis + delta).coerceAtLeast(0L))
    }

    /**
     * Loads the channel with [channelId] if it is not already loaded, or prepares custom stream details.
     *
     * Guarded so a recomposition, or a rotation that re-runs the effect, does not restart
     * a stream that is already playing (AC-PLAY-07).
     */
    @Suppress("LongParameterList")
    fun load(
        channelId: Long,
        customUrl: String? = null,
        customTitle: String? = null,
        startPositionMillis: Long? = null,
        /**
         * Which episode this is, when it is one.
         *
         * Carried from the series screen rather than derived here, because an episode's
         * stream URL says nothing about where it sits in a run and the player never sees
         * the episode list it came from.
         */
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) {
        if (loadedChannelId == channelId && customUrl == null) return
        loadedChannelId = channelId

        viewModelScope.launch {
            val channel = channelRepository.findById(channelId) ?: return@launch
            val playUrl = customUrl ?: channel.streamUrl
            val playTitle = customTitle ?: channel.name
            val isEpisode = customUrl != null && channel.kind == MediaKind.SERIES
            playing = PlayingItem(
                sourceId = channel.sourceId,
                kind = channel.kind,
                // The series' name, not the episode's: history lists titles, and the
                // episode is named by its season and number underneath.
                title = channel.name,
                artworkUrl = channel.logoUrl,
                seriesStableKey = channel.stableKey.takeIf { isEpisode },
                seasonNumber = seasonNumber.takeIf { isEpisode },
                episodeNumber = episodeNumber.takeIf { isEpisode },
            )
            val playbackKey = customUrl ?: channel.stableKey
            prepare(
                PlayableItem(
                    id = playbackKey,
                    title = playTitle,
                    url = playUrl,
                    isLive = channel.kind == MediaKind.LIVE,
                    // An explicit position wins over the saved one, which is what makes
                    // "Start from beginning" different from "Resume" for an item that has
                    // a resume point. Live has no meaningful position either way.
                    startPositionMillis = when {
                        channel.kind == MediaKind.LIVE -> 0L
                        startPositionMillis != null -> startPositionMillis
                        else -> historyRepository.resumePosition(playbackKey)
                    },
                    subtitles = subtitlesFor(channel.kind, channelId, playbackKey),
                ),
            )
        }
    }

    /**
     * Every sidecar subtitle this title has: the panel's, then the viewer's own (INC-F10).
     *
     * **The details call is made for a film and for nothing else.** It is one request, on an
     * explicit press of play rather than on a scroll, and it is usually already cached by the
     * screen the viewer pressed play from. Live has no details call at all, and an episode's
     * subtitles are not something `get_series_info` carries.
     */
    private suspend fun subtitlesFor(
        kind: MediaKind,
        channelId: Long,
        playbackKey: String,
    ): List<SubtitleFile> {
        val fromPanel = if (kind == MediaKind.VOD) {
            (channelRepository.getVodDetails(channelId) as? VodDetailsResult.Success)
                ?.details
                ?.subtitles
                .orEmpty()
        } else {
            emptyList()
        }
        return fromPanel + subtitleRepository.forTitle(playbackKey)
    }

    /**
     * Copies the file at [pickedUri] into the app and restarts this item with it loaded.
     *
     * A restart, because a subtitle track cannot be added to something already playing: the
     * engine takes them as part of the media item. It resumes at the position it was at, so what
     * a viewer sees is a moment of buffering rather than a film starting again.
     */
    fun attachSubtitleFile(pickedUri: String) {
        val item = prepared ?: return
        viewModelScope.launch {
            when (val result = subtitleRepository.attach(item.id, pickedUri)) {
                is AttachResult.Attached -> {
                    prepare(
                        item.copy(
                            subtitles = item.subtitles.filterNot { it.origin == SubtitleOrigin.PICKED } +
                                result.subtitle,
                            startPositionMillis = state.value.positionMillis,
                        ),
                    )
                    _subtitleNotice.value = SubtitleNotice.ATTACHED
                }

                AttachResult.NotSubtitles -> _subtitleNotice.value = SubtitleNotice.NOT_SUBTITLES
                AttachResult.TooLarge -> _subtitleNotice.value = SubtitleNotice.TOO_LARGE
                AttachResult.Unreadable -> _subtitleNotice.value = SubtitleNotice.UNREADABLE
            }
        }
    }

    /** Forgets the picked file and restarts without it. The panel's own subtitles stay. */
    fun detachSubtitleFile() {
        val item = prepared ?: return
        viewModelScope.launch {
            subtitleRepository.detach(item.id)
            prepare(
                item.copy(
                    subtitles = item.subtitles.filterNot { it.origin == SubtitleOrigin.PICKED },
                    startPositionMillis = state.value.positionMillis,
                ),
            )
        }
    }

    /**
     * Says something about subtitles, or stops saying it.
     *
     * One method for both because the screen does both from the same place: it shows what came
     * back, waits, and clears it. [SubtitleNotice.NO_PICKER] is set from the screen rather than
     * from here, because the absence of a file picker is something only the screen can discover.
     */
    fun showSubtitleNotice(notice: SubtitleNotice?) {
        _subtitleNotice.value = notice
    }

    private fun prepare(item: PlayableItem) {
        prepared = item
        controller.prepare(item)
    }

    fun togglePlayPause() {
        if (state.value.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMillis: Long) = controller.seekTo(positionMillis)

    fun retry() = controller.retry()

    /**
     * Selects whichever kind the menu asked for.
     *
     * One entry point rather than a `when` at each call site: both apps draw the same menu and
     * would otherwise each write the same two-branch mapping, which is how the two frontends
     * come to disagree about a thing neither of them decided. It is also the only entry point —
     * the per-kind methods this replaced had no caller outside it.
     */
    fun selectTrack(kind: TrackMenuKind, trackId: String?) = when (kind) {
        TrackMenuKind.AUDIO -> controller.selectAudioTrack(trackId)
        TrackMenuKind.SUBTITLES -> controller.selectTextTrack(trackId)
    }

    fun controllerHandle(): PlayerController = controller

    /** Stops playback when the screen leaves the foreground, so no audio leaks. */
    fun onStopped() {
        controller.pause()
        rememberPosition()
    }

    override fun onCleared() {
        rememberPosition()
        controller.release()
        super.onCleared()
    }

    /**
     * Persists the VOD resume point and the history entry beside it (AC-PLAY-03).
     *
     * Live has no meaningful position and is never recorded — a channel is not something
     * anyone continues, and putting one in "continue watching" would fill the row with
     * things that cannot be resumed.
     */
    private fun rememberPosition() {
        val entry = currentHistoryEntry() ?: return
        viewModelScope.launch { historyRepository.saveProgress(entry) }
    }

    /**
     * What is playing as a history entry, or null when there is nothing worth remembering.
     *
     * Null covers all three cases together — nothing loaded, a live channel, a position of
     * zero — because the caller's response to each is identical, and separating them would
     * imply an order of precedence that does not exist.
     */
    private fun currentHistoryEntry(): HistoryEntry? {
        val current = state.value
        val item = current.item?.takeIf { !it.isLive && current.positionMillis > 0L }
        val playing = playing
        if (item == null || playing == null) return null

        return HistoryEntry(
            stableKey = item.id,
            sourceId = playing.sourceId,
            kind = playing.kind,
            title = playing.title,
            artworkUrl = playing.artworkUrl,
            positionMillis = current.positionMillis,
            // Zero until the engine has read the container. A history tile draws no progress
            // bar rather than an empty one when that is all we have.
            durationMillis = current.durationMillis.coerceAtLeast(0L),
            seriesStableKey = playing.seriesStableKey,
            seasonNumber = playing.seasonNumber,
            episodeNumber = playing.episodeNumber,
        )
    }
}
