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

package dev.vibrato.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vibrato.core.data.ChannelRepository
import dev.vibrato.core.data.PlayerSettingsRepository
import dev.vibrato.core.media.PlayableItem
import dev.vibrato.core.media.PlaybackState
import dev.vibrato.core.media.PlayerController
import dev.vibrato.core.model.AspectRatioMode
import dev.vibrato.core.model.MediaKind
import dev.vibrato.core.model.PlayerSettings
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
    fun load(channelId: Long, customUrl: String? = null, customTitle: String? = null) {
        if (loadedChannelId == channelId && customUrl == null) return
        loadedChannelId = channelId

        viewModelScope.launch {
            val channel = channelRepository.findById(channelId) ?: return@launch
            val playUrl = customUrl ?: channel.streamUrl
            val playTitle = customTitle ?: channel.name
            controller.prepare(
                PlayableItem(
                    id = customUrl ?: channel.stableKey,
                    title = playTitle,
                    url = playUrl,
                    isLive = channel.kind == MediaKind.LIVE,
                    startPositionMillis = if (channel.kind == MediaKind.LIVE) {
                        0L
                    } else {
                        channelRepository.resumePosition(customUrl ?: channel.stableKey)
                    },
                ),
            )
        }
    }

    fun togglePlayPause() {
        if (state.value.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMillis: Long) = controller.seekTo(positionMillis)

    fun retry() = controller.retry()

    fun selectAudioTrack(trackId: String?) = controller.selectAudioTrack(trackId)

    fun selectTextTrack(trackId: String?) = controller.selectTextTrack(trackId)

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

    /** Persists the VOD resume point (AC-PLAY-03). Live has no meaningful position. */
    private fun rememberPosition() {
        val current = state.value
        val item = current.item ?: return
        if (item.isLive || current.positionMillis <= 0L) return

        viewModelScope.launch {
            channelRepository.saveResumePosition(item.id, current.positionMillis)
        }
    }
}
