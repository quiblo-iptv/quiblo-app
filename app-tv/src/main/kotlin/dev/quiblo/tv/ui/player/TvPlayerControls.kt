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

package dev.quiblo.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiblo.core.model.AspectRatioMode
import dev.quiblo.core.model.SeekInterval
import dev.quiblo.tv.R

/**
 * What the player offers, as buttons the remote walks through.
 *
 * **This used to be a readout, and the note above it said so proudly.** The argument was that
 * the remote already drives playback directly, so focusable buttons would only add a layer of
 * navigation between a viewer and a press they can already make. That holds for exactly the
 * five things a remote has keys for — play, seek, and channel up and down — and it is why
 * everything past those five had to be smuggled onto a key that already meant something else:
 * subtitles arrived on a second press of Down, and picture fit on Up, on a film, where zapping
 * happened not to be using it. A key map with no keys left cannot take another feature, and
 * next and previous episode are two more.
 *
 * So the transport is drawn where a phone draws it — the middle of the screen, play in the
 * centre, seeking either side of it, and the episode steps outside those — and the rest sit in
 * a row underneath. The keys all still work with nothing on screen; this is a second way in,
 * not a replacement, and [TvPlayerScreen]'s key map is what keeps the two agreeing.
 *
 * Nothing here changes size when it takes focus. A focused control that grows reports a
 * rectangle that changes every frame while the animation runs, and this project has already
 * spent four wrong answers on what that does to a list — see `TvBrowseScrollStabilityTest`.
 * These sit in no list, so it would cost nothing today and would be a trap set for whoever
 * puts them in one.
 */
@Composable
@Suppress("LongParameterList")
internal fun TvPlayerControls(
    state: TvControlsState,
    actions: TvControlActions,
    /** Where the remote lands when the controls appear: play/pause, always. */
    playPauseFocus: FocusRequester,
) {
    /*
     * Down and up between the two rows are stated, not left to geometry.
     *
     * Compose finds a focus target in a direction by looking at where things are, and these two
     * rows barely overlap: the transport is centred and the options sit against the left margin,
     * so which button is "below" the play button depends on how wide the row happened to come
     * out. "Down goes to the options row" is the behaviour that was asked for, so it is written
     * down rather than inferred from a layout that will change the first time a button is added.
     */
    val firstOptionFocus = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        TransportRow(
            state = state,
            actions = actions,
            playPauseFocus = playPauseFocus,
            firstOptionFocus = firstOptionFocus,
            modifier = Modifier.align(Alignment.Center),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(SCREEN_MARGIN),
        ) {
            Text(
                text = state.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Progress(state)

            OptionsRow(
                state = state,
                actions = actions,
                firstOptionFocus = firstOptionFocus,
                playPauseFocus = playPauseFocus,
            )
        }
    }
}

/**
 * Play, the two seeks, and the two episode steps.
 *
 * Every button carries where the four arrows go from it, rather than letting Compose work it
 * out from the layout. **That was measured, not assumed:** with the geometry left to decide,
 * Down from the play button landed on picture fit — the options row sits against the left
 * margin and the transport is centred, so the button nearest below the middle one is the row's
 * *last* — and Right off the end of the options row jumped back up to the previous-episode
 * button, because it was the nearest thing to the right of anywhere. Both are what a search
 * over rectangles is for, and neither is what a viewer means by pressing down.
 *
 * `FocusRequester.Cancel` at the ends stops a press wrapping somewhere surprising. A viewer at
 * the left of the row pressing left should find nothing, which is what the end of a row is.
 */
