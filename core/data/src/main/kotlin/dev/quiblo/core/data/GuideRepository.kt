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

import dev.quiblo.core.database.dao.ProgrammeDao
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.Programme
import dev.quiblo.core.model.Source
import dev.quiblo.core.model.SourceKind
import dev.quiblo.source.api.GuideResult
import dev.quiblo.source.api.GuideSource
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * What came of asking a panel for one channel's guide.
 *
 * **This was a `Boolean` and that was the defect.** Five different things produced `false` —
 * the source cannot supply a guide at all, the panel is refusing this app, the panel answered
 * with nothing, the request failed, and the channel carries no stream id — and every screen
 * above rendered all five as a blank line. A viewer looking at that blank line cannot tell
 * whether to wait, to change something, or to telephone their provider, and neither could
 * anybody reading a bug report about it.
 */
enum class GuideOutcome {
    /** Listings came back and are cached. The guide works. */
    STORED,

    /** The panel answered and had nothing for this channel — usually no `epg_channel_id`. */
    EMPTY,

    /** The panel is refusing us, or we are inside the backoff from the last time it did. */
    BLOCKED,

    /** This source has no guide to give. An M3U playlist is always this. */
    UNSUPPORTED,

    /** Asked, and it did not work for some other reason. */
    FAILED,
}

