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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * How long something runs, in words: `1h 52m`, or `48m` for anything under the hour.
 *
 * Lives here for the reason [TitleFacts] does — a film's detail screen, a series' episode list
 * and both apps all draw this, and a length written two ways in one product reads as two
 * products. Null in gives null out, and callers omit the fact rather than printing a dash.
 *
 * @param seconds the length as the provider counts it. Zero and negative are treated as absent:
 *   panels send `0` for "we did not measure this", and `0m` on screen states something else.
 */
@Composable
fun runtimeLabel(seconds: Int?): String? {
    val total = seconds?.takeIf { it > 0 } ?: return null
    val minutes = total / SECONDS_PER_MINUTE
    // Rounded up rather than down, so a 40-second trailer is "1m" and never "0m". A length of
    // no minutes is not a length anybody wants to read.
    val wholeMinutes = if (minutes == 0) 1 else minutes
    val hours = wholeMinutes / MINUTES_PER_HOUR
    val remainder = wholeMinutes % MINUTES_PER_HOUR

    return when {
        hours == 0 -> stringResource(R.string.runtime_minutes, remainder)
        remainder == 0 -> stringResource(R.string.runtime_hours, hours)
        else -> stringResource(R.string.runtime_hours_minutes, hours, remainder)
    }
}

/** The same thing for a length already counted in minutes, as the metadata service gives it. */
@Composable
fun runtimeLabelFromMinutes(minutes: Int?): String? =
    runtimeLabel(minutes?.takeIf { it > 0 }?.times(SECONDS_PER_MINUTE))

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
