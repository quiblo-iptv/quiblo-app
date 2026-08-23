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

package dev.quiblo.designsystem

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Hands a web address to whatever on this device opens one, and shrugs if nothing does.
 *
 * **Nothing may depend on this working.** A television box often has no browser at all, and the
 * ones that do open it on a screen a remote can barely drive — so every caller shows the address
 * as text beside the button, and this is the shortcut for a device that happens to be able to
 * take it. A missing browser is a button that did nothing visible, never a crash and never a
 * viewer stuck.
 *
 * Anything other than "there is no such activity" is rethrown: a browser that exists and fails
 * is a fault worth seeing rather than one worth swallowing.
 */
fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }.onFailure { if (it !is ActivityNotFoundException) throw it }
}

/** Where a TMDB key is made. Both apps point at it and neither can fetch anything without one. */
const val TMDB_API_KEY_URL = "https://www.themoviedb.org/settings/api"
