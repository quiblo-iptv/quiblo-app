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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiblo.designsystem.QuibloColours
import dev.quiblo.designsystem.QuibloDisplayFamily
import dev.quiblo.designsystem.QuibloMark
import dev.quiblo.designsystem.QuibloWordmark
import dev.quiblo.tv.ui.common.onTap

/** The mark and the name, at the head of the bar. Decoration: the remote never rests on it. */
@Composable
internal fun TvBarLockup(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        QuibloMark(colour = QuibloColours.Paper, modifier = Modifier.size(LOCKUP_MARK_SIZE))
        Spacer(modifier = Modifier.width(LOCKUP_GAP))
        QuibloWordmark(height = LOCKUP_WORD_HEIGHT)
    }
}

/**
 * The tabs as words, with one soft pill under the selected one.
 *
 * **One pill that travels, rather than one per tab.** It slides to the next word as the remote
 * moves and lifts toward the viewer while the bar holds the remote, so where a press will land
 * reads as a place in space and not as a colour changing. It is drawn from where the words were
 * actually placed, so a hidden tab needs no arithmetic here.
 */
@Composable
internal fun TvBarTabs(
    tabs: List<TvTab>,
    selectedTab: Int,
    isBarFocused: Boolean,
    onTap: (TvTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var placed by remember { mutableStateOf(emptyMap<TvTab, PillBounds>()) }

    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        placed[TvTab.entries[selectedTab]]?.let { TravellingPill(it, isBarFocused) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TAB_GAP),
        ) {
            tabs.forEach { tab ->
                TabLabel(
                    label = stringResource(tab.labelRes),
                    isSelected = tab.ordinal == selectedTab,
                    isBarFocused = isBarFocused,
                    modifier = Modifier
                        .onPlaced { coordinates ->
                            val bounds = with(density) {
                                PillBounds(
                                    x = coordinates.positionInParent().x.toDp(),
                                    width = coordinates.size.width.toDp(),
                                    height = coordinates.size.height.toDp(),
                                )
                            }
                            if (placed[tab] != bounds) placed = placed + (tab to bounds)
                        }
                        .onTap { onTap(tab) },
                )
            }
        }
    }
}

private data class PillBounds(val x: Dp, val width: Dp, val height: Dp)

@Composable
private fun TravellingPill(bounds: PillBounds, isBarFocused: Boolean) {
    val x by animateDpAsState(bounds.x, label = "pillX")
    val width by animateDpAsState(bounds.width, label = "pillWidth")
    val fill by animateFloatAsState(if (isBarFocused) PILL_FOCUSED_ALPHA else PILL_ALPHA, label = "pillFill")
    val ring by animateFloatAsState(if (isBarFocused) 1f else 0f, label = "pillRing")
    val lift by animateFloatAsState(if (isBarFocused) FOCUSED_LIFT else 1f, label = "pillLift")

    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToPx(), 0) }
            .size(width = width, height = bounds.height)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .background(QuibloColours.Paper.copy(alpha = fill), CircleShape)
            .border(RING_WIDTH, QuibloColours.Paper.copy(alpha = ring), CircleShape),
    )
}

@Composable
private fun TabLabel(
    label: String,
    isSelected: Boolean,
    isBarFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (isSelected) 1f else IDLE_LABEL_ALPHA, label = "tabAlpha")
    val lift by animateFloatAsState(if (isSelected && isBarFocused) FOCUSED_LIFT else 1f, label = "tabLift")

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .padding(horizontal = PILL_PADDING_H, vertical = PILL_PADDING_V),
    ) {
        // The heavier weight, unseen, holds the width: choosing a tab must not move its neighbours.
        Text(
            text = label,
            color = Color.Transparent,
            fontSize = TAB_TEXT_SIZE,
            fontFamily = QuibloDisplayFamily,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = label,
            color = QuibloColours.Paper.copy(alpha = alpha),
            fontSize = TAB_TEXT_SIZE,
            fontFamily = QuibloDisplayFamily,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// The proposal's bar, in its own proportions, at a size that reads from a sofa.
private val LOCKUP_MARK_SIZE = 30.dp
private val LOCKUP_GAP = 9.dp
private val LOCKUP_WORD_HEIGHT = 26.dp
private val TAB_TEXT_SIZE = 20.sp
private val TAB_GAP = 4.dp
private val PILL_PADDING_H = 14.dp
private val PILL_PADDING_V = 7.dp
private val RING_WIDTH = 1.5.dp
private const val PILL_ALPHA = 0.14f
private const val PILL_FOCUSED_ALPHA = 0.24f
private const val IDLE_LABEL_ALPHA = 0.62f
private const val FOCUSED_LIFT = 1.06f
