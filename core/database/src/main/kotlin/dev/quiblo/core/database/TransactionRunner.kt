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

import androidx.room.withTransaction

/**
 * Runs several writes as one, for the cases where half of them applied is worse than none.
 *
 * A `@Transaction` DAO method covers a group of statements that live in one DAO — `replaceForSource`
 * is the example. This covers the other shape: work that spans more than one DAO and has to be
 * all-or-nothing anyway. Backup import is the case that needed it, where sources and the favourites
 * that reference them are written through two different DAOs and a failure between the two leaves a
 * restored source whose favourites are missing.
 *
 * An interface rather than a `QuibloDatabase` parameter because `room-ktx` is internal to this
 * module, and because a repository that takes a whole database is a repository a test cannot
 * construct without one.
 */
interface TransactionRunner {

    suspend fun <T> inTransaction(block: suspend () -> T): T

    /**
     * Runs the block as it stands, with no transaction.
     *
     * For tests, which drive fake DAOs holding maps that no transaction could roll back anyway.
     */
    object Direct : TransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }
}

/** The real one. */
class RoomTransactionRunner(private val database: QuibloDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}
