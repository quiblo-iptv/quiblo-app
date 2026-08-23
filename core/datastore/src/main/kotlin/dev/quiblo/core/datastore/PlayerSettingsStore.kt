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

package dev.quiblo.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.quiblo.core.common.TitleScript
import dev.quiblo.core.model.Appearance
import dev.quiblo.core.model.AutoNextDelay
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.SeekInterval
import dev.quiblo.core.model.SubtitleColor
import dev.quiblo.core.model.SubtitleOpacity
import dev.quiblo.core.model.SubtitleStyle
import dev.quiblo.core.model.SubtitleTextSize
import dev.quiblo.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "player_settings",
)

/**
 * Persists the player tuning from the settings screen.
 *
 * Values are stored as enum *names*, not ordinals. An ordinal silently changes meaning the
 * moment someone reorders or inserts an entry, and the user's saved choice would quietly
 * become a different one. A name that no longer exists falls back to the default instead,
 * which is the only safe reading of a value this code no longer understands.
 *
 * Nothing here is sensitive, so this is plain `DataStore` rather than the encrypted store
 * credentials use (AC-XT-04).
 */
class PlayerSettingsStore(context: Context) {

    private val dataStore = context.applicationContext.playerSettingsDataStore

    val settings: Flow<PlayerSettings> = dataStore.data.map { preferences ->
        PlayerSettings(
            seekInterval = preferences.readEnum(SEEK_INTERVAL, SeekInterval.entries, PlayerSettings().seekInterval),
            bufferMode = preferences.readEnum(BUFFER_MODE, BufferMode.entries, PlayerSettings().bufferMode),
            maxBitrate = preferences.readEnum(MAX_BITRATE, MaxBitrateCap.entries, PlayerSettings().maxBitrate),
            autoNextDelay = preferences.readEnum(
                AUTO_NEXT_DELAY,
                AutoNextDelay.entries,
                PlayerSettings().autoNextDelay,
            ),
        )
    }

    suspend fun setSeekInterval(value: SeekInterval) = put(SEEK_INTERVAL, value.name)

    suspend fun setAutoNextDelay(value: AutoNextDelay) = put(AUTO_NEXT_DELAY, value.name)

    suspend fun setBufferMode(value: BufferMode) = put(BUFFER_MODE, value.name)

    suspend fun setMaxBitrate(value: MaxBitrateCap) = put(MAX_BITRATE, value.name)

    /**
     * Appearance, in the same store as the playback tuning.
     *
     * Both are ordinary preferences with nothing sensitive in them, so they share a file.
     * The TMDB key does not, and that distinction is deliberate — see [TmdbKeyStore].
     */
    val appearance: Flow<Appearance> = dataStore.data.map { preferences ->
        Appearance(
            themeMode = preferences.readEnum(THEME_MODE, ThemeMode.entries, Appearance().themeMode),
            dynamicColor = preferences[DYNAMIC_COLOR] ?: Appearance().dynamicColor,
        )
    }

    suspend fun setThemeMode(value: ThemeMode) = put(THEME_MODE, value.name)

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    /**
     * Writing systems the viewer has said they do not read (INC-F14).
     *
     * App-wide rather than per profile, so that two people looking at the same catalogue on the
     * same television are never shown different numbers of titles with no way to tell why.
     *
     * Stored by name for the reason at the top of this file, and a stored name that no longer
     * maps to an entry is dropped rather than defaulted — the safe reading of "hide this" that
     * this code no longer understands is to hide nothing.
     */
    val hiddenScripts: Flow<Set<TitleScript>> = dataStore.data.map { preferences ->
        preferences[HIDDEN_SCRIPTS]
            .orEmpty()
            .mapNotNullTo(mutableSetOf()) { stored ->
                TitleScript.entries.firstOrNull { it.name == stored }
            }
    }

    suspend fun setHiddenScripts(value: Set<TitleScript>) {
        dataStore.edit { preferences ->
            preferences[HIDDEN_SCRIPTS] = value.mapTo(mutableSetOf()) { it.name }
        }
    }

