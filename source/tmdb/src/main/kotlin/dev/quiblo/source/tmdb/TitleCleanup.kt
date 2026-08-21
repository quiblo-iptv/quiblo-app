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

/**
 * What makes two provider titles the same title.
 *
 * **Not the same question as [cleanedForSearch], and conflating the two was #024.** That
 * function builds a *query*, and it is right to strip the year: TMDB takes the year as its
 * own parameter, and a year inside the query text narrows nothing and matches worse. But the
 * metadata cache used its output as a *key*, and a key that has thrown the year away cannot
 * tell `Dune (1984)` from `Dune (2021)`. One row held both films, the first fetched won, and
 * the other film showed the winner's poster and plot for a fortnight at a time.
 *
 * So identity is the search string **plus the year, read from the raw title** — before the
 * brackets that usually carry it are stripped. That last detail is the whole of the fix:
 * reading the year off the cleaned string would find it only when the provider happened not
 * to bracket it, which is how three of `014`'s four opening rows shared a key and the fourth
 * did not.
 *
 * A [searchTitle] of `""` means there was nothing here worth looking up, and callers must
 * keep treating it as "do not ask" rather than as a key a hundred junk titles can share.
 */
fun String.titleIdentity(): TitleIdentity {
    val year = yearInTitle()
    val cleaned = cleanedForSearch().lowercase()
    return TitleIdentity(
        searchTitle = if (year == null) cleaned else cleaned.withoutYear(year),
        year = year ?: NO_YEAR,
    )
}

/**
 * The cleaned title with its year taken out of the words, now that it is held as a field.
 *
 * **A bracketed year is already gone and a bare one is not**, so without this the identity
 * of `Interstellar (2014)` is `interstellar` and the identity of `Interstellar 2014` is
 * `interstellar 2014` — one film, two rows, decided by a provider's punctuation. That is the
 * half of #024 the plan did not predict and the test found: the year has to leave the string
 * on both paths, not just the one the bracket stripper happens to cover.
 *
 * **Unless taking it out leaves nothing.** `2012` is a film, `1917` is a film, and a title
 * that is only a year is a title rather than a date — reducing it to blank would mark it "not
 * worth looking up" and it would never be enriched again. Keeping it costs at most that `2012`
 * carries its own year twice, which nothing can misread.
 *
 * Only the *identity* is stripped. [cleanedForSearch] is untouched, so the query TMDB actually
 * receives is exactly what it was: this changes what the cache calls a row, not what is asked.
 */
private fun String.withoutYear(year: Int): String {
    val stripped = replace(year.toString(), " ").replace(WHITESPACE, " ").trim()
    return stripped.ifBlank { this }
}

/**
 * A cleaned title and the year it was released, together.
 *
 * [year] is [NO_YEAR] rather than null when the provider supplied none, because this is a
 * database key before it is anything else and SQLite does not consider two nulls equal — a
 * nullable year column in a primary key would let every undated title insert a fresh row on
 * every visit, which is the cache failing open rather than a title staying separate.
 */
data class TitleIdentity(val searchTitle: String, val year: Int)

/**
 * The year of a title whose provider did not say.
 *
 * Not a match for anything dated. An absent year is an absence and not a wildcard: folding
 * an undated `Dune` into either dated one would be guessing which, and guessing wrong here
 * is the silent failure this whole mechanism exists to stop.
 */
const val NO_YEAR: Int = 0

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
        ")\\b|مترجم|مدبلج",
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
