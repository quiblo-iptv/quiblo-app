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

package dev.quiblo.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A text field a remote can actually leave — with the keyboard up or down.
 *
 * One implementation for the whole television app rather than one per screen. Two screens
 * need typing and both meet the same two traps; a copy each is how a fix lands in one and
 * is forgotten in the other, which is the failure this project has already had with the
 * guide-request guards.
 *
 * **The keyboard opens on a press, never on focus.** A `BasicTextField` asks for the IME the
 * moment it is focused, and on a television focus is something a viewer passes *through*: moving
 * down a settings list threw an on-screen keyboard over the screen at every field on the way,
 * and getting rid of it cost a Back press each time. So the field has two stages that measure
 * identically — a resting box that is focusable and does nothing, and an editor composed only
 * after a centre press. Nothing about this is a television quirk to be sorry about: a phone
 * field is also tapped before it is typed into, and focus there is the tap.
 *
 * **Keyboard down.** A Compose text field treats the D-pad's up and down as cursor movement
 * within the text, so focus enters and never comes out. The keys are intercepted before the
 * field sees them and focus is moved explicitly. Left and right are deliberately left alone:
 * those really are cursor movement, and a viewer editing a long URL needs them.
 *
 * **Keyboard up, which is the usual case on a television and was the broken one.** The IME
 * takes the D-pad for itself — it is how a viewer moves around the on-screen keys — so the
 * interception above never runs at all. Every field typed went into whichever one was
 * focused when the keyboard first appeared, silently, appending to what was already there.
 * The way out is the keyboard's own action key: [ImeAction.Next] makes it "next field", and
 * [ImeAction.Done] on the last one dismisses the keyboard, which is what makes the buttons
 * underneath reachable at all.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    isLast: Boolean = false,
    /**
     * Hands Right to whatever sits beside this field, instead of moving the cursor.
     *
     * **Off by default, and it has to be** — this is the objection `docs/STOPPERS.md` S10 raised
     * against putting a control next to a text box: left and right belong to the cursor, so a
     * neighbour cannot be reached by a remote moving horizontally. That is true of a field
     * somebody is editing a URL or a password in, where losing the cursor keys would be a real
     * loss.
     *
     * It is not true of the search field. Its text is a few words typed on an on-screen
     * keyboard, which does its own editing, and nobody has ever walked a search term character
     * by character with a D-pad. So that one field trades the cursor for a neighbour, and every
     * other field keeps it. One rule, one opt-in, which is the bar S10 set for this being worth
     * doing at all.
     */
    onExitRight: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    var isEditing by remember { mutableStateOf(false) }
    var exit by remember { mutableStateOf<FieldExit?>(null) }

    /*
     * One requester across both stages, because only one of them is ever composed.
     *
     * Leaving the editor destroys the focused node, and a screen with nothing focused is a
     * screen a remote cannot move on. So every way out of the editor lands here first, and
     * whatever else that way out wanted — the next field, the row above — happens after.
     */
    val anchor = remember { FocusRequester() }

    LaunchedEffect(exit) {
        val leaving = exit ?: return@LaunchedEffect
        keyboard?.hide()
        anchor.tryRequestFocus()
        leaving.direction?.let(focusManager::moveFocus)
        leaving.andThen?.invoke()
        exit = null
    }

    val stopEditing: (FieldExit) -> Unit = {
        isEditing = false
        exit = it
    }

    if (isEditing) {
        EditingField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            keyboardType = keyboardType,
            isPassword = isPassword,
            isLast = isLast,
            onExitRight = onExitRight,
            stopEditing = stopEditing,
            modifier = modifier.focusRequester(anchor),
        )
    } else {
        RestingField(
            value = value,
            label = label,
            isPassword = isPassword,
            onStartEditing = { isEditing = true },
            onExitRight = onExitRight,
            modifier = modifier.focusRequester(anchor),
        )
    }
}

/**
 * The field as it looks when nobody is typing into it.
 *
 * Focusable and clickable and otherwise inert — no `BasicTextField`, which is the whole point:
 * a component that is not composed cannot ask for a keyboard. It draws the same [FieldBox] as
 * the editor so that swapping between the two changes no measured bound. That is not tidiness.
 * A focus target whose size changes underneath the scrollable holding it is defect #008, and
 * the note on `TvPoster` explains what that cost to find.
 */
