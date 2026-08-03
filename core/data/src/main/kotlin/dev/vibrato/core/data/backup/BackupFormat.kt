/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.core.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk shape of an export (AC-DATA-01).
 *
 * Versioned from the first release rather than from the first breaking change, because a
 * file written today will outlive the code that wrote it, and a file with no version is
 * one that can never be safely rejected (AC-DATA-04).
 *
 * Credentials are deliberately absent. An export is a document a user will put in cloud
 * storage or a chat window without much thought, and the only representation of an Xtream
 * password that is safe in that setting is none at all (AC-DATA-03). Import therefore
 * restores sources and favourites, and asks for passwords again.
 */
@Serializable
data class BackupFile(
    @SerialName("schema_version")
    val schemaVersion: Int,
    @SerialName("exported_at_epoch_millis")
    val exportedAtEpochMillis: Long,
    val sources: List<BackupSource> = emptyList(),
    val favorites: List<BackupFavorite> = emptyList(),
) {
    companion object {
        /**
         * The version this build writes, and the highest it can read.
         *
         * Bump on any change that an older build could misread. Adding an optional field
         * with a default is not such a change; removing one, or changing what an existing
         * field means, is.
         */
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * One configured source.
 *
 * Carries no row id: ids are local to a database, and an import into an app that already
 * has sources must not be able to collide with or overwrite them.
 */
@Serializable
data class BackupSource(
    val name: String,
    val kind: String,
    val url: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    /**
     * True when this source needed credentials that the export did not carry.
     *
     * Lets the import tell the user which sources will need a password typed in again,
     * rather than leaving them to discover it when a refresh fails.
     */
    @SerialName("requires_credentials")
    val requiresCredentials: Boolean = false,
)

/**
 * One favourite, keyed the way favourites are keyed everywhere else.
 *
 * [sourceUrl] rather than a source id, so a favourite still finds its source after an
 * import has assigned entirely new ids (AC-FAV-03 applied across devices).
 */
@Serializable
data class BackupFavorite(
    @SerialName("source_url")
    val sourceUrl: String,
    @SerialName("stable_key")
    val stableKey: String,
    @SerialName("favorited_at_epoch_millis")
    val favoritedAtEpochMillis: Long,
)
