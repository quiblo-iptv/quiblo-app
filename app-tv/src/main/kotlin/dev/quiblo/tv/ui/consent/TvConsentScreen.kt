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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.feature.sources.AddSourceState
import dev.quiblo.feature.sources.SourcesViewModel
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.ui.common.TvFocusRow
import dev.quiblo.tv.ui.common.tryRequestFocus
import dev.quiblo.tv.ui.sources.FORM_WIDTH
import dev.quiblo.tv.ui.sources.TvAddSourceForm
import org.koin.androidx.compose.koinViewModel

/**
 * What this app is, what you are agreeing to, and the playlist it needs — the first three
 * screens of a fresh install (`FREEZE.md` Amendment 9).
 *
 * **The text is on the screen and the link is extra.** A television has no browser worth using,
 * so a link that is the only way to read the terms is a term nobody here can read. The wiki page
 * is the full version and this is the part that matters, written to be read from a sofa.
 *
 * **Three screens rather than two**, split where the subject changes: what the app is, what is
 * being agreed, and then the one thing a viewer has to do before this app can show them
 * anything at all. The third page is new, and it exists because the flow used to end at the
 * terms and drop somebody into an empty app whose next step was four presses deep in Settings.
 *
 * **There is no decline button**, and that is a decision rather than an omission. Somebody who
 * will not accept the terms of a player they downloaded can read this and leave; an app that
 * force-quits on decline is theatre, and it teaches people to press the button without reading.
 *
 * **No dialog anywhere in here.** The television app has no modals — a floating box over a
 * screen a remote cannot reach around is the one interaction pattern this project has ruled
 * out — so "the dialog on first launch" is a page, like the two before it.
 */
@Composable
fun TvConsentScreen(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = koinViewModel(key = "tv-consent-sources"),
) {
    var page by remember { mutableStateOf(ConsentPage.WHAT) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OVERSCAN, vertical = OVERSCAN),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (page) {
            ConsentPage.WHAT -> ReadingPage(
                title = stringResource(R.string.tv_consent_what_title),
                body = stringResource(R.string.tv_consent_what_body),
                action = stringResource(R.string.tv_consent_next),
                onAction = { page = ConsentPage.TERMS },
            )

            ConsentPage.TERMS -> ReadingPage(
                title = stringResource(R.string.tv_consent_terms_title),
                body = stringResource(R.string.tv_consent_terms_body),
                footnote = stringResource(R.string.tv_consent_terms_link),
                action = stringResource(R.string.tv_consent_next),
                onAction = { page = ConsentPage.PLAYLIST },
            )

            ConsentPage.PLAYLIST -> PlaylistPage(viewModel = viewModel, onDone = onAccept)
        }
    }
}

