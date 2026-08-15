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

import dev.quiblo.core.common.SCRIPT_MASK_UNKNOWN
import dev.quiblo.core.common.scriptMask
import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.source.tmdb.titleIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Fills in the cleaned identity and script mask of rows written before schema 19.
 *
 * `MIGRATION_18_19` adds the three columns and reads nothing, because a migration runs on the
 * first access to the database with every screen waiting behind it, and cleaning sixty-seven
 * thousand titles there is ten to twenty seconds of a television frozen on its own splash. This
 * does the same work afterwards, in batches, off the main thread, while the app is usable.
 *
 * **Nothing depends on it having finished.** A row still carrying [SCRIPT_MASK_UNKNOWN] is
 * filtered in Kotlin by exactly the code that filtered every row before this column existed, so a
 * catalogue halfway through hides what it always hid. That is the whole reason "unknown" is a
 * third value rather than an empty mask: the alternative is a window in which a hidden writing
 * system quietly stops hiding, and the release before this one was spent fixing precisely that.
 *
 * **Run once per launch and then not at all.** After the first pass the query that finds work
 * returns nothing, which costs one indexed count. A fresh install never has work here: rows
 * written by `Channel.toEntity` arrive with their identity already computed.
 */
class CatalogueIdentityBackfill(
    private val channelDao: ChannelDao,
    /**
     * Where the cleaning happens.
     *
     * `Default` and not `IO`: this is eight regex passes and a codepoint walk per row, which is
     * processor work interleaved with short database writes. Putting it on the IO pool would
     * occupy threads sized for blocking with something that never blocks.
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * Fills every waiting row, a batch at a time, and returns how many it wrote.
     *
     * Cancellable at every batch boundary and at every row: the caller is an application scope,
     * and a backfill that ignored cancellation would keep a television's processor busy after the
     * app had gone. Interrupting it costs nothing — the rows it did not reach keep the value that
     * means "not computed", which is the same state it started in.
     */
    suspend fun run(): Int = withContext(dispatcher) {
        var written = 0
        var batch = channelDao.titlesWithoutIdentity(SCRIPT_MASK_UNKNOWN, BATCH_SIZE)
        while (batch.isNotEmpty()) {
            batch.forEach { row ->
                currentCoroutineContext().ensureActive()
                val identity = row.name.titleIdentity()
                channelDao.setIdentity(
                    id = row.id,
                    searchTitle = identity.searchTitle,
                    identityYear = identity.year,
                    scriptMask = row.name.scriptMask(),
                )
                written++
            }
            batch = channelDao.titlesWithoutIdentity(SCRIPT_MASK_UNKNOWN, BATCH_SIZE)
        }
        written
    }

    /** Whether anything is still waiting. Used by tests and by nothing on a hot path. */
    suspend fun remaining(): Int = channelDao.countWithoutIdentity(SCRIPT_MASK_UNKNOWN)

    private companion object {
        /**
         * How many rows are cleaned between cancellation checks and database round trips.
         *
         * Five hundred is a few milliseconds of work and a few hundred kilobytes of strings —
         * small enough that cancelling is prompt and that nothing here ever holds a catalogue in
         * memory, large enough that the query itself is not the cost.
         */
        const val BATCH_SIZE = 500
    }
}
