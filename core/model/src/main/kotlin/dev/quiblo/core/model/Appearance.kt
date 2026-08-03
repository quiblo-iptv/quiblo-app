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

package dev.quiblo.core.model

/**
 * How the app should be themed.
 *
 * [SYSTEM] is the default and stays the default: following the device is right for most
 * people most of the time, and AC-NFR-09 requires it to work. The overrides exist because
 * "most of the time" is not always — a viewer watching in a dark room does not want the
 * app to turn white at sunrise.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Visual preferences.
 *
 * @property dynamicColor whether to derive the palette from the device wallpaper on
 *   Android 12 and later. On by default because it makes the app feel native to the phone
 *   it is on; switchable off because it also means the app's own identity disappears, and
 *   some people would rather keep it.
 */
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)
