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

import dev.quiblo.core.model.Profile
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A profile repository standing in for a chosen profile.
 *
 * Every profile-scoped query takes the id from here, so a test that did not supply one would
 * be testing the "nobody has chosen yet" path — which reads nothing and writes nowhere, and
 * would make every assertion below pass for the wrong reason.
 */
internal fun fakeProfiles(id: Long = 1L): ProfileRepository = mockk<ProfileRepository>().apply {
    every { activeProfileId } returns id
    every { activeProfile } returns MutableStateFlow(Profile(id = id, name = "Test"))
}
