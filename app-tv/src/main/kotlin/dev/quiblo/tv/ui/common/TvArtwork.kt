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

package dev.quiblo.tv.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/**
 * Pictures that would not load this session, so a tile scrolled back to does not ask again.
 *
 * **A provider's artwork URL is no promise that there is a picture behind it.** Panels list
 * covers on hosts that died years ago, paths that 404, and files that are not images, and a
 * tile used to take the URL's word for it: the provider's link was non-blank, so it won, and
 * the poster the metadata service had already found for the same title was never looked at.
 * The viewer saw a grey box where every other app showed a cover.
 *
 * Remembered for the session rather than per tile, because a dead host costs a connection
 * timeout and a row scrolled back and forth would otherwise pay it every time. Only ever used
 * to *skip to a later candidate* — the last one is always tried — so a picture that failed
 * while the Wi-Fi was waking is never the reason a tile shows nothing.
 */
internal object BrokenArtwork {

    private val urls = object : LinkedHashMap<String, Unit>(0, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Unit>) = size > REMEMBERED_MAX
    }

    fun contains(url: String): Boolean = synchronized(urls) { url in urls }

    fun add(url: String) {
        synchronized(urls) { urls[url] = Unit }
    }

    /** A viewer asking for the title again — "Reload info" — is asked again, all of it. */
    fun forget(candidates: Collection<String>) {
        synchronized(urls) { candidates.forEach(urls::remove) }
    }

    private const val LOAD_FACTOR = 0.75f
    private const val REMEMBERED_MAX = 2_000
}

/**
 * The pictures a title could be shown with, best first, and which of them to try now.
 *
 * The provider's own artwork, then the metadata service's — in that order, because what the
 * panel sends is what the viewer's other devices show. When one fails to load, [failed] moves
 * to the next, which is the retry: a dead provider link becomes the TMDB poster rather than a
 * placeholder. When the last fails too, [url] is null and the caller draws its placeholder.
 */
@Stable
internal class ArtworkChain(private val candidates: List<String>) {

    /** Failed while this chain was on screen. Snapshot state, so a failure recomposes. */
    private val failedHere = mutableStateListOf<String>()

    /**
     * The picture to load now: the first one that has not failed here, skipping any that failed
     * earlier in the session — unless it is the last one left, which is always given its chance.
     */
    val url: String?
        get() {
            val untried = candidates.filterNot(failedHere::contains)
            return untried.firstOrNull { !BrokenArtwork.contains(it) } ?: untried.lastOrNull()
        }

    /** Says [url] did not load. The next candidate, if there is one, is tried at once. */
    fun failed(url: String) {
        BrokenArtwork.add(url)
        if (url !in failedHere) failedHere += url
    }
}

/**
 * An [ArtworkChain] over [urls], blank and repeated ones dropped.
 *
 * A new chain whenever the candidates change — a metadata answer arriving with a poster the
 * tile did not have is a new chance, not a continuation of the old one's failures.
 *
 * @param retry null, or a value that changes each time every candidate should be tried again
 *   from the top, including ones the session has written off. "Reload info" passes its answer:
 *   a viewer pressing it is asking for exactly that.
 */
@Composable
internal fun rememberArtworkChain(vararg urls: String?, retry: Any? = null): ArtworkChain =
    rememberArtworkChain(urls.asList(), retry)

/** The same, over a list. */
@Composable
internal fun rememberArtworkChain(urls: List<String?>, retry: Any? = null): ArtworkChain {
    val candidates = urls.mapNotNull { url -> url?.takeIf(String::isNotBlank) }.distinct()
    return remember(candidates, retry) {
        if (retry != null) BrokenArtwork.forget(candidates)
        ArtworkChain(candidates)
    }
}
