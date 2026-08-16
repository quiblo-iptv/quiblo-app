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

package dev.quiblo.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiblo.core.data.ProfileRepository
import dev.quiblo.core.datastore.ConsentStore
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.designsystem.ProfileAvatar
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.browse.TvForYouScreen
import dev.quiblo.tv.ui.browse.TvPosterRows
import dev.quiblo.tv.ui.common.AmbientRequest
import dev.quiblo.tv.ui.common.LocalAmbientSink
import dev.quiblo.tv.ui.common.ambientBackdrop
import dev.quiblo.tv.ui.common.driftingGlow
import dev.quiblo.tv.ui.common.insistOnFocus
import dev.quiblo.tv.ui.common.onTap
import dev.quiblo.tv.ui.common.rememberAmbient
import dev.quiblo.tv.ui.common.tryRequestFocus
import dev.quiblo.tv.ui.consent.TvConsentScreen
import dev.quiblo.tv.ui.detail.TvMovieScreen
import dev.quiblo.tv.ui.live.TvLiveScreen
import dev.quiblo.tv.ui.player.TvPlaybackRequest
import dev.quiblo.tv.ui.player.TvPlayerScreen
import dev.quiblo.tv.ui.profiles.TvProfileScreen
import dev.quiblo.tv.ui.search.TvSearchScreen
import dev.quiblo.tv.ui.series.TvSeriesScreen
import dev.quiblo.tv.ui.settings.TvSettingsScreen
import dev.quiblo.tv.ui.sources.TvSourcesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The television shell: a text tab bar across the top, content beneath it.
 *
 * T0 of docs/PLAN-TV.md. The content areas are deliberately placeholders — the entire point
 * of this milestone is to prove the focus model on a real remote before anything is built
 * on top of it, because a navigation model that cannot be driven by a D-pad makes every
 * screen above it worthless.
 */
@Composable
fun TvApp(
    /**
     * Ends the activity.
     *
     * The host's, because only it can finish itself, and a parameter rather than a `LocalContext`
     * cast so that a test can compose this app and watch it ask to be closed without one.
     */
    onExit: () -> Unit = {},
    /**
     * Whether the content should make room for a soft keyboard.
     *
     * False on a television, where the keyboard is a full-screen overlay and the window is
     * deliberately held still — see `TvSettingsFieldStabilityTest` and the note in
     * `TvMainActivity`. True on the handsets `022` opened this app to, where a keyboard that
     * covers the bottom of the window would cover the field being typed into.
     */
    insetForKeyboard: Boolean = false,
) {
    Box(modifier = if (insetForKeyboard) Modifier.imePadding() else Modifier) {
        TvConsentGate { TvAppBehindConsent(onExit = onExit) }
    }
}

/**
 * What the app is, before it asks who is watching (`FREEZE.md` Amendment 9).
 *
 * In front of the profile gate, and in that order deliberately: "who is watching" is a question
 * about this household, and it should not be the first thing an app says to somebody who has not
 * yet been told what the app is.
 *
 * A wrapper rather than a branch inside [TvApp], which is what the phone does with the same two
 * gates — one shape for one decision, in both apps.
 */
@Composable
private fun TvConsentGate(content: @Composable () -> Unit) {
    val consent: ConsentStore = koinInject()

    // `null` is "the store has not answered yet", a frame or two. Nothing is drawn then, because
    // flashing the terms at somebody who accepted them a year ago is worse than a blank frame.
    val needsConsent by consent.needsConsent.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    when (needsConsent) {
        null -> Unit
        true -> TvConsentScreen(onAccept = { scope.launch { consent.accept() } })
        else -> content()
    }
}

