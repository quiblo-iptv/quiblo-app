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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.datastore.ConsentStore
import dev.quiblo.designsystem.openLink
import dev.quiblo.player.R
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * What this app is, and what you are agreeing to, before anything else (`FREEZE.md` Amendment 9).
 *
 * In front of [ProfileGate] rather than behind it: "who is watching" is a question about this
 * household, and it should not be the first thing an app says to somebody who has not yet been
 * told what the app is.
 *
 * The same two screens as the television, and the same text — a phone has a browser, so the link
 * here opens it, but the essential words are still on the screen. Consent that depends on the
 * reader leaving the app to find out what they are consenting to is not consent.
 */
@Composable
fun ConsentGate(content: @Composable () -> Unit) {
    val consent: ConsentStore = koinInject()

    // `null` is "the store has not answered yet", a frame or two. Nothing is drawn then: showing
    // the terms to somebody who accepted them a year ago, even for one frame, is worse than a
    // blank one.
    val needsConsent by consent.needsConsent.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    when (needsConsent) {
        null -> Unit
        true -> ConsentScreens(onAccept = { scope.launch { consent.accept() } })
        else -> content()
    }
}

@Composable
private fun ConsentScreens(onAccept: () -> Unit) {
    var onSecondScreen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                if (onSecondScreen) R.string.consent_terms_title else R.string.consent_what_title,
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = stringResource(
                if (onSecondScreen) R.string.consent_terms_body else R.string.consent_what_body,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (onSecondScreen) {
            // Nothing depends on this opening. A device with no browser at all is unusual and
            // not impossible, and the terms are readable without ever leaving this screen — so a
            // failure here is a button that did nothing visible, not a person unable to read
            // what they are agreeing to.
            TextButton(onClick = { openLink(context, TERMS_URL) }) {
                Text(text = stringResource(R.string.consent_terms_link))
            }
        }

        Button(onClick = { if (onSecondScreen) onAccept() else onSecondScreen = true }) {
            Text(
                text = stringResource(
                    if (onSecondScreen) R.string.consent_start else R.string.consent_next,
                ),
            )
        }
    }
}

/** The page `006` gate 3 built for this to point at. */
private const val TERMS_URL = "https://quiblo-iptv.github.io/quiblo-wiki/wiki/terms"