@Composable
private fun TransportRow(
    state: TvControlsState,
    actions: TvControlActions,
    playPauseFocus: FocusRequester,
    firstOptionFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val buttons = buildList {
        // Only where there is one. A series' first episode has nothing before it, and a
        // control that is present and does nothing is the shape this project deletes.
        if (state.hasPreviousEpisode) {
            add(
                PlayerButton(
                    icon = Icons.Filled.SkipPrevious,
                    label = stringResource(R.string.tv_a11y_previous_episode),
                    onClick = actions.previousEpisode,
                ),
            )
        }
        if (state.isSeekable) {
            add(
                PlayerButton(
                    icon = state.seekInterval.rewindIcon(),
                    label = stringResource(R.string.tv_a11y_rewind, state.seekInterval.seconds),
                    onClick = { actions.skip(-1) },
                ),
            )
        }
        add(
            PlayerButton(
                icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = stringResource(if (state.isPlaying) R.string.tv_a11y_pause else R.string.tv_a11y_play),
                onClick = actions.playPause,
                size = PLAY_BUTTON_SIZE,
                focusRequester = playPauseFocus,
            ),
        )
        if (state.isSeekable) {
            add(
                PlayerButton(
                    icon = state.seekInterval.forwardIcon(),
                    label = stringResource(R.string.tv_a11y_forward, state.seekInterval.seconds),
                    onClick = { actions.skip(1) },
                ),
            )
        }
        if (state.hasNextEpisode) {
            add(
                PlayerButton(
                    icon = Icons.Filled.SkipNext,
                    label = stringResource(R.string.tv_a11y_next_episode),
                    onClick = actions.nextEpisode,
                ),
            )
        }
    }

    ButtonRow(
        buttons = buttons,
        // Nothing is above the transport row, and down is always the options row whichever
        // button the remote is on.
        up = FocusRequester.Cancel,
        down = firstOptionFocus,
        modifier = modifier,
    )
}

/**
 * Subtitles, audio and picture fit.
 *
 * Subtitles and audio open the same panel at different places rather than being one button
 * called "tracks". They are two questions a viewer arrives with — "why can I not understand
 * this" and "why can I not read this" — and answering both with one control means whichever
 * they wanted is a scroll away from where the panel opened.
 *
 * Each appears only when the panel actually has that section, which is the same rule the old
 * hint line followed: the menu drops audio when a stream has one track, and a button that
 * opens a heading with nothing under it is worse than no button.
 */