@Composable
private fun TvAppBehindConsent(onExit: () -> Unit) {
    // Nobody watches anything until the app knows whose favourites it would be reading. The
    // gate is here rather than inside the shell so that no screen below it ever has to cope
    // with there being no profile — they simply are not composed yet.
    val profiles: ProfileRepository = koinInject()
    val activeProfile by profiles.activeProfile.collectAsStateWithLifecycle()

    if (activeProfile == null) {
        TvProfileScreen()
        return
    }

    // Leaving a profile is a suspending write, and the screen it is triggered from is about
    // to stop existing — so it runs on a scope that outlives the composition.
    val scope = rememberCoroutineScope()
    val profileSwitch = { scope.launch { profiles.signOut() } }

    // Opens on Search, which is also where Back comes to rest — so the first tab is the home
    // tab in both directions rather than only one. Focus starts in the bar rather than in the
    // field, so opening the app does not open a keyboard at somebody who came here to watch
    // something.
    var selectedTab by remember { mutableIntStateOf(TvTab.SEARCH.ordinal) }

    // What is on top of the shell — as a stack, because back has to pop one step of it.
    //
    // This was a single nullable overlay that every new screen replaced, and that shape *was*
    // #020: a replace keeps no memory of what it replaced, so backing out of an episode could
    // only land on the shell, and the series the viewer had been reading was gone along with
    // which episode they were on. AC-TV-03 asked for exactly that until Amendment 7, which is
    // why the criterion was restated before this line was written.
    //
    // Empty means the shell. The entries stay exclusive — the top one is the only thing
    // composed — so "playing and in settings" is still not expressible.
    val backStack = remember { mutableStateListOf<TvOverlay>() }

    // Playing replaces the whole shell rather than sitting inside it: a television plays
    // full screen, and leaving the bar drawn over video would be the same mistake the phone
    // app made for a fortnight.
    val current = backStack.lastOrNull()
    if (current == null) {
        TvShell(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            /*
             * Closing the app, and forgetting who was watching on the way out.
             *
             * **Both halves, and the second is the reported one.** Backing out used to fall
             * through to the system, which backgrounds an activity rather than finishing it — so
             * the process survived, the chosen profile survived with it, and the next launch
             * resumed straight into somebody else's favourites. Finishing alone would not fix
             * that either: the chooser is cleared in `Application.onCreate`, which does not run
             * again while the process is cached. Signing out is what makes the promise keepable
             * whether the process dies or not.
             *
             * The sign-out is awaited before finishing, because a write racing an activity that
             * is going away is a write that sometimes happens.
             */
            onExit = {
                scope.launch {
                    profiles.signOut()
                    onExit()
                }
            },
            onOpenSettings = { backStack.add(TvOverlay.Settings) },
            onOpen = { items, index -> overlayFor(items, index)?.let(backStack::add) },
            onOpenChannel = { channel -> backStack.add(detailFor(channel)) },
            // The same sign-out Settings offers, one press closer. Both land on the chooser,
            // because there is only one thing "switch profile" can mean.
            onSwitchProfile = { profileSwitch() },
            activeProfileName = activeProfile?.name,
            activeProfileAvatar = activeProfile?.avatar,
        )
    } else {
        TvOverlayScreen(
            overlay = current,
            onPush = backStack::add,
            onPop = { backStack.popRestoringCursor() },
            onReplaceTop = { backStack[backStack.lastIndex] = it },
            activeProfileName = activeProfile?.name,
            onSwitchProfile = { profileSwitch() },
        )
    }
}

/**
 * Pops one step, and hands the screen underneath the cursor it earned.
 *
 * Backing out of an episode should not merely reach the series again — it should reach the
 * episode the viewer was on, which is the second half of #020. That is recorded here rather
 * than stored anywhere: the fact is "which step of this journey was taken", it is true only
 * while the journey exists, and a table holding it would be a second source of truth for
 * something watch history already knows and would drift from the continue-watching row.
 */
private fun SnapshotStateList<TvOverlay>.popRestoringCursor() {
    val leaving = removeLastOrNull() ?: return
    val episode = (leaving as? TvOverlay.Playing)?.request as? TvPlaybackRequest.Episode ?: return
    val beneath = lastOrNull() as? TvOverlay.Series ?: return
    this[lastIndex] = beneath.copy(focusEpisodeId = episode.episodeId)
}

/**
 * Whichever screen is currently drawn over the shell.
 *
 * Separated from [TvApp] so that one holds the state and this one spends it. They were a
 * single function until Sources moved in here from the tab bar, at which point the routing
 * and the state-keeping between them were more than any one function should be asked to hold
 * in view at once.
 */
