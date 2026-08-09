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

package dev.quiblo.tv.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.data.MetadataScanState
import dev.quiblo.core.data.ScanRefusal
import dev.quiblo.core.model.BufferMode
import dev.quiblo.core.model.Category
import dev.quiblo.core.model.MaxBitrateCap
import dev.quiblo.core.model.MediaKind
import dev.quiblo.core.model.SeekInterval
import dev.quiblo.feature.settings.BackupUiState
import dev.quiblo.feature.settings.SettingsViewModel
import dev.quiblo.feature.settings.TmdbCheck
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.ui.common.TvTextField
import org.koin.androidx.compose.koinViewModel

/**
 * Settings, on a remote.
 *
 * Reuses `SettingsViewModel` whole — every control here writes through the same store the
 * phone writes through, so a setting means the same thing on both and can only be wrong in
 * one place. The gear in the top bar used to be focusable with no `onClick` at all, which
 * is what #004 was: not a missing screen so much as a control wired to nothing.
 *
 * **Two phone settings are deliberately absent: theme mode and dynamic colour.** The
 * television theme is always dark and says so in its own documentation — a television is
 * watched at a distance in a dim room, and it has no wallpaper for a dynamic palette to be
 * drawn from. Putting the controls here would mean shipping two switches that change
 * nothing on screen, which is the exact shape of the "hollow feature" this project has
 * already had to delete once.
 */
@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    /**
     * Opens the playlists and accounts.
     *
     * Sources used to be a tab of its own, which put a thing done once — typing a URL and a
     * password on a remote — beside the four things done every evening. It is configuration,
     * and this is where configuration lives.
     */
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(key = "tv-settings"),
) {
    val playerSettings by viewModel.playerSettings.collectAsStateWithLifecycle()
    val channelLogosEnabled by viewModel.channelLogosEnabled.collectAsStateWithLifecycle()
    val tmdbApiKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle()
    val tmdbCheck by viewModel.tmdbCheck.collectAsStateWithLifecycle()
    val categoryKind by viewModel.selectedCategoryKind.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val scanState by viewModel.metadataScan.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    // SAF, so the file lands wherever the viewer chooses and the app needs no storage
    // permission at all (AC-DATA-01, AC-TV-07). The same contracts the phone uses — a
    // television's document picker is the system's problem, not this screen's.
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri: Uri? ->
        if (uri == null) {
            viewModel.dismiss()
        } else {
            viewModel.export { contents ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(contents.toByteArray()) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            viewModel.dismiss()
        } else {
            val contents = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            viewModel.import(contents)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.tv_settings),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }

        // First, because it is the one setting an app with nothing in it needs, and because
        // a viewer arriving here from an empty catalogue is looking for exactly this.
        item { SectionHeading(stringResource(R.string.tv_settings_sources)) }

        item {
            ActionRow(
                label = stringResource(R.string.tv_settings_sources_label),
                description = stringResource(R.string.tv_settings_sources_detail),
                action = stringResource(R.string.tv_settings_sources_manage),
                onClick = onOpenSources,
            )
        }

        item { SectionHeading(stringResource(R.string.tv_settings_playback)) }

        item {
            OptionRow(
                label = stringResource(R.string.tv_settings_seek_interval),
                options = SeekInterval.entries,
                selected = playerSettings.seekInterval,
                labelFor = { stringResource(R.string.tv_settings_seconds, it.seconds) },
                onSelect = viewModel::setSeekInterval,
            )
        }

        item {
            OptionRow(
                label = stringResource(R.string.tv_settings_buffer),
                options = BufferMode.entries,
                selected = playerSettings.bufferMode,
                labelFor = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                onSelect = viewModel::setBufferMode,
            )
        }

        item {
            OptionRow(
                label = stringResource(R.string.tv_settings_bitrate),
                options = MaxBitrateCap.entries,
                selected = playerSettings.maxBitrate,
                labelFor = { cap ->
                    cap.bitsPerSecond
                        ?.let { stringResource(R.string.tv_settings_mbps, it / MBPS) }
                        ?: stringResource(R.string.tv_settings_unlimited)
                },
                onSelect = viewModel::setMaxBitrate,
            )
        }

        item { SectionHeading(stringResource(R.string.tv_settings_artwork)) }

        item {
            OptionRow(
                label = stringResource(R.string.tv_settings_channel_logos),
                description = stringResource(R.string.tv_settings_channel_logos_detail),
                options = listOf(false, true),
                selected = channelLogosEnabled,
                labelFor = {
                    stringResource(if (it) R.string.tv_settings_on else R.string.tv_settings_off)
                },
                onSelect = viewModel::setChannelLogosEnabled,
            )
        }

        item {
            TmdbKeyRow(
                currentKey = tmdbApiKey,
                check = tmdbCheck,
                onSave = viewModel::saveTmdbKey,
                onClear = viewModel::clearTmdbKey,
            )
        }

        // Only with a key, because without one there is nothing to ask and the control would
        // be the sort of button that does nothing this project has already deleted nine of.
        if (!tmdbApiKey.isNullOrBlank()) {
            item {
                MetadataScanRow(
                    state = scanState,
                    onStart = viewModel::startMetadataScan,
                    onCancel = viewModel::cancelMetadataScan,
                    onDismiss = viewModel::dismissMetadataScan,
                )
            }
        }

        item { SectionHeading(stringResource(R.string.tv_settings_categories)) }

        item {
            OptionRow(
                label = stringResource(R.string.tv_settings_category_kind),
                options = listOf(MediaKind.LIVE, MediaKind.VOD, MediaKind.SERIES),
                selected = categoryKind,
                labelFor = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                onSelect = viewModel::selectCategoryKind,
            )
        }

        items(items = categories, key = { "${categoryKind.name}-${it.title}" }) { category ->
            CategoryEditRow(
                category = category,
                onToggleHidden = { viewModel.setCategoryHidden(category, !category.isHidden) },
                onRename = { viewModel.renameCategory(category, it) },
            )
        }

        item { SectionHeading(stringResource(R.string.tv_settings_backup)) }

        item {
            BackupRow(
                state = backupState,
                onExport = { exportLauncher.launch(BACKUP_FILE_NAME) },
                onImport = { importLauncher.launch(arrayOf(BACKUP_MIME_TYPE, ANY_MIME_TYPE)) },
            )
        }
    }
}

