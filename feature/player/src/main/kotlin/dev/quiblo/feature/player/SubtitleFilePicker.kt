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

package dev.quiblo.feature.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.quiblo.core.media.PlaybackState
import dev.quiblo.core.model.SubtitleOrigin

/**
 * Opening the device's file picker for a subtitle file, and saying what happened (INC-F10).
 *
 * Shared by both apps on purpose. The television draws its own menu and always will, but which
 * actions exist, what they are called and what the picker filters on are decisions — and the
 * lesson `PLAN-TV.md` §2 records is that sharing the decisions is what stops the two frontends
 * quietly disagreeing about a thing neither of them decided.
 */
@Composable
fun rememberSubtitleFilePicker(
    onPicked: (String) -> Unit,
    onNoPicker: () -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(launcher) {
        {
            // Every type, because subtitle files have no reliable one. A `.srt` is reported as
            // `application/octet-stream` by most providers and as nothing at all by some, so a
            // filter would hide exactly the files this is for. What the file actually is gets
            // decided by reading it, which is the only way that answer is ever right.
            //
            // Wrapped because a television frequently has no document picker installed at all,
            // and an uncaught `ActivityNotFoundException` from a subtitle menu is a crash.
            runCatching { launcher.launch(arrayOf(ANY_TYPE)) }.onFailure { onNoPicker() }
            Unit
        }
    }
}

/**
 * The subtitle actions to offer, or none.
 *
 * "Remove" appears only when there is something to remove, and what there is to remove is read
 * from the item the engine is playing rather than from a flag kept beside it — one source, so the
 * button cannot outlive the file.
 */
@Composable
fun rememberSubtitleActions(state: PlaybackState): List<TrackMenuAction> {
    val add = stringResource(R.string.player_subtitles_add_file)
    val remove = stringResource(R.string.player_subtitles_remove_file)
    val hasPicked = state.item?.subtitles.orEmpty().any { it.origin == SubtitleOrigin.PICKED }

    return remember(add, remove, hasPicked) {
        buildList {
            add(TrackMenuAction(TrackMenuActionKind.ADD_SUBTITLE_FILE, add))
            if (hasPicked) add(TrackMenuAction(TrackMenuActionKind.REMOVE_SUBTITLE_FILE, remove))
        }
    }
}

/** What to say about the last attempt, in words the screen owns. */
@Composable
fun subtitleNoticeText(notice: SubtitleNotice): String = stringResource(
    when (notice) {
        SubtitleNotice.ATTACHED -> R.string.player_subtitles_attached
        SubtitleNotice.NOT_SUBTITLES -> R.string.player_subtitles_not_subtitles
        SubtitleNotice.UNREADABLE -> R.string.player_subtitles_unreadable
        SubtitleNotice.TOO_LARGE -> R.string.player_subtitles_too_large
        SubtitleNotice.NO_PICKER -> R.string.player_subtitles_no_picker
    },
)

/**
 * Turns a menu action into the thing it does.
 *
 * Shared for the same reason [rememberSubtitleActions] is: which action does what is a decision,
 * and a decision written twice is a decision waiting to be made differently the second time.
 */
fun subtitleActionHandler(
    onPick: () -> Unit,
    onRemove: () -> Unit,
): (TrackMenuActionKind) -> Unit = { kind ->
    when (kind) {
        TrackMenuActionKind.ADD_SUBTITLE_FILE -> onPick()
        TrackMenuActionKind.REMOVE_SUBTITLE_FILE -> onRemove()
    }
}

private const val ANY_TYPE = "*/*"
