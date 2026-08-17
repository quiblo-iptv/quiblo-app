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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.model.Source
import dev.quiblo.core.model.SourceKind
import dev.quiblo.feature.sources.AddSourceState
import dev.quiblo.feature.sources.SourcesViewModel
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.common.TvFocusRow
import dev.quiblo.tv.ui.common.tryRequestFocus
import org.koin.androidx.compose.koinViewModel

/**
 * Sources, driven by a remote.
 *
 * Text entry on a television is genuinely unpleasant — an Xtream account means a URL, a
 * username and a password typed on an on-screen keyboard, in front of whoever is in the
 * room. It is offered because it has to be, but the form is kept as short as the protocol
 * allows and nothing optional is asked for.
 *
 * Opened from Settings rather than from the tab bar, so it carries its own heading and its
 * own way out: what used to name this screen was the tab that selected it.
 *
 * **The column is centred and has a width of its own.** It used to start at the left edge and
 * run to sixty per cent of whatever the panel happened to be, which put a form full of long
 * URLs against the bezel of a fifty-inch television and left half the screen empty beside it.
 * A fixed [FORM_WIDTH] inside a centred parent is a column; a fraction of an unknown panel is
 * a guess.
 */
@Composable
fun TvSourcesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = koinViewModel(key = "tv-sources"),
) {
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

    // Explicitly disabled while the form is open rather than relying on which handler was
    // composed last: the form's own back — the one that saves whatever has been typed from a
    // stray press — has to win, and "the innermost one wins" is a rule that survives exactly
    // until somebody moves a composable.
    BackHandler(enabled = !showForm, onBack = onBack)

    Column(modifier = modifier.fillMaxSize()) {
        when {
            addState is AddSourceState.Working -> Working()

            showForm -> {
                // AC-TV-03: back must retreat one step, not leave the app. Without this the
                // form's only exit is the Cancel button, and a viewer who presses back —
                // which is what back is for — is thrown out to the launcher having lost
                // whatever they had typed.
                BackHandler { showForm = false }

                CentredColumn {
                    TvAddSourceForm(
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
                        modifier = Modifier.width(FORM_WIDTH),
                    )
                }
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

/**
 * Puts one column of [FORM_WIDTH] in the middle of the panel.
 *
 * A `Box` with a centred child rather than padding worked out from the screen width: padding
 * has to be recomputed for every panel and is wrong on all the ones nobody measured.
 */
@Composable
private fun CentredColumn(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        content()
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
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = stringResource(R.string.tv_sources_title),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .width(FORM_WIDTH)
                    .padding(bottom = 18.dp),
            )
        }

        item {
            ResultBanner(addState = addState, onDismiss = onDismissResult)
        }

        item {
            // The one control this screen always has, so it is where focus lands when the
            // screen appears or comes back from the form.
            TvFocusRow(
                label = stringResource(R.string.tv_sources_add),
                onClick = onAdd,
                modifier = Modifier
                    .width(FORM_WIDTH)
                    .focusRequester(focusRequester),
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
                    modifier = Modifier
                        .width(FORM_WIDTH)
                        .padding(top = 24.dp),
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

    TvFocusRow(label = message, onClick = onDismiss, modifier = Modifier.width(FORM_WIDTH))
}

@Composable
private fun SourceRow(source: Source, onRefresh: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .width(FORM_WIDTH)
            .padding(top = 8.dp),
    ) {
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
            TvFocusRow(
                label = stringResource(R.string.tv_sources_refresh),
                onClick = onRefresh,
                modifier = Modifier.width(BUTTON_WIDTH),
            )
            TvFocusRow(
                label = stringResource(R.string.tv_sources_delete),
                onClick = onDelete,
                modifier = Modifier.width(BUTTON_WIDTH),
            )
        }
    }
}
