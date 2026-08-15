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

package dev.quiblo.core.data

import dev.quiblo.core.datastore.PlayerSettingsStore
import dev.quiblo.core.model.Appearance
import dev.quiblo.core.model.AutoNextDelay
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SeekInterval
import dev.quiblo.core.model.SubtitleStyle
import dev.quiblo.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Player tuning, for the settings screen to edit and the player to read.
 *
 * A thin pass-through over [PlayerSettingsStore] on purpose. Features talk to `:core:data`
 * and nothing else (docs/PLAN.md §2), and honouring that for a small case is what stops
 * the rule eroding: the first feature to reach past it makes the second one reasonable.
 */
class PlayerSettingsRepository(private val store: PlayerSettingsStore) {

    /** Emits on every change, so a player already on screen picks up an edit immediately. */
    val settings: Flow<PlayerSettings> = store.settings

    suspend fun setSeekInterval(value: SeekInterval) = store.setSeekInterval(value)

    suspend fun setBufferMode(value: BufferMode) = store.setBufferMode(value)

    suspend fun setMaxBitrate(value: MaxBitrateCap) = store.setMaxBitrate(value)

    suspend fun setAutoNextDelay(value: AutoNextDelay) = store.setAutoNextDelay(value)

    val appearance: Flow<Appearance> = store.appearance

    suspend fun setThemeMode(value: ThemeMode) = store.setThemeMode(value)

    suspend fun setDynamicColor(enabled: Boolean) = store.setDynamicColor(enabled)

    /**
     * Whether advanced search offers a row of live channels. Off unless the viewer asks.
     *
     * Here rather than in a repository of its own because it is one boolean in the same file as
     * the rest of the ordinary preferences, and a class that forwarded a single flow would be a
     * layer that only forwards.
     */
    val showLiveInSearch: Flow<Boolean> = store.showLiveInSearch

    suspend fun setShowLiveInSearch(enabled: Boolean) = store.setShowLiveInSearch(enabled)

    /**
     * How subtitles are drawn (INC-F11).
     *
     * Read by the player and written from inside it, where the effect is visible. There is no
     * copy of this on the settings screen: a caption colour chosen against a grey card is a
     * guess, and the same choice made over the film it will sit on is not.
     */
    val subtitleStyle: Flow<SubtitleStyle> = store.subtitleStyle

    suspend fun setSubtitleStyle(value: SubtitleStyle) = store.setSubtitleStyle(value)
}
