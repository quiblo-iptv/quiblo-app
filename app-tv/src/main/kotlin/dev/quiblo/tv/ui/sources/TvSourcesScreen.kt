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

package dev.quiblo.tv.ui.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Source
import dev.quiblo.core.model.SourceKind
import dev.quiblo.feature.sources.AddSourceState
import dev.quiblo.feature.sources.SourcesViewModel
import dev.quiblo.tv.R
import org.koin.androidx.compose.koinViewModel

/**
 * Sources, driven by a remote.
 *
 * Text entry on a television is genuinely unpleasant — an Xtream account means a URL, a
 * username and a password typed on an on-screen keyboard, in front of whoever is in the
 * room. It is offered because it has to be, but the form is kept as short as the protocol
 * allows and nothing optional is asked for.
 */
@Composable
fun TvSourcesScreen(modifier: Modifier = Modifier, viewModel: SourcesViewModel = koinViewModel(key = "tv-sources")) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }

    // Swapping one branch for another destroys whatever the remote was pointing at, so each
    // new branch has to claim focus itself. Without this the viewer is left holding a remote
    // that does nothing until they find their way back to the tab bar.
    //
    // **Not on the first composition, though.** Selecting this tab composes the screen, and
    // grabbing focus then pulls the remote out of the tab bar the instant the viewer lands
    // on Sources — so they can no longer continue along the bar to the settings icon, which
    // sits past the last tab. Focus belongs to the bar until the viewer presses down into
    // the content; only a change *after* that — the form opening, a refresh finishing — is
    // this screen's to claim.
    val focusRequester = remember { FocusRequester() }
    var hasComposed by remember { mutableStateOf(false) }
    LaunchedEffect(showForm, addState is AddSourceState.Working) {
        if (hasComposed) focusRequester.tryRequestFocus() else hasComposed = true
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            addState is AddSourceState.Working -> Working()

            showForm -> {
                // AC-TV-03: back must retreat one step, not leave the app. Without this the
                // form's only exit is the Cancel button, and a viewer who presses back —
                // which is what back is for — is thrown out to the launcher having lost
                // whatever they had typed.
                BackHandler { showForm = false }

                AddSourceForm(
                    focusRequester = focusRequester,
                    // The form closes only when the save was actually accepted. It used to
                    // close either way, so a rejected save left no source, no message, and
                    // nothing to distinguish it from never having pressed Save.
                    onAddM3u = { name, url ->
                        viewModel.addM3uSource(name, url).also { showForm = !it }
                    },
                    onAddXtream = { name, url, user, pass ->
                        viewModel.addXtreamSource(name, url, user, pass).also { showForm = !it }
                    },
                    onCancel = { showForm = false },
                )
            }

            else -> SourceList(
                focusRequester = focusRequester,
                sources = sources,
                addState = addState,
                onAdd = { showForm = true },
                onRefresh = viewModel::refresh,
                onDelete = viewModel::delete,
                onDismissResult = viewModel::dismissResult,
            )
        }
    }
}

@Composable
private fun Working() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Text(
                text = stringResource(R.string.tv_sources_working),
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun SourceList(
    focusRequester: FocusRequester,
    sources: List<Source>,
    addState: AddSourceState,
    onAdd: () -> Unit,
    onRefresh: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismissResult: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ResultBanner(addState = addState, onDismiss = onDismissResult)
        }

        item {
            // The one control this screen always has, so it is where focus lands when the
            // screen appears or comes back from the form.
            FocusRow(
                label = stringResource(R.string.tv_sources_add),
                onClick = onAdd,
                modifier = Modifier.focusRequester(focusRequester),
            )
        }

        items(items = sources, key = { it.id }) { source ->
            SourceRow(source = source, onRefresh = { onRefresh(source.id) }, onDelete = { onDelete(source.id) })
        }

        if (sources.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.tv_sources_empty),
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultBanner(addState: AddSourceState, onDismiss: () -> Unit) {
    val message = when (addState) {
        is AddSourceState.Added -> stringResource(R.string.tv_sources_added, addState.channelCount)
        is AddSourceState.Failed -> stringResource(R.string.tv_sources_failed)
        else -> return
    }

    FocusRow(label = message, onClick = onDismiss)
}

@Composable
private fun SourceRow(source: Source, onRefresh: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = source.name,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (source.kind == SourceKind.XTREAM) {
                stringResource(R.string.tv_sources_kind_xtream)
            } else {
                stringResource(R.string.tv_sources_kind_m3u)
            },
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusRow(
                label = stringResource(R.string.tv_sources_refresh),
                onClick = onRefresh,
                modifier = Modifier.width(BUTTON_WIDTH),
            )
            FocusRow(
                label = stringResource(R.string.tv_sources_delete),
                onClick = onDelete,
                modifier = Modifier.width(BUTTON_WIDTH),
            )
        }
    }
}

