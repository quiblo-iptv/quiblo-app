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

package dev.quiblo.core.data

import dev.quiblo.core.database.dao.WatchEventDao
import dev.quiblo.core.database.entity.WatchEventEntity
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.WatchOrigin
import java.util.Calendar
import java.util.TimeZone

/**
 * The log of what was watched, when, and from where.
 *
 * `resume_positions` keeps one row per title, which answers "where was I" and cannot answer three
 * questions a suggestion wants: **how often** something was watched — a film seen five times is a
 * comfort film and the strongest statement anybody makes without typing — **at what hour**, and
 * **from where**. All three are facts about an occasion, and an occasion is what a row here is.
 *
 * **It is a log, so it is bounded.** A household watching every evening writes a few hundred rows
 * a year, which is nothing; but nothing that only grows should be written without saying where it
 * stops. [RETENTION_DAYS] is a year, which is well past the point where the scorer's own decay has
 * reduced an event to noise.
 *
 * **Nothing here leaves the device**, and there is nowhere for it to go: `FREEZE.md` §2 says there
 * is no backend and §4.5 says the app never phones home. This is a table in the same database as
 * the favourites, deleted with the profile it belongs to.
 */
class WatchEventRepository(
    private val dao: WatchEventDao,
    private val profiles: ProfileRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Writes down one viewing.
     *
     * Called once when playback of a title ends rather than on every position write: an occasion
     * is a sitting, not a heartbeat, and a row per ten seconds would make "how many times" mean
     * "how long".
     */
    @Suppress("LongParameterList")
    suspend fun record(
        sourceId: Long,
        stableKey: String,
        kind: MediaKind,
        title: String,
        fraction: Double,
        origin: WatchOrigin,
    ) {
        dao.insert(
            WatchEventEntity(
                profileId = profiles.activeProfileId,
                sourceId = sourceId,
                stableKey = stableKey,
                kind = kind.name,
                title = title,
                startedAtEpochMillis = now(),
                fraction = fraction.coerceIn(0.0, 1.0),
                origin = origin.name,
            ),
        )
        dao.deleteOlderThan(now() - RETENTION_MILLIS)
    }

    /** How many times each title has been watched through, by provider identity. */
    suspend fun playsByStableKey(sourceId: Long): Map<String, Int> =
        dao.playCounts(profiles.activeProfileId, sourceId, COUNTED_FRACTION)
            .associate { it.stableKey to it.plays }

    /**
     * The hour each title is usually started, as a circular mean.
     *
     * A plain average is wrong on a clock: something watched at 23:00 and at 01:00 averages to
     * midday, which is the one hour it was never watched. Averaging the angles instead puts it at
     * midnight, where it belongs.
     */
    suspend fun usualHourByStableKey(sourceId: Long): Map<String, Int> =
        dao.recent(profiles.activeProfileId, sourceId, EVENT_WINDOW)
            .groupBy { it.stableKey }
            .mapValues { (_, events) -> circularMeanHour(events.map { hourOf(it.startedAtEpochMillis) }) }

    /**
     * The strongest thing the viewer did to reach each title.
     *
     * Strongest rather than most recent: somebody who searched for a title once and then resumed
     * it four times searched for it. The order is the intent it took to get there.
     */
    suspend fun strongestOriginByStableKey(sourceId: Long): Map<String, WatchOrigin> =
        dao.recent(profiles.activeProfileId, sourceId, EVENT_WINDOW)
            .groupBy { it.stableKey }
            .mapValues { (_, events) ->
                events.mapNotNull { runCatching { WatchOrigin.valueOf(it.origin) }.getOrNull() }
                    .minByOrNull { ORIGIN_STRENGTH.indexOf(it) }
                    ?: WatchOrigin.ROW
            }

    /** How many distinct titles have been watched through, for the cold-start rule. */
    suspend fun distinctTitlesWatched(sourceId: Long): Int =
        dao.distinctTitles(profiles.activeProfileId, sourceId, COUNTED_FRACTION)

    /** Forgets one title's occasions, for the viewer who removes it from their history. */
    suspend fun forget(stableKey: String) = dao.deleteFor(profiles.activeProfileId, stableKey)

    private fun hourOf(epochMillis: Long): Int =
        Calendar.getInstance(TimeZone.getDefault())
            .apply { timeInMillis = epochMillis }
            .get(Calendar.HOUR_OF_DAY)

    private fun circularMeanHour(hours: List<Int>): Int {
        if (hours.isEmpty()) return 0
        val radians = hours.map { it * 2 * Math.PI / HOURS_IN_DAY }
        val meanAngle = Math.atan2(
            radians.sumOf { Math.sin(it) } / radians.size,
            radians.sumOf { Math.cos(it) } / radians.size,
        )
        val hour = meanAngle * HOURS_IN_DAY / (2 * Math.PI)
        return ((hour + HOURS_IN_DAY) % HOURS_IN_DAY).toInt()
    }

    private companion object {
        /**
         * How much of something has to be watched for the occasion to count towards "how often".
         *
         * A quarter. Opening something and closing it is not a viewing, and counting it as one is
         * how a title somebody keeps bouncing off becomes their most-watched.
         */
        const val COUNTED_FRACTION = 0.25

        /** A year. Well past the point where the scorer's own decay has made an event noise. */
        const val RETENTION_DAYS = 365L
        const val RETENTION_MILLIS = RETENTION_DAYS * 24 * 60 * 60 * 1000

        /** How many recent events the derived answers read. Bounded, because a log is not. */
        const val EVENT_WINDOW = 500

        /** Most intent first. See [strongestOriginByStableKey]. */
        val ORIGIN_STRENGTH = listOf(
            WatchOrigin.SEARCH,
            WatchOrigin.FAVOURITE,
            WatchOrigin.ROW,
            WatchOrigin.CONTINUE,
        )

        const val HOURS_IN_DAY = 24
    }
}
