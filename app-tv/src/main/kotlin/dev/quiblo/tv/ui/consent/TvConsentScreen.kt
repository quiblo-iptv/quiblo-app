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

package dev.quiblo.tv.ui.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.TvChip

/**
 * What this app is, and what you are agreeing to — the first two screens of a fresh install
 * (`FREEZE.md` Amendment 9).
 *
 * **The text is on the screen and the link is extra.** A television has no browser worth using,
 * so a link that is the only way to read the terms is a term nobody here can read. The wiki page
 * is the full version and this is the part that matters, written to be read from a sofa.
 *
 * **Two screens rather than one**, split where the subject changes: what the app is, then what
 * is being agreed. Three would be a tutorial nobody reads.
 *
 * **There is no decline button**, and that is a decision rather than an omission. Somebody who
 * will not accept the terms of a player they downloaded can read this and leave; an app that
 * force-quits on decline is theatre, and it teaches people to press the button without reading.
 */
@Composable
fun TvConsentScreen(onAccept: () -> Unit, modifier: Modifier = Modifier) {
    var onSecondScreen by remember { mutableStateOf(false) }

    // The remote has to land somewhere, and there is exactly one control per screen. Re-requested
    // when the screen changes, because the previous button stops existing at that moment and
    // focus would otherwise be left on nothing at all.
    val action = remember { FocusRequester() }
    LaunchedEffect(onSecondScreen) {
        runCatching { action.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OVERSCAN, vertical = OVERSCAN),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(
                if (onSecondScreen) R.string.tv_consent_terms_title else R.string.tv_consent_what_title,
            ),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource(
                if (onSecondScreen) R.string.tv_consent_terms_body else R.string.tv_consent_what_body,
            ),
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 17.sp,
            lineHeight = 26.sp,
            modifier = Modifier.width(BODY_WIDTH),
        )

        if (onSecondScreen) {
            Text(
                text = stringResource(R.string.tv_consent_terms_link),
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.width(BODY_WIDTH),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        TvChip(
            label = stringResource(
                if (onSecondScreen) R.string.tv_consent_start else R.string.tv_consent_next,
            ),
            isSelected = true,
            onClick = { if (onSecondScreen) onAccept() else onSecondScreen = true },
            modifier = Modifier.focusRequester(action),
        )
    }
}

/**
 * The panel's own margin.
 *
 * A television overscans, and this screen has no list to scroll away from the edge — text that
 * starts at the physical edge of the panel is text with its first characters cut off on some
 * sets. 48dp is the figure the rest of the television UI uses.
 */
private val OVERSCAN = 48.dp

/** Short enough to read across, rather than the full 864dp of panel. */
private val BODY_WIDTH = 720.dp
