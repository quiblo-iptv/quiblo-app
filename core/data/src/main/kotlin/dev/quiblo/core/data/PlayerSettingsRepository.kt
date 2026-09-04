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
import dev.quiblo.core.model.AppTab
import dev.quiblo.core.model.Appearance
import dev.quiblo.core.model.AutoNextDelay
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.CatalogueSyncInterval
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.PlayerSettings
import dev.quiblo.core.model.Profile
import dev.quiblo.core.model.SeekInterval
import dev.quiblo.core.model.SubtitleStyle
import dev.quiblo.core.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * The settings one viewer has chosen, for the screens that edit them and the code that obeys them.
 *
 * A thin pass-through over [PlayerSettingsStore] on purpose. Features talk to `:core:data`
 * and nothing else (docs/PLAN.md §2), and honouring that for a small case is what stops
 * the rule eroding: the first feature to reach past it makes the second one reasonable.
 *
 * **What it adds to the store is the profile, and that is the whole of `029` #6.** Every read is
 * `flatMapLatest` over the active profile rather than a one-off id, so switching person redraws
 * the theme, the shelves and the tab bar the way it already redrew favourites — a setting that
 * stayed until something else happened to reload it would be worse than one that never changed.
 * Every write goes to whoever is watching now.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// A getter and a setter per preference, and the count is the number of settings this app has