@Composable
private fun TvOverlayScreen(
    overlay: TvOverlay,
    onPush: (TvOverlay) -> Unit,
    onPop: () -> Unit,
    onReplaceTop: (TvOverlay) -> Unit,
    activeProfileName: String?,
    onSwitchProfile: () -> Unit,
) {
    when (overlay) {
        is TvOverlay.Playing -> TvPlayerScreen(
            request = overlay.request,
            onBack = onPop,
            // Zapping replaces this step rather than adding one. Twenty channels in, back
            // should be one press back to where the viewer started watching, not twenty.
            onZap = { direction ->
                val live = overlay.request as? TvPlaybackRequest.Live
                if (live != null) onReplaceTop(TvOverlay.Playing(live.zappedBy(direction)))
            },
            // The same replace, for the same reason: six episodes into an evening, back
            // belongs to the series, not to episode five. It also keeps `popRestoringCursor`
            // honest — the top of the stack is always the episode actually playing, so the
            // series reopens on that row rather than on whichever one was pressed first.
            onStepEpisode = { direction ->
                val episode = (overlay.request as? TvPlaybackRequest.Episode)?.steppedBy(direction)
                if (episode != null) onReplaceTop(TvOverlay.Playing(episode))
            },
        )

        is TvOverlay.Series -> TvSeriesScreen(
            channel = overlay.channel,
            // Set when the viewer backs out of an episode, so the list reopens on the row
            // they were watching rather than at the top of the season (#020).
            focusEpisodeId = overlay.focusEpisodeId,
            onPlayEpisode = { episode, run, resumeFrom ->
                onPush(
                    TvOverlay.Playing(
                        TvPlaybackRequest.Episode(
                            channel = overlay.channel,
                            episodeId = episode.id,
                            streamUrl = episode.streamUrl,
                            episodeTitle = episode.title,
                            seasonNumber = episode.seasonNumber,
                            episodeNumber = episode.episodeNumber,
                            startPositionMillis = resumeFrom,
                            run = run,
                            // Found rather than passed. The screen knows which row was
                            // pressed and the run is sorted, so the two indices are not the
                            // same number and handing over the wrong one would put the
                            // viewer one episode out for the rest of the series.
                            runIndex = run.indexOfFirst { it.id == episode.id }.coerceAtLeast(0),
                        ),
                    ),
                )
            },
            onBack = onPop,
            modifier = Modifier.padding(SCREEN_PADDING),
        )

        is TvOverlay.Movie -> TvMovieScreen(
            channel = overlay.channel,
            // A null position means "whatever was stored", which is what the player does
            // with a film by default; zero means the viewer chose to start again.
            onPlay = { startAt ->
                onPush(TvOverlay.Playing(TvPlaybackRequest.Film(overlay.channel, startPositionMillis = startAt)))
            },
            onBack = onPop,
            modifier = Modifier.padding(SCREEN_PADDING),
        )

        TvOverlay.Settings -> TvSettingsScreen(
            onBack = onPop,
            onOpenSources = { onPush(TvOverlay.Sources) },
            activeProfileName = activeProfileName,
            // Straight back to the chooser: there is nothing to confirm, and whatever was on
            // screen belonged to the profile being left.
            onSwitchProfile = onSwitchProfile,
            modifier = Modifier.padding(SCREEN_PADDING),
        )

        // Reached from Settings, and back returns there rather than to the catalogue — which
        // is now what popping one step does, rather than a destination this screen has to
        // name for itself. It used to name it, and that was the only reason the two-step
        // journey worked anywhere in this app.
        TvOverlay.Sources -> TvSourcesScreen(
            onBack = onPop,
            modifier = Modifier.padding(SCREEN_PADDING),
        )
    }
}

/**
 * What pressing a row in a catalogue opens.
 *
 * Only live plays immediately, because only a live channel is a thing you simply watch. A
 * film opens its own screen — that is #005, and it is where a resume point can be offered at
 * all — and a series has no stream of its own to play.
 *
 * Decided per item rather than per screen, because a favourites list and a page of search
 * results both hold every kind at once, and deciding it per screen is how a series reached
 * the player.
 *
 * Null when the index does not exist, which leaves the viewer where they are rather than
 * opening a screen about nothing.
 */
