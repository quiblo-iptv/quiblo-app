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

import dev.quiblo.core.database.dao.TitleVersionDao
import dev.quiblo.core.model.Channel
import dev.quiblo.core.model.MediaKind
import dev.quiblo.source.tmdb.titleIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * One of the ways a provider lists a title, and what to call it.
 *
 * [label] is what tells this listing from the others — "4K", "FHD MULTI-SUB" — and it is derived
 * rather than parsed: see [labelVersions].
 */
data class TitleVersion(val channel: Channel, val label: String)

/**
 * Every listing of one title, for the detail screen that lets a viewer pick between them.
 *
 * **This is the other half of the merge setting.** With merging on, the catalogue shows the
 * provider's first listing of a title and folds the rest away; without somewhere to find them
 * again that would be a feature that loses a viewer their 4K copy. The identity is the same three
 * columns the merge predicate groups on — cleaned title, year and kind — so what is offered here
 * is exactly what browse folded away.
 *
 * **Nothing is offered while merging is off.** Every listing is already its own row in every list
 * then, and a picker for rows a viewer can already see is a control with nothing to do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TitleVersionsRepository(
    private val dao: TitleVersionDao,
    private val mergeDuplicates: Flow<Boolean> = flowOf(false),
) {

    /**
     * The listings of [channel]'s title, itself included, or an empty list when there is nothing
     * to choose between.
     *
     * A single listing returns empty rather than a list of one: a picker offering the thing
     * already on screen is the hollow-feature shape this project deletes.
     */
    fun observeVersions(channel: Channel): Flow<List<TitleVersion>> =
        mergeDuplicates.flatMapLatest { merge ->
            val identity = channel.name.titleIdentity()
            if (!merge || identity.searchTitle.isBlank() || channel.kind == MediaKind.LIVE) {
                flowOf(emptyList())
            } else {
                dao.observeVersions(
                    sourceId = channel.sourceId,
                    kind = channel.kind.name,
                    searchTitle = identity.searchTitle,
                    identityYear = identity.year,
                ).map { rows ->
                    if (rows.size < 2) emptyList() else labelVersions(rows.map { it.toDomain() })
                }
            }
        }
}

/**
 * What to call each listing, given all of them.
 *
 * **The difference between the names, not a parse of them.** A provider writes quality and
 * language into the title in whatever way it likes — `Dune (2021) 4K`, `Dune (2021) [FHD] MULTI`,
 * `AR| Dune 2021` — and a list of known tags would be a list that is wrong for the next panel.
 * What is reliable is that the listings of one title share a beginning and differ at the end, so
 * the label is the end: the common prefix removed, tidied of the punctuation it was joined with.
 *
 * A listing whose name adds nothing — identical to another, or entirely inside the shared prefix —
 * keeps its whole name, because a chip with nothing on it cannot be chosen deliberately.
 */
internal fun labelVersions(channels: List<Channel>): List<TitleVersion> {
    val shared = channels.map { it.name }.commonPrefix()
    return channels.map { channel ->
        val remainder = channel.name.drop(shared.length)
            .trim()
            .trim { it in TRIM_CHARS }
            .dropUnmatchedBrackets()
        TitleVersion(channel = channel, label = remainder.ifBlank { channel.name })
    }
}

/**
 * Drops the brackets the cut left without a partner.
 *
 * The shared beginning is cut at a character, and a provider that writes the year one way in one
 * listing and another way in the next — `Avatar 2009` beside `Avatar (2009) HD` — shares only
 * `Avatar `. What is left of the second is `(2009) HD`; trimming takes the `(` off the front
 * because it is an edge, and leaves the `)` in the middle because it is not, so the chip reads
 * `2009) HD`.
 *
 * **The brackets that survive are the ones that still come in pairs.** A closing bracket with no
 * opener before it is the cut showing through, and so is an opener with no closer after it.
 * Removing them is the smallest edit that cannot invent a label: `(2009) HD` becomes `2009 HD`,
 * and a listing genuinely called `Dune (Extended)` keeps every character it had.
 */
private fun String.dropUnmatchedBrackets(): String {
    val unmatched = mutableSetOf<Int>()
    val openers = ArrayDeque<Int>()
    forEachIndexed { index, char ->
        when (char) {
            in BRACKET_OPENERS -> openers.addLast(index)
            in BRACKET_CLOSERS -> if (openers.isEmpty()) unmatched += index else openers.removeLast()
        }
    }
    unmatched += openers
    if (unmatched.isEmpty()) return this

    return filterIndexed { index, _ -> index !in unmatched }
        // Cutting a bracket out of the middle leaves the spaces that were either side of it.
        .replace(REPEATED_SPACE, " ")
        .trim()
        .trim { it in TRIM_CHARS }
}

/** The longest beginning every one of these shares, cut at a character rather than at a word. */
private fun List<String>.commonPrefix(): String {
    if (isEmpty()) return ""
    var prefix = first()
    for (name in drop(1)) {
        prefix = prefix.commonPrefixWith(name)
        if (prefix.isEmpty()) return ""
    }
    return prefix
}

/** What a provider joins a quality tag on with, and what is left dangling when one is cut off. */
private const val TRIM_CHARS = " -–|:.[]()_"

/** Bracket halves, in the pairs [dropUnmatchedBrackets] matches them up in. */
private const val BRACKET_OPENERS = "([{"
private const val BRACKET_CLOSERS = ")]}"

/** Two or more spaces, which is what removing a bracket from the middle of a name leaves. */
private val REPEATED_SPACE = Regex(" {2,}")
