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
 * The year out of whatever a provider calls a release date.
 *
 * Panels are not consistent about this field and never have been: `2021-10-22`, `2021`,
 * `22/10/2021`, `October 22, 2021`, an empty string, and `0000-00-00` have all been seen in the
 * one response shape. So this looks for a plausible year anywhere in the string rather than
 * parsing a format, and returns null when it finds none.
 *
 * Lives in `:core:model` because both a source module reading a panel's field and a screen
 * reading a film's date need the same answer, and two copies of "which four digits are the
 * year" is two places for them to disagree about `22/10/2021`.
 *
 * The range is deliberately narrow. Nothing in a catalogue was released before cinema existed,
 * and a year far in the future is a parsing accident rather than a forthcoming film — without
 * the bound, `1080` out of a quality tag reads as a mediaeval release.
 */
fun releaseYearIn(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    return YEAR.findAll(value)
        .mapNotNull { it.value.toIntOrNull() }
        .firstOrNull { it in EARLIEST_YEAR..LATEST_YEAR }
}

/** Four digits standing alone, so `1080p` and `2160` inside `x2160` are not candidates. */
private val YEAR = Regex("""(?<!\d)\d{4}(?!\d)""")

private const val EARLIEST_YEAR = 1895
private const val LATEST_YEAR = 2100
