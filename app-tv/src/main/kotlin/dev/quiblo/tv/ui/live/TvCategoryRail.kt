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

package dev.quiblo.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiblo.core.model.Category
import dev.quiblo.tv.R

/**
 * The category picker for the Live list.
 *
 * A rail down the left rather than the dropdown the bug report suggested. On a remote a
 * dropdown costs three presses — open, move, close — and hides the choices until it is
 * opened, where a rail is one press to the left and shows what there is to pick. It also
 * leaves the channel list exactly as it was, which is what #002 asked for.
 *
 * **Selection follows a press, never focus.** Moving through the rail with the D-pad only
 * moves through it; the list changes when the viewer presses. Selection-following-focus is
 * what made the tab bar unusable before it was rebuilt, and the failure is worse here: a
 * rail that reloaded the list on every step would issue a query per category the remote
 * passed over.
 */
@Composable
fun TvCategoryRail(
    categories: List<Category>,
    selectedCategory: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // "All channels" is a category in the rail but not in the data — it is the absence
        // of a filter. First, because it is where a viewer starts and where they come back
        // to, and having to scroll up past the alphabet to clear a filter is a trap.
        item(key = ALL_KEY) {
            RailRow(
                label = stringResource(R.string.tv_category_all),
                count = categories.sumOf { it.itemCount },
                isSelected = selectedCategory == null,
                onClick = { onSelect(null) },
            )
        }

        items(items = categories, key = { it.title }) { category ->
            RailRow(
                label = category.displayTitle.takeIf { it != Category.UNGROUPED_TITLE }
                    ?: stringResource(R.string.tv_category_ungrouped),
                count = category.itemCount,
                isSelected = selectedCategory == category.title,
                // The provider's own title is what is selected, never the display name:
                // a renamed category still filters on what the panel calls it.
                onClick = { onSelect(category.title) },
            )
        }
    }
}

@Composable
private fun RailRow(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (isFocused || isSelected) 1f else IDLE_ALPHA),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
        )

        // The count is what tells a viewer whether a category is worth entering at all,
        // and a panel routinely returns some with a handful of channels and some with
        // thousands.
        Text(
            text = count.toString(),
            color = Color.White.copy(alpha = if (isFocused) 0.8f else 0.45f),
            fontSize = 13.sp,
        )
    }
}

/** Wide enough for a panel's category names, narrow enough to leave the list room. */
private val RAIL_WIDTH = 300.dp
private const val IDLE_ALPHA = 0.62f
private const val ALL_KEY = "__all__"
