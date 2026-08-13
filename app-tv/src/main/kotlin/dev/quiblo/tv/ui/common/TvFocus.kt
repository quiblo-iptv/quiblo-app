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

package dev.quiblo.tv.ui.common

import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus, tolerating there being nothing to focus.
 *
 * A content area can legitimately hold no focusable at all — an empty catalogue, a spinner, a
 * line of explanatory text — and [FocusRequester] treats that as a programming error and throws.
 * Here it is an ordinary state, and the right behaviour is to leave focus where it is rather
 * than to bring the app down.
 *
 * One copy, in the package both the screens and the panels can see. There were two, in files far
 * enough apart that nobody noticed the second one being written.
 */
fun FocusRequester.tryRequestFocus() {
    runCatching { requestFocus() }
}
