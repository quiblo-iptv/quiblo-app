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

package dev.quiblo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.quiblo.core.data.ProfileRepository
import dev.quiblo.core.model.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Who there is to be, and who is currently being it. */
data class ProfilesUiState(
    val profiles: List<Profile> = emptyList(),
    val active: Profile? = null,
) {
    /** True on a device nobody has set up yet, where the only options are Add and Guest. */
    val isFirstRun: Boolean get() = profiles.none { !it.isGuest }
}

/**
 * The chooser, and switching afterwards.
 *
 * Shared by the phone and the television, like every other ViewModel here — the two screens
 * differ in how they are driven and not at all in what they mean.
 */
class ProfilesViewModel(private val profiles: ProfileRepository) : ViewModel() {

    val uiState: StateFlow<ProfilesUiState> = combine(
        profiles.profiles,
        profiles.activeProfile,
    ) { all, active ->
        ProfilesUiState(profiles = all, active = active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), ProfilesUiState())

    /**
     * Adds a profile and immediately becomes it.
     *
     * Somebody typing their name on a remote has said who they are; asking them to then pick
     * themselves off a list would be a second answer to a question already answered.
     */
    fun addAndSelect(name: String) = viewModelScope.launch {
        profiles.addProfile(name)?.let { profiles.select(it) }
    }

    fun select(profile: Profile) = viewModelScope.launch {
        profiles.select(profile)
    }

    /** [guestName] comes from the screen's resources: a ViewModel holds no display strings. */
    fun startGuest(guestName: String) = viewModelScope.launch {
        profiles.startGuestSession(guestName)
    }

    /** Back to the chooser. A guest session ends here rather than lingering until restart. */
    fun switchProfile() = viewModelScope.launch {
        profiles.signOut()
    }

    /** Takes the profile's favourites and resume points with it, by foreign key. */
    fun delete(profile: Profile) = viewModelScope.launch {
        profiles.delete(profile)
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