/**
 * A focusable, pressable row.
 *
 * Everything a remote can act on looks the same on purpose: a viewer should be able to tell
 * what is actionable without learning a vocabulary of shapes.
 */
@Composable
private fun FocusRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (isFocused) 1f else 0.75f),
            fontSize = 17.sp,
        )
    }
}

/**
 * The add-source form.
 *
 * Xtream and M3U in one form rather than a mode toggle: the fields a user leaves empty say
 * which they meant. A URL alone is a playlist; a URL with a username and password is an
 * account. That removes a control from a screen where every control costs a D-pad press.
 */
@Composable
private fun AddSourceForm(
    focusRequester: FocusRequester,
    onAddM3u: (String, String) -> Boolean,
    onAddXtream: (String, String, String, String) -> Boolean,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Set when a save was refused, so the refusal says something. A form that simply does
    // nothing when Save is pressed is indistinguishable from a broken button.
    var wasRejected by remember { mutableStateOf(false) }

    val canSubmit = url.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_sources_form_hint),
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (wasRejected) {
            Text(
                text = stringResource(R.string.tv_sources_incomplete),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TvField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.tv_sources_name),
            modifier = Modifier.focusRequester(focusRequester),
        )
        TvField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.tv_sources_url),
            keyboardType = KeyboardType.Uri,
        )
        TvField(
            value = username,
            onValueChange = { username = it },
            label = stringResource(R.string.tv_sources_username),
        )
        TvField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.tv_sources_password),
            isPassword = true,
            isLast = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusRow(
                label = stringResource(R.string.tv_sources_save),
                onClick = {
                    val accepted = when {
                        !canSubmit -> false
                        // Neither credential given means a playlist; either one given means
                        // an account, and an account needs both.
                        username.isBlank() && password.isBlank() -> onAddM3u(name, url)
                        else -> onAddXtream(name, url, username, password)
                    }
                    wasRejected = !accepted
                },
                modifier = Modifier.width(BUTTON_WIDTH),
            )
            FocusRow(
                label = stringResource(R.string.tv_sources_cancel),
                onClick = onCancel,
                modifier = Modifier.width(BUTTON_WIDTH),
            )
        }
    }
}

/**
 * A text field a remote can actually leave — with the keyboard up or down.
 *
 * Two different traps, and only one of them was handled.
 *
 * **Keyboard down.** A Compose text field treats the D-pad's up and down as cursor movement
 * within the text, so focus enters and never comes out. Intercepting the two keys before
 * the field sees them and moving focus explicitly is the fix, and that is what
 * [onPreviewKeyEvent] below does. Left and right are deliberately left alone: those really
 * are cursor movement, and a viewer editing a long URL needs them.
 *
 * **Keyboard up, which is the usual case on a television and was the broken one.** The IME
 * takes the D-pad for itself — it is how a viewer moves around the on-screen keys — so
 * `onPreviewKeyEvent` never runs at all. Every field typed went into whichever field was
 * focused when the keyboard first appeared, silently, appending to whatever was already
 * there. The way out is the keyboard's own action key: [ImeAction.Next] turns it into
 * "next field", and [ImeAction.Done] on the last one dismisses rather than moving nowhere.
 */
@Composable
private fun TvField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    isLast: Boolean = false,
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            // Clearing focus is what dismisses the keyboard, which is what a viewer wants
            // from the last field — the buttons below are unreachable until it is gone.
            onDone = { focusManager.clearFocus() },
        ),
        modifier = modifier
            .fillMaxWidth(FIELD_WIDTH_FRACTION)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }

                    Key.DirectionUp -> {
                        focusManager.moveFocus(FocusDirection.Up)
                        true
                    }

                    else -> false
                }
            },
    )
}

private val BUTTON_WIDTH = 220.dp
private const val FIELD_WIDTH_FRACTION = 0.6f

/**
 * Requests focus, tolerating there being nothing to focus.
 *
 * The branch being focused may not have composed yet on the frame this runs, and a
 * [FocusRequester] treats that as a programming error and throws. Leaving focus where it is
 * for a frame is the right outcome; bringing the app down is not.
 */
private fun FocusRequester.tryRequestFocus() {
    runCatching { requestFocus() }
}
