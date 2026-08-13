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

package dev.quiblo.core.data.backup

import dev.quiblo.core.data.ProfileRepository
import dev.quiblo.core.database.TransactionRunner
import dev.quiblo.core.database.dao.FavoriteDao
import dev.quiblo.core.database.dao.SourceDao
import dev.quiblo.core.database.entity.FavoriteEntity
import dev.quiblo.core.database.entity.SourceEntity
import dev.quiblo.core.model.SourceKind
import kotlinx.serialization.json.Json

/** Why an import was refused, or what it restored. */
sealed interface ImportResult {

    /**
     * @param sourcesRestored how many sources were added.
     * @param favoritesRestored how many favourites were reattached.
     * @param credentialsNeeded sources that will not load until a password is re-entered.
     */
    data class Success(
        val sourcesRestored: Int,
        val favoritesRestored: Int,
        val credentialsNeeded: List<String>,
    ) : ImportResult

    /** The file parsed but was written by a newer build (AC-DATA-04). */
    data class VersionTooNew(val fileVersion: Int, val supportedVersion: Int) : ImportResult

    /** The file was not a Quiblo export, or was corrupt. */
    data object Unreadable : ImportResult
}

/**
 * Export and import of the user's configuration (AC-DATA-01 → 04).
 *
 * Channel rows are never exported. They are a cache of what a provider served and can run
 * to tens of thousands of entries; a refresh rebuilds them in seconds. What cannot be
 * rebuilt is which sources the user configured and what they favourited, so that is
 * exactly what an export carries.
 */
class BackupRepository(
    private val sourceDao: SourceDao,
    private val favoriteDao: FavoriteDao,
    /**
     * Whose favourites are exported, and whose they become on import.
     *
     * The backup format has no notion of a profile and deliberately keeps none: a file that
     * carried them would have to answer what happens when it is imported onto a device where
     * those people do not exist. So a backup is one person's favourites — whoever is watching
     * when it is written, and whoever is watching when it is read.
     */
    private val profiles: ProfileRepository,
    private val now: () -> Long = System::currentTimeMillis,
    /**
     * Makes the write half of an import all-or-nothing.
     *
     * The class already refused a file from a newer schema on the grounds that "a half-applied
     * import is worse than none" — and then applied the rest a statement at a time, across two
     * DAOs, with nothing to undo the sources if the favourites failed. This is that stated
     * standard actually being met.
     */
    private val transactions: TransactionRunner = TransactionRunner.Direct,
) {

    suspend fun export(): String {
        val sources = sourceDao.allOnce()

        val backupSources = sources.map { entity ->
            BackupSource(
                name = entity.name,
                kind = entity.kind,
                url = entity.url,
                createdAtEpochMillis = entity.createdAtEpochMillis,
                requiresCredentials = entity.kind == SourceKind.XTREAM.name,
            )
        }

        val backupFavorites = sources.flatMap { entity ->
            favoriteDao.allFor(profiles.activeProfileId, entity.id).map { favorite ->
                BackupFavorite(
                    sourceUrl = entity.url,
                    stableKey = favorite.stableKey,
                    favoritedAtEpochMillis = favorite.favoritedAtEpochMillis,
                )
            }
        }

        return json.encodeToString(
            BackupFile(
                schemaVersion = BackupFile.CURRENT_SCHEMA_VERSION,
                exportedAtEpochMillis = now(),
                sources = backupSources,
                favorites = backupFavorites,
            ),
        )
    }

    /**
     * Restores an export.
     *
     * Additive rather than destructive: a source whose URL is already configured is left
     * alone instead of being duplicated or overwritten, so importing the wrong file is an
     * inconvenience rather than a way to lose what is already set up.
     */
    @Suppress("SwallowedException", "TooGenericExceptionCaught", "ReturnCount")
    suspend fun import(contents: String): ImportResult {
        val backup = try {
            json.decodeFromString<BackupFile>(contents)
        } catch (_: Exception) {
            return ImportResult.Unreadable
        }

        // Checked before anything is written. A file from a future schema may carry fields
        // this build would silently drop, and a half-applied import is worse than none.
        if (backup.schemaVersion > BackupFile.CURRENT_SCHEMA_VERSION) {
            return ImportResult.VersionTooNew(backup.schemaVersion, BackupFile.CURRENT_SCHEMA_VERSION)
        }
        if (backup.schemaVersion < 1) return ImportResult.Unreadable

        // Everything from here writes, so everything from here is one transaction.
        return transactions.inTransaction { apply(backup) }
    }

    private suspend fun apply(backup: BackupFile): ImportResult {
        val existingByUrl = sourceDao.allOnce().associateBy { it.url }
        val idByUrl = existingByUrl.mapValues { it.value.id }.toMutableMap()
        var sourcesRestored = 0
        val credentialsNeeded = mutableListOf<String>()

        backup.sources.forEach { source ->
            if (existingByUrl.containsKey(source.url)) return@forEach
            if (SourceKind.entries.none { it.name == source.kind }) return@forEach

            val id = sourceDao.insert(
                SourceEntity(
                    id = 0L,
                    name = source.name,
                    kind = source.kind,
                    url = source.url,
                    createdAtEpochMillis = source.createdAtEpochMillis,
                    // Never carried over: this device has not loaded this source yet, and
                    // claiming otherwise would hide that it holds no channels.
                    lastRefreshedEpochMillis = null,
                ),
            )
            idByUrl[source.url] = id
            sourcesRestored++
            if (source.requiresCredentials) credentialsNeeded += source.name
        }

        var favoritesRestored = 0
        backup.favorites.forEach { favorite ->
            val sourceId = idByUrl[favorite.sourceUrl] ?: return@forEach
            favoriteDao.add(
                FavoriteEntity(
                    sourceId = sourceId,
                    stableKey = favorite.stableKey,
                    favoritedAtEpochMillis = favorite.favoritedAtEpochMillis,
                    profileId = profiles.activeProfileId,
                ),
            )
            favoritesRestored++
        }

        return ImportResult.Success(
            sourcesRestored = sourcesRestored,
            favoritesRestored = favoritesRestored,
            credentialsNeeded = credentialsNeeded,
        )
    }

    private companion object {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }
}
