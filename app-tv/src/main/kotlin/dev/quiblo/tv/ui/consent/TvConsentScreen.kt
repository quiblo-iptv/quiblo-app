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
import dev.quiblo.designsystem.TMDB_API_KEY_URL
import dev.quiblo.feature.settings.SettingsViewModel
import dev.quiblo.feature.sources.AddSourceState
import dev.quiblo.feature.sources.SourcesViewModel
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.TvChip
import dev.quiblo.tv.ui.common.TvFocusRow
import dev.quiblo.tv.ui.common.tryRequestFocus
import dev.quiblo.tv.ui.settings.TmdbKeyRow
import dev.quiblo.tv.ui.sources.FORM_WIDTH
import dev.quiblo.tv.ui.sources.TvAddSourceForm
import org.koin.androidx.compose.koinViewModel

/**
 * What this app is, what you are agreeing to, the playlist it needs and the key that describes
 * it — the first four screens of a fresh install (`FREEZE.md` Amendment 9).
 *
 * **The text is on the screen and the link is extra.** A television has no browser worth using,
 * so a link that is the only way to read the terms is a term nobody here can read. The wiki page
 * is the full version and this is the part that matters, written to be read from a sofa.
 *
 * **A page per subject**: what the app is, what is being agreed, the one thing a viewer has to
 * do before this app can show them anything at all, and the one thing that turns a list of
 * filenames into a catalogue with posters. The last two exist for the same reason — the flow
 * used to end at the terms and drop somebody into an empty app whose next steps were both four
 * presses deep in Settings.
 *
 * **The metadata key is asked for last and can be skipped in one press**, because unlike a
 * playlist the app works without one. It is asked for at all because nothing on screen says a
 * key exists, so a viewer who never opens Settings never finds out why their films have no
 * posters.
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
    settingsViewModel: SettingsViewModel = koinViewModel(key = "tv-consent-settings"),
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

            ConsentPage.PLAYLIST -> PlaylistPage(
                viewModel = viewModel,
                onDone = { page = ConsentPage.METADATA },
            )

            ConsentPage.METADATA -> MetadataPage(onDone = onAccept, viewModel = settingsViewModel)
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
 * The playlist: now, or later.
 *
 * **Whichever button is pressed leads to the last page rather than into the app.** Consent is
 * accepted on the way out of *that* one, which keeps the rule this page was built under: the
 * flow is not left half way, and the app is never entered by a route that skipped a page.
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

/**
 * The last page: a key for the film database, or none.
 *
 * **Skipping is one press and costs nothing that cannot be had later.** Without a key the app
 * plays everything it did before — posters, plots, ratings and the genre and year filters are
 * what a key buys, and every one of them arrives the moment a key is saved in Settings.
 *
 * **The address is on the screen as well as behind the button.** A television box often has no
 * browser, and the ones that do are painful to type a login into with a remote; the likely way
 * anybody does this is on the phone in their hand, reading the address off the television. The
 * button is the shortcut for a device that can take it, and nothing depends on it — see
 * [openLink].
 */
@Composable
private fun MetadataPage(onDone: () -> Unit, viewModel: SettingsViewModel) {
    val savedKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle()
    val check by viewModel.tmdbCheck.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    Heading(stringResource(R.string.tv_consent_metadata_title))

    Text(
        text = stringResource(R.string.tv_consent_metadata_body),
        color = BODY_COLOUR,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        modifier = Modifier.width(BODY_WIDTH),
    )

    Text(
        text = TMDB_API_KEY_URL,
        color = Color.White,
        fontSize = 17.sp,
        modifier = Modifier.width(BODY_WIDTH),
    )

    Spacer(modifier = Modifier.height(6.dp))

    // The same row Settings uses, rather than a second one: a field that saves a key and reports
    // what the service said about it is exactly what this page needs, and a copy of it here is
    // how the two come to disagree about what a rejected key looks like.
    TmdbKeyRow(
        currentKey = savedKey,
        check = check,
        onSave = viewModel::saveTmdbKey,
        onClear = viewModel::clearTmdbKey,
    )

    // Save, Clear and Get a key are [TmdbKeyRow]'s own; this is the way out of the flow.
    TvChip(
        label = stringResource(
            if (savedKey.isNullOrBlank()) R.string.tv_consent_metadata_skip else R.string.tv_consent_start,
        ),
        isSelected = true,
        onClick = onDone,
        modifier = Modifier.focusRequester(focusRequester),
    )
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
private enum class ConsentPage { WHAT, TERMS, PLAYLIST, METADATA }

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
