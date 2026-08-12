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

package dev.quiblo.player.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.data.ProfileRepository
import dev.quiblo.designsystem.AvatarFaces
import dev.quiblo.designsystem.ProfileAvatar
import dev.quiblo.feature.settings.ProfilesViewModel
import dev.quiblo.player.R
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Stands in front of the app until somebody has said who they are.
 *
 * The same rule as the television: nothing is drawn that would have to read favourites or a
 * resume point before the app knows whose they are. Keeping it here rather than as a
 * navigation destination means no screen below has a state for "no profile" at all.
 */
@Composable
fun ProfileGate(content: @Composable () -> Unit) {
    val profiles: ProfileRepository = koinInject()
    val active by profiles.activeProfile.collectAsStateWithLifecycle()

    if (active == null) ProfileChooser() else content()
}

@Composable
private fun ProfileChooser(viewModel: ProfilesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var avatar: String? by remember { mutableStateOf(null) }
    val guestName = stringResource(R.string.profile_guest)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(
                    if (state.isFirstRun) R.string.profile_welcome else R.string.profile_who,
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.profile_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }

        items(items = state.profiles, key = { it.id }) { profile ->
            Card(
                onClick = { viewModel.select(profile) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    leadingContent = {
                        ProfileAvatar(
                            name = profile.name,
                            avatar = profile.avatar,
                            size = LIST_AVATAR_SIZE,
                        )
                    },
                    headlineContent = { Text(profile.name) },
                    supportingContent = if (profile.isGuest) {
                        { Text(stringResource(R.string.profile_guest_detail)) }
                    } else {
                        null
                    },
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AvatarPicker(
                        selected = avatar,
                        onSelect = { avatar = if (avatar == it) null else it },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.profile_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Button(
                            onClick = {
                                viewModel.addAndSelect(name, avatar)
                                name = ""
                                avatar = null
                            },
                            enabled = name.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.profile_add))
                        }

                        // As prominent as adding, because the person who needs it is the one
                        // who did not ask for any of this and should not have to create
                        // anything to watch something.
                        OutlinedButton(onClick = { viewModel.startGuest(guestName) }) {
                            Text(stringResource(R.string.profile_continue_as_guest))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pick a face, or none.
 *
 * A row of the whole set rather than a dialog behind a button: there are eight of them, they
 * are the size of a thumb, and a picker that has to be opened is a picker most people creating
 * a profile will never see. Selecting the chosen one again clears it, which is the only way
 * back to the initial-on-a-colour fallback once a face has been tapped.
 *
 * No "none" tile. An empty circle offered beside eight pictures reads as a broken one, and the
 * fallback is not an absence — it is what the circle draws when nobody has chosen.
 */
@Composable
private fun AvatarPicker(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        AvatarFaces.forEach { face ->
            val isSelected = face.key == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(face.key) }
                    .padding(3.dp),
            ) {
                ProfileAvatar(name = face.key, avatar = face.key, size = PICKER_AVATAR_SIZE)
            }
        }
    }
}

/** Big enough to recognise beside a name, small enough not to become the row. */
private val LIST_AVATAR_SIZE = 40.dp

/** A thumb's width, so the whole set fits without the row becoming a screen of its own. */
private val PICKER_AVATAR_SIZE = 44.dp