    /**
     * Whether advanced search offers a row of live channels.
     *
     * **Off by default**, which is the one setting in this file whose default is a subtraction.
     * A live channel has no metadata and never will, so in a genre search it is matched on the
     * genre word appearing in its own name — a deliberately weak rule that fills a column which
     * would otherwise always be empty, with the column nobody filtering by genre asked for.
     *
     * App-wide rather than per profile, like the hidden scripts above and for the same reason.
     */
    val showLiveInSearch: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_LIVE_IN_SEARCH] ?: false
    }

    suspend fun setShowLiveInSearch(enabled: Boolean) {
        dataStore.edit { it[SHOW_LIVE_IN_SEARCH] = enabled }
    }

    /**
     * Whether one film listed four times is shown once.
     *
     * A panel that carries a film in SD, HD, FHD and 4K lists it four times, and a panel that
     * also carries a subtitled cut lists it eight; the browse grid then reads as a catalogue four
     * times its real size in which nothing can be found twice. When this is on, the lists show
     * the provider's first listing of each title and the detail screen offers the rest.
     *
     * **Off by default, and that is a deliberate subtraction like [showLiveInSearch].** Merging
     * hides rows a provider sent, and hiding rows nobody asked to hide is how a viewer comes to
     * believe their account is missing something. A title that is genuinely two different films
     * with the same name and year would be merged too — rare, and worth saying no by default.
     *
     * App-wide rather than per profile, like everything else in this file: it is a statement
     * about what the catalogue *is*, not about what one person wants to see of it.
     */
    val mergeDuplicateTitles: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[MERGE_DUPLICATE_TITLES] ?: false
    }

    suspend fun setMergeDuplicateTitles(enabled: Boolean) {
        dataStore.edit { it[MERGE_DUPLICATE_TITLES] = enabled }
    }

    /**
     * Whether the player lights its black bars with the colours of the picture.
     *
     * **On by default**, because it is what the screen should have looked like from the start: a
     * film is 2.35:1 and a panel is 16:9, so a film plays inside two dead-black bars for its whole
     * length, and an unlit rectangle either side of the picture is not neutral on a television in
     * a dark room. It is a switch rather than a fixed behaviour because it is a taste, and one
     * with a real cost on the other side — the surface is sampled while the film plays, and
     * somebody who does not want it should not be paying for it.
     *
     * Off means the sampling loop never starts, not that its answer is discarded. Same rule as
     * [showLiveInSearch] above.
     *
     * App-wide rather than per profile, like everything else in this file.
     */
    val ambientPlayer: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AMBIENT_PLAYER] ?: true
    }

    suspend fun setAmbientPlayer(enabled: Boolean) {
        dataStore.edit { it[AMBIENT_PLAYER] = enabled }
    }

    /**
     * How subtitles are drawn (INC-F11).
     *
     * Separate from [settings] rather than folded into it. `PlayerSettings` is engine tuning that
     * the controller is handed on every change, and a caption colour is not tuning — pushing one
     * through that path would rebuild nothing and mean nothing. They persist in the same file
     * because they are the same kind of preference; they travel separately because they are read
     * by different code.
     */
    val subtitleStyle: Flow<SubtitleStyle> = dataStore.data.map { preferences ->
        val defaults = SubtitleStyle()
        SubtitleStyle(
            matchSystem = preferences[SUBTITLE_MATCH_SYSTEM] ?: defaults.matchSystem,
            textSize = preferences.readEnum(SUBTITLE_SIZE, SubtitleTextSize.entries, defaults.textSize),
            textColor = preferences.readEnum(SUBTITLE_TEXT_COLOR, SubtitleColor.entries, defaults.textColor),
            background = preferences.readEnum(SUBTITLE_BACKGROUND, SubtitleColor.entries, defaults.background),
            backgroundOpacity = preferences.readEnum(
                SUBTITLE_OPACITY,
                SubtitleOpacity.entries,
                defaults.backgroundOpacity,
            ),
        )
    }

    /**
     * Writes the whole style in one edit.
     *
     * One write rather than a setter per property, because every change also clears
     * [SubtitleStyle.matchSystem], and two edits would leave a moment where the stored style says
     * it is following the system and carries an explicit size.
     */
    suspend fun setSubtitleStyle(value: SubtitleStyle) {
        dataStore.edit { preferences ->
            preferences[SUBTITLE_MATCH_SYSTEM] = value.matchSystem
            preferences[SUBTITLE_SIZE] = value.textSize.name
            preferences[SUBTITLE_TEXT_COLOR] = value.textColor.name
            preferences[SUBTITLE_BACKGROUND] = value.background.name
            preferences[SUBTITLE_OPACITY] = value.backgroundOpacity.name
        }
    }

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    private fun <T : Enum<T>> Preferences.readEnum(
        key: Preferences.Key<String>,
        values: List<T>,
        default: T,
    ): T {
        val stored = this[key] ?: return default
        return values.firstOrNull { it.name == stored } ?: default
    }

    private companion object {
        val SEEK_INTERVAL = stringPreferencesKey("seek_interval")
        val BUFFER_MODE = stringPreferencesKey("buffer_mode")
        val MAX_BITRATE = stringPreferencesKey("max_bitrate")
        val AUTO_NEXT_DELAY = stringPreferencesKey("auto_next_delay")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SHOW_LIVE_IN_SEARCH = booleanPreferencesKey("show_live_in_search")
        val AMBIENT_PLAYER = booleanPreferencesKey("ambient_player")
        val MERGE_DUPLICATE_TITLES = booleanPreferencesKey("merge_duplicate_titles")
        val HIDDEN_SCRIPTS = stringSetPreferencesKey("hidden_scripts")
        val SUBTITLE_MATCH_SYSTEM = booleanPreferencesKey("subtitle_match_system")
        val SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
        val SUBTITLE_TEXT_COLOR = stringPreferencesKey("subtitle_text_color")
        val SUBTITLE_BACKGROUND = stringPreferencesKey("subtitle_background")
        val SUBTITLE_OPACITY = stringPreferencesKey("subtitle_opacity")
    }
}
