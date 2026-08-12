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
 * Who is watching.
 *
 * Owns favourites and resume positions, and nothing else. Player settings, hidden categories,
 * the metadata key and the sources themselves stay app-wide: they describe the television and
 * the account behind it rather than the person on the sofa, and a household that has to
 * configure its playlist twice would rightly call that a bug.
 *
 * There is no password and no PIN. This answers "whose favourites are these", not "who is
 * allowed to watch what" — the second is a different feature, and `docs/PLAN.md` §6 keeps it
 * parked deliberately.
 */
data class Profile(
    val id: Long,
    val name: String,
    /**
     * True for the throwaway one.
     *
     * A guest's favourites and resume points last as long as the session does: they are
     * deleted when the guest leaves, and again at every startup, because a process the system
     * kills never gets to tidy up after itself.
     */
    val isGuest: Boolean = false,
    /**
     * Which illustrated face this profile chose, or null for none.
     *
     * A key into a fixed set the app ships, and deliberately not a picture the viewer supplied.
     * A chosen file would need copying into app storage, deleting when the profile is deleted,
     * a size cap, and a decision about whether it belongs in a backup — and `AC-DATA` promises
     * a portable backup, which somebody's photograph would quietly stop being.
     *
     * Null draws the initial-on-a-colour fallback, which is a picture too. Nobody is left
     * looking at a blank circle.
     */
    val avatar: String? = null,
) {
    companion object {
        /**
         * The id used while nobody has chosen yet.
         *
         * Matches no row — ids are generated from 1 — so every profile-scoped query returns
         * nothing and every write lands nowhere. That is the correct behaviour for a moment
         * when the app does not yet know whose data it would be reading or writing, and it
         * means no screen needs a special case for it.
         */
        const val NONE_ID = 0L
    }
}