/**
 * The metadata key, which is the one setting on this screen that has to be typed.
 *
 * Held locally until Save rather than written per keystroke: a key is meaningless
 * half-entered, and writing one would clear the cached metadata on every character.
 */
@Composable
private fun TmdbKeyRow(
    currentKey: String?,
    check: TmdbCheck,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var draft by remember(currentKey) { mutableStateOf(currentKey.orEmpty()) }

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(LABEL_WIDTH)) {
                Text(
                    text = stringResource(R.string.tv_settings_tmdb),
                    color = Color.White,
                    fontSize = 17.sp,
                )
                Text(
                    text = stringResource(R.string.tv_settings_tmdb_detail),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            TvTextField(
                value = draft,
                onValueChange = { draft = it },
                label = stringResource(R.string.tv_settings_tmdb_field),
                modifier = Modifier.width(FIELD_WIDTH),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = LABEL_WIDTH, top = 8.dp),
        ) {
            TvChip(
                label = stringResource(R.string.tv_settings_save),
                isSelected = false,
                onClick = { onSave(draft) },
            )
            TvChip(
                label = stringResource(R.string.tv_settings_clear),
                isSelected = false,
                onClick = onClear,
            )

            // The service is the only thing that can say whether a key works, so the answer
            // is reported rather than guessed at from its shape.
            val message = when (check) {
                TmdbCheck.Checking -> R.string.tv_settings_tmdb_checking
                TmdbCheck.Accepted -> R.string.tv_settings_tmdb_accepted
                TmdbCheck.Rejected -> R.string.tv_settings_tmdb_rejected
                TmdbCheck.Cleared -> R.string.tv_settings_tmdb_cleared
                TmdbCheck.Idle -> null
            }
            message?.let {
                Text(
                    text = stringResource(it),
                    color = if (check == TmdbCheck.Rejected) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.White.copy(alpha = 0.75f)
                    },
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * The catalogue scan: one control, and a running count of what it has learned.
 *
 * The count is on screen rather than a spinner because this takes the better part of an hour
 * on a large account, and an hour of spinner is indistinguishable from an hour of nothing
 * happening. It is also what tells a viewer they can leave — the numbers keep moving when
 * they come back, because the scan does not belong to this screen.
 */
@Composable
private fun MetadataScanRow(
    state: MetadataScanState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(LABEL_WIDTH)) {
            Text(
                text = stringResource(R.string.tv_settings_scan),
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 22.sp,
            )
            Text(
                text = stringResource(R.string.tv_settings_scan_detail),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Spacer(modifier = Modifier.width(COLUMN_GAP))

        Column {
            val isWorking = state is MetadataScanState.Preparing || state is MetadataScanState.Running
            TvChip(
                label = stringResource(
                    if (isWorking) R.string.tv_settings_scan_stop else R.string.tv_settings_scan_start,
                ),
                isSelected = isWorking,
                onClick = {
                    when {
                        isWorking -> onCancel()
                        // A report has to be put away before the control offers a new scan,
                        // so a finished count is not replaced by a fresh zero on a stray
                        // press — on a remote, a stray press is the normal kind.
                        state !is MetadataScanState.Idle -> onDismiss()
                        else -> onStart()
                    }
                },
            )

            scanMessage(state)?.let {
                Text(
                    text = it,
                    color = if (state is MetadataScanState.Stopped) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.White.copy(alpha = 0.75f)
                    },
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun scanMessage(state: MetadataScanState): String? = when (state) {
    MetadataScanState.Idle -> null
    MetadataScanState.Preparing -> stringResource(R.string.tv_settings_scan_preparing)
    is MetadataScanState.Running ->
        stringResource(R.string.tv_settings_scan_progress, state.done, state.total, state.found)

    is MetadataScanState.Finished ->
        stringResource(R.string.tv_settings_scan_finished, state.found, state.missing)

    is MetadataScanState.Cancelled ->
        stringResource(R.string.tv_settings_scan_cancelled, state.found)

    // Named rather than vague, because the three refusals need three different responses
    // from the viewer: wait, fix the key, or check the network.
    is MetadataScanState.Stopped -> stringResource(
        when (state.reason) {
            ScanRefusal.RATE_LIMITED -> R.string.tv_settings_scan_rate_limited
            ScanRefusal.KEY_REJECTED -> R.string.tv_settings_scan_key_rejected
            ScanRefusal.UNAVAILABLE -> R.string.tv_settings_scan_unavailable
        },
        state.found,
    )
}

/**
 * One category, with the two edits the phone offers: hide it, or rename it.
 *
 * The rename is local and the provider's own title stays the key, so the edit reattaches
 * after every refresh rather than being lost with the ids.
 */
@Composable
private fun CategoryEditRow(
    category: Category,
    onToggleHidden: () -> Unit,
    onRename: (String?) -> Unit,
) {
    // Editing is opened, not always present.
    //
    // Every category used to carry its own text field, so a source with two hundred
    // categories was two hundred input boxes stacked down the screen — visually heavy, and
    // slow to walk past with a remote when all you wanted was to hide one. The pencil is a
    // single focusable that reveals the field when you press it.
    var isEditing by remember(category.title) { mutableStateOf(false) }
    var draft by remember(category.title) { mutableStateOf(category.customName.orEmpty()) }

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(LABEL_WIDTH)) {
                Text(
                    text = category.displayTitle,
                    color = Color.White.copy(alpha = if (category.isHidden) 0.4f else 0.9f),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Only when a rename is in force, so the row stays one line for the many
                // categories nobody has touched.
                if (category.customName != null) {
                    Text(
                        text = category.title,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(COLUMN_GAP))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconChip(
                    icon = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.tv_settings_rename_field),
                    isActive = isEditing,
                    onClick = { isEditing = !isEditing },
                )
                TvChip(
                    label = stringResource(
                        if (category.isHidden) R.string.tv_settings_show else R.string.tv_settings_hide,
                    ),
                    isSelected = category.isHidden,
                    onClick = onToggleHidden,
                )
            }
        }

        if (isEditing) {
            Row(
                modifier = Modifier.padding(start = LABEL_WIDTH + COLUMN_GAP, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = stringResource(R.string.tv_settings_rename_field),
                    isLast = true,
                    modifier = Modifier.width(RENAME_WIDTH),
                )
                TvChip(
                    label = stringResource(R.string.tv_settings_apply),
                    isSelected = false,
                    // A blank rename means "use the provider name", which is also how the
                    // override row is removed rather than left saying nothing.
                    onClick = {
                        onRename(draft.ifBlank { null })
                        isEditing = false
                    },
                )
            }
        }
    }
}

/** A square control carrying an icon rather than a word. Same focus treatment as a chip. */
@Composable
private fun IconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(38.dp)
            .background(
                color = if (isActive) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (isFocused || isActive) 1f else 0.6f),
            modifier = Modifier.size(19.dp),
        )
    }
}

/** Export and import, and whatever the last attempt had to say about itself. */
@Composable
private fun BackupRow(state: BackupUiState, onExport: () -> Unit, onImport: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(LABEL_WIDTH)) {
                Text(
                    text = stringResource(R.string.tv_settings_backup_label),
                    color = Color.White,
                    fontSize = 17.sp,
                )
                Text(
                    text = stringResource(R.string.tv_settings_backup_detail),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvChip(
                    label = stringResource(R.string.tv_settings_export),
                    isSelected = false,
                    onClick = onExport,
                )
                TvChip(
                    label = stringResource(R.string.tv_settings_import),
                    isSelected = false,
                    onClick = onImport,
                )
            }
        }

        backupMessage(state)?.let {
            Text(
                text = it,
                color = if (state is BackupUiState.Failed || state is BackupUiState.ImportRejected) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.White.copy(alpha = 0.75f)
                },
                fontSize = 14.sp,
                modifier = Modifier.padding(start = LABEL_WIDTH, top = 8.dp),
            )
        }
    }
}

