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

package dev.quiblo.core.database

import android.database.SQLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Forces everything committed so far out of the write-ahead log and into the database file.
 *
 * Room writes in WAL mode, where a committed transaction lands in a side file and is folded into
 * the database later. That is the right trade for a phone, and it is the wrong one for an hour of
 * work on a television: a set-top box cut at the mains, or an emulator killed rather than closed,
 * loses whatever the log still held. The transaction was committed and the data is gone anyway,
 * which is exactly the failure this exists to bound.
 *
 * It is not called after every write. A checkpoint is a disk flush, and one per row would make a
 * catalogue scan slower than the network it is waiting on. It is called at intervals during long
 * work and once when that work ends, so the worst a power cut can cost is the interval.
 *
 * An interface rather than a `QuibloDatabase` parameter, for the reason [TransactionRunner] gives:
 * a repository that takes a whole database is a repository a test cannot construct without one.
 */
interface DurabilityCheckpoint {

    /**
     * Flushes the log. Returns having either done so or failed quietly.
     *
     * A checkpoint that cannot run is not worth interrupting the caller for — the data is still
     * committed, and the only thing lost is the guarantee about when it reaches the file. Nothing
     * a viewer is doing should end because a flush was busy.
     */
    suspend fun flush()

    /** Does nothing, for tests driving fake DAOs that have no log to flush. */
    object None : DurabilityCheckpoint {
        override suspend fun flush() = Unit
    }
}

/** The real one. */
class RoomDurabilityCheckpoint(
    private val database: QuibloDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DurabilityCheckpoint {

    override suspend fun flush() = withContext(ioDispatcher) {
        try {
            // TRUNCATE rather than PASSIVE: passive gives up the moment a reader is in the way,
            // which on a scan with four workers is most of the time.
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            Unit
        } catch (_: SQLException) {
            // Busy, read-only, or a journal mode with no log at all. See the note on [flush].
            Unit
        }
    }
}
