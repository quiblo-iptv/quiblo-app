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

import android.content.Context
import android.net.Uri
import dev.quiblo.source.api.ContentFetcher
import dev.quiblo.source.api.FetchResult
import dev.quiblo.source.api.SourceError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a playlist the user picked with the system document picker.
 *
 * Using SAF rather than a storage permission is why Quiblo asks for no file permission
 * at all (AC-NFR-04). The trade-off is that access is granted per document and can be
 * revoked, so an unreadable URI is an expected outcome rather than a bug.
 */
class LocalFileContentFetcher(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ContentFetcher {

    override fun handles(location: String): Boolean {
        val normalized = location.trim().lowercase()
        return normalized.startsWith("content://") || normalized.startsWith("file://")
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetch(location: String): FetchResult = withContext(ioDispatcher) {
        try {
            val uri = Uri.parse(location.trim())
            val body = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }

            if (body == null) {
                FetchResult.Failure(SourceError.FileUnreadable)
            } else {
                FetchResult.Success(body = body, contentType = context.contentResolver.getType(uri))
            }
        } catch (_: SecurityException) {
            // The persisted permission was revoked, or the picker grant expired.
            FetchResult.Failure(SourceError.FileUnreadable)
        } catch (_: Exception) {
            FetchResult.Failure(SourceError.FileUnreadable)
        }
    }
}
