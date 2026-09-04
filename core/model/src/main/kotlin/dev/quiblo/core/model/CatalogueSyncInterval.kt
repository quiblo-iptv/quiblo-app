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
 * How often the catalogue is re-read without anybody asking (`FEAT-031`).
 *
 * **This used to be four days, and four days was a guess** — `024` recorded it as one, openly:
 * "a guess about how often a provider adds things". It was wrong in the direction that matters,
 * because a provider adds a film and a household cannot see it for most of a week.
 *
 * It is a setting rather than a better constant because the right answer is not ours to know.
 * It depends on how often one provider adds things and on how tolerant one panel is of being
 * asked — two facts that live with the viewer, not in this repository.
 *
 * **App-wide, not per profile.** It decides how often this device talks to a provider, and a
 * household where that depended on who pressed a profile first would be a household that cannot
 * say how often its own box phones out. The same reasoning as `checkUpdatesOnLaunch`, which is
 * the other setting in this app that is about the device rather than the viewer.
 *
 * The floor is four hours rather than something smaller because WorkManager will not run a
 * periodic job more often than every fifteen minutes anyway, and because each run costs a panel
 * several requests — see `XtreamSource`, where being asked too often is answered with a block.
 */
enum class CatalogueSyncInterval(val hours: Long) {
    FOUR_HOURS(4L),
    EIGHT_HOURS(8L),
    TWELVE_HOURS(12L),
    DAILY(24L),
}
