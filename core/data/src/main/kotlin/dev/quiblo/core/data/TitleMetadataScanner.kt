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

import dev.quiblo.core.database.dao.ChannelDao
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.tmdb.TmdbAnswer
import dev.quiblo.source.tmdb.TmdbRefusal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Why a scan stopped early, in terms a screen can act on.
 *
 * A restatement of `TmdbRefusal` rather than a reuse of it, so that nothing above
 * `:core:data` has to know a metadata service exists, let alone which one. The rule in
 * PLAN.md §2 is that features talk to `:core:data` and nothing else; a settings screen
 * importing `:source:tmdb` to render a message would be the first crack in it.
 */
enum class ScanRefusal {
    /** The service asked us to slow down. Waiting fixes it. */
    RATE_LIMITED,

    /** The key was refused. Waiting does not fix it. */
    KEY_REJECTED,

    /** Unreachable, timed out, or answering with nonsense. Worth trying later. */
    UNAVAILABLE,
}

/** How far a scan has got, and how it ended if it has. */
sealed interface MetadataScanState {

    /** Never run, or run and then acknowledged. */
    data object Idle : MetadataScanState

    /** Reading the catalogue and subtracting what is already cached. No requests yet. */
    data object Preparing : MetadataScanState

    data class Running(
        val done: Int,
        val total: Int,
        val found: Int,
        val missing: Int,
    ) : MetadataScanState

    /** Every title asked about. [missing] are titles TMDB genuinely holds nothing for. */
    data class Finished(val total: Int, val found: Int, val missing: Int) : MetadataScanState

    /**
     * TMDB stopped answering, so the scan stopped asking.
     *
     * Everything fetched up to that point is cached and counted; starting again resumes from
     * there rather than from the beginning.
     */
    data class Stopped(
        val reason: ScanRefusal,
        val done: Int,
        val total: Int,
        val found: Int,
    ) : MetadataScanState

    data class Cancelled(val done: Int, val total: Int, val found: Int) : MetadataScanState
}

/**
 * How far along a scan is, from 0 to 1, or null when there is no bar to draw.
 *
 * Null while preparing — the total is not known until the work list is built, and a bar
 * drawn against a total of zero would sit at either end and mean neither. A stopped or
 * cancelled scan keeps its fraction rather than clearing it: where it got to is exactly what
 * a viewer needs, since starting again carries on from there.
 *
 * Shared by both settings screens so the phone and the television cannot disagree about what
 * "half way" means.
 */
val MetadataScanState.progressFraction: Float?
    get() = when (this) {
        is MetadataScanState.Running -> fractionOf(done, total)
        is MetadataScanState.Stopped -> fractionOf(done, total)
        is MetadataScanState.Cancelled -> fractionOf(done, total)
        MetadataScanState.Idle, MetadataScanState.Preparing -> null
        is MetadataScanState.Finished -> 1f
    }

private fun fractionOf(done: Int, total: Int): Float? =
    if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null

/**
 * Fills the metadata cache for a whole catalogue, one title at a time.
 *
 * The genre filter and the scores on posters are only as complete as this cache, and the
 * cache normally fills a poster at a time as somebody browses — which means a viewer who has
 * looked at four rows can filter by the genres of four rows. This walks the lot instead.
 *
 * **It is one request per title, not two.** The cheap search record now carries genres (see
 * `TmdbClient.summary`), and plots and cast are what a detail screen fetches when it opens.
 * Asking for the full record here would double an hour-long job for facts nothing being
 * filled in ever shows.
 *
 * Three properties matter more than speed, and all three come from the same decision to
 * write each answer down as it arrives:
 *
 * - **Resumable.** Titles already answered for are subtracted before anything is asked, so a
 *   scan interrupted at 60% is a scan that starts again at 60%.
 * - **Interruptible.** Cancelling loses nothing but the requests in flight.
 * - **Honest.** A refusal — a rate limit, a rejected key, a dead network — ends the scan
 *   rather than being recorded as thirty thousand titles that match nothing. That was the
 *   defect this feature would have shipped with; see [TmdbAnswer].
 */
