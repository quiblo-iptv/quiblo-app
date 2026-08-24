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
 * Persists the settings a person chooses, filed under the person who chose them.
 *
 * Values are stored as enum *names*, not ordinals. An ordinal silently changes meaning the
 * moment someone reorders or inserts an entry, and the user's saved choice would quietly
 * become a different one. A name that no longer exists falls back to the default instead,
 * which is the only safe reading of a value this code no longer understands.
 *
 * **Every read and write takes a profile id, and that is the whole of `029` #6.** These were
 * app-wide, with a comment on each one explaining why — and the explanation was wrong in the same
 * way each time: it argued from what the *catalogue* is, when a theme, a seek interval and a list
 * of shelves are statements about what one viewer wants to look at. Two people on one television
 * were sharing them, and the profile switch that redraws favourites left every one of these
 * behind. See [Scoped] for what a profile that has never chosen reads.
 *
 * `Profile.NONE_ID` is a legal id here rather than a case to guard: it is the moment before the
 * chooser has been answered, and reading the pre-profile answer then is exactly right — the splash
 * and the chooser are themed by it.
 *
 * Nothing here is sensitive, so this is plain `DataStore` rather than the encrypted store
 * credentials use (AC-XT-04).
 */
// A getter and a setter for each of eleven preferences, and the count is the number of settings
// this app has rather than any tangle between them: every one is two or three lines that read or
// write one key. Splitting the class to satisfy a threshold would file the same eleven behind two
// names and leave every caller fetching both.
@Suppress("TooManyFunctions")
class PlayerSettingsStore(context: Context) {

    private val dataStore = context.applicationContext.playerSettingsDataStore

    fun settings(profileId: Long): Flow<PlayerSettings> = dataStore.data.map { preferences ->
        PlayerSettings(
            seekInterval = preferences.readEnum(SEEK_INTERVAL, profileId, SeekInterval.entries) {
                PlayerSettings().seekInterval
            },
            bufferMode = preferences.readEnum(BUFFER_MODE, profileId, BufferMode.entries) {
                PlayerSettings().bufferMode
            },
            maxBitrate = preferences.readEnum(MAX_BITRATE, profileId, MaxBitrateCap.entries) {
                PlayerSettings().maxBitrate
            },
            autoNextDelay = preferences.readEnum(AUTO_NEXT_DELAY, profileId, AutoNextDelay.entries) {
                PlayerSettings().autoNextDelay
            },
        )
    }

    suspend fun setSeekInterval(profileId: Long, value: SeekInterval) =
        put(SEEK_INTERVAL, profileId, value.name)

    suspend fun setAutoNextDelay(profileId: Long, value: AutoNextDelay) =
        put(AUTO_NEXT_DELAY, profileId, value.name)

    suspend fun setBufferMode(profileId: Long, value: BufferMode) =
        put(BUFFER_MODE, profileId, value.name)

    suspend fun setMaxBitrate(profileId: Long, value: MaxBitrateCap) =
        put(MAX_BITRATE, profileId, value.name)

    /**
     * Appearance, in the same store as the playback tuning.
     *
     * Both are ordinary preferences with nothing sensitive in them, so they share a file.
     * The TMDB key does not, and that distinction is deliberate — see [TmdbKeyStore].
     */
    fun appearance(profileId: Long): Flow<Appearance> = dataStore.data.map { preferences ->
        Appearance(
            themeMode = preferences.readEnum(THEME_MODE, profileId, ThemeMode.entries) {
                Appearance().themeMode
            },
            dynamicColor = preferences.scopedBoolean(DYNAMIC_COLOR, profileId)
                ?: Appearance().dynamicColor,
        )
    }

    suspend fun setThemeMode(profileId: Long, value: ThemeMode) = put(THEME_MODE, profileId, value.name)

    suspend fun setDynamicColor(profileId: Long, enabled: Boolean) = put(DYNAMIC_COLOR, profileId, enabled)

    /**
     * Writing systems the viewer has said they do not read (INC-F14).
     *
     * Stored by name for the reason at the top of this file, and a stored name that no longer
     * maps to an entry is dropped rather than defaulted — the safe reading of "hide this" that
     * this code no longer understands is to hide nothing.
     */
    fun hiddenScripts(profileId: Long): Flow<Set<TitleScript>> = dataStore.data.map { preferences ->
        preferences.scopedStringSet(HIDDEN_SCRIPTS, profileId)
            .orEmpty()
            .mapNotNullTo(mutableSetOf()) { stored ->
                TitleScript.entries.firstOrNull { it.name == stored }
            }
    }

