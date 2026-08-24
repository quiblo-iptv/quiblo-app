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

import androidx.annotation.StringRes

/**
 * The two halves of the settings screen (`029` #6).
 *
 * **Profile** holds everything one person chooses — their theme, their playback tuning, the tabs
 * and shelves and writing systems they want to see. **App** holds the television itself: the
 * sources, the metadata key, the backup file and whether this device asks about updates.
 *
 * An enum rather than a boolean because the tab row is indexed by ordinal and a third section is
 * the obvious next request — a boolean would have to be replaced rather than extended, and every
 * `if (isProfileTab)` written against it with it.
 */
internal enum class SettingsSection(@param:StringRes val labelRes: Int) {
    PROFILE(R.string.settings_section_profile),
    APP(R.string.settings_section_app),
}