private fun overlayFor(items: List<Channel>, index: Int): TvOverlay? {
    val channel = items.getOrNull(index) ?: return null
    return when (channel.kind) {
        MediaKind.LIVE -> TvOverlay.Playing(TvPlaybackRequest.Live(channel, items, index))
        MediaKind.VOD -> TvOverlay.Movie(channel)
        MediaKind.SERIES -> TvOverlay.Series(channel)
    }
}

/** Where a title already begun is picked up: its own screen, never the player directly. */
private fun detailFor(channel: Channel): TvOverlay = when (channel.kind) {
    MediaKind.SERIES -> TvOverlay.Series(channel)
    else -> TvOverlay.Movie(channel)
}

/**
 * A screen drawn over the shell.
 *
 * Exclusive by construction: the shell is `null`, and everything else replaces it whole.
 */
private sealed interface TvOverlay {
    data class Playing(val request: TvPlaybackRequest) : TvOverlay

    /**
     * A series' episode list.
     *
     * [focusEpisodeId] is null on the way in and set on the way back out of an episode, which
     * is the difference between opening a series and returning to one. Both matter and they
     * want opposite things: opening shows the title and artwork with nothing scrolled (#015),
     * returning puts the remote back where it was (#020).
     */
    data class Series(val channel: Channel, val focusEpisodeId: String? = null) : TvOverlay

    data class Movie(val channel: Channel) : TvOverlay
    data object Settings : TvOverlay
    data object Sources : TvOverlay
}

