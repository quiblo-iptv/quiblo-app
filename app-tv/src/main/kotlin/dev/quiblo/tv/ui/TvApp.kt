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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.tv.R
import dev.quiblo.tv.ui.browse.TvPosterRows
import dev.quiblo.tv.ui.detail.TvMovieScreen
import dev.quiblo.tv.ui.live.TvLiveScreen
import dev.quiblo.tv.ui.player.TvPlaybackRequest
import dev.quiblo.tv.ui.player.TvPlayerScreen
import dev.quiblo.tv.ui.profiles.TvProfileScreen
import dev.quiblo.tv.ui.search.TvSearchScreen
import dev.quiblo.tv.ui.series.TvSeriesScreen
import dev.quiblo.tv.ui.settings.TvSettingsScreen
import dev.quiblo.tv.ui.sources.TvSourcesScreen
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
fun TvApp() {
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

    // What is on top of the shell, if anything.
    //
    // One piece of state rather than three booleans, because the states are exclusive: a
    // viewer is watching, or reading a series, or in settings, or in the shell. Three flags
    // can express "playing and in settings", which is not a thing, and the code would have
    // to keep proving it never happens.
    var overlay: TvOverlay? by remember { mutableStateOf(null) }

    // Playing replaces the whole shell rather than sitting inside it: a television plays
    // full screen, and leaving the bar drawn over video would be the same mistake the phone
    // app made for a fortnight.
    val current = overlay
    if (current == null) {
        TvShell(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            onOpenSettings = { overlay = TvOverlay.Settings },
            onOpen = { items, index -> overlay = overlayFor(items, index) ?: overlay },
            onOpenChannel = { channel -> overlay = detailFor(channel) },
        )
    } else {
        TvOverlayScreen(
            overlay = current,
            onOverlay = { overlay = it },
            activeProfileName = activeProfile?.name,
            onSwitchProfile = { profileSwitch() },
        )
    }
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
    onOverlay: (TvOverlay?) -> Unit,
    activeProfileName: String?,
    onSwitchProfile: () -> Unit,
) {
    when (overlay) {
        is TvOverlay.Playing -> TvPlayerScreen(
            request = overlay.request,
            onBack = { onOverlay(null) },
            // Only live has a queue to move through, and the key map means only live gets
            // here at all.
            onZap = { direction ->
                val live = overlay.request as? TvPlaybackRequest.Live
                if (live != null) onOverlay(TvOverlay.Playing(live.zappedBy(direction)))
            },
        )

        is TvOverlay.Series -> TvSeriesScreen(
            channel = overlay.channel,
            onPlayEpisode = { episode, resumeFrom ->
                onOverlay(
                    TvOverlay.Playing(
                        TvPlaybackRequest.Episode(
                            channel = overlay.channel,
                            streamUrl = episode.streamUrl,
                            episodeTitle = episode.title,
                            seasonNumber = episode.seasonNumber,
                            episodeNumber = episode.episodeNumber,
                            startPositionMillis = resumeFrom,
                        ),
                    ),
                )
            },
            // Back from an episode list lands on the catalogue it came from, not on the
            // player it might have opened (AC-TV-03).
            onBack = { onOverlay(null) },
            modifier = Modifier.padding(SCREEN_PADDING),
        )

        is TvOverlay.Movie -> TvMovieScreen(
            channel = overlay.channel,
            // A null position means "whatever was stored", which is what the player does
            // with a film by default; zero means the viewer chose to start again.
            onPlay = { startAt ->
                onOverlay(TvOverlay.Playing(TvPlaybackRequest.Film(overlay.channel, startPositionMillis = startAt)))
            },
            onBack = { onOverlay(null) },
            modifier = Modifier.padding(SCREEN_PADDING),
        )

        TvOverlay.Settings -> TvSettingsScreen(
            onBack = { onOverlay(null) },
            onOpenSources = { onOverlay(TvOverlay.Sources) },
            activeProfileName = activeProfileName,
            // Straight back to the chooser: there is nothing to confirm, and whatever was on
            // screen belonged to the profile being left.
            onSwitchProfile = onSwitchProfile,
            modifier = Modifier.padding(SCREEN_PADDING),
        )

        // Reached from Settings, and back returns there rather than to the catalogue: a
        // viewer who went two steps in expects two steps out (AC-TV-03).
        TvOverlay.Sources -> TvSourcesScreen(
            onBack = { onOverlay(TvOverlay.Settings) },
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
    data class Series(val channel: Channel) : TvOverlay
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
) {
    // Focus starts in the bar, so the first thing a viewer sees is where they are. An app
    // that opens with nothing focused leaves the remote apparently dead.
    val barFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { barFocusRequester.tryRequestFocus() }

    // Back walks to the first tab, and only then leaves the app.
    //
    // The alternative — back exiting from wherever the viewer happens to be — is how a
    // television app loses somebody three tabs deep with one stray press. Focus returns to
    // the bar with it, so the remote is visibly somewhere rather than apparently dead.
    BackHandler(enabled = selectedTab != TvTab.SEARCH.ordinal) {
        onSelectTab(TvTab.SEARCH.ordinal)
        barFocusRequester.tryRequestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        TvTopBar(
            selectedTab = selectedTab,
            onSelect = onSelectTab,
            focusRequester = barFocusRequester,
            onEnterContent = { contentFocusRequester.tryRequestFocus() },
            onOpenSettings = onOpenSettings,
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
    var onGear by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_PADDING, vertical = 20.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (val action = tvBarAction(event.key, TvBarState(selectedTab, onGear), lastIndex)) {
                    is TvBarAction.Move -> {
                        onGear = action.state.onGear
                        if (action.state.selectedTab != selectedTab) onSelect(action.state.selectedTab)
                        true
                    }

                    TvBarAction.EnterContent -> {
                        onGear = false
                        onEnterContent()
                        true
                    }

                    TvBarAction.OpenSettings -> {
                        onOpenSettings()
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
            // tabs rather than the gear. While focus is down in the content the selected tab
            // stays legible but recedes, which is how a viewer tells where a press will land.
            val isBarFocused = isFocused && !onGear

            if (tab.isIconOnly) {
                IconTab(
                    icon = Icons.Filled.Search,
                    contentDescription = stringResource(tab.labelRes),
                    isSelected = index == selectedTab,
                    isBarFocused = isBarFocused,
                )
            } else {
                TextTab(
                    label = stringResource(tab.labelRes),
                    isSelected = index == selectedTab,
                    isBarFocused = isBarFocused,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        BarIcon(
            icon = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.tv_settings),
            isHighlighted = onGear && isFocused,
        )
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
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            isSelected && isBarFocused -> 1f
            isSelected -> SELECTED_ALPHA
            else -> IDLE_ALPHA
        },
        label = "iconTabAlpha",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
) {
    // Not focusable, by design. The bar above owns focus and decides where along itself the
    // remote is resting; this only has to look like the answer.
    Box(
        modifier = Modifier
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

/**
 * Requests focus, tolerating there being nothing to focus.
 *
 * A content area can legitimately hold no focusable at all — an empty catalogue, a spinner,
 * a line of explanatory text — and [FocusRequester] treats that as a programming error and
 * throws. Here it is an ordinary state, and the right behaviour is to leave focus where it
 * is rather than to bring the app down.
 */
private fun FocusRequester.tryRequestFocus() {
    runCatching { requestFocus() }
}
