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
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.model.Source
import dev.quiblo.core.model.SourceKind
import dev.quiblo.source.api.CredentialStore
import dev.quiblo.source.api.Credentials
import dev.quiblo.source.api.MediaSource
import dev.quiblo.source.api.SourceError
import dev.quiblo.source.api.SourceReport
import dev.quiblo.source.api.SourceRequest
import dev.quiblo.source.api.SourceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The outcome of adding or refreshing a source. */
sealed interface RefreshOutcome {

    /** Content was stored. [report] may still describe skipped entries (AC-PL-04). */
    data class Success(val sourceId: Long, val report: SourceReport) : RefreshOutcome

    data class Failure(val error: SourceError) : RefreshOutcome
}

/**
 * The only layer `:feature:*` talks to for source and channel data (docs/PLAN.md §2).
 *
 * Features never see Room entities, Ktor responses, or `MediaSource` implementations.
 * That containment is what makes swapping or adding a protocol a one-module change.
 */
class SourceRepository(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val mediaSources: Map<SourceKind, MediaSource>,
    private val credentialStore: CredentialStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeSources(): Flow<List<Source>> =
        sourceDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /**
     * Adds a source and immediately loads it.
     *
     * The row is inserted first so that a failed load leaves the user with a source they
     * can retry, rather than silently discarding what they typed. A source that has never
     * loaded is distinguishable by its null `lastRefreshedEpochMillis`.
     */
    suspend fun addSource(
        name: String,
        url: String,
        kind: SourceKind,
        credentials: Credentials? = null,
    ): RefreshOutcome {
        val entity = Source(
            id = Source.UNSAVED_ID,
            name = name.trim(),
            kind = kind,
            url = url.trim(),
            createdAtEpochMillis = now(),
        ).toEntity().copy(id = 0L)

        val sourceId = sourceDao.insert(entity)

        // Stored before the first load, because the load needs them, and stored encrypted
        // rather than on the source row so a database dump cannot leak them (AC-XT-04).
        if (credentials != null) {
            credentialStore.put(sourceId, credentials)
        }

        return refresh(sourceId)
    }

    /** Reloads a source, replacing its stored content only if the load succeeds. */
    suspend fun refresh(sourceId: Long): RefreshOutcome {
        val source = sourceDao.findById(sourceId)?.toDomain()
        val mediaSource = source?.let { mediaSources[it.kind] }

        return when {
            source == null -> RefreshOutcome.Failure(SourceError.Unknown("Source not found"))

            mediaSource == null ->
                RefreshOutcome.Failure(SourceError.Unknown("No handler for ${source.kind}"))

            else -> store(sourceId, mediaSource.load(SourceRequest(sourceId, source.url)))
        }
    }

    /**
     * Commits a successful load.
     *
     * A failed load leaves the previously stored channels untouched, so losing the
     * network does not empty a list the user was already looking at (AC-DATA-05).
     */
    private suspend fun store(sourceId: Long, result: SourceResult): RefreshOutcome = when (result) {
        is SourceResult.Failure -> RefreshOutcome.Failure(result.error)

        is SourceResult.Success -> {
            val entities = result.channels.mapIndexed { index, channel ->
                channel.toEntity(sortIndex = index)
            }
            channelDao.replaceForSource(sourceId, entities)
            sourceDao.markRefreshed(sourceId, now())
            RefreshOutcome.Success(sourceId, result.report)
        }
    }

    suspend fun deleteSource(sourceId: Long) {
        // Credentials are not in the database, so the cascade cannot reach them. Clearing
        // them explicitly is what stops a removed account leaving material behind.
        credentialStore.clear(sourceId)
        // Channels and favourites cascade via the foreign key.
        sourceDao.deleteById(sourceId)
    }
}
