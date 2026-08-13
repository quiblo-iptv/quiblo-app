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

package dev.quiblo.tv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Profile
import dev.quiblo.designsystem.ProfileAvatar
import dev.quiblo.feature.settings.ProfilesViewModel
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.ui.common.TvTextField
import org.koin.androidx.compose.koinViewModel

/**
 * Who is watching.
 *
 * The first thing the television shows, and the only screen that stands between a launch and
 * the catalogue. It is not a login: there is no password and nothing is hidden from anybody.
 * It exists so that one household's continue-watching row is not three people's viewing
 * mixed together, which is the state this app was in until now.
 *
 * **Guest is offered as prominently as a profile**, because the person who most needs it is
 * the one who did not ask for any of this — a visitor handed the remote, who should not have
 * to create anything to watch something, and whose evening should not still be listed under
 * "continue watching" tomorrow.
 */
@Composable
fun TvProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfilesViewModel = koinViewModel(key = "tv-profiles"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isAdding by remember { mutableStateOf(false) }
    val guestName = stringResource(R.string.tv_profile_guest)

    // Nothing is behind this screen until somebody is chosen, so back leaves the app rather
    // than dropping into a catalogue that has no idea whose it is. Only while adding does
    // back mean "not that, then".
    BackHandler(enabled = isAdding) { isAdding = false }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (state.isFirstRun) R.string.tv_profile_welcome else R.string.tv_profile_who,
            ),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource(R.string.tv_profile_detail),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )

        if (isAdding) {
            AddProfile(
                onAdd = { name ->
                    viewModel.addAndSelect(name)
                    isAdding = false
                },
                onCancel = { isAdding = false },
            )
        } else {
            Chooser(
                profiles = state.profiles,
                onSelect = viewModel::select,
                onAdd = { isAdding = true },
                onGuest = { viewModel.startGuest(guestName) },
            )
        }
    }
}

/**
 * The tiles: everyone who exists, then Add, then Guest.
 *
 * A `LazyRow` because a household can name as many people as it likes and the row has to
 * scroll rather than shrink the tiles past legibility from a sofa.
 */
@Composable
private fun Chooser(
    profiles: List<Profile>,
    onSelect: (Profile) -> Unit,
    onAdd: () -> Unit,
    onGuest: () -> Unit,
) {
    val firstTile = remember { FocusRequester() }

    // Width, not size.
    //
    // This row used to fill the remaining height, which quietly defeated the centring of the
    // column above it: the heading was pushed to the top of the screen and the tiles sat in a
    // tall box beneath, so the whole screen read as top-aligned however the column was told to
    // arrange itself. Wrapping the height lets the heading, the caption and the tiles centre
    // together as one block (#016).
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
    ) {
        items(items = profiles, key = { it.id }) { profile ->
            ProfileTile(
                label = profile.name,
                icon = Icons.Filled.Person,
                avatar = profile.avatar,
                // A guest is a session rather than a person, so it keeps the plain figure.
                hasAvatar = !profile.isGuest,
                onClick = { onSelect(profile) },
                modifier = if (profile == profiles.firstOrNull()) {
                    Modifier.focusRequester(firstTile)
                } else {
                    Modifier
                },
            )
        }

        item(key = ADD_KEY) {
            ProfileTile(
                label = stringResource(R.string.tv_profile_add),
                icon = Icons.Filled.Add,
                onClick = onAdd,
            )
        }

        item(key = GUEST_KEY) {
            ProfileTile(
                label = stringResource(R.string.tv_profile_guest),
                icon = Icons.Filled.Person,
                // Said plainly on the tile rather than discovered afterwards: whatever is
                // watched here is gone when the session ends, and somebody choosing it
                // deserves to know that before they choose it.
                detail = stringResource(R.string.tv_profile_guest_detail),
                onClick = onGuest,
            )
        }
    }

    // Something must hold focus the moment this appears, or the remote looks broken on the
    // very first screen of the app.
    LaunchedFocus(firstTile, profiles.isNotEmpty())
}

/**
 * Claims focus once, tolerating there being nothing to claim it.
 *
 * On a first run there are no profile tiles, so the requester has nothing attached to it and
 * `requestFocus` would throw. The `LazyRow`'s own first focusable — Add — takes focus in that
 * case, which is the right target anyway.
 */
@Composable
private fun LaunchedFocus(requester: FocusRequester, enabled: Boolean) {
    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (enabled) runCatching { requester.requestFocus() }
    }
}

@Composable
private fun AddProfile(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TvTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.tv_profile_name),
            isLast = true,
            modifier = Modifier
                .width(FIELD_WIDTH)
                .focusRequester(field),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            TvChip(
                label = stringResource(R.string.tv_profile_save),
                isSelected = false,
                onClick = { if (name.isNotBlank()) onAdd(name) },
            )
            TvChip(
                label = stringResource(R.string.tv_profile_cancel),
                isSelected = false,
                onClick = onCancel,
            )
        }
    }
}

/**
 * One tile.
 *
 * Focus scales it and gives it a border, exactly as a poster does — the same affordance in
 * the same app means the same thing. The focusable sits *outside* the animating scale, which
 * is #008's fix and is repeated here rather than rediscovered: a focus node whose bounds
 * change every frame makes whatever scrolls above it chase them.
 */
@Composable
private fun ProfileTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    /**
     * The face this profile chose, when the tile stands for a profile at all.
     *
     * Null on Add and on Guest — neither is somebody, and a picture on either would say it
     * was. They keep [icon], which is what the slot held before anyone could choose.
     */
    avatar: String? = null,
    hasAvatar: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) FOCUSED_SCALE else 1f, label = "profileScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(TILE_WIDTH)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        /*
         * Circular, and it is the slot a picture goes in.
         *
         * Round because that is what a "who is watching" tile is everywhere a viewer has met
         * one, and the square it used to be read as a card rather than as a person (#016).
         *
         * It is deliberately a fixed round frame with its content centred inside, rather than
         * an icon that happens to be drawn on a round background: `013` INC-F0 fills this with
         * a chosen avatar, and a slot that already clips to a circle takes an image without the
         * screen being laid out a second time.
         */
        Box(
            modifier = Modifier
                .size(TILE_SIZE)
                .clip(CircleShape)
                .background(color = Color.White.copy(alpha = if (isFocused) 0.22f else 0.10f))
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (hasAvatar) {
                // Fills the slot the frame was built as. The focus border rings it, so a
                // focused tile still reads as focused with a picture in it (AC-TV-02).
                ProfileAvatar(name = label, avatar = avatar, size = TILE_SIZE)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = if (isFocused) 1f else 0.7f),
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = label,
            color = Color.White.copy(alpha = if (isFocused) 1f else 0.75f),
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        detail?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val SCREEN_PADDING = 48.dp
private val TILE_WIDTH = 160.dp
private val TILE_SIZE = 120.dp
private val FIELD_WIDTH = 420.dp
private const val FOCUSED_SCALE = 1.08f
private const val ADD_KEY = "__add__"
private const val GUEST_KEY = "__guest__"