class TitleMetadataScanner(
    private val channelDao: ChannelDao,
    private val metadataRepository: TitleMetadataRepository,
    /**
     * Deliberately not a ViewModel's scope.
     *
     * The scan outlives the screen that starts it: an hour of lookups that stopped the
     * moment a viewer pressed Back would never finish on a television, where Back is how you
     * leave every screen. As a singleton, this scope is the application's.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val workers: Int = DEFAULT_WORKERS,
) {

    private val _state = MutableStateFlow<MetadataScanState>(MetadataScanState.Idle)
    val state: StateFlow<MetadataScanState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * The one reason to stop everything.
     *
     * Read by every worker before it asks, so a refusal takes effect within one request per
     * worker rather than at the end of the list. `@Volatile` because those workers are on
     * different threads of the IO dispatcher.
     */
    @Volatile
    private var refusal: ScanRefusal? = null

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Starts a scan of [sourceId], or does nothing if one is already running.
     *
     * Silently does nothing when no key is configured. There is no error to report: the
     * control that starts this is only offered once a key has been accepted, and a race
     * between clearing the key and pressing it is not worth a message.
     */
    fun start(sourceId: Long) {
        // Synchronised because the check and the launch are two steps, and this is a singleton
        // both frontends can reach: two presses close together each saw "not running" and each
        // started a scan, so the same catalogue was walked twice against one rate limit.
        synchronized(this) {
            if (isRunning || !metadataRepository.isEnabled) return
            refusal = null
            job = scope.launch { scan(sourceId) }
        }
    }

    /** Stops the scan. What has already been written down stays written down. */
    fun cancel() {
        synchronized(this) { job }?.cancel()
    }

    /** Returns the reported state to [MetadataScanState.Idle] once the viewer has seen it. */
    fun acknowledge() {
        if (!isRunning) _state.value = MetadataScanState.Idle
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private suspend fun scan(sourceId: Long) {
        _state.value = MetadataScanState.Preparing

        val work = workList(sourceId)
        val total = work.size
        var done = 0
        var found = 0
        var missing = 0

        _state.value = MetadataScanState.Running(done = 0, total = total, found = 0, missing = 0)

        try {
            work.asFlow()
                .flatMapMerge(concurrency = workers) { item ->
                    flow {
                        // Checked here rather than only at collection, so a refusal stops the
                        // requests themselves instead of merely stopping the counting.
                        if (refusal == null) {
                            emit(metadataRepository.answerFor(item.title, item.kind, acceptPartial = true))
                        }
                    }
                }
                .collect { answer ->
                    done++
                    when (answer) {
                        is TmdbAnswer.Found -> found++
                        TmdbAnswer.NoMatch -> missing++
                        is TmdbAnswer.Refused -> refusal = answer.reason.toScanRefusal()
                    }
                    _state.value = MetadataScanState.Running(done, total, found, missing)
                }
        } catch (cancellation: CancellationException) {
            // Reported before rethrowing: a StateFlow write does not suspend, so it still
            // lands, and a viewer who cancels deserves to see where it got to.
            _state.value = MetadataScanState.Cancelled(done = done, total = total, found = found)
            throw cancellation
        }

        _state.value = refusal
            ?.let { MetadataScanState.Stopped(reason = it, done = done, total = total, found = found) }
            ?: MetadataScanState.Finished(total = total, found = found, missing = missing)
    }

    /**
     * Every title worth asking about, once each.
     *
     * Three reductions, in this order, and each of them is most of the work saved:
     *
     * 1. **Live channels are excluded by the query.** A television channel is not a title.
     * 2. **Collapsed to distinct cleaned titles.** A panel that lists one film in four
     *    qualities has one title to look up, and the cache is keyed by the cleaned form —
     *    so the other three would be cache hits anyway, counted as work that was never work.
     * 3. **Minus what the cache already answers.** This is what makes a second scan cost
     *    almost nothing, and what makes an interrupted one resume.
     */
    private suspend fun workList(sourceId: Long): List<ScanItem> {
        val cached = metadataRepository.freshlyCachedKeys()

        // Insertion-ordered, so the scan walks the catalogue in the provider's own order and
        // a viewer watching the counter sees it move through something recognisable.
        val chosen = LinkedHashMap<CacheIdentity, ScanItem>()

        channelDao.titlesForMetadata(sourceId).forEach { row ->
            val identity = row.name.cacheIdentity(row.kind) ?: return@forEach
            val kind = row.kind.toMediaKindOrNull() ?: return@forEach
            if (identity in cached) return@forEach

            // First row wins, and since #024 that is simply true rather than a compromise.
            // The year is part of the identity now, so every row sharing one is the same
            // title at a different quality and any of them asks the same question — where
            // this used to have to prefer whichever row still carried its year, because
            // without it a 1996 Fargo and a 2014 Fargo were the same work item.
            chosen.getOrPut(identity) { ScanItem(row.name, kind) }
        }

        return chosen.values.toList()
    }

    /** One title to ask about: the provider's own wording, and which catalogue to ask. */
    private data class ScanItem(val title: String, val kind: MediaKind)

    private companion object {
        /**
         * How many lookups are in flight at once.
         *
         * Not the throttle — `TmdbRateLimiter` is, and it is what decides the sustained rate.
         * This only decides how much of the waiting happens in parallel: enough workers that
         * a slow reply does not idle the budget, few enough that cancelling does not leave a
         * crowd of requests in flight. The distinction between the two numbers is the lesson
         * from the panel blocks — a concurrency cap is not a rate limit.
         */
        const val DEFAULT_WORKERS = 4
    }
}

/**
 * The two kinds a metadata service has anything to say about.
 *
 * Null for anything else, live included, rather than throwing: the column is a string
 * written by whatever wrote the row, and a scan is not the place to discover that a
 * database holds a kind this build has never heard of.
 */
internal fun TmdbRefusal.toScanRefusal(): ScanRefusal = when (this) {
    TmdbRefusal.RATE_LIMITED -> ScanRefusal.RATE_LIMITED
    TmdbRefusal.KEY_REJECTED -> ScanRefusal.KEY_REJECTED
    TmdbRefusal.UNAVAILABLE -> ScanRefusal.UNAVAILABLE
}

private fun String.toMediaKindOrNull(): MediaKind? = when (this) {
    MediaKind.VOD.name -> MediaKind.VOD
    MediaKind.SERIES.name -> MediaKind.SERIES
    else -> null
}
