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

package dev.quiblo.core.data

import dev.quiblo.core.database.dao.ChannelTitle
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.tmdb.NO_YEAR
import dev.quiblo.source.tmdb.cleanedForSearch
import dev.quiblo.source.tmdb.titleIdentity

/**
 * Somewhere to look up a title by its name rather than by its id.
 *
 * **Because nothing in this app stores a TMDB id against a channel.** A provider's catalogue and
 * a metadata service have no key in common: the join has always been a *cleaned title*, made by
 * the same rules on both sides, and the two callers that need it — the popular row and the
 * suggestions row — would otherwise each build their own version of it and drift.
 *
 * Built once per composition of a row and thrown away. On the account this project is tested
 * against that is a pass over sixty thousand names, which is why it belongs on a background
 * dispatcher and why the two callers share one rather than making two.
 */
class CatalogueIndex(titles: List<ChannelTitle>) {

    /**
     * Cleaned title and kind, to the catalogue rows that carry it.
     *
     * A list rather than one row, because a provider lists the same film four times in four
     * qualities and any of them will play. The first is taken, which is the provider's own
     * order and the same rule the metadata scan uses.
     */
    private val byTitle: Map<Key, List<Entry>> = titles
        .mapNotNull { row ->
            val kind = row.kind.toMediaKindOrNull() ?: return@mapNotNull null
            val identity = row.name.titleIdentity().takeIf { it.searchTitle.isNotBlank() }
                ?: return@mapNotNull null
            Entry(id = row.id, year = identity.year) to Key(identity.searchTitle, kind)
        }
        .groupBy({ it.second }, { it.first })

    /**
     * The catalogue row for [title], if the viewer's provider carries it.
     *
     * **The year narrows rather than decides.** A row matching it is preferred — `Dune (1984)`
     * and `Dune (2021)` are two films and this is the only thing that tells them apart — but a
     * provider that dates nothing is ordinary, and refusing to match on that basis would empty
     * the row for exactly the catalogues that need it most. A wrong-year match shows the film the
     * viewer owns under a name they recognise; no match shows nothing at all.
     */
    fun find(title: String, year: Int?, kind: MediaKind): Long? {
        val cleaned = title.cleanedForSearch().lowercase().takeIf { it.isNotBlank() } ?: return null
        val candidates = byTitle[Key(cleaned, kind)].orEmpty()
        if (candidates.isEmpty()) return null

        val dated = year?.takeIf { it != NO_YEAR }
        return candidates.firstOrNull { it.year == dated }?.id ?: candidates.first().id
    }

    private data class Key(val searchTitle: String, val kind: MediaKind)

    private data class Entry(val id: Long, val year: Int)
}

/**
 * `MediaKind.valueOf` without the throw: a stored kind this build does not know is skipped.
 *
 * One copy in the package rather than one per caller. There were two, and the second was written
 * because nobody saw the first — the same way this project came to have two `tryRequestFocus`.
 */
internal fun String.toMediaKindOrNull(): MediaKind? =
    MediaKind.entries.firstOrNull { it.name == this }
