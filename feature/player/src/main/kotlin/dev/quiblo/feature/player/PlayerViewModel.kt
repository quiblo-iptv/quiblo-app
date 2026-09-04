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
import dev.quiblo.core.data.ApplicationScope
import dev.quiblo.core.data.AttachResult
import dev.quiblo.core.data.ChannelRepository
import dev.quiblo.core.data.PlayerSettingsRepository
import dev.quiblo.core.data.SubtitleRepository
import dev.quiblo.core.data.WatchEventRepository
import dev.quiblo.core.data.WatchHistoryRepository
import dev.quiblo.core.media.PlayableItem
import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.media.PlaybackStatus
import dev.quiblo.core.media.PlayerController
import dev.quiblo.core.model.AspectRatioMode
import dev.quiblo.core.model.HistoryEntry
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SubtitleFile
import dev.quiblo.core.model.SubtitleOrigin
import dev.quiblo.core.model.SubtitleStyle
import dev.quiblo.core.model.WatchOrigin
import dev.quiblo.source.api.VodDetailsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
// Seven collaborators, and the seventh is the watch log — a different question from the resume
// point beside it: one is "where was I", the other is "what did I choose, and how often". Merging
// them into one repository would put two tables with two lifetimes behind one name.
@Suppress("LongParameterList")
class PlayerViewModel(
    private val controller: PlayerController,
    private val channelRepository: ChannelRepository,
    private val historyRepository: WatchHistoryRepository,
    private val subtitleRepository: SubtitleRepository,
    private val settingsRepository: PlayerSettingsRepository,
    /**
     * Where the resume point is written, and deliberately not [viewModelScope].
     *
     * On the phone this ViewModel belongs to its navigation entry, so the back press that makes a
     * resume point worth writing is the same event that cancels the coroutine writing it. See
     * [ApplicationScope].
     */
    private val applicationScope: ApplicationScope,
    private val watchEvents: WatchEventRepository,
) : ViewModel() {

    /** Where the viewer was when they chose what is playing. Set by [load]. */
    private var chosenFrom: WatchOrigin = WatchOrigin.ROW

    /** Whether this sitting has already been written down. See [recordOccasion]. */
    private var recorded = false

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

    /**
     * The playback key of whatever [load] last accepted.
     *
     * The identity of *what is playing*, not of the row it came from. The guard used to be a
     * channel id plus "and no custom URL", which meant it stopped guarding for the one case
     * that needs it most: an episode is always played with a custom URL, so re-entering the
     * screen — a configuration change the activity does not declare, a restore after process
     * death, navigating back into the player — re-ran [load] and restarted the episode from
     * the position it originally opened at, discarding both the viewer's progress and any
     * subtitle they had attached. A key covers both cases, because a plain channel's key is
     * its stable key and an episode's is its URL.
     */
    private var loadedRequest: LoadRequest? = null

    /**
     * What identifies one request to play something.
     *
     * All three parts matter. The channel is the row; the custom URL is which episode of it, if
     * any; and the start position is what separates "Resume" from "Start from beginning" for the
     * same item. Every one of them is a navigation argument, so this value is stable across a
     * screen being recreated and different whenever the viewer actually asked for something else.
     */
    private data class LoadRequest(
        val channelId: Long,
        val customUrl: String?,
        val startPositionMillis: Long?,
    )

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
     * a stream that is already playing (AC-PLAY-07). The guard is on the whole request — see
     * [LoadRequest] — because guarding on the channel id alone silently exempted every episode.
     */
    @Suppress("LongParameterList")
    fun load(
        channelId: Long,
        customUrl: String? = null,
        customTitle: String? = null,
        startPositionMillis: Long? = null,
        /** Where the viewer was when they chose this. Recorded once, when playback ends. */
        origin: WatchOrigin = WatchOrigin.ROW,
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
        val request = LoadRequest(channelId, customUrl, startPositionMillis)
        if (loadedRequest == request) return
        loadedRequest = request
        chosenFrom = origin
        recorded = false

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

    /**
     * How subtitles are drawn, pushed at the renderer as it changes (INC-F11).
     *
     * Eager, and separate from [settings], because this one has to reach the view the moment it
     * is edited — the viewer is looking at the effect while they choose it.
     */
    val subtitleStyle: StateFlow<SubtitleStyle> = settingsRepository.subtitleStyle
        .onEach(controller::applySubtitleStyle)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleStyle())

    /**
     * Whether a paused player lets the screen dim (`FEAT-032`).
     *
     * On unless switched off, and the initial value says so rather than starting `false`: a
     * player that holds the screen on for the first frame after a pause and releases it a moment
     * later is a panel that flickers back to full brightness for no reason.
     */
    val dimWhilePaused: StateFlow<Boolean> = settingsRepository.dimWhilePaused
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Whether this window should still be declared as being watched (`FEAT-032`).
     *
     * Combined here rather than in either player, so the two apps cannot drift on what counts as
     * watching. Buffering does: a stream that is loading is one somebody is waiting for. Only a
     * deliberate pause releases the screen, and only when the viewer has left the setting on.
     */
    val keepScreenAwake: StateFlow<Boolean> =
        combine(state, settingsRepository.dimWhilePaused) { playback, dim ->
            !(dim && playback.status == PlaybackStatus.PAUSED)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Whether the player lights its black bars with the colours of the picture.
     *
     * On unless switched off, and the default here matches the store's rather than restating it
     * as `false` — a screen that starts with the feature off and turns it on a frame later is a
     * flash of black bars at the start of every film.
     *
     * Off means the surface is never sampled at all. Same rule as the live-in-search switch: a
     * feature switched off does not do its work and throw the answer away.
     */
    val ambientPlayer: StateFlow<Boolean> = settingsRepository.ambientPlayer
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
    fun selectTrack(kind: TrackMenuKind, trackId: String?) {
        when (kind) {
            TrackMenuKind.AUDIO -> controller.selectAudioTrack(trackId)
            TrackMenuKind.SUBTITLES -> controller.selectTextTrack(trackId)
            TrackMenuKind.SUBTITLE_SIZE,
            TrackMenuKind.SUBTITLE_TEXT_COLOUR,
            TrackMenuKind.SUBTITLE_BACKGROUND,
            -> changeSubtitleStyle(kind, trackId)
        }
    }

    /**
     * Applies one appearance choice (INC-F11).
     *
     * The mapping itself is [withChoice] — a plain function, tested as one, because which row
     * changes what is the whole of this feature's behaviour and none of it needs a player.
     */
    private fun changeSubtitleStyle(kind: TrackMenuKind, id: String?) {
        val next = subtitleStyle.value.withChoice(kind, id) ?: return
        viewModelScope.launch { settingsRepository.setSubtitleStyle(next) }
    }

    fun controllerHandle(): PlayerController = controller

    /** Stops playback when the screen leaves the foreground, so no audio leaks. */
    fun onStopped() {
        controller.pause()
        rememberPosition()
        recordOccasion()
    }

    override fun onCleared() {
        rememberPosition()
        recordOccasion()
        controller.release()
        super.onCleared()
    }

    /**
     * Writes down that this was watched, once per sitting.
     *
     * **Once**, which is what makes "how many times" mean how many times rather than how long: a
     * row per position write would turn a two-hour film into seven hundred viewings. The guard is
     * cleared by [load], so opening the same title again tomorrow is a second occasion and opening
     * it twice in one screen's lifetime is not.
     *
     * Live is never recorded, for the same reason it is never given a resume point: a channel is
     * not something anybody continues, and a suggestion built from one would be a suggestion built
     * from what happened to be on.
     */
    @Suppress("ReturnCount")
    private fun recordOccasion() {
        if (recorded) return
        val current = state.value
        val item = current.item?.takeIf { !it.isLive } ?: return
        val playing = playing ?: return
        val fraction = if (current.durationMillis > 0L) {
            (current.positionMillis.toDouble() / current.durationMillis).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        if (fraction <= 0.0 && current.positionMillis <= 0L) return

        recorded = true
        applicationScope.launch {
            watchEvents.record(
                sourceId = playing.sourceId,
                stableKey = item.id,
                kind = playing.kind,
                title = playing.title,
                fraction = fraction,
                origin = chosenFrom,
            )
        }
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
        applicationScope.launch { historyRepository.saveProgress(entry) }
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

    /*
     * A resume point every ten seconds of playback, rather than only when playback stops.
     *
     * Stop and dispose were the two moments a position was written, and both assume the app gets
     * to run afterwards. A process killed for memory while a film is paused, a television switched
     * off at the wall, a crash: each of those lost everything since the title was opened, which on
     * a two-hour film is up to two hours of "where was I".
     *
     * **Driven by the position rather than by a timer**, and that is the whole of why there is no
     * clock here. A ten-second timer keeps running while a film is paused, while a viewer reads a
     * description, and for as long as the screen exists — writing the same number to the database
     * over and over. Watching the position cross a ten-second boundary writes exactly when there
     * is something new to say and stops on its own when nothing is moving.
     *
     * `rememberPosition` returns immediately for live, for nothing loaded, and for a position
     * still at zero, so the first crossing of a fresh item costs nothing.
     *
     * **This block is last in the class, and that is load-bearing.** `viewModelScope` dispatches on
     * `Dispatchers.Main.immediate`, which on the main thread runs the body *now* rather than after
     * the constructor returns — so an `init` placed above a property it reads collects a null and
     * takes the app down the moment anything is played. It was placed above `state`, and it did.
     * Kotlin runs initialisers in declaration order; anything this touches has to be declared
     * before it. `PlayerStartsCollectingImmediatelyTest` is the guard, and it fails when this block
     * moves back up.
     */
    init {
        viewModelScope.launch {
            controller.state.map { it.positionMillis / PERSIST_INTERVAL_MILLIS }
                .distinctUntilChanged()
                .collect { rememberPosition() }
        }
    }

    private companion object {
        /**
         * How often a position is written down while something is playing.
         *
         * Ten seconds of *playback*, not of wall clock: the position is what is watched, so a
         * paused film writes nothing and a film left open on a details screen writes nothing.
         * Ten is the most a viewer can lose to a process that never gets to run its shutdown, and
         * it is less than the time it takes to notice. The in-memory position already ticks every
         * 500ms; this is about how often it reaches the database.
         */
        const val PERSIST_INTERVAL_MILLIS = 10_000L
    }
}