/** The tab bar and whichever catalogue is selected beneath it. */
@Composable
private fun TvShell(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpen: (List<Channel>, Int) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onSwitchProfile: () -> Unit,
    /**
     * Closes the app, having first forgotten who was watching.
     *
     * Both halves matter and only one of them is obvious. See [TvApp], where it is built.
     */
    onExit: () -> Unit,
    activeProfileName: String?,
    activeProfileAvatar: String?,
) {
    // Focus starts in the bar, so the first thing a viewer sees is where they are. An app
    // that opens with nothing focused leaves the remote apparently dead.
    val barFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    // Insists rather than tries, and that distinction is `022` #4. `requestFocus()` returns a
    // boolean rather than throwing when its node is not placed yet, and `tryRequestFocus` drops
    // that `false` on the floor — so the shell sat with *nothing* focused: no highlight on the
    // gear or the face, and a remote that appeared dead because the bar's key handler was never
    // reached. This effect runs after composition and before layout has necessarily placed
    // anything, and the shell leaves composition whenever an overlay opens, so it lost that race
    // again on every return from Settings, a detail screen or the player.
    LaunchedEffect(Unit) { barFocusRequester.insistOnFocus() }

    /**
     * Whether one back press has already been spent, and the app is waiting for the second.
     *
     * Its own state rather than a timestamp compared on the next press, so the notice on screen
     * and the meaning of the next press are one fact. Cleared by the effect below and by walking
     * off Search, because a viewer who has gone somewhere else has stopped leaving.
     */
    var exitArmed by remember { mutableStateOf(false) }

    LaunchedEffect(exitArmed) {
        if (!exitArmed) return@LaunchedEffect
        delay(EXIT_WINDOW_MILLIS)
        exitArmed = false
    }

    /*
     * Back walks to the first tab, and then takes two presses to leave.
     *
     * **Leaving used to mean falling through to the system**, which *backgrounds* an activity
     * rather than finishing it. The process survived, the chosen profile survived with it, and
     * the next launch resumed straight into somebody else's favourites — which is the reported
     * half: "so I can pick profile again". [onExit] is what makes leaving mean leaving.
     *
     * Two presses rather than one, because back is also how a viewer walks out of everything
     * else, and a stray press that closes the app is the one mistake a television app cannot
     * let somebody make. Two presses rather than a dialog because this app has none, and a first
     * modal on the way out would be a poor place to grow one.
     */
    BackHandler {
        when (tvBackAction(TvBackState(selectedTab, exitArmed))) {
            TvBackAction.GoToSearch -> {
                onSelectTab(TvTab.SEARCH.ordinal)
                // The bar is already placed here — this is a press, not a first composition — so
                // one ask is enough and there is no coroutine to insist from.
                barFocusRequester.tryRequestFocus()
                exitArmed = false
            }

            TvBackAction.ArmExit -> exitArmed = true

            TvBackAction.Exit -> onExit()
        }
    }

    /*
     * The room the catalogue sits in.
     *
     * Black everywhere was the honest default and it reads as bleak: a television fills a dark
     * room, and three metres of unlit rectangle is a void with posters floating in it rather
     * than a background. The focused tile says what it is showing through `LocalAmbientSink`
     * and that picture lights the corners — the same answer Google TV and every set-top box
     * arrive at, for the same reason.
     *
     * The black stays underneath. This is light added to it, never a replacement for it, so a
     * poster with no usable colour in it leaves the screen exactly as it was.
     */
    var ambientRequest: AmbientRequest by remember { mutableStateOf(AmbientRequest.None) }
    val ambient = rememberAmbient((ambientRequest as? AmbientRequest.Artwork)?.url)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .ambientBackdrop(ambient)
            // Search's own light, drawn here rather than on the search screen — which is what
            // makes it reach all four edges and pass behind the tab bar, like every other tab's.
            // See `AmbientRequest`. Applied only while it is wanted, because an infinite
            // transition that is always composed is a frame's work on every tab.
            .then(if (ambientRequest is AmbientRequest.Drift) Modifier.driftingGlow() else Modifier),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalAmbientSink provides { ambientRequest = it }) {
                TvTopBar(
                    selectedTab = selectedTab,
                    onSelect = onSelectTab,
                    focusRequester = barFocusRequester,
                    onEnterContent = { contentFocusRequester.tryRequestFocus() },
                    onOpenSettings = onOpenSettings,
                    onSwitchProfile = onSwitchProfile,
                    activeProfileName = activeProfileName,
                    activeProfileAvatar = activeProfileAvatar,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = SCREEN_PADDING, end = SCREEN_PADDING, bottom = SCREEN_PADDING)
                        .focusRequester(contentFocusRequester)
                        .focusGroup(),
                ) {
                    when (TvTab.entries[selectedTab]) {
                        TvTab.SEARCH -> TvSearchScreen(onOpen = onOpen)

                        TvTab.LIVE -> TvLiveScreen(onPlay = onOpen)

                        TvTab.FOR_YOU -> TvForYouScreen(onPlay = onOpen)

                        TvTab.MOVIES -> TvPosterRows(
                            kind = MediaKind.VOD,
                            onPlay = onOpen,
                            onResume = onOpenChannel,
                        )

                        TvTab.SERIES -> TvPosterRows(
                            kind = MediaKind.SERIES,
                            onPlay = onOpen,
                            onResume = onOpenChannel,
                        )

                        TvTab.FAVOURITES -> TvPosterRows(
                            kind = MediaKind.VOD,
                            favouritesOnly = true,
                            onPlay = onOpen,
                            onResume = onOpenChannel,
                        )
                    }
                }
            }
        }

        /*
         * "Press back again to close", along the bottom.
         *
         * A line rather than a dialog, because this app has none and the way out is a poor place
         * to grow the first one. A line rather than a `Toast`, because a toast on a television is
         * a phone-sized rectangle in a corner of a three-metre screen, drawn by the system in the
         * system's own type at the system's own size.
         *
         * It takes no focus and no space: the shell underneath is laid out exactly as it is
         * without it, so arming the exit cannot move anything the viewer was looking at.
         */
        if (exitArmed) {
            Text(
                text = stringResource(R.string.tv_back_again_to_close),
                color = Color.White.copy(alpha = EXIT_NOTICE_ALPHA),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SCREEN_PADDING),
            )
        }
    }
}

/** Long enough to find the button again, short enough that a stray press cannot close it later. */
private const val EXIT_WINDOW_MILLIS = 3_000L

/** Legible from a sofa, and quieter than anything the viewer came here to look at. */
private const val EXIT_NOTICE_ALPHA = 0.75f

