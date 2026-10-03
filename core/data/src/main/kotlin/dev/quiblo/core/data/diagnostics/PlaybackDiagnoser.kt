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

package dev.quiblo.core.data.diagnostics

import dev.quiblo.core.data.toDomain
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.model.SourceKind
import dev.quiblo.core.network.ConnectivityChecker
import dev.quiblo.source.api.AccountHealth
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.SourceRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One playback failure, explained (`FEAT-035`).
 *
 * @property account what the provider said about the account, for the figures a headline names —
 *   the expiry date, the screens in use. Null when there was no answer.
 * @property details one line of redacted evidence for a bug report. Host only: no username, no
 *   password, no path (AC-XT-04).
 */
data class Diagnosis(
    val verdict: Verdict,
    val account: AccountHealth?,
    val details: String,
    val title: String,
    val atEpochMillis: Long,
)

/**
 * Gathers the evidence for a failed stream and hands it to [verdict].
 *
 * Three questions, cheapest first: is this device online; what does the provider say about the
 * account; what did the stream itself say. The second is one authentication call, made through the
 * source's own rate limiter, and its answer is kept for [ACCOUNT_CACHE_MILLIS] per source — a
 * viewer zapping through five dead channels asks the panel once, not five times.
 *
 * Nothing here is written to disk or sent anywhere but the provider the viewer configured. Every
 * diagnosis is also added to [log], which lives in memory and dies with the process.
 */
class PlaybackDiagnoser(
    private val sourceDao: SourceDao,
    private val mediaSources: Map<SourceKind, MediaSource>,
    private val connectivity: ConnectivityChecker,
    private val log: PlaybackLog,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private class Cached(val evidence: AccountEvidence, val atEpochMillis: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<Long, Cached>()

    /**
     * Explains why [streamUrl] failed.
     *
     * [streamUrl] is read for its host and nothing else, and never stored.
     */
    suspend fun diagnose(
        sourceId: Long,
        streamUrl: String,
        title: String,
        stream: StreamEvidence,
    ): Diagnosis {
        val online = connectivity.isOnline()
        // Offline settles it, and the provider is not asked a question the device cannot carry.
        val account = if (online) accountEvidence(sourceId) else AccountEvidence.Unavailable
        val health = (account as? AccountEvidence.Answered)?.health

        return Diagnosis(
            verdict = verdict(online, account, stream),
            account = health,
            details = detailsLine(stream, account, hostOf(streamUrl)),
            title = title,
            atEpochMillis = now(),
        ).also(log::record)
    }

    /** The account answer, from the cache when it is fresh enough. */
    private suspend fun accountEvidence(sourceId: Long): AccountEvidence = mutex.withLock {
        cache[sourceId]
            ?.takeIf { now() - it.atEpochMillis < ACCOUNT_CACHE_MILLIS }
            ?.evidence
            ?: askProvider(sourceId).also { cache[sourceId] = Cached(it, now()) }
    }

    private suspend fun askProvider(sourceId: Long): AccountEvidence {
        val source = sourceDao.findById(sourceId)?.toDomain() ?: return AccountEvidence.Unavailable
        val mediaSource = mediaSources[source.kind] ?: return AccountEvidence.Unavailable
        if (!mediaSource.checksAccount) return AccountEvidence.NotApplicable

        return mediaSource.accountHealth(SourceRequest(sourceId, source.url))
            ?.let(AccountEvidence::Answered)
            ?: AccountEvidence.Unavailable
    }

    private companion object {
        /** Long enough to cover a run of channel changes, short enough to notice a renewal. */
        const val ACCOUNT_CACHE_MILLIS = 60_000L
    }
}
