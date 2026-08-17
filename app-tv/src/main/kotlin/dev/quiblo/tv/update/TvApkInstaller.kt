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

package dev.quiblo.tv.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where a downloaded release APK lives, and how it is handed to the installer.
 *
 * **One directory, agreed with the manifest.** `res/xml/update_paths.xml` shares exactly this
 * path, so a second opinion about where downloads go would produce a `content://` URI the
 * installer cannot open — and the failure would be a shrug from the system rather than an error
 * anybody could read.
 */
internal object TvApkInstaller {

    fun updatesDirectory(context: Context): File =
        File(context.getExternalFilesDir(null), DIRECTORY).apply { mkdirs() }

    /**
     * Opens the system installer on [apk].
     *
     * The viewer still has to allow it — Android asks whether installs from this source are
     * permitted, and that prompt is the system's and cannot be skipped or pre-answered. This
     * only gets as far as asking.
     *
     * @return false when there is no installer to open, which is the case on a locked-down
     *   television. The caller says so and points at the file, because a verified APK sitting in
     *   a known directory is still installable from a file manager — which is what was asked for.
     */
    fun install(context: Context, apk: File): Boolean {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        }.getOrNull() ?: return false

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            // The installer is another app reading a file inside this one's storage, so the
            // grant travels with the intent. Without it the URI resolves and the read fails.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Started from a context that is not an activity in some call paths, and a new task
            // is what makes the installer come up over the app rather than inside it.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private const val DIRECTORY = "updates"
    private const val MIME_APK = "application/vnd.android.package-archive"
}