@Composable
private fun OptionsRow(
    state: TvControlsState,
    actions: TvControlActions,
    firstOptionFocus: FocusRequester,
    playPauseFocus: FocusRequester,
) {
    /*
     * Built as a list because the row's first button is not a fixed one — subtitles and audio
     * each appear only where there is something to choose — and "down from the transport lands
     * on whichever is first" has to hold for all four combinations. Writing the modifier onto
     * one of the three by name would be right in three cases and silently wrong in the fourth.
     */
    val options = buildList {
        if (state.hasSubtitleChoice) {
            add(
                PlayerButton(
                    icon = Icons.Filled.ClosedCaption,
                    label = stringResource(R.string.tv_player_subtitles),
                    onClick = actions.openSubtitles,
                    size = OPTION_BUTTON_SIZE,
                    focusRequester = firstOptionFocus,
                ),
            )
        }
        if (state.hasAudioChoice) {
            add(
                PlayerButton(
                    icon = Icons.Filled.Audiotrack,
                    label = stringResource(R.string.tv_player_audio),
                    onClick = actions.openAudio,
                    size = OPTION_BUTTON_SIZE,
                    // Only when it is first — subtitles take the requester when they are
                    // present, and two nodes sharing one is the state that throws.
                    focusRequester = firstOptionFocus.takeIf { !state.hasSubtitleChoice },
                ),
            )
        }
        add(
            PlayerButton(
                icon = Icons.Filled.AspectRatio,
                label = stringResource(R.string.tv_a11y_aspect, state.aspectRatioMode.name),
                onClick = actions.cycleAspect,
                size = OPTION_BUTTON_SIZE,
                // Picture fit is the only one of the three that is always drawn, so on a film
                // with a single audio track it is the row, and the row's first button.
                focusRequester = firstOptionFocus.takeIf {
                    !state.hasSubtitleChoice && !state.hasAudioChoice
                },
            ),
        )
    }

    ButtonRow(
        buttons = options,
        up = playPauseFocus,
        // The bottom of the screen. Down from here is nothing, not a wrap round to the
        // transport row above.
        down = FocusRequester.Cancel,
        modifier = Modifier.padding(top = 14.dp),
    ) {
        // Live is the one case with a control the screen cannot draw: the channel keys. It
        // keeps its sentence, because there is no button that would do the same thing.
        if (state.isLive) {
            Text(
                text = stringResource(R.string.tv_player_hint_zap),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * A row of circles, wired so the remote cannot leave it by accident.
 *
 * Both rows are built through this rather than each writing its own `forEachIndexed`, because
 * the wiring is the part that was wrong and one copy of it is one place to be wrong.
 */
@Composable
private fun ButtonRow(
    buttons: List<PlayerButton>,
    up: FocusRequester,
    down: FocusRequester,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { index, button ->
            CircleButton(
                icon = button.icon,
                label = button.label,
                onClick = button.onClick,
                size = button.size,
                modifier = Modifier
                    .then(
                        button.focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                    )
                    .focusProperties {
                        this.up = up
                        this.down = down
                        if (index == 0) left = FocusRequester.Cancel
                        if (index == buttons.lastIndex) right = FocusRequester.Cancel
                    },
            )
        }

        trailing()
    }
}

/** One button, described before it is drawn, so a row can be wired by position. */
private data class PlayerButton(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val size: Dp = BUTTON_SIZE,
    val focusRequester: FocusRequester? = null,
)

/** The bar and the clock, or the word LIVE where neither means anything. */
@Composable
private fun Progress(state: TvControlsState) {
    if (state.isSeekable && state.durationMillis > 0L) {
        val progress = (state.positionMillis.toFloat() / state.durationMillis).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(BAR_HEIGHT)
                    .background(Color.White, RoundedCornerShape(2.dp)),
            )
        }
        Text(
            text = "${state.positionMillis.asClock()} / ${state.durationMillis.asClock()}",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else if (state.isLive) {
        Text(
            text = stringResource(R.string.tv_player_live),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * One transparent circle with an icon in it.
 *
 * Transparent so the picture stays readable behind the controls, and filled solid white the
 * moment it takes focus: on a panel across a room a subtle highlight is no highlight, and this
 * is the same inversion `DetailButton` uses everywhere else in the app.
 *
 * [label] is both the spoken description and the only description — an icon says nothing to
 * TalkBack, which runs on Android TV as it does on a phone.
 */
@Composable
private fun CircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = BUTTON_SIZE,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(size)
            .background(
                color = if (isFocused) Color.White else Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.35f),
                shape = CircleShape,
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            // Already spoken by the box above. Repeating it here makes TalkBack say it twice.
            contentDescription = null,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(size * ICON_FRACTION),
        )
    }
}

/**
 * Everything the controls draw from.
 *
 * A value rather than a dozen parameters, for the reason [KeyContext] is one: this grew a field
 * per feature, and a call site passing nine booleans stops saying which is which long before it
 * stops compiling.
 */
internal data class TvControlsState(
    val title: String,
    val isPlaying: Boolean,
    val isSeekable: Boolean,
    val isLive: Boolean,
    val positionMillis: Long,
    val durationMillis: Long,
    val seekInterval: SeekInterval,
    val aspectRatioMode: AspectRatioMode,
    val hasAudioChoice: Boolean,
    val hasSubtitleChoice: Boolean,
    val hasNextEpisode: Boolean,
    val hasPreviousEpisode: Boolean,
)

/** What the buttons do. The screen supplies them; this file only decides where they sit. */
internal data class TvControlActions(
    val playPause: () -> Unit,
    val skip: (Int) -> Unit,
    val nextEpisode: () -> Unit,
    val previousEpisode: () -> Unit,
    val openAudio: () -> Unit,
    val openSubtitles: () -> Unit,
    val cycleAspect: () -> Unit,
)

/**
 * The seek icons carry their own number, so the button says how far it jumps without a label.
 *
 * Material has a glyph for 5, 10 and 30 and none for 15, which the settings screen offers. That
 * one borrows the 10 rather than falling back to a bare arrow: the icon is a picture of "go
 * back", the exact figure is on the spoken label and in Settings where it was chosen, and a
 * plain arrow beside a numbered one reads as a different control rather than the same one.
 */
private fun SeekInterval.rewindIcon(): ImageVector = when (this) {
    SeekInterval.FIVE -> Icons.Filled.Replay5
    SeekInterval.THIRTY -> Icons.Filled.Replay30
    else -> Icons.Filled.Replay10
}

private fun SeekInterval.forwardIcon(): ImageVector = when (this) {
    SeekInterval.FIVE -> Icons.Filled.Forward5
    SeekInterval.THIRTY -> Icons.Filled.Forward30
    else -> Icons.Filled.Forward10
}

/**
 * The panel has 444dp of usable height after overscan, so these are sized against that rather
 * than against a phone: the transport row is a tenth of it and the options row less.
 */
private val BUTTON_SIZE = 52.dp
private val PLAY_BUTTON_SIZE = 68.dp
private val OPTION_BUTTON_SIZE = 44.dp
private val BUTTON_GAP = 18.dp
private val SCREEN_MARGIN = 48.dp
private val BAR_HEIGHT = 4.dp
private const val ICON_FRACTION = 0.5f

private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

internal fun Long.asClock(): String {
    val total = this / MILLIS_PER_SECOND
    val hours = total / SECONDS_PER_HOUR
    val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
