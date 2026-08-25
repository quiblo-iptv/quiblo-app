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

import dev.quiblo.core.model.MediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs the catalogue's opening queries before a screen asks for them.
 *
 * **The splash screen was five seconds of animation followed by the loading**, and the loading is
 * the part a viewer waits for: the first browse screen opens a thirty-thousand-row table through
 * indexes SQLite has not read a page of since the process started. Every one of those pages is a
 * disk read on a television's storage, which is where the second or two before the first poster
 * goes.
 *
 * So the same queries are run while the sting is playing, and their answers are thrown away. What
 * survives is what makes the difference: SQLite's page cache holds the index pages the real query
 * will walk, and Room has compiled the statements. The screen that follows pays for neither.
 *
 * **The results are deliberately discarded.** Holding them would mean a second copy of the
 * catalogue outliving the screen that wanted it, and the queries are cheap to repeat once the
 * pages are hot — which is the whole claim being made here.
 *
 * Run again whenever the profile changes, because a profile is part of every one of these queries:
 * the favourites join, the hidden categories and the per-viewer merge switch all key off it, so
 * the answers a new viewer gets are new answers. The pages underneath them are the same pages,
 * which is why the second run is the fast one.
 */
class CatalogueWarmup(
    private val sourceRepository: SourceRepository,
    private val channelRepository: ChannelRepository,
    private val categoryRepository: CategoryRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val lock = Mutex()
    private var running: Deferred<Unit>? = null

    /**
     * Warms what the first screens read, and returns when that is done or [timeoutMillis] has
     * passed — whichever is first.
     *
     * The timeout is the caller's promise to the viewer, not a limit on the work: a warm-up that
     * outlives it carries on in the background rather than being cancelled, because the pages it
     * is reading are the pages the screen behind the splash is about to want.
     *
     * Concurrent callers join the run already in flight rather than starting a second one. The
     * splash and the profile gate both ask, and on a cold start they ask within a frame of each
     * other.
     */
    suspend fun warm(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        val work = lock.withLock {
            running?.takeIf { it.isActive } ?: scope.async { run() }.also { running = it }
        }
        withTimeoutOrNull(timeoutMillis) { work.await() }
    }

    private suspend fun run() {
        val sourceId = sourceRepository.observeSources().first().firstOrNull()?.id ?: return

        // The four reads the first two screens make, in the order they are drawn. Sequential
        // rather than parallel: they contend for one database connection and one disk, and
        // racing them would make the first answer later rather than the last one earlier.
        watchHistoryRepository.observeHistory(sourceId, WARMED_KINDS).first()
        channelRepository.observeRecentlyAdded(
            sourceId = sourceId,
            limit = RECENT_WARM_LIMIT,
            sinceEpochMillis = now() - RECENT_WINDOW_MILLIS,
        ).first()

        for (kind in WARMED_KINDS) {
            categoryRepository.observeCategories(sourceId, kind).first()
            channelRepository.observeCategoryRows(sourceId, kind).first()
        }
    }

    private companion object {

        /** Films and series. Live is a different screen with a different query, and it is fast. */
        val WARMED_KINDS = listOf(MediaKind.VOD, MediaKind.SERIES)

        /**
         * As many as the Home screen's first row keeps, so this reads what that reads.
         *
         * Deliberately not the cap the row itself declares: this module cannot see the feature
         * layer, and a warm-up asking for slightly the wrong number still warms the right pages.
         */
        const val RECENT_WARM_LIMIT = 15

        /** Thirty days, the same window Home calls "recently added". */
        const val RECENT_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000

        /** Long enough for a cold catalogue, short enough that a stuck read is not a locked app. */
        const val DEFAULT_TIMEOUT_MILLIS = 4_000L
    }
}
