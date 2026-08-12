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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * When are two provider titles the same title?
 *
 * `TmdbTest` covers [cleanedForSearch] thoroughly and **every one of its assertions is about
 * producing a good query string.** Nothing there asserts identity, and that gap is why #024
 * survived: `cleanedForSearch` strips bracketed groups whole, a provider year is almost always
 * bracketed, so `Dune (1984)` and `Dune (2021)` cleaned to one string and the metadata cache
 * filed two different films under one key. The first fetched won, permanently, and the other
 * film showed its poster and its plot.
 *
 * So these tests assert the opposite property to that file's: not "does this search well" but
 * **"do these two titles stay apart"**. They are the first entries in the fixture corpus
 * `014` asks grouping to be built against, arriving a release early because the cache needed
 * them first.
 *
 * The rule being asserted throughout: **separation is cheap and a false merge is not.** Two
 * keys for one film costs a duplicate fetch. One key for two films shows a viewer the wrong
 * film, silently, and never corrects itself.
 */
@DisplayName("title identity")
class TitleIdentityTest {

    @Test
    @DisplayName("the two Dunes are not the same title")
    fun `separates a remake from its original`() {
        // The governing failure of 014, and current behaviour before #024.
        assertNotEquals("Dune (1984)".titleIdentity(), "Dune (2021)".titleIdentity())
    }

    @Test
    @DisplayName("why the year cannot be left to the cleaner — the defect, pinned")
    fun `the search string alone cannot tell two films apart`() {
        // This asserts the *bug*, on purpose, and it must keep passing.
        //
        // `cleanedForSearch` is a query builder and it is right to throw the year away: TMDB
        // takes the year as its own parameter and a year inside the query text only makes
        // the search worse. The fault was never in this function — it was in using its
        // output as an identity. Pinning that here means the next person to reach for
        // `cleanedForSearch()` as a key finds out in the test suite rather than on a panel.
        assertEquals("Dune (1984)".cleanedForSearch(), "Dune (2021)".cleanedForSearch())
    }

    @Test
    fun `reads the year whether or not the provider bracketed it`() {
        // Three of 014's four opening rows share a key and the fourth does not, decided
        // entirely by whether the provider used brackets. That is the wrong split and the
        // wrong merge in one example, and it is what pulling the year out of the raw title
        // rather than out of the cleaned one fixes.
        val bracketed = "Interstellar (2014) [AR]".titleIdentity()
        val bare = "Interstellar 2014 1080p".titleIdentity()

        assertEquals(bracketed, bare)
        assertEquals(2014, bracketed.year)
    }

    @Test
    @DisplayName("the same film at four qualities is one title")
    fun `merges what differs only in decoration`() {
        val identities = listOf(
            "Interstellar (2014) [AR]",
            "Interstellar 2014 1080p",
            "INTERSTELLAR - 2014 - 4K",
            "|EN| Interstellar 2014 WEB-DL x265",
        ).map { it.titleIdentity() }

        assertEquals(1, identities.distinct().size, "these are one film at four qualities")
    }

    @Test
    fun `a title with no year keeps its own identity`() {
        // Not folded into any dated title. A provider that supplies no year is telling us
        // nothing, and nothing is not a match — it is an absence, and the safe reading of an
        // absence is that this is a title we cannot place.
        val undated = "Dune".titleIdentity()

        assertEquals(NO_YEAR, undated.year)
        assertNotEquals(undated, "Dune (1984)".titleIdentity())
        assertNotEquals(undated, "Dune (2021)".titleIdentity())
    }

    @Test
    @DisplayName("a resolution is not a year")
    fun `does not take 1080 for a release date`() {
        assertEquals(NO_YEAR, "Movie 1080p".titleIdentity().year)
    }

    @Test
    @DisplayName("titles that were already separate stay separate")
    fun `keeps the rows the cleaner already got right`() {
        // 016's danger table. The cleaner gets four of six right on its own, and #024 must
        // not trade its one failure for a new one — so these are regression cover, not new
        // behaviour.
        assertNotEquals("The Office US".titleIdentity(), "The Office UK".titleIdentity())
        assertNotEquals("Rambo 2".titleIdentity(), "Rambo II".titleIdentity())
        assertNotEquals("Batman".titleIdentity(), "Batman Begins".titleIdentity())
    }

    @Test
    fun `keeps merging punctuation variants of one title`() {
        assertEquals("Spider-Man".titleIdentity(), "Spider Man".titleIdentity())
    }

    @Test
    @DisplayName("case and spacing are decoration, not identity")
    fun `ignores what the provider shouted`() {
        assertEquals("interstellar   2014".titleIdentity(), "INTERSTELLAR (2014)".titleIdentity())
    }

    @Test
    @DisplayName("a film whose title is a year keeps it")
    fun `does not reduce 2012 to nothing`() {
        // Taking the year out of the words is right until the words are only a year. `2012`
        // and `1917` are films; an identity of `""` would mark them "not worth looking up"
        // and they would never be enriched again — a silent loss, which is the failure mode
        // this whole item is about.
        assertEquals("2012", "2012".titleIdentity().searchTitle)
        assertEquals("1917", "1917 (2019)".titleIdentity().searchTitle)
    }

    @Test
    @DisplayName("a number in a title is not a release date, but it may be read as one")
    fun `keeps a numbered sequel apart from its original`() {
        // `yearInTitle` reads 2049 out of "Blade Runner 2049" and always has — that predates
        // #024 and is already what narrows the search. What matters here is only that the two
        // films stay apart, which they do: one carries the number, the other does not.
        assertNotEquals("Blade Runner".titleIdentity(), "Blade Runner 2049".titleIdentity())
    }

    @Test
    fun `a title that is nothing but decoration has no identity`() {
        // Never sent, never cached. Blank is how the repository already recognises "there is
        // nothing here worth looking up", and the identity has to preserve that signal
        // rather than turn it into a key that a hundred junk titles would share.
        assertEquals("", "|AR| [1080p] HD".titleIdentity().searchTitle)
        assertEquals("", "مسلسل الاختيار".titleIdentity().searchTitle)
    }
}