@Composable
private fun RestingField(
    value: String,
    label: String,
    isPassword: Boolean,
    onStartEditing: () -> Unit,
    onExitRight: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    FieldBox(
        isFocused = isFocused,
        modifier = modifier
            // `clickable` is the focus target as well as the press target, so there is no
            // `focusable()` in front of it. One in front makes a second focus target, and then
            // the node the remote focuses is the outer of the two while the key handling sits on
            // the inner — the field takes focus and centre does nothing.
            //
            // `indication = null` for the same reason every other television control does it:
            // a ripple is a fingertip's feedback and there is no fingertip. The border is the
            // feedback here.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onStartEditing,
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Only Right, and only where a field asked for it. Up and down are ordinary
                // traversal here — there is no cursor to compete with them while resting.
                if (event.key == Key.DirectionRight) {
                    onExitRight?.let {
                        it()
                        true
                    } ?: false
                } else {
                    false
                }
            },
    ) {
        FieldText(value = value, label = label, isPassword = isPassword)
    }
}

/** The field once a viewer has pressed it, keyboard and all. */
@Composable
private fun EditingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    isPassword: Boolean,
    isLast: Boolean,
    onExitRight: (() -> Unit)?,
    stopEditing: (FieldExit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val editor = remember { FocusRequester() }

    // The press has already happened by the time this composes, so asking for focus here is
    // asking for the keyboard the viewer just asked for.
    LaunchedEffect(Unit) { editor.tryRequestFocus() }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = TEXT_SIZE),
        cursorBrush = SolidColor(Color.White),
        interactionSource = interactionSource,
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
            onNext = { stopEditing(FieldExit(direction = FocusDirection.Down)) },
            onDone = { stopEditing(FieldExit()) },
        ),
        decorationBox = { field ->
            FieldBox(isFocused = isFocused) {
                if (value.isEmpty()) {
                    Text(text = label, color = PLACEHOLDER, fontSize = TEXT_SIZE)
                }
                field()
            }
        },
        modifier = modifier
            .focusRequester(editor)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    // Up and down leave the editor rather than walking the cursor, and take
                    // the keyboard with them. Where they land is unchanged from before there
                    // were two stages.
                    Key.DirectionDown -> {
                        stopEditing(FieldExit(direction = FocusDirection.Down))
                        true
                    }

                    Key.DirectionUp -> {
                        stopEditing(FieldExit(direction = FocusDirection.Up))
                        true
                    }

                    // Back closes the IME first — Android gives it that key before anything
                    // here sees it — so this is the second press, and it puts the field down.
                    Key.Back -> {
                        stopEditing(FieldExit())
                        true
                    }

                    Key.DirectionRight -> onExitRight?.let {
                        stopEditing(FieldExit(andThen = it))
                        true
                    } ?: false

                    else -> false
                }
            },
    )
}

/**
 * The rectangle both stages draw.
 *
 * A box we draw, not `OutlinedTextField`.
 *
 * **Material's outlined field reserves height above its border for the label to float up
 * into, so the component's bounds are taller than the outline a viewer can see.** Anything
 * drawn against those bounds — a glow, a border, a focus ring — lands in the empty strip
 * rather than on the box, which is exactly what the travelling highlight did on the panel:
 * it traced a rectangle a centimetre above the field and read as a smudge floating near it.
 *
 * So the field has one rectangle now and it is the one on screen. The label is a
 * placeholder inside the box that the typed text replaces, rather than a caption that
 * animates out of it — on a television nobody is filling a form quickly enough to need the
 * label to persist, and the popping animation was the other half of what looked wrong.
 */
@Composable
private fun FieldBox(
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = if (isFocused) 0.14f else 0.07f),
                shape = RoundedCornerShape(FIELD_CORNER),
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = Color.White.copy(alpha = if (isFocused) 1f else 0.35f),
                shape = RoundedCornerShape(FIELD_CORNER),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

/** What a resting field has to say: the value, the same bullets a password shows, or the label. */
@Composable
private fun FieldText(value: String, label: String, isPassword: Boolean) {
    val shown = when {
        value.isEmpty() -> label
        isPassword -> BULLET.repeat(value.length)
        else -> value
    }
    Text(
        text = shown,
        color = if (value.isEmpty()) PLACEHOLDER else Color.White,
        fontSize = TEXT_SIZE,
        maxLines = 1,
    )
}

/**
 * A way out of the editor, and what should happen once focus is back on the field.
 *
 * Both halves run in that order and never the other way round. Moving focus from a node that
 * has just left the composition moves it from nowhere.
 */
private data class FieldExit(
    val direction: FocusDirection? = null,
    val andThen: (() -> Unit)? = null,
)

/** Big enough to read from a sofa, and the same on every field in the app. */
private val TEXT_SIZE = 17.sp

/** The same grey the editor's placeholder uses, so the two stages read as one field. */
private val PLACEHOLDER = Color.White.copy(alpha = 0.5f)

/** What `PasswordVisualTransformation` draws, so a resting password looks like an editing one. */
private const val BULLET = "•"

/** One shape for the box, so the travelling glow has an outline it can actually trace. */
internal val FIELD_CORNER = 12.dp
