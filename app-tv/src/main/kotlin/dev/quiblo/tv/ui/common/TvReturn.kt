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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * "The viewer is coming *back* to this screen, not arriving at it."
 *
 * The shell leaves composition whenever anything opens over it — a film, a series, the player,
 * Settings — so every screen underneath is built again from nothing on the way back. Scroll
 * positions survive that, because they are saved state and the shell is wrapped in a holder that
 * keeps them; **where the remote was does not**, because focus is not state anybody saves. Without
 * this, backing out of a film landed the viewer at the top of the results they had searched, which
 * is `027`'s first report and the one that made the app feel like it forgot things.
 *
 * A one-shot flag rather than a boolean parameter, and the difference is the whole reason it
 * exists. A boolean would be true for as long as the shell was up, so *switching tabs* would look
 * like a return and the content would snatch focus off the tab bar as the viewer walked along it —
 * which is the one thing on this screen that must never happen. Armed by the pop, read once by
 * whichever list is on screen, and false from then on.
 *
 * **It is deliberately not observable.** Nothing recomposes because this changed: it is read
 * inside an effect that is already running for another reason — the rows arriving — and a
 * `mutableStateOf` here would only invite a reader to draw something from it.
 */
@Stable
class TvReturnSignal {

    private var isPending = false

    /** Says that the screen underneath is about to be composed again after an overlay closed. */
    fun arm() {
        isPending = true
    }

    /**
     * True once per arming, for the one caller that acts on it.
     *
     * Consuming rather than reading is what keeps a second list — a tab switched to afterwards —
     * from restoring a cursor that belongs to a journey that is over.
     */
    fun consume(): Boolean = isPending.also { isPending = false }
}

/**
 * The signal, for the lists that restore themselves from it.
 *
 * Static because it never changes for the life of the shell: the object is the same one
 * throughout, and only the flag inside it moves. Reading it costs nothing and changing it
 * recomposes nothing.
 *
 * The default is an unarmed signal, so a screen composed in a test or a preview simply behaves
 * like a first arrival — which is what it is.
 */
val LocalTvReturn = staticCompositionLocalOf { TvReturnSignal() }
