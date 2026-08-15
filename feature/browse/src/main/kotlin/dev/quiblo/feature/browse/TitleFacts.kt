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

package dev.quiblo.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quiblo.core.model.AuthorLabel
import dev.quiblo.core.model.TitleMetadata

/**
 * The metadata service's facts about one title: certificate, score, genres, who made it,
 * and who is in it.
 *
 * Lives here rather than in `:feature:vod` because films and series show the same facts and
 * both modules already depend on this one. Two copies would be two places to fix the day a
 * field is added, and only one of them would get fixed.
 */
@Composable
fun TitleFacts(
    metadata: TitleMetadata,
    modifier: Modifier = Modifier,
    /**
     * The year, when the screen has one worth showing and nowhere else to put it.
     *
     * Passed in rather than read off [metadata] because the provider's own year wins wherever
     * there is one, and only the caller knows whether the panel supplied it. Left null by
     * screens that already print a year on a line of their own — a fact stated twice reads as
     * two facts.
     */
    year: Int? = null,
) {
    if (metadata.isEmpty && year == null) return

    Column(modifier = modifier) {
        val chips = listOfNotNull(
            year?.toString(),
            metadata.ageRating?.takeIf { it.isNotBlank() },
            metadata.rating?.let { stringResource(R.string.title_facts_rating, it) },
            metadata.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
        )
        if (chips.isNotEmpty()) {
            FactLine(text = chips.joinToString(FACT_SEPARATOR))
        }

        metadata.author?.takeIf { it.isNotBlank() }?.let { author ->
            // A series has a creator, not a director. Naming it wrongly is a small error
            // that reads as the app not knowing what it is showing.
            val label = when (metadata.authorLabel) {
                AuthorLabel.DIRECTOR -> R.string.title_facts_director
                AuthorLabel.CREATOR -> R.string.title_facts_creator
            }
            FactLine(text = stringResource(label, author), topPadding = 4.dp)
        }

        metadata.topCast.takeIf { it.isNotEmpty() }?.let { cast ->
            FactLine(
                text = stringResource(R.string.title_facts_cast, cast.joinToString(", ")),
                topPadding = 4.dp,
            )
        }
    }
}

@Composable
private fun FactLine(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = topPadding),
    )
}

/**
 * A score, drawn to sit on top of poster artwork.
 *
 * Its own opaque plate rather than plain text, for the same reason the poster card has a
 * scrim: artwork is arbitrary, and a number drawn straight onto it is legible or not
 * depending on the film.
 */
@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.title_facts_rating, rating),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = BADGE_BACKGROUND_ALPHA),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private const val FACT_SEPARATOR = "  •  "
private const val BADGE_BACKGROUND_ALPHA = 0.6f
