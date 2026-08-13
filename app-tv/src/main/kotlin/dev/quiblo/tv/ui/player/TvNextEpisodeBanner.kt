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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiblo.core.media.PlaybackStatus
import dev.quiblo.core.model.AutoNextDelay
import dev.quiblo.tv.R
import kotlinx.coroutines.delay

/**
 * The end of an episode, and the offer of the next one.
 *
 * **This is the closest thing this app has to a modal, and it is deliberately not one.** The
 * television has no dialogs at all — the pattern everywhere else is an inline reveal, and the
 * reason recorded against it is that the first modal built sets the shape of every modal after
 * it. Nothing here is modal in the way that matters: playback has already stopped, the banner
 * covers a corner rather than the picture, it cannot be arrived at by accident, and both ways
 * out are on screen. Back also leaves, and leaving cancels.
 *
 * It slides in from the right because it arrives on its own. Something that appears without
 * being asked for has to be seen arriving, or a viewer looks up at a countdown already at two
 * with no idea what started it.
 *
 * Focus lands on Play now, so a viewer who wants the next episode presses OK once and a viewer
 * who wants to read the credits presses left then OK. The countdown runs either way.
 */
@Composable
internal fun TvNextEpisodeBanner(
    isVisible: Boolean,
    delaySetting: AutoNextDelay,
    /** Which episode is being offered, in the words the series list uses. */
    episodeLabel: String,
    onPlayNext: () -> Unit,
    onStop: () -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(animationSpec = tween(SLIDE_MILLIS)) { it } + fadeIn(),
        exit = slideOutHorizontally(animationSpec = tween(SLIDE_MILLIS)) { it } + fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(BANNER_MARGIN),
            contentAlignment = Alignment.TopEnd,
        ) {
            Banner(
                delaySetting = delaySetting,
                episodeLabel = episodeLabel,
                onPlayNext = onPlayNext,
                onStop = onStop,
            )
        }
    }
}

/**
 * The card itself, and the count inside it.
 *
 * Separate from the animation so the countdown starts when the banner does. Composing it inside
 * `AnimatedVisibility` means it is created on the way in and disposed on the way out, so the
 * remaining seconds need no resetting: there is nothing left to reset.
 */
@Composable
private fun Banner(
    delaySetting: AutoNextDelay,
    episodeLabel: String,
    onPlayNext: () -> Unit,
    onStop: () -> Unit,
) {
    var remaining by rememberSaveable(delaySetting) { mutableIntStateOf(delaySetting.seconds) }
    val playFocus = remember { FocusRequester() }

    /*
     * One tick a second, and the last tick starts the episode.
     *
     * Keyed on `remaining` rather than run as a loop so that each second is its own suspension:
     * a loop would hold a coroutine across the whole count and would keep counting through a
     * recomposition that changed the setting underneath it.
     */
    LaunchedEffect(remaining, delaySetting) {
        if (!delaySetting.isAutomatic) return@LaunchedEffect
        if (remaining <= 0) {
            onPlayNext()
            return@LaunchedEffect
        }
        delay(ONE_SECOND_MILLIS)
        remaining -= 1
    }

    // AC-TV-02, and the failure three screens in this app have already had: something has to
    // hold focus the moment this appears or the remote looks dead.
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .widthIn(max = BANNER_MAX_WIDTH)
            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(14.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (delaySetting.isAutomatic) {
                stringResource(R.string.tv_player_next_episode_in, remaining)
            } else {
                stringResource(R.string.tv_player_next_episode_ready)
            },
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            // Polite rather than assertive: nothing has gone wrong, and a number that changes
            // every second must not interrupt whatever TalkBack is already saying.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )

        Text(
            text = episodeLabel,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BannerButton(
                label = stringResource(R.string.tv_player_next_episode_stop),
                onClick = onStop,
            )
            BannerButton(
                label = stringResource(R.string.tv_player_next_episode_play),
                onClick = onPlayNext,
                isPrimary = true,
                modifier = Modifier.focusRequester(playFocus),
            )
        }
    }
}

/**
 * Transparent until it takes focus, like everything else the player draws.
 *
 * Its own button rather than `DetailButton` because that one is sized for a detail screen's
 * action row and this one sits inside a corner card; sharing it would have meant a size
 * parameter on a control used in eleven other places to serve this one.
 */
@Composable
private fun BannerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .background(
                color = when {
                    isFocused -> Color.White
                    isPrimary -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.10f)
                },
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = if (isPrimary || isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * Whether the next episode should be offered at all.
 *
 * A plain function because this is the whole of when the banner appears, and every part of it
 * is a mistake worth a test: offering a next episode for a film, offering one at the end of a
 * series, offering one again after the viewer said no, and — the one that would be worst —
 * offering one while a stream is merely buffering rather than finished.
 *
 * [PlaybackStatus.ENDED] is the only status that means it. An error is not an ending: a stream
 * that failed halfway has its own screen with a Try again on it, and skipping to the next
 * episode instead would quietly lose the second half of the one being watched.
 *
 * **[playingId] is what stops the offer chasing its own tail.** Stepping to the next episode
 * replaces the request the instant the button is pressed, and the engine is still reporting the
 * *previous* episode's ending until the new one has been read out of the database and prepared —
 * a gap of one database read. For that gap the request is already the next episode, `hasNext` is
 * true of it, and the dismissal has been reset by the new request, so every other condition here
 * says yes and the banner slides straight back in for an episode that has not started. It is
 * short enough never to reach zero and long enough to see, and on a series watched through it
 * happens between every pair of episodes.
 *
 * So the ending has to belong to the episode being asked about. An episode's playback key is its
 * stream URL — see `PlayerViewModel.load` — which is exactly what [PlaybackState.item] carries
 * back.
 */
internal fun shouldOfferNextEpisode(
    request: TvPlaybackRequest,
    status: PlaybackStatus,
    /** The id of whatever the engine currently holds, from `PlaybackState.item`. */
    playingId: String?,
    isDismissed: Boolean,
): Boolean {
    if (isDismissed || status != PlaybackStatus.ENDED) return false
    val episode = request as? TvPlaybackRequest.Episode ?: return false
    return playingId == episode.streamUrl && episode.hasNext
}

private const val ONE_SECOND_MILLIS = 1_000L
private const val SLIDE_MILLIS = 320
private val BANNER_MARGIN = 40.dp
private val BANNER_MAX_WIDTH = 380.dp