/** The two pages that are only words and one button. */
@Composable
private fun ReadingPage(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    footnote: String? = null,
) {
    // The remote has to land somewhere, and there is exactly one control per reading page.
    // Re-requested when the page changes, because the previous button stops existing at that
    // moment and focus would otherwise be left on nothing at all.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(title) { focusRequester.tryRequestFocus() }

    Heading(title)

    Text(
        text = body,
        color = BODY_COLOUR,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        modifier = Modifier.width(BODY_WIDTH),
    )

    if (footnote != null) {
        Text(
            text = footnote,
            color = FOOTNOTE_COLOUR,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.width(BODY_WIDTH),
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    TvChip(
        label = action,
        isSelected = true,
        onClick = onAction,
        modifier = Modifier.focusRequester(focusRequester),
    )
}

/**
 * The last page: a playlist now, or later.
 *
 * **Consent is accepted on the way out of this page, by whichever button is pressed.** It used
 * to be accepted at the end of the terms page, which would have left a viewer able to save a
 * source while the app still believed nobody had agreed to anything — a state nothing else here
 * expects.
 *
 * **It waits for the source to load rather than accepting and running.** Adding a playlist
 * starts a fetch that can take a while and can fail, and a viewer dropped into an empty app
 * mid-fetch cannot tell a slow provider from a rejected one. So the page holds, says which
 * happened, and only then goes in.
 */
@Composable
private fun PlaylistPage(viewModel: SourcesViewModel, onDone: () -> Unit) {
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }

    // The remote lands on the first control of whichever branch is composed. Keyed on both,
    // because the branch changes without the page changing.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(showForm, addState) { focusRequester.tryRequestFocus() }

    Heading(stringResource(R.string.tv_consent_playlist_title))

    Text(
        text = stringResource(R.string.tv_consent_playlist_body),
        color = BODY_COLOUR,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        modifier = Modifier.width(BODY_WIDTH),
    )

    Spacer(modifier = Modifier.height(6.dp))

    when (val state = addState) {
        // Nothing is dismissed on this page, so Added and Failed are the two ends of one
        // attempt rather than banners over a list.
        is AddSourceState.Working -> Loading()

        is AddSourceState.Added -> Outcome(
            message = stringResource(R.string.tv_sources_added, state.channelCount),
            action = stringResource(R.string.tv_consent_start),
            onAction = onDone,
            focusRequester = focusRequester,
        )

        is AddSourceState.Failed -> Outcome(
            message = stringResource(R.string.tv_sources_failed),
            action = stringResource(R.string.tv_consent_playlist_retry),
            onAction = {
                viewModel.dismissResult()
                showForm = true
            },
            focusRequester = focusRequester,
            secondAction = stringResource(R.string.tv_consent_playlist_skip),
            onSecondAction = onDone,
        )

        else -> if (showForm) {
            TvAddSourceForm(
                focusRequester = focusRequester,
                onAddM3u = viewModel::addM3uSource,
                onAddXtream = viewModel::addXtreamSource,
                onCancel = { showForm = false },
                modifier = Modifier.width(FORM_WIDTH),
                cancelLabel = stringResource(R.string.tv_consent_playlist_not_now),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvChip(
                    label = stringResource(R.string.tv_consent_playlist_add),
                    isSelected = true,
                    onClick = { showForm = true },
                    modifier = Modifier.focusRequester(focusRequester),
                )
                TvChip(
                    label = stringResource(R.string.tv_consent_playlist_skip),
                    isSelected = false,
                    onClick = onDone,
                )
            }
        }
    }
}

@Composable
private fun Loading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = Color.White)
        Text(
            text = stringResource(R.string.tv_sources_working),
            color = BODY_COLOUR,
            fontSize = 17.sp,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** What became of the playlist, and the one or two ways on from it. */
@Composable
private fun Outcome(
    message: String,
    action: String,
    onAction: () -> Unit,
    focusRequester: FocusRequester,
    secondAction: String? = null,
    onSecondAction: (() -> Unit)? = null,
) {
    Text(
        text = message,
        color = BODY_COLOUR,
        fontSize = 17.sp,
        modifier = Modifier.width(BODY_WIDTH),
    )

    Spacer(modifier = Modifier.height(6.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvFocusRow(
            label = action,
            onClick = onAction,
            modifier = Modifier
                .width(OUTCOME_BUTTON_WIDTH)
                .focusRequester(focusRequester),
        )
        if (secondAction != null && onSecondAction != null) {
            TvFocusRow(
                label = secondAction,
                onClick = onSecondAction,
                modifier = Modifier.width(OUTCOME_BUTTON_WIDTH),
            )
        }
    }
}

@Composable
private fun Heading(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 30.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Which of the three pages is showing.
 *
 * An enum rather than the boolean this was: a flag named after the second of three states is
 * how a third one gets bolted on as a second flag, and then two of the four combinations mean
 * nothing.
 */
private enum class ConsentPage { WHAT, TERMS, PLAYLIST }

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

/** Wide enough for "Loaded 12,431 channels." without wrapping it. */
private val OUTCOME_BUTTON_WIDTH = 260.dp

private val BODY_COLOUR = Color.White.copy(alpha = 0.80f)

private val FOOTNOTE_COLOUR = Color.White.copy(alpha = 0.50f)
