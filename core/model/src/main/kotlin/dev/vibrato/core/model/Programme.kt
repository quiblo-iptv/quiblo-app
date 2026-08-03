/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.core.model

/**
 * One entry in the electronic programme guide.
 *
 * Deliberately source-agnostic. Only Xtream supplies programme data in v1
 * (docs/FREEZE.md §3), but this type and its storage accept programmes from any
 * provider, so adding XMLTV later needs no schema migration (FREEZE §4.3).
 *
 * @property channelKey the channel this belongs to, matched against a channel's
 *   `tvg-id`/`epg_channel_id` rather than a local row id, so the guide survives a
 *   playlist refresh.
 * @property startEpochMillis UTC. Panels report a variety of local formats alongside a
 *   Unix timestamp; only the timestamp is trustworthy, and rendering converts to the
 *   device's zone at display time (AC-EPG-03).
 */
data class Programme(
    val id: Long,
    val sourceId: Long,
    val channelKey: String,
    val title: String,
    val description: String? = null,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
) {
    /** True when [nowEpochMillis] falls inside this programme. */
    fun isLiveAt(nowEpochMillis: Long): Boolean =
        nowEpochMillis in startEpochMillis until endEpochMillis

    /**
     * How far through the programme [nowEpochMillis] is, from 0f to 1f.
     *
     * Drives the progress bar in AC-EPG-01. Guards against the zero and negative
     * durations that malformed guide data produces.
     */
    fun progressAt(nowEpochMillis: Long): Float {
        val duration = endEpochMillis - startEpochMillis
        if (duration <= 0L) return 0f
        val elapsed = (nowEpochMillis - startEpochMillis).toFloat() / duration.toFloat()
        return elapsed.coerceIn(0f, 1f)
    }
}
