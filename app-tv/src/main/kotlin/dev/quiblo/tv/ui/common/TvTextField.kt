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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    /*
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
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() },
        ),
        decorationBox = { field ->
            Box(
                modifier = Modifier
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
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = TEXT_SIZE,
                    )
                }
                field()
            }
        },
        modifier = modifier.onPreviewKeyEvent { event ->
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

                // Only where a field has asked for it. Unhandled everywhere else, so the
                // cursor keeps the key on every field that is genuinely edited by hand.
                Key.DirectionRight -> onExitRight?.let {
                    it()
                    true
                } ?: false

                else -> false
            }
        },
    )
}

/** Big enough to read from a sofa, and the same on every field in the app. */
private val TEXT_SIZE = 17.sp

/** One shape for the box, so the travelling glow has an outline it can actually trace. */
internal val FIELD_CORNER = 12.dp