/**
 * The electronic programme guide.
 *
 * Guide data is fetched per channel, on demand, and cached. An account with 20,000
 * channels cannot be bulk-fetched — that would be 20,000 requests — so the UI asks only
 * for the channels a user can actually see.
 *
 * Because everything is read from the cache rather than the network, the guide keeps
 * rendering with no connection at all (AC-EPG-05).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideRepository(
    private val programmeDao: ProgrammeDao,
    private val sourceDao: SourceDao,
    private val mediaSources: Map<SourceKind, MediaSource>,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * How often "now" is re-asked.
     *
     * Injected so a test can prove the query re-runs without waiting a real minute.
     */
    private val nowTickMillis: Long = NOW_TICK_MILLIS,
) {

    /**
     * A clock, as a flow.
     *
     * **The timestamp has to be re-read, and this is why.** Both "on now" queries take the
     * current time as a *bind parameter*, so whatever value is passed is fixed for the life of
     * the query. Room only re-emits when the `programmes` table is written, and when it does it
     * re-runs with the same bound timestamp — so a list left open showed whatever was airing at
     * the moment it was opened, indefinitely, while looking entirely correct.
     *
     * A minute is the resolution the screens actually need: a programme boundary is only ever
     * accurate to the minute the panel reported, and re-querying faster would re-map every row
     * for a change nobody can see.
     */
    private fun nowTicks(): Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(nowTickMillis)
        }
    }

    /** Now and next for one channel, straight from the cache. */
    fun observeNowNext(sourceId: Long, channelKey: String): Flow<List<Programme>> =
        nowTicks().flatMapLatest { instant ->
            programmeDao.observeNowNext(sourceId, channelKey, instant)
                .map { rows -> rows.map { it.toDomain() } }
        }

    /** Whatever is airing right now across a source, keyed by channel (AC-EPG-01). */
    fun observeNowPlaying(sourceId: Long): Flow<Map<String, Programme>> =
        nowTicks().flatMapLatest { instant ->
            programmeDao.observeNowPlaying(sourceId, instant)
                .map { rows -> rows.associate { it.channelKey to it.toDomain() } }
        }

    /**
     * One channel's listing across a window, straight from the cache (INC-F4).
     *
     * Read from storage rather than from the fetch, for the reason everything else here is: with
     * no connection the timeline still draws whatever was last stored (AC-EPG-05).
     */
    fun observeSchedule(
        sourceId: Long,
        channelKey: String,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): Flow<List<Programme>> =
        programmeDao.observeBetween(sourceId, channelKey, fromEpochMillis, toEpochMillis)
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * Fetches the whole listing for one channel and replaces what is cached (INC-F4).
     *
     * The same shape as [refreshGuideFor] and deliberately a separate entry point: this one is
     * a heavier call and is made on an explicit request from the viewer, never from a scroll.
     */
    suspend fun refreshFullGuideFor(channel: Channel): GuideOutcome =
        refresh(channel) { source, guideSource, streamId ->
            guideSource.fullGuideFor(
                request = SourceRequest(channel.sourceId, source.url),
                channelKey = channel.stableKey,
                providerStreamId = streamId,
            )
        }

    /**
     * Fetches and caches the guide for one channel, if its source can supply one.
     *
     * Does nothing for a source with no guide capability, which is how an M3U source ends up
     * with no guide UI rather than an empty placeholder (AC-EPG-04).
     *
     * @return which of the several different nothings happened. It used to return `false` for
     *   all of them, and a screen cannot tell a viewer anything useful from that.
     */
    suspend fun refreshGuideFor(channel: Channel): GuideOutcome =
        refresh(channel) { source, guideSource, streamId ->
            guideSource.guideFor(
                request = SourceRequest(channel.sourceId, source.url),
                channelKey = channel.stableKey,
                providerStreamId = streamId,
            )
        }

    /**
     * The parts both refreshes share: the guards, the backoff, and the wholesale replace.
     *
     * Which call to make is the only difference between them, so it is the only thing passed in.
     * Duplicating the block handling would give the two paths two chances to disagree about when
     * a panel has had enough, and the panel does not care which of them asked.
     */
    private suspend fun refresh(
        channel: Channel,
        fetch: suspend (Source, GuideSource, String) -> GuideResult,
    ): GuideOutcome {
        // A channel the provider gave no stream id for, and a source that has no guide to
        // give, are both "there was never going to be a guide here" rather than a failure.
        val streamId = channel.providerStreamId ?: return GuideOutcome.UNSUPPORTED
        if (now() < blockedUntilEpochMillis) return GuideOutcome.BLOCKED
        val source = sourceDao.findById(channel.sourceId)?.toDomain() ?: return GuideOutcome.FAILED
        val guideSource = mediaSources[source.kind] as? GuideSource ?: return GuideOutcome.UNSUPPORTED

        return when (val result = fetch(source, guideSource, streamId)) {
            is GuideResult.Failure -> {
                // The guide is the one thing the app fetches in bulk, so it is the one
                // thing that can keep a panel's anti-flood rule tripped. When the panel
                // says it is refusing us, back off entirely rather than continuing to
                // fire a request per visible row and extending the block.
                if (result.error == SourceError.ProviderBlocked) {
                    blockedUntilEpochMillis = now() + BLOCK_BACKOFF_MILLIS
                    GuideOutcome.BLOCKED
                } else {
                    GuideOutcome.FAILED
                }
            }

            is GuideResult.Success -> {
                // Replaced wholesale rather than merged: the panel's answer is the truth,
                // and a stale programme lingering next to a fresh one is worse than none.
                programmeDao.replaceFor(
                    sourceId = channel.sourceId,
                    channelKey = channel.stableKey,
                    programmes = result.programmes.map { it.toEntity() },
                )
                if (result.programmes.isEmpty()) GuideOutcome.EMPTY else GuideOutcome.STORED
            }
        }
    }

    suspend fun hasGuideFor(sourceId: Long, channelKey: String): Boolean =
        programmeDao.countFor(sourceId, channelKey) > 0

    /** Drops programmes that finished more than a day ago. */
    suspend fun pruneFinished() {
        programmeDao.deleteFinished(now() - RETENTION_MILLIS)
    }

    /**
     * When background guide fetching may resume after a panel refused one.
     *
     * Deliberately in-memory and not persisted: a block is a transient state of the
     * provider, and carrying it across launches would leave a user with no guide long
     * after the panel started answering again.
     */
    @Volatile
    private var blockedUntilEpochMillis: Long = 0L

    private companion object {
        /**
         * How often the "on now" queries re-ask what the time is.
         *
         * A minute, because that is the resolution a programme boundary has in the first place.
         */
        const val NOW_TICK_MILLIS = 60L * 1000L

        const val RETENTION_MILLIS = 24L * 60L * 60L * 1000L

        /** Long enough for a panel's flood window to clear, short enough to self-heal. */
        const val BLOCK_BACKOFF_MILLIS = 15L * 60L * 1000L
    }
}