// rather than any tangle between them: every member here forwards one value to or from the store.
// Splitting the class to satisfy a threshold would file the same settings behind two names and
// leave every caller fetching both — the same reasoning `PlayerSettingsStore` carries.
@Suppress("TooManyFunctions")
class PlayerSettingsRepository(
    private val store: PlayerSettingsStore,
    private val profiles: ProfileRepository,
) {

    /** Emits on every change, so a player already on screen picks up an edit immediately. */
    val settings: Flow<PlayerSettings> = perProfile(store::settings)

    suspend fun setSeekInterval(value: SeekInterval) = store.setSeekInterval(profiles.activeProfileId, value)

    suspend fun setBufferMode(value: BufferMode) = store.setBufferMode(profiles.activeProfileId, value)

    suspend fun setMaxBitrate(value: MaxBitrateCap) = store.setMaxBitrate(profiles.activeProfileId, value)

    suspend fun setAutoNextDelay(value: AutoNextDelay) = store.setAutoNextDelay(profiles.activeProfileId, value)

    val appearance: Flow<Appearance> = perProfile(store::appearance)

    suspend fun setThemeMode(value: ThemeMode) = store.setThemeMode(profiles.activeProfileId, value)

    suspend fun setDynamicColor(enabled: Boolean) = store.setDynamicColor(profiles.activeProfileId, enabled)

    /**
     * Whether advanced search offers a row of live channels. Off unless the viewer asks.
     *
     * Here rather than in a repository of its own because it is one boolean in the same file as
     * the rest of the ordinary preferences, and a class that forwarded a single flow would be a
     * layer that only forwards.
     */
    val showLiveInSearch: Flow<Boolean> = perProfile(store::showLiveInSearch)

    suspend fun setShowLiveInSearch(enabled: Boolean) =
        store.setShowLiveInSearch(profiles.activeProfileId, enabled)

    /**
     * Whether one film listed in four qualities is shown once. Off unless the viewer asks.
     *
     * Read by the catalogue queries rather than filtered afterwards: a page of twenty rows that
     * turns into five in Kotlin is a page that shrinks as it is scrolled.
     */
    val mergeDuplicateTitles: Flow<Boolean> = perProfile(store::mergeDuplicateTitles)

    suspend fun setMergeDuplicateTitles(enabled: Boolean) =
        store.setMergeDuplicateTitles(profiles.activeProfileId, enabled)

    /**
     * Whether the catalogue is drawn as one grid rather than a shelf per category (`029` #3).
     *
     * **Reported as `false` while [mergeDuplicateTitles] is off**, rather than left to each screen
     * to remember. Collapsing the shelves without merging the duplicates gives one grid in which
     * every film appears four times in a row, which is the worst of both settings and is what a
     * screen reading the raw switch would draw.
     */
    val mergeCategories: Flow<Boolean> =
        combine(perProfile(store::mergeCategories), mergeDuplicateTitles) { collapse, merge ->
            collapse && merge
        }

    suspend fun setMergeCategories(enabled: Boolean) =
        store.setMergeCategories(profiles.activeProfileId, enabled)

    /**
     * Which tabs this viewer has switched off (`029` #5).
     *
     * Decoded here rather than at the two shells, so a stored name that no longer matches a tab is
     * dropped in one place. See [AppTab] for why the four are the four.
     */
    val hiddenTabs: Flow<Set<AppTab>> = perProfile(store::hiddenTabs).map(AppTab::decode)

    /**
     * Hides or shows one tab, refusing to leave the bar empty.
     *
     * The clamp is here rather than on each shell: a bar with nothing in it is a shell that opens
     * on a tab it has been told not to draw, and neither app has anywhere to put that. Asking to
     * hide the last visible one is answered by leaving it visible, which is what a switch that
     * springs back means.
     */
    suspend fun setTabHidden(tab: AppTab, hidden: Boolean) {
        val current = AppTab.decode(store.hiddenTabs(profiles.activeProfileId).first())
        val updated = if (hidden) current + tab else current - tab
        if (updated.size >= AppTab.entries.size) return
        if (updated == current) return
        store.setHiddenTabs(profiles.activeProfileId, updated.mapTo(mutableSetOf()) { it.name })
    }

    /** Whether the player lights its black bars from the picture. On unless switched off. */
    val ambientPlayer: Flow<Boolean> = perProfile(store::ambientPlayer)

    suspend fun setAmbientPlayer(enabled: Boolean) = store.setAmbientPlayer(profiles.activeProfileId, enabled)

    /**
     * Whether the app asks about a newer release when it opens (`029` #7).
     *
     * App-wide rather than per profile, and the only setting here that is: it decides whether this
     * device makes a request. See `PlayerSettingsStore.checkUpdatesOnLaunch`.
     */
    val checkUpdatesOnLaunch: Flow<Boolean> = store.checkUpdatesOnLaunch

    suspend fun setCheckUpdatesOnLaunch(enabled: Boolean) = store.setCheckUpdatesOnLaunch(enabled)

    /**
     * How often the catalogue is re-read on its own (`FEAT-031`).
     *
     * App-wide, like the one above and for the same reason: it says how often this device talks
     * to a provider. `SyncScheduler` watches this and re-registers the job when it changes.
     */
    val catalogueSyncInterval: Flow<CatalogueSyncInterval> = store.catalogueSyncInterval

    suspend fun setCatalogueSyncInterval(value: CatalogueSyncInterval) =
        store.setCatalogueSyncInterval(value)

    /**
     * Whether a paused player lets the screen dim (`FEAT-032`).
     *
     * App-wide, like the two above: it is about this device's screen, not about the viewer. On
     * by default — see `PlayerSettingsStore.dimWhilePaused`.
     */
    val dimWhilePaused: Flow<Boolean> = store.dimWhilePaused

    suspend fun setDimWhilePaused(enabled: Boolean) = store.setDimWhilePaused(enabled)

    /**
     * How subtitles are drawn (INC-F11).
     *
     * Read by the player and written from inside it, where the effect is visible. There is no
     * copy of this on the settings screen: a caption colour chosen against a grey card is a
     * guess, and the same choice made over the film it will sit on is not.
     */
    val subtitleStyle: Flow<SubtitleStyle> = perProfile(store::subtitleStyle)

    suspend fun setSubtitleStyle(value: SubtitleStyle) =
        store.setSubtitleStyle(profiles.activeProfileId, value)

    /**
     * One setting, followed through every change of who is watching.
     *
     * `flatMapLatest` rather than reading [ProfileRepository.activeProfileId] once: the id is a
     * field read and would be correct at subscription time and stale a second later. Nobody has
     * chosen yet is `Profile.NONE_ID`, which reads the answers the app had before profiles owned
     * settings — the right thing behind the chooser, which is themed by them.
     */
    private fun <T> perProfile(of: (Long) -> Flow<T>): Flow<T> =
        profiles.activeProfile.flatMapLatest { of(it?.id ?: Profile.NONE_ID) }
}