@Composable
private fun backupMessage(state: BackupUiState): String? = when (state) {
    BackupUiState.Idle -> null
    BackupUiState.Working -> stringResource(R.string.tv_settings_backup_working)
    is BackupUiState.Exported -> stringResource(R.string.tv_settings_backup_exported, state.byteCount)
    is BackupUiState.Imported -> stringResource(
        R.string.tv_settings_backup_imported,
        state.sourcesRestored,
        state.favoritesRestored,
    )
    // Names both versions rather than failing vaguely, as the phone's wording does — a
    // viewer who is told only "wrong format" has nothing to act on.
    is BackupUiState.ImportRejected -> stringResource(
        R.string.tv_settings_backup_rejected,
        state.fileVersion,
        state.supportedVersion,
    )
    BackupUiState.Failed -> stringResource(R.string.tv_settings_backup_failed)
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
    )
}

/**
 * One setting: a name on the left, its choices as a row of chips on the right.
 *
 * Every choice is on screen rather than behind a dialog, because a remote opening a dialog
 * to pick one of four values is three presses where one will do, and because seeing the
 * alternatives is most of how a viewer understands what a setting means. Up and down move
 * between settings; left and right move between the values of one.
 */
@Composable
private fun <T> OptionRow(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        // Top, not centre. A two-line description used to centre itself against the chips,
        // so the chips landed beside the middle of the sentence and read as part of it.
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(LABEL_WIDTH)) {
            Text(text = label, color = Color.White, fontSize = 17.sp, lineHeight = 22.sp)
            description?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(COLUMN_GAP))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.focusGroup(),
        ) {
            options.forEach { option ->
                TvChip(
                    label = labelFor(option),
                    isSelected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

/**
 * A row that leads somewhere rather than setting a value.
 *
 * Laid out exactly like [OptionRow] — the same names column, the same gap, the same chip — so
 * that "go here" and "choose this" read as one list to a viewer walking down it with a
 * remote, rather than as two kinds of thing that happen to share a screen.
 */
@Composable
private fun ActionRow(label: String, description: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(LABEL_WIDTH)) {
            Text(text = label, color = Color.White, fontSize = 17.sp, lineHeight = 22.sp)
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Spacer(modifier = Modifier.width(COLUMN_GAP))

        TvChip(label = action, isSelected = false, onClick = onClick)
    }
}

/** The names column. Wide enough for a two-line description without crowding it. */
private val LABEL_WIDTH = 400.dp

/** Air between the names and their controls. Without it the two columns read as one. */
private val COLUMN_GAP = 40.dp
private val FIELD_WIDTH = 460.dp
private val RENAME_WIDTH = 320.dp
private const val MBPS = 1_000_000
private const val BACKUP_MIME_TYPE = "application/json"
private const val BACKUP_FILE_NAME = "quiblo-backup.json"
private const val ANY_MIME_TYPE = "*/*"
