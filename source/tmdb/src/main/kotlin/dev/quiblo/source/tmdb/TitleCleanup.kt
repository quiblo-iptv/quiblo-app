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

package dev.quiblo.source.tmdb

/**
 * Turns a provider's title into something worth sending to a metadata service.
 *
 * **This never changes what the app displays.** Channel and film names are rendered exactly
 * as the provider sent them, in whatever script they sent them in. Everything here exists
 * only to build a search query and a cache key.
 *
 * Provider titles are rarely just a title: they carry a language tag, a release marker, a
 * resolution, a codec, a year in brackets. Searching for `"|AR| The Matrix (1999) 4K PURE"`
 * matches nothing, and the failure is silent — so this cleanup is the difference between
 * enrichment working and appearing to be broken.
 */
fun String.cleanedForSearch(): String = this
    .replace(BRACKETED, " ")
    // Separators are normalised before the markers are matched, not after. `WEB-DL` is one
    // marker written with a hyphen, and a pattern looking for a word boundary never sees it
    // until the hyphen has become a space.
    .replace(NON_TITLE_CHARS, " ")
    .replace(QUALITY_MARKERS, " ")
    .replace(NON_LATIN, " ")
    .replace(WHITESPACE, " ")
    .trim()
    .replace(LEADING_LANGUAGE_TAG, "")
    .trim()
    // A title that is nothing but an uppercase tag is a tag, not a title. Searching for
    // "AR" returns a confident wrong answer, which is worse than returning none.
    //
    // The cost is real and accepted: a film actually titled "IT" is indistinguishable from
    // a language tag by this rule and will not be enriched. Titles in ordinary case — "Up",
    // "Her" — are unaffected, which is what makes the trade worth taking.
    .let { if (it.matches(BARE_TAG)) "" else it }

/** The year in a provider title, when it has one, for narrowing the search. */
fun String.yearInTitle(): Int? = YEAR.find(this)?.groupValues?.get(1)?.toIntOrNull()

private val BRACKETED = Regex("""[\[(][^\])]*[\])]""")

/**
 * Resolution, codec, release-source and audio markers.
 *
 * Deliberately generous. A leftover marker costs a failed search; an over-eager one costs a
 * word of the title, and the words listed here are not words film titles are made of.
 */
private val QUALITY_MARKERS = Regex(
    "(?i)\\b(" +
        "4k|8k|uhd|fhd|hd|sd|hq|hevc|h ?26[45]|x ?26[45]|av1|10 ?bit|" +
        "1080p?|720p?|480p?|2160p?|" +
        "pure|remux|blu ?ray|brrip|bdrip|webrip|web ?dl|hdrip|dvdrip|cam|hdts|" +
        "multi|dual|dubbed|subbed|vostfr|vo|vf|sub|atmos|dts|ac3|aac" +
        ")\\b",
)

/**
 * Anything outside plain ASCII.
 *
 * The service is queried in English, so an Arabic or Cyrillic title returns nothing useful
 * however it is spelled. A title that is *entirely* non-Latin therefore cleans to blank and
 * is never sent — right twice over, because it also spends none of the user's rate limit on
 * a request that could not have matched.
 *
 * Blunt on purpose. The alternative is transliteration, which needs a table per script and
 * would still guess wrong often enough to be worse than not asking.
 */
private val NON_LATIN = Regex("""[^\p{ASCII}]+""")

private val NON_TITLE_CHARS = Regex("""[|_\-–—:]+""")
private val WHITESPACE = Regex("""\s+""")
private val YEAR = Regex("""\b(19\d{2}|20\d{2})\b""")

/** A leading `AR`/`EN`/`FRA` language tag, stripped only when a title follows it. */
private val LEADING_LANGUAGE_TAG = Regex("""^[A-Z]{2,3}\s+(?=\S)""")
private val BARE_TAG = Regex("""^[A-Z]{2,3}$""")
