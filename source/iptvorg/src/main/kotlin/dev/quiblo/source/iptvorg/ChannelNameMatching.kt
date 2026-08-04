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

package dev.quiblo.source.iptvorg

/**
 * Reduces a channel name to something two lists can be compared on.
 *
 * **This never changes what the app displays.** Channel names are rendered exactly as the
 * provider sent them. This exists only to build a lookup key.

 * The problem it solves: a playlist calls a channel `UK| BBC One HD ᴴᴰ` and the reference
 * list calls it `BBC One`. Neither spelling is wrong, and a literal comparison finds
 * nothing for the great majority of real playlists — which would make the whole feature
 * look broken rather than partial.
 *
 * The same function builds the index and queries it, which is the only property that
 * actually matters here: whatever it does, it must do identically on both sides.
 */
fun iptvOrgMatchKey(name: String): String = name
    .lowercase()
    // Country and language prefixes: "UK: ", "|AR| ", "US - ". Stripped before anything
    // else, because the separator characters they rely on are removed a step later.
    .replace(LEADING_REGION_TAG, " ")
    .replace(BRACKETED, " ")
    .replace(QUALITY_MARKERS, " ")
    // Everything that is not a letter or a digit becomes a gap, then gaps disappear
    // entirely. "Sky Sports F1" and "sky-sports-f1" are the same channel, and deciding
    // whether a hyphen is a space or nothing is a question with no right answer.
    .replace(NON_ALPHANUMERIC, " ")
    .replace(WHITESPACE, "")
    .trim()

/**
 * A leading region or language tag: `UK:`, `|AR|`, `[DE]`, `US - `.
 *
 * Bounded to four characters so it cannot eat a real first word, and the accepted
 * separators are deliberately narrow. A bare hyphen is *not* one of them: `bbc-one` is a
 * channel name written with hyphens for spaces, and treating its first three letters as a
 * country code would strip the brand and leave "one" to match whatever else ends that way.
 * A spaced dash — `US - CNN` — is unambiguous and is accepted.
 */
private val LEADING_REGION_TAG = Regex(
    """^\s*(?:[|\[(]\s*[a-z]{2,4}\s*[|\])]|[a-z]{2,4}\s*[:|]|[a-z]{2,4}\s+[-–]\s+)\s*""",
)

private val BRACKETED = Regex("""[\[(][^\])]*[\])]""")

/**
 * Resolution and stream-quality markers.
 *
 * A reference list names a channel; a playlist names a *feed* of it, and the difference is
 * almost always one of these words. `hd` is included at the cost of mangling a channel
 * genuinely called "HD" — a trade worth taking, since the alternative is failing to match
 * the single most common suffix in IPTV playlists.
 */
private val QUALITY_MARKERS = Regex(
    "\\b(4k|8k|uhd|fhd|shd|hd|sd|hq|hevc|h ?26[45]|x ?26[45]|1080p?|720p?|480p?|2160p?|" +
        "raw|backup|alt|vip|multi|plus1)\\b",
)

/** Superscript "ᴴᴰ" and friends survive the ASCII markers above, so they go with the rest. */
private val NON_ALPHANUMERIC = Regex("""[^a-z0-9]+""")

private val WHITESPACE = Regex("""\s+""")
