/*
 * Vibrato — a free, open source IPTV player.
 * Copyright (C) 2026 The Vibrato Authors
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

package dev.vibrato.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibrato.tv.R
import dev.vibrato.tv.ui.live.TvLiveScreen
import dev.vibrato.tv.ui.sources.TvSourcesScreen

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
    var selectedTab by remember { mutableIntStateOf(TvTab.LIVE.ordinal) }

    // Focus starts in the bar, so the first thing a viewer sees is where they are. An app
    // that opens with nothing focused leaves the remote apparently dead.
    val barFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        TvTopBar(
            selectedTab = selectedTab,
            onSelect = { selectedTab = it },
            focusRequester = barFocusRequester,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = SCREEN_PADDING, end = SCREEN_PADDING, bottom = SCREEN_PADDING),
        ) {
            when (TvTab.entries[selectedTab]) {
                TvTab.LIVE -> TvLiveScreen(onChannelClick = { })
                TvTab.SOURCES -> TvSourcesScreen()
                else -> Placeholder(tab = TvTab.entries[selectedTab])
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
 * Focused and selected are separate states and both are visible at once. On a television
 * they routinely disagree: the remote can be pointing along the bar while the content
 * below still belongs to the tab you came from, and a design that shows only one of the
 * two leaves the viewer unable to tell what pressing centre will do.
 */
@Composable
private fun TvTopBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_PADDING, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        BarIcon(
            icon = Icons.Filled.Search,
            contentDescription = stringResource(R.string.tv_tab_search),
        )

        TvTab.entries.forEachIndexed { index, tab ->
            TextTab(
                label = stringResource(tab.labelRes),
                isSelected = index == selectedTab,
                onSelect = { onSelect(index) },
                modifier = if (index == selectedTab) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        BarIcon(
            icon = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.tv_settings),
        )
    }
}

/**
 * One tab.
 *
 * Selection follows focus — moving along the bar switches tab, as Google TV does, rather
 * than requiring a centre press to confirm. That is what makes a remote feel like it is
 * browsing rather than filling in a form.
 */
@Composable
private fun TextTab(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val alpha by animateFloatAsState(
        targetValue = when {
            isFocused -> 1f
            isSelected -> SELECTED_ALPHA
            else -> IDLE_ALPHA
        },
        label = "tabAlpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .onFocusChanged { if (it.isFocused) onSelect() }
            .focusable(interactionSource = interactionSource),
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

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.20f) else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (isFocused) 1f else IDLE_ALPHA),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Stand-in for a tab that has no screen yet.
 *
 * Drawn straight onto the background with no card behind it. A surface around the content
 * area boxes the screen in and reads as a panel inside a page; the reference has content
 * sitting directly on black, and so does this.
 */
@Composable
private fun Placeholder(tab: TvTab) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.tv_placeholder, stringResource(tab.labelRes)),
            color = Color.White.copy(alpha = IDLE_ALPHA),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/** Overscan margin. Televisions crop the edges of the frame, and many still do. */
private val SCREEN_PADDING = 48.dp
private val UNDERLINE_WIDTH = 28.dp
private val TAB_TEXT_SIZE = 20.sp
private const val SELECTED_ALPHA = 0.90f
private const val IDLE_ALPHA = 0.55f