    suspend fun setHiddenScripts(profileId: Long, value: Set<TitleScript>) {
        dataStore.edit { preferences ->
            preferences.putScoped(HIDDEN_SCRIPTS, profileId, value.mapTo(mutableSetOf()) { it.name })
        }
    }

    /**
     * Which tabs this viewer does not want in the bar (`029` #5).
     *
     * Stored by name, and a name that no longer matches a tab is ignored — so a tab this app has
     * since renamed comes back rather than hiding something else by ordinal. The apps clamp the
     * result themselves: a bar with nothing in it is not a state either shell can draw, and the
     * screen that edits this refuses to hide the last one.
     */
    fun hiddenTabs(profileId: Long): Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences.scopedStringSet(HIDDEN_TABS, profileId).orEmpty()
    }

    suspend fun setHiddenTabs(profileId: Long, value: Set<String>) {
        dataStore.edit { it.putScoped(HIDDEN_TABS, profileId, value) }
    }

    /**
     * Whether advanced search offers a row of live channels.
     *
     * **Off by default**, which is one of the settings in this file whose default is a
     * subtraction. A live channel has no metadata and never will, so in a genre search it is
     * matched on the genre word appearing in its own name — a deliberately weak rule that fills a
     * column which would otherwise always be empty, with the column nobody filtering by genre
     * asked for.
     */
    fun showLiveInSearch(profileId: Long): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences.scopedBoolean(SHOW_LIVE_IN_SEARCH, profileId) ?: false
    }

    suspend fun setShowLiveInSearch(profileId: Long, enabled: Boolean) =
        put(SHOW_LIVE_IN_SEARCH, profileId, enabled)

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
     */
    fun mergeDuplicateTitles(profileId: Long): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences.scopedBoolean(MERGE_DUPLICATE_TITLES, profileId) ?: false
    }

    suspend fun setMergeDuplicateTitles(profileId: Long, enabled: Boolean) =
        put(MERGE_DUPLICATE_TITLES, profileId, enabled)

    /**
     * Whether the catalogue is one grid instead of a shelf per category (`029` #3).
     *
     * **Only meaningful while [mergeDuplicateTitles] is on, and that is why it is a second switch
     * rather than a third state of the first.** A provider that lists one film in four qualities
     * usually also files those four under four categories — `FILMS HD`, `FILMS 4K`, `FILMS AR` —
     * so merging the duplicates leaves the same title reachable from several shelves, and the
     * catalogue still reads as several catalogues. Collapsing the shelves is what finishes the
     * job the merge starts, and it is separate because plenty of accounts have categories worth
     * keeping.
     *
     * Off by default. The category shelves are the provider's own organisation of its catalogue,
     * and throwing that away is a choice.
     */
    fun mergeCategories(profileId: Long): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences.scopedBoolean(MERGE_CATEGORIES, profileId) ?: false
    }

    suspend fun setMergeCategories(profileId: Long, enabled: Boolean) =
        put(MERGE_CATEGORIES, profileId, enabled)

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
     */
    fun ambientPlayer(profileId: Long): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences.scopedBoolean(AMBIENT_PLAYER, profileId) ?: true
    }

    suspend fun setAmbientPlayer(profileId: Long, enabled: Boolean) = put(AMBIENT_PLAYER, profileId, enabled)

    /**
     * Whether the app asks GitHub about a newer release when it opens (`029` #7).
     *
     * **The one preference in this file that is not per profile**, and the exception is the point:
     * it decides whether this device makes a request, and a request made on one person's behalf is
     * made by the television. A household where the answer depended on who pressed the profile
     * first would be a household that cannot tell whether the app phones out or not.
     *
     * On by default, and there is no store here to be updated from — a sideloaded APK that never
     * says it is out of date is how a security fix goes uninstalled for a year. What it costs is
     * one request to this project's own releases page at launch, which the consent copy now says.
     * Turning it off means no request is made at all, not that its answer is hidden.
     */
    val checkUpdatesOnLaunch: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[booleanPreferencesKey(CHECK_UPDATES_ON_LAUNCH)] ?: true
    }

    suspend fun setCheckUpdatesOnLaunch(enabled: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(CHECK_UPDATES_ON_LAUNCH)] = enabled }
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
    fun subtitleStyle(profileId: Long): Flow<SubtitleStyle> = dataStore.data.map { preferences ->
        val defaults = SubtitleStyle()
        SubtitleStyle(
            matchSystem = preferences.scopedBoolean(SUBTITLE_MATCH_SYSTEM, profileId) ?: defaults.matchSystem,
            textSize = preferences.readEnum(SUBTITLE_SIZE, profileId, SubtitleTextSize.entries) {
                defaults.textSize
            },
            textColor = preferences.readEnum(SUBTITLE_TEXT_COLOR, profileId, SubtitleColor.entries) {
                defaults.textColor
            },
            background = preferences.readEnum(SUBTITLE_BACKGROUND, profileId, SubtitleColor.entries) {
                defaults.background
            },
            backgroundOpacity = preferences.readEnum(SUBTITLE_OPACITY, profileId, SubtitleOpacity.entries) {
                defaults.backgroundOpacity
            },
        )
    }

    /**
     * Writes the whole style in one edit.
     *
     * One write rather than a setter per property, because every change also clears
     * [SubtitleStyle.matchSystem], and two edits would leave a moment where the stored style says
     * it is following the system and carries an explicit size.
     */
    suspend fun setSubtitleStyle(profileId: Long, value: SubtitleStyle) {
        dataStore.edit { preferences ->
            preferences.putScoped(SUBTITLE_MATCH_SYSTEM, profileId, value.matchSystem)
            preferences.putScoped(SUBTITLE_SIZE, profileId, value.textSize.name)
            preferences.putScoped(SUBTITLE_TEXT_COLOR, profileId, value.textColor.name)
            preferences.putScoped(SUBTITLE_BACKGROUND, profileId, value.background.name)
            preferences.putScoped(SUBTITLE_OPACITY, profileId, value.backgroundOpacity.name)
        }
    }

    private suspend fun put(base: String, profileId: Long, value: String) {
        dataStore.edit { it.putScoped(base, profileId, value) }
    }

    private suspend fun put(base: String, profileId: Long, value: Boolean) {
        dataStore.edit { it.putScoped(base, profileId, value) }
    }

    /**
     * The default is a lambda rather than a value because every caller's default is
     * `PlayerSettings()` or `SubtitleStyle()` — constructing one per property read would build a
     * whole settings object five times to answer five questions about it.
     */
    private inline fun <T : Enum<T>> Preferences.readEnum(
        base: String,
        profileId: Long,
        values: List<T>,
        default: () -> T,
    ): T {
        val stored = scopedString(base, profileId) ?: return default()
        return values.firstOrNull { it.name == stored } ?: default()
    }

    private companion object {
        const val SEEK_INTERVAL = "seek_interval"
        const val BUFFER_MODE = "buffer_mode"
        const val MAX_BITRATE = "max_bitrate"
        const val AUTO_NEXT_DELAY = "auto_next_delay"
        const val THEME_MODE = "theme_mode"
        const val DYNAMIC_COLOR = "dynamic_color"
        const val SHOW_LIVE_IN_SEARCH = "show_live_in_search"
        const val AMBIENT_PLAYER = "ambient_player"
        const val MERGE_DUPLICATE_TITLES = "merge_duplicate_titles"
        const val MERGE_CATEGORIES = "merge_categories"
        const val HIDDEN_SCRIPTS = "hidden_scripts"
        const val HIDDEN_TABS = "hidden_tabs"
        const val SUBTITLE_MATCH_SYSTEM = "subtitle_match_system"
        const val SUBTITLE_SIZE = "subtitle_size"
        const val SUBTITLE_TEXT_COLOR = "subtitle_text_color"
        const val SUBTITLE_BACKGROUND = "subtitle_background"
        const val SUBTITLE_OPACITY = "subtitle_opacity"

        /** App-wide, deliberately. See [checkUpdatesOnLaunch]. */
        const val CHECK_UPDATES_ON_LAUNCH = "check_updates_on_launch"
    }
}
