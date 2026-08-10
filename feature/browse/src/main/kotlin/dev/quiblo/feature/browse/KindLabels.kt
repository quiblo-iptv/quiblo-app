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

package dev.quiblo.feature.browse

import androidx.annotation.StringRes
import dev.quiblo.core.model.MediaKind

/**
 * What the app calls each kind of thing, in one place for both apps.
 *
 * **The vocabulary is Live, Movies, Series, and every screen uses it.** That was a decision
 * rather than a preference: the television called the same catalogue "Films" in search, "VOD"
 * in favourites and "Movies" everywhere else, and a viewer meeting three names for one thing
 * reasonably concludes they are three things (`agile/012` #017, #019).
 *
 * These live in `:feature:browse` because it is the one module both applications depend on, so
 * a word cannot be corrected on the phone and left wrong on the television. `:core:model` would
 * be the tidier home for something about `MediaKind` and cannot be: it is a plain JVM module
 * with no resources, deliberately, and that is what keeps it portable (`FREEZE.md` §4.1).
 *
 * **A domain enum's name is not display text**, which is the mechanism behind two faults in one
 * round. `MediaKind.VOD` printed as `VOD` on the favourites rows, and it was untranslatable
 * besides — AC-NFR-08 forbids user-facing strings outside `strings.xml`, and an enum constant
 * is exactly that with extra steps. CI now greps the UI modules for an enum name used as a label.
 */
@get:StringRes
val MediaKind.labelRes: Int
    get() = when (this) {
        MediaKind.LIVE -> R.string.media_kind_live
        MediaKind.VOD -> R.string.media_kind_movies
        MediaKind.SERIES -> R.string.media_kind_series
    }