/**
 * Text tabs with an underline, as the Google TV home screen does it.
 *
 * No pill, no card, no surface behind the labels — the reference is plain text, and a
 * filled chip reads as a button rather than as a place you are.
 *
 * **The bar is one focus target, not five.** Moving left and right along it still switches
 * tab immediately, which is the Google TV feel this was always after, but the switch is
 * driven by the key press rather than by which label happens to hold focus.
 *
 * That distinction is the whole bug this shape exists to prevent. With a focusable per tab
 * and selection following focus, *any* event that destroyed the focused element inside the
 * content below — opening the add-source form, the refresh spinner appearing, a list
 * emptying — left Compose with no focus target, so it fell back to the first focusable in
 * the tree, which was a tab, which selected itself and threw the viewer onto another
 * screen. Content could silently change which tab you were on. Now it cannot: nothing but a
 * key press on this bar moves the selection.
 */
@Composable
private fun TvTopBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    focusRequester: FocusRequester,
    onEnterContent: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchProfile: () -> Unit,
    activeProfileName: String?,
    activeProfileAvatar: String?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val lastIndex = TvTab.entries.lastIndex

    /**
     * Whether the remote is resting on the settings icon rather than on a tab.
     *
     * The gear is a *position along this bar*, not a focusable of its own, for the same
     * reason the tabs are not: the bar is one focus target and moves by key press. Making
     * the icon separately focusable and handing focus across was tried and does not work —
     * the icon sits inside the bar's own focusable, so a focus search walks past it into
     * the content below, and the gear could not be reached by remote at all. That is half
     * of why #004 read as a missing screen when it was really an unreachable one.
     */
    var spot by remember { mutableStateOf(TvBarSpot.TABS) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_PADDING, vertical = 20.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (val action = tvBarAction(event.key, TvBarState(selectedTab, spot), lastIndex)) {
                    is TvBarAction.Move -> {
                        spot = action.state.spot
                        if (action.state.selectedTab != selectedTab) onSelect(action.state.selectedTab)
                        true
                    }

                    TvBarAction.EnterContent -> {
                        spot = TvBarSpot.TABS
                        onEnterContent()
                        true
                    }

                    TvBarAction.OpenSettings -> {
                        onOpenSettings()
                        true
                    }

                    TvBarAction.SwitchProfile -> {
                        onSwitchProfile()
                        true
                    }

                    TvBarAction.Unhandled -> false
                }
            }
            .focusable(interactionSource = interactionSource),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        TvTab.entries.forEachIndexed { index, tab ->
            // Bright only when the bar itself holds the remote *and* the remote is on the
            // tabs rather than one of the icons past them. While focus is down in the content
            // the selected tab stays legible but recedes, which is how a viewer tells where a
            // press will land.
            val isBarFocused = isFocused && spot == TvBarSpot.TABS

            if (tab.isIconOnly) {
                IconTab(
                    icon = Icons.Filled.Search,
                    contentDescription = stringResource(tab.labelRes),
                    isSelected = index == selectedTab,
                    isBarFocused = isBarFocused,
                    modifier = Modifier.onTap {
                        spot = TvBarSpot.TABS
                        onSelect(index)
                    },
                )
            } else {
                TextTab(
                    label = stringResource(tab.labelRes),
                    isSelected = index == selectedTab,
                    isBarFocused = isBarFocused,
                    modifier = Modifier.onTap {
                        spot = TvBarSpot.TABS
                        onSelect(index)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        /*
         * The gear and the face, as one group.
         *
         * Their own row, because the bar spaces its children 28dp apart to separate five tab
         * labels — and a gap sized for words between two icons at the end of it left them
         * looking like two unrelated controls that happened to land near each other. Nested,
         * they are one child of that row and space themselves.
         */
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BAR_ICON_GAP),
        ) {
            BarIcon(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.tv_settings),
                isHighlighted = spot == TvBarSpot.GEAR && isFocused,
                modifier = Modifier.onTap(onOpenSettings),
            )

            /*
             * Who is watching, on the right of the gear.
             *
             * **Their own face rather than a generic figure**, which is the whole point of it
             * being here: the one thing this control has to answer from across a room is "is
             * this still mine", and a person-shaped outline answers that for nobody. It is the
             * same `ProfileAvatar` the chooser draws, at a corner-control size, so the picture
             * somebody picked when they made the profile is the one they see in the bar.
             *
             * Last along the bar, so it is one press further than Settings. Settings is where a
             * viewer goes on purpose; this is where they go when the remote changes hands,
             * which is rarer and is the one press nobody minds.
             */
            BarProfile(
                name = activeProfileName,
                avatar = activeProfileAvatar,
                isHighlighted = spot == TvBarSpot.PROFILE && isFocused,
                modifier = Modifier.onTap(onSwitchProfile),
            )
        }
    }
}

