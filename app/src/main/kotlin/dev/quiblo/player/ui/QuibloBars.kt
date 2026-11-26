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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiblo.designsystem.ProfileAvatar
import dev.quiblo.designsystem.QUIBLO_WORDMARK
import dev.quiblo.designsystem.QuibloDisplayFamily
import dev.quiblo.designsystem.QuibloMark
import dev.quiblo.designsystem.QuibloWordmark

/**
 * The top of every tab: the mark and the name, then Settings and whoever is watching.
 *
 * **The television's bar, folded to a phone's width.** The television leads with the lockup and
 * keeps the gear and the viewer's own face at the far end; this is the same row, in the same
 * order, for the same reasons — the lockup says which app this is without a word of chrome, and
 * the face answers "is this still mine" from arm's length the way it does from a sofa. What the
 * phone does not take is the tabs: a thumb wants them at the bottom of the screen, and that is
 * [QuibloTabBar]'s job.
 *
 * It sits on a wash of the room's own colour rather than on a surface, so the ambient light
 * behind the app reaches the top of the screen instead of stopping under an opaque strip — the
 * television's top-bar gradient, in the theme's colour rather than in black, so the light theme
 * does not grow a dark band.
 *
 * With [onBack] it is a screen's bar instead — a back arrow and a title in the display face —
 * which is what Settings is, being somewhere you go rather than a tab you are on.
 */
@Composable
internal fun QuibloTopBar(
    title: String?,
    onBack: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    profileName: String?,
    profileAvatar: String?,
    onSwitchProfile: () -> Unit,
    settingsLabel: String,
    switchProfileLabel: String,
    backLabel: String,
) {
    val room = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        room.copy(alpha = 0.96f),
                        room.copy(alpha = 0.82f),
                        room.copy(alpha = 0.40f),
                        Color.Transparent,
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel, tint = ink)
                }
                Text(
                    text = title.orEmpty(),
                    color = ink,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Lockup(
                    colour = ink,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                onOpenSettings?.let { open ->
                    IconButton(onClick = open) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = settingsLabel,
                            tint = ink.copy(alpha = IDLE_ALPHA),
                        )
                    }
                }

                // Null only in the frame between signing out and the chooser arriving; the bar is
                // on its way off screen then, so nothing stands in for it.
                if (profileName != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, ink.copy(alpha = RING_ALPHA), CircleShape)
                            .clickable(
                                onClickLabel = switchProfileLabel,
                                role = Role.Button,
                                onClick = onSwitchProfile,
                            ),
                    ) {
                        ProfileAvatar(name = profileName, avatar = profileAvatar, size = AVATAR_SIZE)
                    }
                }
            }
        }
    }
}

/** The mark and the drawn name. Decoration, read aloud as the name for whoever cannot see it. */
@Composable
private fun Lockup(colour: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = QUIBLO_WORDMARK },
    ) {
        QuibloMark(colour = colour, modifier = Modifier.size(LOCKUP_MARK_SIZE))
        Spacer(modifier = Modifier.width(LOCKUP_GAP))
        QuibloWordmark(height = LOCKUP_WORD_HEIGHT, colour = colour)
    }
}

/** One entry of the bottom bar, as the shell hands it over. */
internal data class TabBarItem(val label: String, val icon: ImageVector)

/**
 * The tabs, with one soft pill under the chosen one — the television's bar, at a thumb's reach.
 *
 * **One pill that travels, not one per tab.** The television draws its selection as a single
 * pill that slides from word to word, so where you are reads as a *place* rather than as a colour
 * changing on two labels at once. This is the same pill, the same alpha, on a spring rather than a
 * tween so a tap feels like it threw it there. The unchosen tabs sit back at the television's
 * idle alpha and the chosen one is drawn at full strength in the heavier weight.
 *
 * It floats: inset from the edges and rounded all round, on the room's raised surface with a
 * hairline edge, so the catalogue reads as running on underneath it rather than ending at a
 * toolbar. Tabs are equal widths, which is what lets the pill be placed by arithmetic rather than
 * by measuring each label.
 */
@Composable
internal fun QuibloTabBar(
    items: List<TabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val ink = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(BAR_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = BAR_FILL_ALPHA))
            .border(1.dp, ink.copy(alpha = EDGE_ALPHA), BAR_SHAPE)
            .padding(PILL_INSET),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(TAB_HEIGHT)) {
            val slot = maxWidth / items.size
            val chosen = selectedIndex.coerceIn(0, items.lastIndex)
            val pillX by animateDpAsState(
                targetValue = slot * chosen,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "pillX",
            )
            // Hidden until something is chosen: a selection off the end of the list (the frame
            // between a tab being switched off and the shell moving away from it) draws nothing.
            if (selectedIndex in items.indices) {
                Box(
                    modifier = Modifier
                        .offset(x = pillX)
                        .width(slot)
                        .fillMaxHeight()
                        .background(ink.copy(alpha = PILL_ALPHA), PILL_SHAPE),
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    Tab(
                        item = item,
                        isSelected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.width(slot).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tab(item: TabBarItem, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (isSelected) 1f else IDLE_LABEL_ALPHA, label = "tabAlpha")
    // The television lifts the chosen word while the remote rests on the bar. A finger has no
    // resting state, so the same lift is spent on the moment of the press instead.
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else if (isSelected) SELECTED_LIFT else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tabScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(PILL_SHAPE)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = item.label
                role = Role.Tab
                selected = isSelected
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = ink.copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = item.label,
            color = ink.copy(alpha = alpha),
            fontFamily = QuibloDisplayFamily,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
        )
    }
}

// The television's lockup proportions, at a phone's size.
private val LOCKUP_MARK_SIZE = 26.dp
private val LOCKUP_GAP = 8.dp
private val LOCKUP_WORD_HEIGHT = 21.dp
private val BAR_HEIGHT = 48.dp
private val AVATAR_SIZE = 32.dp

private val BAR_SHAPE = RoundedCornerShape(28.dp)
private val PILL_SHAPE = RoundedCornerShape(22.dp)
private val PILL_INSET = 6.dp
private val TAB_HEIGHT = 54.dp

/** The television's own numbers: pill fill, and how far back an unchosen label sits. */
private const val PILL_ALPHA = 0.14f
private const val IDLE_LABEL_ALPHA = 0.62f
private const val SELECTED_LIFT = 1.04f
private const val PRESSED_SCALE = 0.92f

private const val IDLE_ALPHA = 0.75f
private const val RING_ALPHA = 0.18f
private const val EDGE_ALPHA = 0.08f
private const val BAR_FILL_ALPHA = 0.92f