/**
 * The active profile's face, drawn as a bar control.
 *
 * Not focusable, for the same reason [BarIcon] is not: the bar above owns focus and decides where
 * along itself the remote is resting. The ring is how that answer is drawn here — a scale would
 * shift the two icons beside each other every time the remote moved between them.
 */
@Composable
private fun BarProfile(
    name: String?,
    avatar: String?,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .border(
                width = if (isHighlighted) 2.dp else 0.dp,
                color = if (isHighlighted) Color.White else Color.Transparent,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Null only in the frame between signing out and the chooser appearing. Nothing is drawn
        // then rather than a placeholder, because the bar is on its way off screen.
        if (name != null) {
            ProfileAvatar(
                name = name,
                avatar = avatar,
                size = BAR_AVATAR_SIZE,
                modifier = Modifier.alpha(if (isHighlighted) 1f else IDLE_ALPHA),
            )
        }
    }
}

/** One tab. A label and an underline — the bar above owns focus and selection. */
@Composable
private fun TextTab(
    label: String,
    isSelected: Boolean,
    isBarFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            isSelected && isBarFocused -> 1f
            isSelected -> SELECTED_ALPHA
            else -> IDLE_ALPHA
        },
        label = "tabAlpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = alpha),
            fontSize = TAB_TEXT_SIZE,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // The underline marks the selected tab. Drawn always, transparent when not
        // selected, so the label never shifts as selection moves along the bar.
        Box(
            modifier = Modifier
                .width(UNDERLINE_WIDTH)
                .height(2.dp)
                .background(
                    color = if (isSelected) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(1.dp),
                ),
        )
    }
}

/**
 * A tab carrying a magnifier instead of a word.
 *
 * Same underline, same alpha rules, same rhythm as [TextTab] — it is a tab, and the only
 * thing different about it is that it needs no translating. The icon sits in a box the height
 * of a tab label so the underlines along the bar stay on one line.
 */
@Composable
private fun IconTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    isBarFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            isSelected && isBarFocused -> 1f
            isSelected -> SELECTED_ALPHA
            else -> IDLE_ALPHA
        },
        label = "iconTabAlpha",
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.height(TAB_LABEL_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier.size(TAB_ICON_SIZE),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .width(UNDERLINE_WIDTH)
                .height(2.dp)
                .background(
                    color = if (isSelected) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(1.dp),
                ),
        )
    }
}

@Composable
private fun BarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    // Not focusable, by design. The bar above owns focus and decides where along itself the
    // remote is resting; this only has to look like the answer.
    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                color = if (isHighlighted) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (isHighlighted) 1f else IDLE_ALPHA),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Overscan margin. Televisions crop the edges of the frame, and many still do. */
private val SCREEN_PADDING = 48.dp
private val UNDERLINE_WIDTH = 28.dp
private val TAB_TEXT_SIZE = 20.sp

/** A 20sp label's line box, near enough — what an icon tab matches so the bar stays level. */
private val TAB_LABEL_HEIGHT = 24.dp
private val TAB_ICON_SIZE = 22.dp
private const val SELECTED_ALPHA = 0.90f
private const val IDLE_ALPHA = 0.55f

/** Close enough that the gear and the face read as one group at the end of the bar. */
private val BAR_ICON_GAP = 8.dp

/** Smaller than the 40dp control around it, so the highlight ring has somewhere to be drawn. */
private val BAR_AVATAR_SIZE = 32.dp
